package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.EngenhariaDto.*;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Estrutura de produto (BOM) versionada.
 * Regras: só o rascunho é editado; um rascunho por produto; ativar torna a versão anterior obsoleta
 * (fechando a vigência dela na véspera); nenhum produto pode, direta ou indiretamente, ser componente de si mesmo.
 */
@Service
@RequiredArgsConstructor
public class BomService {

    /** Profundidade máxima da explosão (um limite de segurança; estruturas reais não chegam perto disso). */
    static final int NIVEIS_MAXIMOS = 20;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final BomRepository bomRepo;
    private final ProdutoRepository produtoRepo;

    /**
     * Quantidade a requisitar de um componente para produzir {@code quantidade} do produto:
     * proporcional à base da estrutura e acrescida da perda (perda de 10% → entra 1/0,9 do líquido).
     */
    public static BigDecimal necessidade(BigDecimal quantidadeItem, BigDecimal perdaPercentual, BigDecimal quantidadeBase,
                                         BigDecimal quantidade) {
        BigDecimal liquida = liquida(quantidadeItem, quantidadeBase, quantidade);
        BigDecimal perda = perdaPercentual == null ? BigDecimal.ZERO : perdaPercentual;
        if (perda.signum() == 0) return liquida;
        BigDecimal aproveitamento = BigDecimal.ONE.subtract(perda.divide(CEM, 10, RoundingMode.HALF_UP));
        return liquida.divide(aproveitamento, 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal liquida(BigDecimal quantidadeItem, BigDecimal quantidadeBase, BigDecimal quantidade) {
        return quantidadeItem.multiply(quantidade).divide(quantidadeBase, 6, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<BomResumo> listar(Long produtoId) {
        return bomRepo.listar(produtoId).stream().map(BomResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public BomOut buscar(Long id) {
        return BomOut.de(bom(id));
    }

    @Transactional
    public BomOut criar(BomIn in, Long usuarioId) {
        Produto produto = FichaTecnicaService.produtoAtivo(produtoRepo, in.produtoId());
        exigirSemRascunho(produto);
        Bom b = Bom.builder().produto(produto).versao(bomRepo.ultimaVersao(produto.getId()) + 1)
                .criadoPorId(usuarioId).build();
        aplicar(b, in);
        return BomOut.de(bomRepo.save(b));
    }

    @Transactional
    public BomOut editar(Long id, BomIn in) {
        Bom b = bom(id);
        exigirRascunho(b);
        if (!b.getProduto().getId().equals(in.produtoId())) {
            throw new BusinessException("O produto de uma estrutura não pode ser trocado. Crie uma estrutura para o outro produto.");
        }
        aplicar(b, in);
        return BomOut.de(bomRepo.save(b));
    }

    @Transactional
    public void excluir(Long id) {
        Bom b = bom(id);
        if (!"RASCUNHO".equals(b.getStatus())) {
            throw new BusinessException("Só rascunhos podem ser excluídos. Versões ativadas ficam no histórico.");
        }
        bomRepo.delete(b);
    }

    @Transactional
    public BomOut ativar(Long id, Long usuarioId) {
        Bom b = bom(id);
        exigirRascunho(b);
        if (b.getItens().isEmpty()) throw new BusinessException("Inclua ao menos um componente antes de ativar a estrutura.");
        for (BomItem i : b.getItens()) {
            if (!Boolean.TRUE.equals(i.getComponente().getAtivo())) {
                throw new BusinessException("O componente " + i.getComponente().getNome() + " está inativo.");
            }
            exigirSemCiclo(b.getProduto(), i.getComponente());
        }
        LocalDate hoje = LocalDate.now();
        bomRepo.ativaDoProduto(b.getProduto().getId()).ifPresent(atual -> {
            atual.setStatus("OBSOLETA");
            atual.setVigenciaFim(max(hoje.minusDays(1), atual.getVigenciaInicio()));
            // grava antes de ativar a nova: o índice único "uma ATIVA por produto" não pode ser adiado
            bomRepo.saveAndFlush(atual);
        });
        b.setStatus("ATIVA");
        b.setVigenciaInicio(hoje);
        b.setAtivadaPorId(usuarioId);
        b.setAtivadaEm(LocalDateTime.now());
        return BomOut.de(bomRepo.saveAndFlush(b));
    }

    @Transactional
    public BomOut obsoletar(Long id) {
        Bom b = bom(id);
        if (!"ATIVA".equals(b.getStatus())) throw new BusinessException("Só a versão ativa pode ser tornada obsoleta.");
        b.setStatus("OBSOLETA");
        b.setVigenciaFim(max(LocalDate.now(), b.getVigenciaInicio()));
        return BomOut.de(bomRepo.save(b));
    }

    /** Copia uma versão (de qualquer situação) para um rascunho novo, que recebe o próximo número. */
    @Transactional
    public BomOut novaVersao(Long id, Long usuarioId) {
        Bom origem = bom(id);
        exigirSemRascunho(origem.getProduto());
        Bom b = Bom.builder().produto(origem.getProduto()).versao(bomRepo.ultimaVersao(origem.getProduto().getId()) + 1)
                .quantidadeBase(origem.getQuantidadeBase()).observacao(origem.getObservacao()).criadoPorId(usuarioId).build();
        for (BomItem i : origem.getItens()) {
            b.getItens().add(BomItem.builder().bom(b).sequencia(i.getSequencia()).componente(i.getComponente())
                    .quantidade(i.getQuantidade()).perdaPercentual(i.getPerdaPercentual()).observacao(i.getObservacao()).build());
        }
        return BomOut.de(bomRepo.save(b));
    }

    /** Explosão multinível: desce pelas estruturas ATIVAS dos componentes que também são fabricados. */
    @Transactional(readOnly = true)
    public List<ExplosaoLinha> explosao(Long id, BigDecimal quantidade) {
        Bom b = bom(id);
        BigDecimal qtd = quantidade == null || quantidade.signum() <= 0 ? b.getQuantidadeBase() : quantidade;
        List<ExplosaoLinha> linhas = new ArrayList<>();
        explodir(b, qtd, 1, new HashSet<>(Set.of(b.getProduto().getId())), linhas);
        return linhas;
    }

    private void explodir(Bom b, BigDecimal quantidade, int nivel, Set<Long> caminho, List<ExplosaoLinha> linhas) {
        if (nivel > NIVEIS_MAXIMOS) throw new BusinessException("A estrutura passa de " + NIVEIS_MAXIMOS + " níveis.");
        for (BomItem i : b.getItens()) {
            Produto c = i.getComponente();
            BigDecimal necessaria = necessidade(i.getQuantidade(), i.getPerdaPercentual(), b.getQuantidadeBase(), quantidade);
            Optional<Bom> sub = caminho.contains(c.getId()) ? Optional.empty() : bomRepo.ativaDoProduto(c.getId());
            linhas.add(new ExplosaoLinha(nivel, c.getId(), c.getCodigoInterno(), c.getNome(), c.getUnidadeMedida(),
                    necessaria, sub.map(Bom::getVersao).orElse(null)));
            if (sub.isPresent()) {
                caminho.add(c.getId());
                explodir(sub.get(), necessaria, nivel + 1, caminho, linhas);
                caminho.remove(c.getId());
            }
        }
    }

    // ── regras internas ─────────────────────────────────────────────────
    private void aplicar(Bom b, BomIn in) {
        b.setQuantidadeBase(in.quantidadeBase());
        b.setObservacao(FichaTecnicaService.texto(in.observacao()));
        Set<Long> vistos = new HashSet<>();
        List<BomItem> novos = new ArrayList<>();
        int seq = 0;
        for (BomItemIn i : in.itens()) {
            Produto c = FichaTecnicaService.produtoAtivo(produtoRepo, i.componenteId());
            if (!vistos.add(c.getId())) {
                throw new BusinessException("O componente " + c.getNome() + " aparece mais de uma vez. Some as quantidades numa linha só.");
            }
            exigirSemCiclo(b.getProduto(), c);
            novos.add(BomItem.builder().bom(b).sequencia(seq += 10).componente(c).quantidade(i.quantidade())
                    .perdaPercentual(i.perdaPercentual() == null ? BigDecimal.ZERO : i.perdaPercentual())
                    .observacao(FichaTecnicaService.texto(i.observacao())).build());
        }
        b.getItens().clear();
        b.getItens().addAll(novos);
    }

    /** Recusa se {@code componente} for o próprio produto ou se a estrutura ativa dele usar o produto em algum nível. */
    void exigirSemCiclo(Produto produto, Produto componente) {
        if (componente.getId().equals(produto.getId())) {
            throw new BusinessException("Um produto não pode ser componente de si mesmo.");
        }
        if (usa(componente.getId(), produto.getId(), new HashSet<>(), 1)) {
            throw new BusinessException("Ciclo na estrutura: " + componente.getNome() + " já usa " + produto.getNome()
                    + " (direta ou indiretamente).");
        }
    }

    private boolean usa(Long produtoId, Long procurado, Set<Long> visitados, int nivel) {
        if (nivel > NIVEIS_MAXIMOS || !visitados.add(produtoId)) return false;
        Optional<Bom> ativa = bomRepo.ativaDoProduto(produtoId);
        if (ativa.isEmpty()) return false;
        for (BomItem i : ativa.get().getItens()) {
            Long c = i.getComponente().getId();
            if (c.equals(procurado) || usa(c, procurado, visitados, nivel + 1)) return true;
        }
        return false;
    }

    private void exigirRascunho(Bom b) {
        if (!"RASCUNHO".equals(b.getStatus())) {
            throw new BusinessException("A versão " + b.getVersao() + " já foi ativada e não pode mais ser alterada. "
                    + "Crie uma nova versão a partir dela.");
        }
    }

    private void exigirSemRascunho(Produto produto) {
        bomRepo.findFirstByProdutoIdAndStatus(produto.getId(), "RASCUNHO").ifPresent(r -> {
            throw new BusinessException("Já existe a versão " + r.getVersao() + " em rascunho para " + produto.getNome()
                    + ". Edite ou exclua esse rascunho.");
        });
    }

    private Bom bom(Long id) {
        return bomRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Estrutura não encontrada: " + id));
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return b == null || a.isAfter(b) ? a : b;
    }
}
