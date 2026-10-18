package com.acougue.modules.pagamento;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.dto.IniciarTransacaoDTO;
import com.acougue.modules.pagamento.dto.ResultadoLocalDTO;
import com.acougue.modules.pagamento.dto.TransacaoTerminalDTO;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.CobrancaTerminal;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.RetornoProvedor;
import com.acougue.repository.PagamentoVendaRepository;
import com.acougue.repository.TransacaoTerminalRepository;
import com.acougue.repository.VendaRepository;
import com.acougue.security.ContextoUsuario;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ciclo de vida de uma cobrança na maquininha:
 *
 *   iniciar ──► PENDENTE ──► APROVADA | NEGADA | CANCELADA | ERRO   (final, imutável)
 *
 * Quem tira do PENDENTE depende do modo do terminal:
 *  - API_NUVEM:    consultar() (polling do PDV) ou webhook da adquirente
 *  - AGENTE_LOCAL: o PDV repassa a resposta do agente TEF (registrarResultadoLocal)
 *  - MANUAL:       o operador confirma e digita o comprovante (registrarResultadoLocal)
 *
 * O fechamento da venda (PdvService) só aceita um pagamento de cartão
 * integrado se ele apontar para uma transação APROVADA desta mesma venda.
 */
@Slf4j
@Service
public class TransacaoTerminalService {

    private final TransacaoTerminalRepository transacaoRepo;
    private final PagamentoVendaRepository pagamentoRepo;
    private final VendaRepository vendaRepo;
    private final TerminalPagamentoService terminalService;
    private final CredenciaisPagamentoService credenciais;
    private final Map<ProvedorPagamento, ProvedorTerminalNuvem> provedores = new EnumMap<>(ProvedorPagamento.class);

    public TransacaoTerminalService(TransacaoTerminalRepository transacaoRepo,
                                    PagamentoVendaRepository pagamentoRepo,
                                    VendaRepository vendaRepo,
                                    TerminalPagamentoService terminalService,
                                    CredenciaisPagamentoService credenciais,
                                    List<ProvedorTerminalNuvem> implementacoes) {
        this.transacaoRepo = transacaoRepo;
        this.pagamentoRepo = pagamentoRepo;
        this.vendaRepo = vendaRepo;
        this.terminalService = terminalService;
        this.credenciais = credenciais;
        implementacoes.forEach(p -> provedores.put(p.provedor(), p));
    }

    @Transactional
    public TransacaoTerminalDTO iniciar(IniciarTransacaoDTO dto) {
        TerminalPagamento terminal = terminalService.buscar(dto.getTerminalId());
        if (!terminal.getAtivo()) {
            throw new BusinessException("A maquininha \"" + terminal.getNome() + "\" está desativada.");
        }
        boolean credito = "CREDITO".equals(dto.getTipo());
        if (credito && !terminal.getAceitaCredito()) {
            throw new BusinessException("Esta maquininha não está configurada para crédito.");
        }
        if (!credito && !terminal.getAceitaDebito()) {
            throw new BusinessException("Esta maquininha não está configurada para débito.");
        }
        int parcelas = credito ? dto.getParcelas() : 1;
        if (parcelas > terminal.getMaxParcelas()) {
            throw new BusinessException("Máximo de " + terminal.getMaxParcelas() + " parcelas nesta maquininha.");
        }

        Venda venda = vendaRepo.findById(dto.getVendaId())
                .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada: " + dto.getVendaId()));
        if (!"ABERTA".equals(venda.getStatus())) {
            throw new BusinessException("A venda #" + venda.getId() + " não está mais aberta.");
        }
        BigDecimal valor = dto.getValor().setScale(2, RoundingMode.HALF_UP);
        if (valor.compareTo(venda.getTotal()) > 0) {
            throw new BusinessException("O valor no cartão não pode ser maior que o total da venda.");
        }

        // Credencial ANTES de gravar: sem ela nem adianta criar a transação.
        String credencial = terminal.getModoIntegracao() == ModoIntegracaoTerminal.API_NUVEM
                ? credenciais.credencialObrigatoria(terminal.getProvedor())
                : null;

        TransacaoTerminal t = transacaoRepo.save(TransacaoTerminal.builder()
                .terminal(terminal)
                .vendaId(venda.getId())
                .provedor(terminal.getProvedor())
                .modoIntegracao(terminal.getModoIntegracao())
                .tipo(dto.getTipo())
                .parcelas(parcelas)
                .valor(valor)
                .usuarioId(ContextoUsuario.atual().getUsuarioId())
                .build());

        if (t.getModoIntegracao() == ModoIntegracaoTerminal.API_NUVEM) {
            try {
                RetornoProvedor r = provedorNuvem(t.getProvedor()).iniciar(new CobrancaTerminal(
                        t.getId(), venda.getId(), terminal.getIdentificadorDispositivo(),
                        t.getTipo(), parcelas, valor), credencial);
                aplicar(t, r);
            } catch (BusinessException e) {
                // Grava o ERRO (em vez de propagar e dar rollback) para ficar o rastro
                // da tentativa; o PDV mostra a mensagem e permite tentar de novo.
                t.finalizar(StatusTransacaoTerminal.ERRO, e.getMessage());
            }
            t = transacaoRepo.save(t);
        }
        return TransacaoTerminalDTO.de(t);
    }

    /** Polling do PDV. Em API_NUVEM pendente, pergunta à adquirente; nos demais só devolve o estado. */
    @Transactional
    public TransacaoTerminalDTO consultar(Long id) {
        TransacaoTerminal t = buscarParaAtualizar(id);
        atualizarPelaAdquirente(t);
        return TransacaoTerminalDTO.de(t);
    }

    @Transactional
    public TransacaoTerminalDTO registrarResultadoLocal(Long id, ResultadoLocalDTO dto) {
        TransacaoTerminal t = buscarParaAtualizar(id);
        if (t.getModoIntegracao() == ModoIntegracaoTerminal.API_NUVEM) {
            throw new BusinessException("Transações em nuvem são confirmadas pela adquirente, não pelo PDV.");
        }
        if (t.getStatus().isFinal()) {
            throw new BusinessException("Esta transação já foi finalizada como " + t.getStatus() + ".");
        }
        t.setNsu(vazioParaNull(dto.getNsu()));
        t.setAutorizacao(vazioParaNull(dto.getAutorizacao()));
        t.setBandeira(vazioParaNull(dto.getBandeira()));
        t.setRespostaBruta(dto.getRespostaBruta());
        if (Boolean.TRUE.equals(dto.getAprovada())) {
            t.finalizar(StatusTransacaoTerminal.APROVADA,
                    t.getModoIntegracao() == ModoIntegracaoTerminal.MANUAL ? "Confirmado manualmente pelo operador" : "Aprovado");
        } else {
            t.finalizar(StatusTransacaoTerminal.NEGADA,
                    dto.getMensagem() != null ? dto.getMensagem() : "Transação negada");
        }
        return TransacaoTerminalDTO.de(transacaoRepo.save(t));
    }

    @Transactional
    public TransacaoTerminalDTO cancelar(Long id) {
        TransacaoTerminal t = buscarParaAtualizar(id);
        if (t.getStatus() == StatusTransacaoTerminal.APROVADA) {
            throw new BusinessException("Esta cobrança já foi APROVADA. Faça o cancelamento/estorno na própria maquininha.");
        }
        if (t.getStatus().isFinal()) {
            return TransacaoTerminalDTO.de(t);
        }
        if (t.getModoIntegracao() == ModoIntegracaoTerminal.API_NUVEM && t.getIdExterno() != null) {
            String credencial = credenciais.credencialObrigatoria(t.getProvedor());
            // Antes de cancelar, confere: o cliente pode ter acabado de pagar.
            atualizarPelaAdquirente(t);
            if (t.getStatus() == StatusTransacaoTerminal.APROVADA) {
                throw new BusinessException("O cliente acabou de pagar — a cobrança foi APROVADA e não será cancelada.");
            }
            if (t.getStatus().isFinal()) {
                return TransacaoTerminalDTO.de(t);
            }
            provedorNuvem(t.getProvedor()).cancelar(t.getIdExterno(), t.getTerminal().getIdentificadorDispositivo(), credencial);
        }
        t.finalizar(StatusTransacaoTerminal.CANCELADA, "Cancelada pelo operador");
        return TransacaoTerminalDTO.de(transacaoRepo.save(t));
    }

    /**
     * Chamado pelos webhooks. O payload da notificação NÃO é usado como verdade:
     * só identifica qual transação reconsultar na adquirente.
     */
    @Transactional
    public void processarNotificacao(ProvedorPagamento provedor, String idExterno) {
        if (idExterno == null || idExterno.isBlank()) return;
        for (TransacaoTerminal encontrada : transacaoRepo.findByProvedorAndIdExterno(provedor, idExterno)) {
            TransacaoTerminal t = buscarParaAtualizar(encontrada.getId());
            atualizarPelaAdquirente(t);
            log.info("Webhook {}: transação {} agora {}", provedor, t.getId(), t.getStatus());
        }
    }

    /**
     * Usado pelo PdvService no fechamento: devolve a transação se ela pode
     * quitar este pagamento, ou lança BusinessException explicando por quê não.
     */
    public TransacaoTerminal validarParaPagamento(Long transacaoId, Long vendaId, String formaPagamento, BigDecimal valor) {
        TransacaoTerminal t = transacaoRepo.findById(transacaoId)
                .orElseThrow(() -> new BusinessException("Transação de cartão #" + transacaoId + " não encontrada."));
        if (t.getStatus() != StatusTransacaoTerminal.APROVADA) {
            throw new BusinessException("A transação de cartão #" + transacaoId + " não está aprovada (" + t.getStatus() + ").");
        }
        if (!vendaId.equals(t.getVendaId())) {
            throw new BusinessException("A transação de cartão #" + transacaoId + " pertence a outra venda.");
        }
        if (!t.getTipo().equals(formaPagamento)) {
            throw new BusinessException("A transação #" + transacaoId + " foi feita no " + t.getTipo()
                    + ", não no " + formaPagamento + ".");
        }
        if (t.getValor().compareTo(valor) != 0) {
            throw new BusinessException("O valor do pagamento difere do valor aprovado na maquininha ("
                    + t.getValor() + ").");
        }
        if (pagamentoRepo.existsByTransacaoTerminalId(transacaoId)) {
            throw new BusinessException("A transação de cartão #" + transacaoId + " já foi usada em outro pagamento.");
        }
        return t;
    }

    // ── internos ───────────────────────────────────────────────

    private void atualizarPelaAdquirente(TransacaoTerminal t) {
        if (t.getStatus().isFinal()
                || t.getModoIntegracao() != ModoIntegracaoTerminal.API_NUVEM
                || t.getIdExterno() == null) {
            return;
        }
        try {
            String credencial = credenciais.credencialObrigatoria(t.getProvedor());
            RetornoProvedor r = provedorNuvem(t.getProvedor())
                    .consultar(t.getIdExterno(), t.getTerminal().getIdentificadorDispositivo(), credencial);
            aplicar(t, r);
            transacaoRepo.save(t);
        } catch (BusinessException e) {
            // Falha de rede/consulta não decide nada: continua PENDENTE e o PDV tenta de novo.
            log.warn("Falha ao consultar transação {} na {}: {}", t.getId(), t.getProvedor(), e.getMessage());
            t.setMensagem("Sem resposta da adquirente, tentando novamente... (" + e.getMessage() + ")");
        }
    }

    private static void aplicar(TransacaoTerminal t, RetornoProvedor r) {
        if (r.idExterno() != null) t.setIdExterno(r.idExterno());
        t.setRespostaBruta(r.respostaBruta());
        if (r.status() == StatusTransacaoTerminal.PENDENTE) {
            t.setMensagem("Aguardando cartão na maquininha...");
            return;
        }
        t.setNsu(r.nsu());
        t.setAutorizacao(r.autorizacao());
        t.setBandeira(r.bandeira());
        t.finalizar(r.status(), r.mensagem());
    }

    private ProvedorTerminalNuvem provedorNuvem(ProvedorPagamento provedor) {
        ProvedorTerminalNuvem p = provedores.get(provedor);
        if (p == null) {
            throw new BusinessException(provedor.getRotulo() + " não tem integração em nuvem.");
        }
        return p;
    }

    private TransacaoTerminal buscarParaAtualizar(Long id) {
        return transacaoRepo.findByIdParaAtualizar(id)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada: " + id));
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
