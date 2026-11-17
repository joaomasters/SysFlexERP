package com.acougue.modules.industrial.gestao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.estrutura.CentroTrabalho;
import com.acougue.modules.industrial.estrutura.CentroTrabalhoRepository;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.Consumo;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.extrusao.ConsumoLoteRepository;
import com.acougue.modules.industrial.gestao.GestaoDto.*;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import com.acougue.modules.industrial.pcp.OrdemProducaoService;
import com.acougue.modules.industrial.pcp.PcpDto.MaterialOut;
import com.acougue.modules.industrial.pcp.PcpDto.OperacaoOut;
import com.acougue.modules.industrial.pcp.PcpDto.OrdemOut;
import com.acougue.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Custo estimado × real da OP.
 * Estimado: materiais previstos (congelados na liberação, ou prévia da engenharia na OP planejada) × custo médio
 * atual do produto + tempo previsto de cada operação × custo/hora (da máquina planejada, senão do centro).
 * Real: consumos válidos × custo do momento do consumo + tempo trabalhado dos apontamentos finalizados
 * (sem as pausas) × custo/hora da máquina apontada, senão do centro. Unitário real = total ÷ produzido bom.
 * Faltando custo de produto ou custo/hora, a parcela conta zero e um aviso diz o que cadastrar.
 */
@Service
@RequiredArgsConstructor
public class CustoService {

    static final BigDecimal SESSENTA = BigDecimal.valueOf(60);

    private final OrdemProducaoService ordemService;
    private final OrdemProducaoRepository ordemRepo;
    private final ProdutoRepository produtoRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final MaquinaRepository maquinaRepo;
    private final ConsumoLoteRepository consumoRepo;
    private final ApontamentoRepository apontamentoRepo;

    static BigDecimal porHora(BigDecimal minutos, BigDecimal custoHora) {
        if (minutos == null || custoHora == null) return BigDecimal.ZERO;
        return minutos.multiply(custoHora).divide(SESSENTA, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal dividir(BigDecimal a, BigDecimal b) {
        return b == null || b.signum() == 0 ? null : a.divide(b, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal desvio(BigDecimal real, BigDecimal estimado) {
        if (real == null || estimado == null || estimado.signum() == 0) return null;
        return real.subtract(estimado).multiply(BigDecimal.valueOf(100)).divide(estimado, 1, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<CustoResumo> listar(String status) {
        List<String> situacoes = status == null || status.isBlank() ? List.of("EM_PRODUCAO", "PAUSADA", "CONCLUIDA", "ENCERRADA") : List.of(status);
        List<CustoResumo> r = new ArrayList<>();
        for (String s : situacoes) {
            for (var o : ordemRepo.listar(s).stream().limit(200).toList()) {
                CustoOP c = custo(o.getId());
                r.add(new CustoResumo(c.ordemId(), c.numero(), c.produtoNome(), c.unidade(), c.status(), c.quantidade(), c.produzido(),
                        c.estimado().unitario(), c.real().unitario(), c.desvioPercentual(), c.avisos().size()));
            }
        }
        r.sort(Comparator.comparing(CustoResumo::ordemId).reversed());
        return r;
    }

    @Transactional(readOnly = true)
    public CustoOP custo(Long ordemId) {
        OrdemOut d = ordemService.buscar(ordemId);
        OrdemProducao o = ordemRepo.findById(ordemId).orElseThrow();
        Set<String> avisos = new LinkedHashSet<>();
        Map<Long, CentroTrabalho> centros = new HashMap<>();
        Map<Long, Maquina> maquinas = new HashMap<>();

        // ── materiais: previsto × custo atual; real = consumos × custo do momento
        Map<Long, BigDecimal> consumido = new HashMap<>();
        Map<Long, BigDecimal> custoConsumido = new HashMap<>();
        for (Consumo c : consumoRepo.validosDaOrdem(ordemId)) {
            consumido.merge(c.getProduto().getId(), c.getQuantidade(), BigDecimal::add);
            if (c.getCustoUnitario() == null) avisos.add("Consumo de " + c.getProduto().getNome() + " sem custo médio no momento da baixa.");
            BigDecimal valor = c.getCustoUnitario() == null ? BigDecimal.ZERO : c.getQuantidade().multiply(c.getCustoUnitario());
            custoConsumido.merge(c.getProduto().getId(), valor, BigDecimal::add);
        }
        List<LinhaMaterial> materiais = new ArrayList<>();
        BigDecimal matEst = BigDecimal.ZERO;
        BigDecimal matReal = BigDecimal.ZERO;
        for (MaterialOut m : d.materiais()) {
            Produto p = produtoRepo.findById(m.produtoId()).orElse(null);
            BigDecimal custo = p == null ? null : p.getPrecoCusto();
            if (custo == null || custo.signum() == 0) avisos.add(m.nome() + " sem custo médio no cadastro: a estimativa de material fica incompleta.");
            BigDecimal previsto = custo == null ? BigDecimal.ZERO : m.quantidadePrevista().multiply(custo).setScale(2, RoundingMode.HALF_UP);
            BigDecimal real = custoConsumido.getOrDefault(m.produtoId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            matEst = matEst.add(previsto);
            matReal = matReal.add(real);
            materiais.add(new LinhaMaterial(m.produtoId(), m.nome(), m.unidade(), m.quantidadePrevista(),
                    consumido.getOrDefault(m.produtoId(), BigDecimal.ZERO), previsto, real));
        }

        // ── processo: tempo previsto × custo/hora; real = tempo trabalhado × custo/hora
        Map<Long, BigDecimal> minReal = new HashMap<>();
        Map<Long, BigDecimal> custoReal = new HashMap<>();
        BigDecimal produzido = BigDecimal.ZERO;
        int ultimaSeq = d.operacoes().stream().mapToInt(OperacaoOut::sequencia).max().orElse(0);
        for (Apontamento a : apontamentoRepo.daOrdem(ordemId)) {
            if (!"FINALIZADO".equals(a.getStatus())) continue;
            BigDecimal min = ExecucaoMapper.minutos(a.getInicio(), a.getFim()).subtract(a.getMinutosParado()).max(BigDecimal.ZERO);
            BigDecimal taxa = a.getMaquina() != null && a.getMaquina().getCustoHora() != null ? a.getMaquina().getCustoHora()
                    : a.getOperacao().getCentroTrabalho().getCustoHora();
            if (taxa == null) avisos.add("Centro " + a.getOperacao().getCentroTrabalho().getNome() + " sem custo/hora: o tempo apontado não entra no custo.");
            minReal.merge(a.getOperacao().getId(), min, BigDecimal::add);
            custoReal.merge(a.getOperacao().getId(), porHora(min, taxa), BigDecimal::add);
            if (a.getOperacao().getSequencia() == ultimaSeq) produzido = produzido.add(a.getQuantidadeBoa());
        }
        List<LinhaProcesso> processo = new ArrayList<>();
        BigDecimal procEst = BigDecimal.ZERO;
        BigDecimal procReal = BigDecimal.ZERO;
        for (OperacaoOut op : d.operacoes()) {
            BigDecimal taxa = null;
            if (op.maquinaId() != null) {
                taxa = maquinas.computeIfAbsent(op.maquinaId(), id -> maquinaRepo.findById(id).orElse(null)) == null ? null
                        : maquinas.get(op.maquinaId()).getCustoHora();
            }
            if (taxa == null) {
                CentroTrabalho c = centros.computeIfAbsent(op.centroTrabalhoId(), id -> centroRepo.findById(id).orElse(null));
                taxa = c == null ? null : c.getCustoHora();
            }
            if (taxa == null) avisos.add("Centro " + op.centroTrabalhoNome() + " sem custo/hora: a estimativa de processo fica incompleta.");
            BigDecimal previsto = porHora(op.tempoPrevistoMin(), taxa);
            BigDecimal real = op.id() == null ? BigDecimal.ZERO : custoReal.getOrDefault(op.id(), BigDecimal.ZERO);
            procEst = procEst.add(previsto);
            procReal = procReal.add(real);
            processo.add(new LinhaProcesso(op.sequencia(), op.descricao(), op.centroTrabalhoNome(), taxa, op.tempoPrevistoMin(),
                    op.id() == null ? BigDecimal.ZERO : minReal.getOrDefault(op.id(), BigDecimal.ZERO), previsto, real));
        }

        BigDecimal estTotal = matEst.add(procEst);
        BigDecimal realTotal = matReal.add(procReal);
        Parcela estimado = new Parcela(matEst, procEst, estTotal, dividir(estTotal, o.getQuantidade()));
        Parcela real = new Parcela(matReal, procReal, realTotal, dividir(realTotal, produzido));
        if (produzido.signum() == 0 && realTotal.signum() > 0) avisos.add("Ainda não há produção boa na última operação: sem custo unitário real.");
        return new CustoOP(o.getId(), o.getNumero(), o.getProduto().getNome(), o.getProduto().getUnidadeMedida(), o.getStatus(),
                o.getQuantidade(), produzido, estimado, real, desvio(real.unitario(), estimado.unitario()), materiais, processo,
                new ArrayList<>(avisos));
    }
}
