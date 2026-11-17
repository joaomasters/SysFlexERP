package com.acougue.modules.industrial.expedicao;

import com.acougue.entity.Cliente;
import com.acougue.entity.NotaFiscalSaida;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.fiscal.NotaFiscalService;
import com.acougue.modules.fiscal.dto.NotaFiscalSaidaDTO;
import com.acougue.modules.industrial.engenharia.FichaTecnica;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.expedicao.ExpedicaoDto.*;
import com.acougue.modules.industrial.extrusao.PerdaService;
import com.acougue.modules.industrial.qualidade.Lote;
import com.acougue.modules.industrial.qualidade.LoteRepository;
import com.acougue.modules.industrial.qualidade.LoteService;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Expedição: pedido do cliente → reserva de lotes LIBERADOS (manual ou FIFO) → conferência lendo o código do lote
 * → carregamento. O carregamento baixa o estoque do produto (SAIDA_EXPEDICAO) e o saldo dos lotes uma única vez;
 * a NF de saída gerada depois sai marcada com a expedição e não baixa o estoque de novo. A nota é criada PENDENTE:
 * nada é emitido automaticamente. Lote bloqueado pela qualidade não é reservado, conferido nem carregado.
 */
@Service
@RequiredArgsConstructor
public class ExpedicaoService {

    private final ExpedicaoRepository expedicaoRepo;
    private final ClienteRepository clienteRepo;
    private final ProdutoRepository produtoRepo;
    private final LoteRepository loteRepo;
    private final LoteService loteService;
    private final EstoqueService estoque;
    private final NotaFiscalService notaFiscalService;
    private final FichaTecnicaRepository fichaRepo;
    private final ExecucaoMapper mapper;

    @Transactional(readOnly = true)
    public List<ClienteOpcao> clientes() {
        return clienteRepo.findByAtivoTrue().stream().sorted(Comparator.comparing(Cliente::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new ClienteOpcao(c.getId(), c.getNome(), c.getCpfCnpj())).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpedicaoResumo> listar(String status) {
        return expedicaoRepo.listar(status == null || status.isBlank() ? null : status).stream()
                .map(e -> new ExpedicaoResumo(e.getId(), e.getNumero(), e.getCliente().getId(), e.getCliente().getNome(),
                        e.getPedidoReferencia(), e.getDataPrevista(), e.getStatus(), e.getItens().size(), e.getNotaFiscalId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpedicaoOut buscar(Long id) {
        return saida(expedicao(id));
    }

    // ── Pedido ───────────────────────────────────────────────────────────
    @Transactional
    public ExpedicaoOut criar(ExpedicaoIn in, Long usuarioId) {
        Expedicao e = Expedicao.builder().criadoPorId(usuarioId).build();
        aplicar(e, in);
        e.setNumero(String.format("EXP%06d", expedicaoRepo.proximoNumero()));
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    @Transactional
    public ExpedicaoOut editar(Long id, ExpedicaoIn in) {
        Expedicao e = expedicao(id);
        conferirVersao(e, in.versao());
        exigirStatus(e, "ABERTA", "Só expedições abertas podem ser alteradas.");
        aplicar(e, in);
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    private void aplicar(Expedicao e, ExpedicaoIn in) {
        Cliente cliente = clienteRepo.findById(in.clienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + in.clienteId()));
        if (!Boolean.TRUE.equals(cliente.getAtivo())) throw new BusinessException("O cliente " + cliente.getNome() + " está inativo.");
        Set<Long> vistos = new HashSet<>();
        Map<Long, ExpedicaoItem> atuais = e.getItens().stream().collect(Collectors.toMap(i -> i.getProduto().getId(), i -> i));
        List<ExpedicaoItem> novos = new ArrayList<>();
        for (ItemIn i : in.itens()) {
            if (!vistos.add(i.produtoId())) throw new BusinessException("Um produto aparece mais de uma vez nos itens.");
            ExpedicaoItem item = atuais.get(i.produtoId());
            if (item == null) {
                Produto p = produtoRepo.findById(i.produtoId())
                        .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + i.produtoId()));
                if (!Boolean.TRUE.equals(p.getAtivo())) throw new BusinessException("O produto " + p.getNome() + " está inativo.");
                item = ExpedicaoItem.builder().expedicao(e).produto(p).build();
            } else if (i.quantidade().compareTo(item.reservado()) < 0) {
                throw new BusinessException(item.getProduto().getNome() + " já tem " + item.reservado().stripTrailingZeros().toPlainString()
                        + " reservado. Tire reservas antes de diminuir a quantidade.");
            }
            item.setQuantidade(i.quantidade());
            novos.add(item);
        }
        for (ExpedicaoItem velho : e.getItens()) {
            if (!vistos.contains(velho.getProduto().getId()) && !velho.getReservas().isEmpty()) {
                throw new BusinessException(velho.getProduto().getNome() + " tem lotes reservados. Tire as reservas antes de remover o item.");
            }
        }
        e.setCliente(cliente);
        e.setPedidoReferencia(texto(in.pedidoReferencia()));
        e.setDataPrevista(in.dataPrevista());
        e.setTransportadora(texto(in.transportadora()));
        e.setPlaca(in.placa() == null || in.placa().isBlank() ? null : in.placa().trim().toUpperCase(Locale.ROOT));
        e.setMotorista(texto(in.motorista()));
        e.setObservacao(texto(in.observacao()));
        e.getItens().retainAll(novos);
        for (ExpedicaoItem n : novos) if (!e.getItens().contains(n)) e.getItens().add(n);
    }

    // ── Reserva de lotes ─────────────────────────────────────────────────
    @Transactional
    public ExpedicaoOut reservar(Long id, ReservaIn in) {
        Expedicao e = expedicao(id);
        exigirStatus(e, "ABERTA", "Só expedições abertas recebem reservas.");
        ExpedicaoItem item = item(e, in.itemId());
        Lote lote = loteRepo.travar(in.loteId()).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + in.loteId()));
        reservarLote(item, lote, in.quantidade());
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    /** Preenche o que falta de cada item com os lotes liberados mais antigos (FIFO). */
    @Transactional
    public ExpedicaoOut reservarAutomatico(Long id) {
        Expedicao e = expedicao(id);
        exigirStatus(e, "ABERTA", "Só expedições abertas recebem reservas.");
        for (ExpedicaoItem item : e.getItens()) {
            BigDecimal falta = item.getQuantidade().subtract(item.reservado());
            for (Lote candidato : loteRepo.liberadosFifo(item.getProduto().getId())) {
                if (falta.signum() <= 0) break;
                Lote lote = loteRepo.travar(candidato.getId()).orElseThrow();
                BigDecimal disponivel = loteService.disponivel(lote);
                if (disponivel.signum() <= 0 || !"LIBERADO".equals(lote.getSituacao())) continue;
                BigDecimal q = disponivel.min(falta);
                reservarLote(item, lote, q);
                falta = falta.subtract(q);
            }
        }
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    private void reservarLote(ExpedicaoItem item, Lote lote, BigDecimal quantidade) {
        if (!lote.getProduto().getId().equals(item.getProduto().getId())) {
            throw new BusinessException("O lote " + lote.getCodigo() + " é de outro produto.");
        }
        if (!"LIBERADO".equals(lote.getSituacao())) {
            throw new BusinessException("O lote " + lote.getCodigo() + " está " + lote.getSituacao().toLowerCase() + " pela qualidade e não pode ser reservado.");
        }
        BigDecimal disponivel = loteService.disponivel(lote);
        if (quantidade.compareTo(disponivel) > 0) {
            throw new BusinessException("O lote " + lote.getCodigo() + " tem " + disponivel.stripTrailingZeros().toPlainString() + " disponível.");
        }
        if (item.reservado().add(quantidade).compareTo(item.getQuantidade()) > 0) {
            throw new BusinessException("A reserva passa da quantidade do item " + item.getProduto().getNome() + ".");
        }
        Optional<ExpedicaoItem.Reserva> existente = item.getReservas().stream()
                .filter(r -> r.getLote().getId().equals(lote.getId())).findFirst();
        if (existente.isPresent()) {
            existente.get().setQuantidade(existente.get().getQuantidade().add(quantidade));
            existente.get().setConferida(false);
        } else {
            item.getReservas().add(ExpedicaoItem.Reserva.builder().item(item).lote(lote).quantidade(quantidade).build());
        }
    }

    @Transactional
    public ExpedicaoOut removerReserva(Long id, Long reservaId) {
        Expedicao e = expedicao(id);
        exigirStatus(e, "ABERTA", "Só expedições abertas têm reservas alteradas.");
        boolean removeu = e.getItens().stream().anyMatch(i -> i.getReservas().removeIf(r -> r.getId().equals(reservaId)));
        if (!removeu) throw new EntityNotFoundException("Reserva não encontrada nesta expedição: " + reservaId);
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    // ── Conferência e carregamento ───────────────────────────────────────
    /** Confere um lote lendo o código (QR da etiqueta). Ler de novo o mesmo lote não muda nada. */
    @Transactional
    public ExpedicaoOut conferir(Long id, ConferenciaIn in, Long usuarioId) {
        Expedicao e = expedicao(id);
        exigirStatus(e, "ABERTA", "A conferência é feita com a expedição aberta.");
        String codigo = in.codigoLote().trim();
        List<ExpedicaoItem.Reserva> doLote = e.getItens().stream().flatMap(i -> i.getReservas().stream())
                .filter(r -> r.getLote().getCodigo().equalsIgnoreCase(codigo)).toList();
        if (doLote.isEmpty()) throw new BusinessException("O lote " + codigo.toUpperCase(Locale.ROOT) + " não está reservado nesta expedição.");
        Lote lote = doLote.get(0).getLote();
        if (!"LIBERADO".equals(lote.getSituacao())) {
            throw new BusinessException("O lote " + lote.getCodigo() + " foi " + lote.getSituacao().toLowerCase() + " pela qualidade. Não carregue.");
        }
        for (ExpedicaoItem.Reserva r : doLote) {
            if (!r.getConferida()) {
                r.setConferida(true);
                r.setConferidaEm(LocalDateTime.now());
                r.setConferidaPorId(usuarioId);
            }
        }
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    @Transactional
    public ExpedicaoOut concluirConferencia(Long id, VersaoIn in) {
        Expedicao e = expedicao(id);
        conferirVersao(e, in.versao());
        exigirStatus(e, "ABERTA", "A expedição não está aberta.");
        List<String> p = pendencias(e);
        if (!p.isEmpty()) throw new BusinessException(String.join(" ", p));
        e.setStatus("CONFERIDA");
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    @Transactional
    public ExpedicaoOut reabrir(Long id, VersaoIn in) {
        Expedicao e = expedicao(id);
        conferirVersao(e, in.versao());
        exigirStatus(e, "CONFERIDA", "Só uma expedição conferida volta para aberta.");
        e.setStatus("ABERTA");
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    /** Carrega: baixa o saldo dos lotes e o estoque, uma vez só (repetir o comando não baixa de novo). */
    @Transactional
    public ExpedicaoOut carregar(Long id, VersaoIn in, Long usuarioId) {
        Expedicao e = expedicao(id);
        if ("CARREGADA".equals(e.getStatus())) return saida(e);
        conferirVersao(e, in.versao());
        exigirStatus(e, "CONFERIDA", "Conclua a conferência antes de carregar.");
        for (ExpedicaoItem item : e.getItens()) {
            for (ExpedicaoItem.Reserva r : item.getReservas()) {
                loteService.baixarExpedicao(r.getLote(), r.getQuantidade(), e.getNumero(), usuarioId);
            }
            estoque.saida(item.getProduto(), item.getQuantidade(), "SAIDA_EXPEDICAO", e.getNumero(), usuarioId);
        }
        e.setStatus("CARREGADA");
        e.setCarregadaEm(LocalDateTime.now());
        e.setCarregadaPorId(usuarioId);
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    @Transactional
    public ExpedicaoOut cancelar(Long id, CancelarIn in) {
        Expedicao e = expedicao(id);
        conferirVersao(e, in.versao());
        if (!Expedicao.ATIVAS.contains(e.getStatus())) {
            throw new BusinessException("Uma expedição " + e.getStatus().toLowerCase() + " não pode ser cancelada.");
        }
        e.setStatus("CANCELADA");
        e.setMotivoCancelamento(in.motivo().trim());
        return saida(expedicaoRepo.saveAndFlush(e));
    }

    /**
     * Cria a NF de saída (PENDENTE) com os itens carregados, ligada à expedição. Não emite: a emissão continua
     * na tela de NF de saída, e como a nota é de expedição, emitir não baixa o estoque de novo.
     */
    @Transactional
    public NotaGerada gerarNotaFiscal(Long id, Long usuarioId) {
        Expedicao e = expedicao(id);
        if (e.getNotaFiscalId() != null) return new NotaGerada(e.getNotaFiscalId(), "JA_GERADA");
        exigirStatus(e, "CARREGADA", "A nota é gerada depois do carregamento.");
        List<NotaFiscalSaidaDTO.ItemDTO> itens = e.getItens().stream().map(i -> new NotaFiscalSaidaDTO.ItemDTO(
                i.getProduto().getId(),
                i.getProduto().getNome() + " · lotes " + i.getReservas().stream().map(r -> r.getLote().getCodigo()).collect(Collectors.joining(", ")),
                i.getQuantidade(),
                i.getProduto().getPrecoVenda())).toList();
        NotaFiscalSaida nf = notaFiscalService.criar(new NotaFiscalSaidaDTO(e.getCliente().getId(), null, null, null,
                "Expedição " + e.getNumero() + (e.getPedidoReferencia() != null ? " · pedido " + e.getPedidoReferencia() : ""),
                itens), usuarioId);
        nf.setExpedicaoId(e.getId());
        e.setNotaFiscalId(nf.getId());
        expedicaoRepo.saveAndFlush(e);
        return new NotaGerada(nf.getId(), nf.getStatus());
    }

    // ── auxiliares ──────────────────────────────────────────────────────
    static List<String> pendencias(Expedicao e) {
        List<String> p = new ArrayList<>();
        for (ExpedicaoItem i : e.getItens()) {
            BigDecimal falta = i.getQuantidade().subtract(i.reservado());
            if (falta.signum() > 0) {
                p.add(i.getProduto().getNome() + ": faltam " + falta.stripTrailingZeros().toPlainString() + " " + i.getProduto().getUnidadeMedida() + " reservar.");
            }
            for (ExpedicaoItem.Reserva r : i.getReservas()) {
                if (!r.getConferida()) p.add("Lote " + r.getLote().getCodigo() + " não conferido.");
                if (!"LIBERADO".equals(r.getLote().getSituacao())) {
                    p.add("Lote " + r.getLote().getCodigo() + " está " + r.getLote().getSituacao().toLowerCase() + " pela qualidade.");
                }
            }
        }
        return p;
    }

    private ExpedicaoOut saida(Expedicao e) {
        Set<Long> produtos = e.getItens().stream().map(i -> i.getProduto().getId()).collect(Collectors.toSet());
        Map<Long, FichaTecnica> fichas = produtos.isEmpty() ? Map.of() : fichaRepo.findByProdutoIdIn(produtos).stream()
                .collect(Collectors.toMap(f -> f.getProduto().getId(), f -> f));
        BigDecimal pesoTotal = BigDecimal.ZERO;
        boolean pesoCompleto = true;
        List<ItemOut> itens = new ArrayList<>();
        for (ExpedicaoItem i : e.getItens()) {
            BigDecimal kg = PerdaService.emKg(i.getProduto(), i.getQuantidade(), fichas.get(i.getProduto().getId()));
            if (kg == null) pesoCompleto = false;
            else pesoTotal = pesoTotal.add(kg);
            BigDecimal reservado = i.reservado();
            itens.add(new ItemOut(i.getId(), i.getProduto().getId(), i.getProduto().getNome(), i.getProduto().getUnidadeMedida(),
                    i.getQuantidade(), reservado, i.getQuantidade().subtract(reservado).max(BigDecimal.ZERO), kg,
                    i.getReservas().stream().map(r -> new ReservaOut(r.getId(), r.getLote().getId(), r.getLote().getCodigo(),
                            r.getLote().getSituacao(), r.getQuantidade(), r.getConferida(), r.getConferidaEm())).toList()));
        }
        Cliente c = e.getCliente();
        String carregadaPor = e.getCarregadaPorId() == null ? null : mapper.nomes(List.of(e.getCarregadaPorId())).get(e.getCarregadaPorId());
        return new ExpedicaoOut(e.getId(), e.getNumero(), c.getId(), c.getNome(), c.getCpfCnpj(), c.getEndereco(),
                e.getPedidoReferencia(), e.getDataPrevista(), e.getStatus(), e.getTransportadora(), e.getPlaca(), e.getMotorista(),
                e.getObservacao(), e.getCarregadaEm(), carregadaPor, e.getNotaFiscalId(), e.getMotivoCancelamento(), e.getVersao(),
                pesoTotal, pesoCompleto, itens, "ABERTA".equals(e.getStatus()) ? pendencias(e) : List.of());
    }

    private static ExpedicaoItem item(Expedicao e, Long itemId) {
        return e.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Item não encontrado nesta expedição: " + itemId));
    }

    private static void exigirStatus(Expedicao e, String status, String mensagem) {
        if (!status.equals(e.getStatus())) throw new BusinessException(mensagem);
    }

    private static void conferirVersao(Expedicao e, Long versao) {
        if (versao != null && !versao.equals(e.getVersao())) {
            throw new BusinessException("A expedição " + e.getNumero() + " foi alterada por outra pessoa. Recarregue e tente de novo.");
        }
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private Expedicao expedicao(Long id) {
        return expedicaoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Expedição não encontrada: " + id));
    }
}
