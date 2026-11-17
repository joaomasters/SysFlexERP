package com.acougue.modules.industrial.gestao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.engenharia.*;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.Consumo;
import com.acougue.modules.industrial.extrusao.ConsumoLoteRepository;
import com.acougue.modules.industrial.gestao.GestaoDto.*;
import com.acougue.modules.industrial.pcp.OpMaterial;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import com.acougue.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * MRP: necessidade líquida de materiais das OPs abertas, em todos os níveis da estrutura.
 * Necessidade bruta = o que as OPs ainda vão consumir (liberadas: previsto − já consumido; planejadas: pela
 * estrutura ativa). Líquida = bruta − estoque − o que já está sendo produzido em OPs abertas (+ estoque mínimo,
 * se pedido). Item com estrutura e roteiro ativos vira sugestão de PRODUZIR (respeitando lote mínimo e múltiplo da
 * ficha) e explode para os componentes; os demais viram sugestão de COMPRAR.
 * As sugestões não são aprovadas sozinhas: a OP só nasce quando o usuário cria pela tela.
 * Os itens são processados por código de nível baixo, para cada produto ser liquidado uma vez só com toda a demanda.
 */
@Service
@RequiredArgsConstructor
public class MrpService {

    static final int NIVEIS_MAXIMOS = 20;

    private final OrdemProducaoRepository ordemRepo;
    private final BomRepository bomRepo;
    private final RoteiroRepository roteiroRepo;
    private final FichaTecnicaRepository fichaRepo;
    private final ProdutoRepository produtoRepo;
    private final ConsumoLoteRepository consumoRepo;
    private final ApontamentoRepository apontamentoRepo;

    /** Arredonda para o lote: no mínimo o lote mínimo e, havendo múltiplo, o próximo múltiplo acima. */
    static BigDecimal aplicarLote(BigDecimal liquida, FichaTecnica f) {
        BigDecimal q = liquida;
        if (f == null) return q;
        if (f.getLoteMinimo() != null && q.compareTo(f.getLoteMinimo()) < 0) q = f.getLoteMinimo();
        if (f.getLoteMultiplo() != null && f.getLoteMultiplo().signum() > 0) {
            q = q.divide(f.getLoteMultiplo(), 0, RoundingMode.CEILING).multiply(f.getLoteMultiplo());
        }
        return q;
    }

    private static final class Demanda {
        BigDecimal bruta = BigDecimal.ZERO;
        LocalDate necessarioEm;
        final Set<String> origens = new LinkedHashSet<>();

        void somar(BigDecimal q, LocalDate data, String origem) {
            bruta = bruta.add(q);
            if (data != null && (necessarioEm == null || data.isBefore(necessarioEm))) necessarioEm = data;
            origens.add(origem);
        }
    }

    @Transactional(readOnly = true)
    public ResultadoMrp calcular(boolean incluirEstoqueMinimo) {
        LocalDate hoje = LocalDate.now();
        List<String> avisos = new ArrayList<>();
        List<OrdemProducao> abertas = ordemRepo.listar(null).stream().filter(o -> OrdemProducao.ABERTAS.contains(o.getStatus())).toList();
        Map<Long, Demanda> demanda = new HashMap<>();
        Map<Long, BigDecimal> emProducao = new HashMap<>();

        Map<Long, List<Apontamento>> apontamentos = new HashMap<>();
        if (!abertas.isEmpty()) {
            for (Apontamento a : apontamentoRepo.validosDasOrdens(abertas.stream().map(OrdemProducao::getId).toList())) {
                apontamentos.computeIfAbsent(a.getOrdem().getId(), k -> new ArrayList<>()).add(a);
            }
        }

        for (OrdemProducao o : abertas) {
            LocalDate data = o.getInicioPrevisto().isBefore(hoje) ? hoje : o.getInicioPrevisto();
            // recebimento previsto: o que a OP ainda vai entregar do produto
            OpOperacao ultima = o.getOperacoes().stream().max(Comparator.comparing(OpOperacao::getSequencia)).orElse(null);
            BigDecimal produzido = ultima == null ? BigDecimal.ZERO : apontamentos.getOrDefault(o.getId(), List.of()).stream()
                    .filter(a -> a.getOperacao().getId().equals(ultima.getId())).map(Apontamento::getQuantidadeBoa)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            emProducao.merge(o.getProduto().getId(), o.getQuantidade().subtract(produzido).max(BigDecimal.ZERO), BigDecimal::add);

            if ("PLANEJADA".equals(o.getStatus())) {
                Optional<Bom> bom = bomRepo.ativaDoProduto(o.getProduto().getId());
                if (bom.isEmpty()) {
                    avisos.add(o.getNumero() + ": produto sem estrutura ativa, materiais fora do cálculo.");
                    continue;
                }
                for (BomItem i : bom.get().getItens()) {
                    demanda.computeIfAbsent(i.getComponente().getId(), k -> new Demanda()).somar(
                            BomService.necessidade(i.getQuantidade(), i.getPerdaPercentual(), bom.get().getQuantidadeBase(), o.getQuantidade()),
                            data, o.getNumero());
                }
            } else {
                Map<Long, BigDecimal> consumido = new HashMap<>();
                for (Consumo c : consumoRepo.validosDaOrdem(o.getId())) consumido.merge(c.getProduto().getId(), c.getQuantidade(), BigDecimal::add);
                for (OpMaterial m : o.getMateriais()) {
                    BigDecimal falta = m.getQuantidadePrevista().subtract(consumido.getOrDefault(m.getProduto().getId(), BigDecimal.ZERO));
                    if (falta.signum() > 0) demanda.computeIfAbsent(m.getProduto().getId(), k -> new Demanda()).somar(falta, data, o.getNumero());
                }
            }
        }

        // código de nível baixo: o nível mais profundo em que o produto aparece nas estruturas ativas
        Map<Long, Integer> nivel = new HashMap<>();
        Map<Long, Optional<Bom>> boms = new HashMap<>();
        for (Long p : new ArrayList<>(demanda.keySet())) descer(p, 0, nivel, boms, new HashSet<>());

        List<Long> ordem = new ArrayList<>(nivel.keySet());
        ordem.sort(Comparator.comparing((Long p) -> nivel.get(p)).thenComparing(p -> p));
        Map<Long, FichaTecnica> fichas = new HashMap<>();
        if (!ordem.isEmpty()) fichaRepo.findByProdutoIdIn(ordem).forEach(f -> fichas.put(f.getProduto().getId(), f));

        List<SugestaoMrp> sugestoes = new ArrayList<>();
        for (Long produtoId : ordem) {
            Demanda dm = demanda.get(produtoId);
            Produto p = produtoRepo.findById(produtoId).orElse(null);
            if (p == null) continue;
            BigDecimal estoque = p.getEstoqueAtual() == null ? BigDecimal.ZERO : p.getEstoqueAtual().max(BigDecimal.ZERO);
            BigDecimal minimo = incluirEstoqueMinimo && p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : BigDecimal.ZERO;
            BigDecimal bruta = dm == null ? BigDecimal.ZERO : dm.bruta;
            BigDecimal recebimento = emProducao.getOrDefault(produtoId, BigDecimal.ZERO);
            BigDecimal liquida = bruta.add(minimo).subtract(estoque).subtract(recebimento);
            if (liquida.signum() <= 0) continue;
            liquida = liquida.setScale(4, RoundingMode.UP);

            Optional<Bom> bom = boms.computeIfAbsent(produtoId, bomRepo::ativaDoProduto);
            boolean fabricado = bom.isPresent() && roteiroRepo.ativoDoProduto(produtoId).isPresent();
            FichaTecnica ficha = fichas.get(produtoId);
            LocalDate necessario = dm == null || dm.necessarioEm == null ? hoje : dm.necessarioEm;
            int prazo = ficha != null && ficha.getPrazoProducaoDias() != null ? ficha.getPrazoProducaoDias() : 0;
            LocalDate iniciar = necessario.minusDays(prazo);
            if (iniciar.isBefore(hoje)) iniciar = hoje;
            List<String> origens = new ArrayList<>(dm == null ? List.of() : dm.origens);
            if (minimo.signum() > 0 && (dm == null || bruta.subtract(estoque).subtract(recebimento).signum() <= 0)) origens.add("estoque mínimo");

            if (fabricado) {
                BigDecimal sugerida = aplicarLote(liquida, ficha);
                String obs = sugerida.compareTo(liquida) > 0 ? "Arredondado pelo lote da ficha técnica." : null;
                sugestoes.add(new SugestaoMrp(produtoId, p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(), "PRODUZIR",
                        nivel.get(produtoId), bruta, estoque, recebimento, minimo, liquida, sugerida, necessario, iniciar, origens, obs));
                // a OP sugerida também precisa de materiais: explode para o nível de baixo
                for (BomItem i : bom.get().getItens()) {
                    demanda.computeIfAbsent(i.getComponente().getId(), k -> new Demanda()).somar(
                            BomService.necessidade(i.getQuantidade(), i.getPerdaPercentual(), bom.get().getQuantidadeBase(), sugerida),
                            iniciar, "sugestão " + p.getNome());
                }
            } else {
                String obs = bom.isPresent() ? "Tem estrutura mas não tem roteiro ativo: sugerido como compra." : null;
                sugestoes.add(new SugestaoMrp(produtoId, p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(), "COMPRAR",
                        nivel.get(produtoId), bruta, estoque, recebimento, minimo, liquida, liquida, necessario, iniciar, origens, obs));
            }
        }
        sugestoes.sort(Comparator.comparing(SugestaoMrp::iniciarEm).thenComparing(SugestaoMrp::nivel).thenComparing(SugestaoMrp::nome));
        return new ResultadoMrp(hoje, abertas.size(), incluirEstoqueMinimo, sugestoes, avisos);
    }

    /** Registra o nível de cada produto alcançável a partir de {@code produtoId} pelas estruturas ativas. */
    private void descer(Long produtoId, int n, Map<Long, Integer> nivel, Map<Long, Optional<Bom>> boms, Set<Long> caminho) {
        if (n > NIVEIS_MAXIMOS || !caminho.add(produtoId)) return;
        nivel.merge(produtoId, n, Math::max);
        Optional<Bom> bom = boms.computeIfAbsent(produtoId, bomRepo::ativaDoProduto);
        if (bom.isPresent()) for (BomItem i : bom.get().getItens()) descer(i.getComponente().getId(), n + 1, nivel, boms, caminho);
        caminho.remove(produtoId);
    }
}
