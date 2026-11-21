package com.acougue.modules.industrial.qualidade;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.expedicao.ExpedicaoRepository;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Lote de produto: nasce da produção (QUARENTENA se o produto tem plano de inspeção ativo, senão LIBERADO),
 * pode ser dividido e unido (com genealogia), bloqueado, sucateado e baixado na expedição.
 * Toda variação de saldo vai para o extrato (ind_lote_movimento). As operações que mexem no saldo travam o lote.
 *
 * Situações: QUARENTENA → LIBERADO (inspeção aprovada, ou manual se o produto não tem plano) | BLOQUEADO;
 * LIBERADO → BLOQUEADO; BLOQUEADO → QUARENTENA (reinspeção, sem NC aberta) ou LIBERADO só por NC encerrada
 * com "usar como está". ESTORNADO é final.
 */
@Service
@RequiredArgsConstructor
public class LoteService {

    private final LoteRepository loteRepo;
    private final LoteGenealogiaRepository genealogiaRepo;
    private final LoteMovimentoRepository movimentoRepo;
    private final PlanoInspecaoRepository planoRepo;
    private final NaoConformidadeRepository ncRepo;
    private final ExpedicaoRepository expedicaoRepo;
    private final EstoqueService estoque;
    private final ExecucaoMapper mapper;

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // ── Produção ─────────────────────────────────────────────────────────
    /** Chamado ao finalizar a última operação com quantidade boa: cria o lote com o código gerado no apontamento. */
    @Transactional
    public Lote registrarProducao(OrdemProducao ordem, Long apontamentoId, String codigo, BigDecimal quantidade, Long usuarioId) {
        boolean temPlano = planoRepo.ativoDoProduto(ordem.getProduto().getId()).isPresent();
        Lote l = loteRepo.saveAndFlush(Lote.builder().codigo(codigo).produto(ordem.getProduto()).ordem(ordem)
                .apontamentoId(apontamentoId).origem("PRODUCAO").quantidadeInicial(quantidade).saldo(quantidade)
                .situacao(temPlano ? "QUARENTENA" : "LIBERADO")
                .motivoSituacao(temPlano ? "Aguardando inspeção" : null)
                .situacaoEm(LocalDateTime.now()).criadoPorId(usuarioId).build());
        movimento(l, "PRODUCAO", quantidade, ordem.getNumero() + "/AP" + apontamentoId, usuarioId);
        return l;
    }

    /** Estorno do apontamento: só se o lote não foi dividido, reservado, expedido nem sucateado. */
    @Transactional
    public void estornarProducao(Long apontamentoId, Long usuarioId) {
        Optional<Lote> achado = loteRepo.findByApontamentoId(apontamentoId);
        if (achado.isEmpty()) return;
        Lote l = loteRepo.travar(achado.get().getId()).orElseThrow();
        if ("ESTORNADO".equals(l.getSituacao())) return;
        if (l.getSaldo().compareTo(l.getQuantidadeInicial()) != 0 || genealogiaRepo.existsByLotePaiId(l.getId())
                || expedicaoRepo.reservadoDoLote(l.getId()).signum() > 0) {
            throw new BusinessException("O lote " + l.getCodigo() + " já foi dividido, reservado ou expedido e não pode ser estornado.");
        }
        movimento(l, "ESTORNO_PRODUCAO", l.getSaldo().negate(), "AP" + apontamentoId, usuarioId);
        l.setSaldo(BigDecimal.ZERO);
        situacao(l, "ESTORNADO", "Apontamento estornado", usuarioId);
    }

    // ── Consultas ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LoteOut> listar(String situacao, Long produtoId) {
        List<Lote> lotes = loteRepo.listar(situacao == null || situacao.isBlank() ? null : situacao, produtoId);
        return saidas(lotes);
    }

    @Transactional(readOnly = true)
    public LoteOut buscar(Long id) {
        return saidas(List.of(lote(id))).get(0);
    }

    List<LoteOut> saidas(List<Lote> lotes) {
        if (lotes.isEmpty()) return List.of();
        Map<Long, BigDecimal> reservado = new HashMap<>();
        for (Object[] r : expedicaoRepo.reservadoDosLotes(lotes.stream().map(Lote::getId).toList())) {
            reservado.put((Long) r[0], (BigDecimal) r[1]);
        }
        Map<Long, Boolean> planos = new HashMap<>();
        return lotes.stream().map(l -> {
            BigDecimal res = reservado.getOrDefault(l.getId(), BigDecimal.ZERO);
            boolean temPlano = planos.computeIfAbsent(l.getProduto().getId(), pid -> planoRepo.ativoDoProduto(pid).isPresent());
            Produto p = l.getProduto();
            return new LoteOut(l.getId(), l.getCodigo(), p.getId(), p.getNome(), p.getUnidadeMedida(),
                    l.getOrdem() == null ? null : l.getOrdem().getId(), l.getOrdem() == null ? null : l.getOrdem().getNumero(),
                    l.getOrigem(), l.getQuantidadeInicial(), l.getSaldo(), res, l.getSaldo().subtract(res).max(BigDecimal.ZERO),
                    l.getSituacao(), l.getMotivoSituacao(), l.getSituacaoEm(), l.getCreatedAt(), l.getVersao(), temPlano);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimentoOut> movimentos(Long loteId) {
        List<LoteMovimento> lista = movimentoRepo.findByLoteIdOrderByCreatedAtAscIdAsc(loteId);
        Map<Long, String> nomes = mapper.nomes(lista.stream().map(LoteMovimento::getUsuarioId).toList());
        return lista.stream().map(m -> new MovimentoOut(m.getTipo(), m.getQuantidade(), m.getDocumento(),
                nomes.get(m.getUsuarioId()), m.getCreatedAt())).toList();
    }

    /** Saldo que ainda pode ser reservado, dividido ou unido (saldo menos reservas em expedições abertas). */
    public BigDecimal disponivel(Lote l) {
        return l.getSaldo().subtract(expedicaoRepo.reservadoDoLote(l.getId())).max(BigDecimal.ZERO);
    }

    // ── Situação ─────────────────────────────────────────────────────────
    @Transactional
    public LoteOut mudarSituacao(Long id, SituacaoIn in, Long usuarioId) {
        Lote l = loteRepo.travar(id).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + id));
        if (!in.versao().equals(l.getVersao())) {
            throw new BusinessException("O lote " + l.getCodigo() + " foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
        String de = l.getSituacao();
        String para = in.situacao();
        String motivo = texto(in.motivo());
        if (!Lote.SITUACOES.contains(para) || "ESTORNADO".equals(para)) throw new BusinessException("Situação inválida: " + para);
        if (de.equals(para)) throw new BusinessException("O lote já está nesta situação.");
        if ("ESTORNADO".equals(de)) throw new BusinessException("O lote " + l.getCodigo() + " foi estornado.");
        switch (para) {
            case "LIBERADO" -> {
                if (!"QUARENTENA".equals(de)) {
                    throw new BusinessException("Lote bloqueado só é liberado pelo encerramento da não conformidade (usar como está).");
                }
                if (planoRepo.ativoDoProduto(l.getProduto().getId()).isPresent()) {
                    throw new BusinessException("O produto tem plano de inspeção: libere o lote pela inspeção.");
                }
            }
            case "BLOQUEADO" -> {
                if (motivo == null) throw new BusinessException("Informe o motivo do bloqueio.");
            }
            case "QUARENTENA" -> {
                if (motivo == null) throw new BusinessException("Informe o motivo.");
                if (ncRepo.abertasDoLote(l.getId()) > 0) throw new BusinessException("Encerre antes a não conformidade aberta deste lote.");
            }
            default -> { }
        }
        situacao(l, para, motivo, usuarioId);
        return buscar(l.getId());
    }

    /** Muda a situação e registra quem e por quê (usado também por inspeção e não conformidade). */
    void situacao(Lote l, String para, String motivo, Long usuarioId) {
        l.setSituacao(para);
        l.setMotivoSituacao(motivo);
        l.setSituacaoPorId(usuarioId);
        l.setSituacaoEm(LocalDateTime.now());
        loteRepo.saveAndFlush(l);
    }

    // ── Divisão e união ──────────────────────────────────────────────────
    @Transactional
    public List<LoteOut> dividir(Long id, DivisaoIn in, Long usuarioId) {
        Lote pai = loteRepo.travar(id).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + id));
        if ("ESTORNADO".equals(pai.getSituacao())) throw new BusinessException("O lote " + pai.getCodigo() + " foi estornado.");
        BigDecimal total = in.quantidades().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disponivel = disponivel(pai);
        if (total.compareTo(disponivel) > 0) {
            throw new BusinessException("O lote " + pai.getCodigo() + " tem " + disponivel.stripTrailingZeros().toPlainString()
                    + " disponível para dividir.");
        }
        List<Lote> filhos = new ArrayList<>();
        for (BigDecimal q : in.quantidades()) {
            Lote f = novoLote(pai.getProduto(), pai.getOrdem(), "DIVISAO", q, pai.getSituacao(),
                    "Dividido de " + pai.getCodigo(), usuarioId);
            genealogiaRepo.save(LoteGenealogia.builder().lote(f).lotePai(pai).quantidade(q).build());
            movimento(f, "DIVISAO", q, pai.getCodigo(), usuarioId);
            filhos.add(f);
        }
        pai.setSaldo(pai.getSaldo().subtract(total));
        loteRepo.saveAndFlush(pai);
        movimento(pai, "DIVISAO", total.negate(), filhos.size() == 1 ? filhos.get(0).getCodigo() : filhos.size() + " lotes", usuarioId);
        return saidas(filhos);
    }

    @Transactional
    public LoteOut unir(UniaoIn in, Long usuarioId) {
        Set<Long> ids = new HashSet<>();
        List<Lote> pais = new ArrayList<>();
        // trava em ordem de id: duas uniões com os mesmos lotes não se travam mutuamente
        List<ParteUniao> partes = in.partes().stream().sorted(Comparator.comparing(ParteUniao::loteId)).toList();
        for (ParteUniao p : partes) {
            if (!ids.add(p.loteId())) throw new BusinessException("O mesmo lote aparece duas vezes.");
            pais.add(loteRepo.travar(p.loteId()).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + p.loteId())));
        }
        Lote primeiro = pais.get(0);
        for (int i = 0; i < pais.size(); i++) {
            Lote l = pais.get(i);
            if (!l.getProduto().getId().equals(primeiro.getProduto().getId())) throw new BusinessException("Só lotes do mesmo produto podem ser unidos.");
            if ("ESTORNADO".equals(l.getSituacao())) throw new BusinessException("O lote " + l.getCodigo() + " foi estornado.");
            if (!l.getSituacao().equals(primeiro.getSituacao())) {
                throw new BusinessException("Só lotes na mesma situação podem ser unidos (" + l.getCodigo() + " está " + l.getSituacao().toLowerCase() + ").");
            }
            BigDecimal disp = disponivel(l);
            if (partes.get(i).quantidade().compareTo(disp) > 0) {
                throw new BusinessException("O lote " + l.getCodigo() + " tem " + disp.stripTrailingZeros().toPlainString() + " disponível.");
            }
        }
        BigDecimal total = partes.stream().map(ParteUniao::quantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean mesmaOrdem = pais.stream().allMatch(l -> l.getOrdem() != null && primeiro.getOrdem() != null
                && l.getOrdem().getId().equals(primeiro.getOrdem().getId()));
        Lote novo = novoLote(primeiro.getProduto(), mesmaOrdem ? primeiro.getOrdem() : null, "UNIAO", total, primeiro.getSituacao(),
                "União de " + pais.size() + " lotes", usuarioId);
        movimento(novo, "UNIAO", total, pais.size() + " lotes", usuarioId);
        for (int i = 0; i < pais.size(); i++) {
            Lote l = pais.get(i);
            BigDecimal q = partes.get(i).quantidade();
            genealogiaRepo.save(LoteGenealogia.builder().lote(novo).lotePai(l).quantidade(q).build());
            l.setSaldo(l.getSaldo().subtract(q));
            loteRepo.saveAndFlush(l);
            movimento(l, "UNIAO", q.negate(), novo.getCodigo(), usuarioId);
        }
        return buscar(novo.getId());
    }

    private Lote novoLote(Produto produto, OrdemProducao ordem, String origem, BigDecimal quantidade, String situacao,
                          String motivo, Long usuarioId) {
        return loteRepo.saveAndFlush(Lote.builder().codigo(String.format("LT%06d", loteRepo.proximoNumero())).produto(produto)
                .ordem(ordem).origem(origem).quantidadeInicial(quantidade).saldo(quantidade).situacao(situacao)
                .motivoSituacao(motivo).situacaoEm(LocalDateTime.now()).criadoPorId(usuarioId).build());
    }

    // ── Saídas de saldo ──────────────────────────────────────────────────
    /** Sucateia o saldo do lote (disposição de não conformidade): baixa o estoque como descarte. */
    @Transactional
    public void sucatear(Lote lote, String documento, Long usuarioId) {
        Lote l = loteRepo.travar(lote.getId()).orElseThrow();
        if (expedicaoRepo.reservadoDoLote(l.getId()).signum() > 0) {
            throw new BusinessException("O lote " + l.getCodigo() + " está reservado numa expedição. Tire a reserva antes de sucatear.");
        }
        BigDecimal q = l.getSaldo();
        if (q.signum() > 0) {
            estoque.saida(l.getProduto(), q, "SAIDA_DESCARTE", documento, usuarioId);
            l.setSaldo(BigDecimal.ZERO);
            loteRepo.saveAndFlush(l);
            movimento(l, "SUCATA", q.negate(), documento, usuarioId);
        }
    }

    /** Baixa do saldo do lote no carregamento da expedição (o estoque do produto é baixado pela expedição). */
    @Transactional
    public void baixarExpedicao(Lote lote, BigDecimal quantidade, String documento, Long usuarioId) {
        Lote l = loteRepo.travar(lote.getId()).orElseThrow();
        if (!"LIBERADO".equals(l.getSituacao())) {
            throw new BusinessException("O lote " + l.getCodigo() + " está " + l.getSituacao().toLowerCase()
                    + " pela qualidade e não pode ser carregado.");
        }
        if (l.getSaldo().compareTo(quantidade) < 0) throw new BusinessException("O lote " + l.getCodigo() + " não tem saldo para o carregamento.");
        l.setSaldo(l.getSaldo().subtract(quantidade));
        loteRepo.saveAndFlush(l);
        movimento(l, "EXPEDICAO", quantidade.negate(), documento, usuarioId);
    }

    private void movimento(Lote l, String tipo, BigDecimal quantidade, String documento, Long usuarioId) {
        movimentoRepo.save(LoteMovimento.builder().loteId(l.getId()).tipo(tipo).quantidade(quantidade).documento(documento)
                .usuarioId(usuarioId).build());
    }

    public Lote lote(Long id) {
        return loteRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + id));
    }
}
