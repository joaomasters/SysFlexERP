package com.acougue.modules.pagamento;

import com.acougue.entity.ModoIntegracaoTerminal;
import com.acougue.entity.TerminalPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.dto.SalvarTerminalDTO;
import com.acougue.repository.TerminalPagamentoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

/** Cadastro das maquininhas físicas. */
@Service
@RequiredArgsConstructor
public class TerminalPagamentoService {

    private final TerminalPagamentoRepository terminalRepo;

    public List<TerminalPagamento> listar() {
        return terminalRepo.findAllByOrderByAtivoDescNomeAsc();
    }

    /** O que o PDV oferece no modal de pagamento. */
    public List<TerminalPagamento> listarAtivos() {
        return terminalRepo.findByAtivoTrueOrderByNomeAsc();
    }

    public TerminalPagamento buscar(Long id) {
        return terminalRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maquininha não encontrada: " + id));
    }

    @Transactional
    public TerminalPagamento criar(SalvarTerminalDTO dto) {
        TerminalPagamento terminal = new TerminalPagamento();
        aplicar(terminal, dto);
        return terminalRepo.save(terminal);
    }

    @Transactional
    public TerminalPagamento atualizar(Long id, SalvarTerminalDTO dto) {
        TerminalPagamento terminal = buscar(id);
        aplicar(terminal, dto);
        return terminalRepo.save(terminal);
    }

    private void aplicar(TerminalPagamento t, SalvarTerminalDTO dto) {
        validar(dto);
        t.setNome(dto.getNome().trim());
        t.setProvedor(dto.getProvedor());
        t.setModoIntegracao(dto.getModoIntegracao());
        // Só guarda o campo que faz sentido para o modo — evita "lixo" de
        // configuração antiga confundindo quem der manutenção depois.
        t.setIdentificadorDispositivo(dto.getModoIntegracao() == ModoIntegracaoTerminal.API_NUVEM
                ? dto.getIdentificadorDispositivo().trim() : null);
        t.setAgenteUrl(dto.getModoIntegracao() == ModoIntegracaoTerminal.AGENTE_LOCAL
                ? semBarraFinal(dto.getAgenteUrl().trim()) : null);
        t.setAceitaDebito(dto.isAceitaDebito());
        t.setAceitaCredito(dto.isAceitaCredito());
        t.setMaxParcelas(dto.isAceitaCredito() ? dto.getMaxParcelas() : 1);
        t.setAtivo(dto.isAtivo());
    }

    static void validar(SalvarTerminalDTO dto) {
        if (!dto.isAceitaDebito() && !dto.isAceitaCredito()) {
            throw new BusinessException("A maquininha precisa aceitar débito, crédito ou ambos.");
        }
        switch (dto.getModoIntegracao()) {
            case API_NUVEM -> {
                if (!dto.getProvedor().suportaApiNuvem()) {
                    throw new BusinessException(dto.getProvedor().getRotulo()
                            + " não tem integração em nuvem. Use o modo Agente local (TEF) ou Manual.");
                }
                if (dto.getIdentificadorDispositivo() == null || dto.getIdentificadorDispositivo().isBlank()) {
                    throw new BusinessException("Informe o identificador da maquininha " +
                            "(Mercado Pago: device_id · Stone: número de série).");
                }
            }
            case AGENTE_LOCAL -> {
                if (!urlHttpValida(dto.getAgenteUrl())) {
                    throw new BusinessException("Informe a URL do agente local, ex: http://localhost:12345");
                }
            }
            case MANUAL -> { /* nada a validar */ }
        }
    }

    private static boolean urlHttpValida(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String semBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
