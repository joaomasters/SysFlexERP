package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Fornecedor;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import com.acougue.repository.FornecedorRepository;
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
 * Lotes de matéria-prima. Fluxo: recebido em QUARENTENA → LIBERADO (só com a composição dentro da liga) ou
 * BLOQUEADO (com motivo). Os dados do lote só mudam enquanto ele está em quarentena e sem consumo.
 * O saldo é a quantidade recebida menos os consumos válidos que apontaram o lote.
 */
@Service
@RequiredArgsConstructor
public class LoteMpService {

    private final LoteMpRepository loteRepo;
    private final ConsumoLoteRepository consumoLoteRepo;
    private final LigaService ligaService;
    private final ProdutoRepository produtoRepo;
    private final FornecedorRepository fornecedorRepo;
    private final FichaTecnicaRepository fichaRepo;

    /** Transições: liberar e bloquear a partir da quarentena; um lote liberado ou bloqueado volta para quarentena. */
    static final Map<String, Set<String>> TRANSICOES = Map.of(
            "QUARENTENA", Set.of("LIBERADO", "BLOQUEADO"),
            "LIBERADO", Set.of("BLOQUEADO", "QUARENTENA"),
            "BLOQUEADO", Set.of("QUARENTENA"));

    @Transactional(readOnly = true)
    public List<LoteMpOut> listar(Long produtoId, String status) {
        List<LoteMp> lotes = loteRepo.listar(produtoId, status == null || status.isBlank() ? null : status);
        Map<Long, BigDecimal> consumido = consumidos(lotes.stream().map(LoteMp::getId).toList());
        return lotes.stream().map(l -> saida(l, consumido.getOrDefault(l.getId(), BigDecimal.ZERO))).toList();
    }

    @Transactional(readOnly = true)
    public LoteMpOut buscar(Long id) {
        LoteMp l = lote(id);
        return saida(l, consumoLoteRepo.consumidoDoLote(id));
    }

    @Transactional(readOnly = true)
    public List<LoteDisponivel> disponiveis(Long produtoId) {
        List<LoteMp> lotes = loteRepo.listar(produtoId, "LIBERADO");
        Map<Long, BigDecimal> consumido = consumidos(lotes.stream().map(LoteMp::getId).toList());
        return lotes.stream()
                .map(l -> new LoteDisponivel(l.getId(), l.getNumero(), l.getLiga().getCodigo(),
                        l.getQuantidade().subtract(consumido.getOrDefault(l.getId(), BigDecimal.ZERO)), l.getDataRecebimento(),
                        l.getCertificado()))
                .filter(d -> d.saldo().signum() > 0)
                // FIFO: o lote mais antigo primeiro
                .sorted(Comparator.comparing(LoteDisponivel::dataRecebimento).thenComparing(LoteDisponivel::id))
                .toList();
    }

    @Transactional
    public LoteMpOut salvar(Long id, LoteMpIn in, Long usuarioId) {
        LoteMp l = id == null ? LoteMp.builder().criadoPorId(usuarioId).build() : lote(id);
        if (id != null) {
            if (in.versao() != null && !in.versao().equals(l.getVersao())) {
                throw new BusinessException("O lote " + l.getNumero() + " foi alterado por outra pessoa. Recarregue e tente de novo.");
            }
            if (!"QUARENTENA".equals(l.getStatus())) {
                throw new BusinessException("Só lotes em quarentena podem ser alterados. Volte o lote para quarentena antes.");
            }
            if (consumoLoteRepo.consumidoDoLote(id).signum() > 0) throw new BusinessException("Este lote já foi consumido e não pode ser alterado.");
        }
        Produto produto = produtoRepo.findById(in.produtoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + in.produtoId()));
        if (!Boolean.TRUE.equals(produto.getAtivo())) throw new BusinessException("O produto " + produto.getNome() + " está inativo.");
        Liga liga = ligaService.ligaAtiva(in.ligaId());
        fichaRepo.findByProdutoId(produto.getId()).filter(f -> f.getLiga() != null && !f.getLiga().getId().equals(liga.getId()))
                .ifPresent(f -> {
                    throw new BusinessException("O produto " + produto.getNome() + " é da liga " + f.getLiga().getCodigo() + ".");
                });
        String numero = in.numero().trim().toUpperCase(Locale.ROOT);
        loteRepo.doProdutoComNumero(produto.getId(), numero).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe o lote " + numero + " deste produto.");
        });
        Fornecedor fornecedor = in.fornecedorId() == null ? null : fornecedorRepo.findById(in.fornecedorId())
                .orElseThrow(() -> new EntityNotFoundException("Fornecedor não encontrado: " + in.fornecedorId()));

        Set<String> vistos = new HashSet<>();
        List<LoteMpComposicao> comp = new ArrayList<>();
        for (ComposicaoIn c : in.composicao()) {
            if (!vistos.add(c.elemento())) throw new BusinessException("O elemento " + c.elemento() + " aparece mais de uma vez.");
            comp.add(LoteMpComposicao.builder().lote(l).elemento(c.elemento()).percentual(c.percentual()).build());
        }
        l.setNumero(numero);
        l.setProduto(produto);
        l.setLiga(liga);
        l.setFornecedor(fornecedor);
        l.setCertificado(LigaService.texto(in.certificado()));
        l.setDataRecebimento(in.dataRecebimento());
        l.setQuantidade(in.quantidade());
        l.setDiametroMm(in.diametroMm());
        l.setComprimentoMm(in.comprimentoMm());
        l.setObservacao(LigaService.texto(in.observacao()));
        l.getComposicao().clear();
        l = loteRepo.saveAndFlush(l);
        // o save pode devolver outra instância (merge): a composição precisa apontar para a gravada
        for (LoteMpComposicao c : comp) c.setLote(l);
        l.getComposicao().addAll(comp);
        l = loteRepo.saveAndFlush(l);
        return saida(l, BigDecimal.ZERO);
    }

    @Transactional
    public LoteMpOut mudarStatus(Long id, StatusLoteIn in, Long usuarioId) {
        LoteMp l = lote(id);
        if (!in.versao().equals(l.getVersao())) {
            throw new BusinessException("O lote " + l.getNumero() + " foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
        String de = l.getStatus();
        String para = in.status();
        if (!LoteMp.STATUS.contains(para)) throw new BusinessException("Situação inválida: " + para);
        if (!TRANSICOES.getOrDefault(de, Set.of()).contains(para)) {
            throw new BusinessException("Um lote " + de.toLowerCase() + " não pode passar para " + para.toLowerCase() + ".");
        }
        String motivo = LigaService.texto(in.motivo());
        if (!"LIBERADO".equals(para) && motivo == null) throw new BusinessException("Informe o motivo.");
        if ("LIBERADO".equals(para)) {
            List<String> desvios = LigaService.desvios(l.getLiga(), composicao(l));
            if (!desvios.isEmpty()) {
                throw new BusinessException("Composição fora da especificação da liga " + l.getLiga().getCodigo() + ": "
                        + String.join("; ", desvios) + ".");
            }
        }
        l.setStatus(para);
        l.setMotivoStatus(motivo);
        l.setStatusPorId(usuarioId);
        l.setStatusEm(LocalDateTime.now());
        l = loteRepo.saveAndFlush(l);
        return saida(l, consumoLoteRepo.consumidoDoLote(id));
    }

    /** Material com lotes cadastrados é controlado por lote: o consumo precisa dizer de qual lote saiu. */
    @Transactional(readOnly = true)
    public boolean controlado(Long produtoId) {
        return loteRepo.existsByProdutoId(produtoId);
    }

    /**
     * Confere e trava o lote para um consumo: precisa ser do produto, estar liberado e ter saldo.
     * A trava dura até o fim da transação do consumo.
     */
    @Transactional
    public LoteMp exigirConsumivel(Long loteId, Long produtoId, BigDecimal quantidade) {
        LoteMp l = loteRepo.travar(loteId).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + loteId));
        if (!l.getProduto().getId().equals(produtoId)) throw new BusinessException("O lote " + l.getNumero() + " é de outro material.");
        if (!"LIBERADO".equals(l.getStatus())) {
            throw new BusinessException("O lote " + l.getNumero() + " está " + l.getStatus().toLowerCase() + " e não pode ser consumido.");
        }
        BigDecimal saldo = l.getQuantidade().subtract(consumoLoteRepo.consumidoDoLote(loteId));
        if (saldo.compareTo(quantidade) < 0) {
            throw new BusinessException("O lote " + l.getNumero() + " tem só " + saldo.stripTrailingZeros().toPlainString()
                    + " " + l.getProduto().getUnidadeMedida() + " de saldo.");
        }
        return l;
    }

    // ── auxiliares ──────────────────────────────────────────────────────
    static Map<String, BigDecimal> composicao(LoteMp l) {
        return l.getComposicao().stream().collect(Collectors.toMap(LoteMpComposicao::getElemento, LoteMpComposicao::getPercentual));
    }

    private Map<Long, BigDecimal> consumidos(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] linha : consumoLoteRepo.consumidoDosLotes(ids)) m.put((Long) linha[0], (BigDecimal) linha[1]);
        return m;
    }

    private LoteMpOut saida(LoteMp l, BigDecimal consumido) {
        Map<String, LigaElemento> faixas = l.getLiga().getElementos().stream()
                .collect(Collectors.toMap(LigaElemento::getElemento, e -> e));
        List<ComposicaoOut> comp = l.getComposicao().stream().map(c -> {
            LigaElemento f = faixas.get(c.getElemento());
            BigDecimal min = f == null ? null : f.getMinimo();
            BigDecimal max = f == null ? null : f.getMaximo();
            boolean dentro = (min == null || c.getPercentual().compareTo(min) >= 0) && (max == null || c.getPercentual().compareTo(max) <= 0);
            return new ComposicaoOut(c.getElemento(), c.getPercentual(), min, max, dentro);
        }).toList();
        Produto p = l.getProduto();
        return new LoteMpOut(l.getId(), l.getNumero(), p.getId(), p.getNome(), p.getUnidadeMedida(), l.getLiga().getId(),
                l.getLiga().getCodigo(), l.getFornecedor() == null ? null : l.getFornecedor().getId(),
                l.getFornecedor() == null ? null : l.getFornecedor().getNome(), l.getCertificado(), l.getDataRecebimento(),
                l.getQuantidade(), consumido, l.getQuantidade().subtract(consumido), l.getDiametroMm(), l.getComprimentoMm(),
                l.getStatus(), l.getMotivoStatus(), l.getStatusEm(), l.getObservacao(), l.getVersao(), comp,
                LigaService.desvios(l.getLiga(), composicao(l)));
    }

    private LoteMp lote(Long id) {
        return loteRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + id));
    }
}
