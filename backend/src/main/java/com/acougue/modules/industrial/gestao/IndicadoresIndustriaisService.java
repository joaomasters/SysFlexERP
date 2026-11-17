package com.acougue.modules.industrial.gestao;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.FichaTecnica;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.Parada;
import com.acougue.modules.industrial.execucao.ParadaRepository;
import com.acougue.modules.industrial.expedicao.Expedicao;
import com.acougue.modules.industrial.expedicao.ExpedicaoItem;
import com.acougue.modules.industrial.expedicao.ExpedicaoRepository;
import com.acougue.modules.industrial.extrusao.PerdaService;
import com.acougue.modules.industrial.gestao.GestaoDto.*;
import com.acougue.modules.industrial.pcp.*;
import com.acougue.modules.industrial.qualidade.InspecaoRepository;
import com.acougue.modules.industrial.qualidade.LoteRepository;
import com.acougue.modules.industrial.qualidade.NaoConformidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Indicadores do período: produção e refugo, cumprimento de prazo das OPs, paradas por motivo e máquina,
 * qualidade, expedição e OEE por máquina.
 *
 * OEE = disponibilidade × performance × qualidade, só quando os dados sustentam o número:
 * a máquina precisa ter turnos (tempo planejado), ao menos {@value #MIN_APONTAMENTOS} apontamentos finalizados no
 * período, tempo padrão em todas as operações apontadas, apontamentos dentro do tempo dos turnos e performance
 * plausível (até 120%). Faltando algo, o OEE fica em branco e os motivos dizem o que corrigir.
 */
@Service
@RequiredArgsConstructor
public class IndicadoresIndustriaisService {

    static final int DIAS_MAXIMOS = 92;
    static final int MIN_APONTAMENTOS = 3;
    static final BigDecimal PERFORMANCE_MAXIMA = new BigDecimal("1.20");

    private final ApontamentoRepository apontamentoRepo;
    private final ParadaRepository paradaRepo;
    private final OpEventoRepository eventoRepo;
    private final OrdemProducaoRepository ordemRepo;
    private final InspecaoRepository inspecaoRepo;
    private final NaoConformidadeRepository ncRepo;
    private final LoteRepository loteRepo;
    private final ExpedicaoRepository expedicaoRepo;
    private final MaquinaRepository maquinaRepo;
    private final FichaTecnicaRepository fichaRepo;

    /** Minutos de [inicio, fim] que caem dentro de [de, ate) (fim nulo = agora). */
    static BigDecimal minutosDentro(LocalDateTime inicio, LocalDateTime fim, LocalDateTime de, LocalDateTime ate) {
        LocalDateTime f = fim == null ? LocalDateTime.now() : fim;
        LocalDateTime a = inicio.isAfter(de) ? inicio : de;
        LocalDateTime b = f.isBefore(ate) ? f : ate;
        if (!b.isAfter(a)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Duration.between(a, b).getSeconds()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal razao(BigDecimal a, BigDecimal b) {
        return b == null || b.signum() == 0 ? null : a.divide(b, 4, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Painel painel(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) throw new BusinessException("O fim do período não pode ser antes do início.");
        if (ChronoUnit.DAYS.between(de, ate) + 1 > DIAS_MAXIMOS) throw new BusinessException("Consulte no máximo " + DIAS_MAXIMOS + " dias por vez.");
        LocalDateTime ini = de.atStartOfDay();
        LocalDateTime fim = ate.plusDays(1).atStartOfDay();

        // ── produção e refugo (apontamentos finalizados no período)
        List<Apontamento> aps = apontamentoRepo.finalizadosNoPeriodo(ini, fim);
        Map<Long, Integer> ultimaSeq = new HashMap<>();
        Map<Long, ProducaoAcc> porProduto = new LinkedHashMap<>();
        BigDecimal boaTotal = BigDecimal.ZERO;
        BigDecimal refugoTotal = BigDecimal.ZERO;
        for (Apontamento a : aps) {
            OrdemProducao o = a.getOrdem();
            int ult = ultimaSeq.computeIfAbsent(o.getId(), id -> o.getOperacoes().stream().mapToInt(OpOperacao::getSequencia).max().orElse(0));
            boaTotal = boaTotal.add(a.getQuantidadeBoa());
            refugoTotal = refugoTotal.add(a.getQuantidadeRefugo());
            ProducaoAcc acc = porProduto.computeIfAbsent(o.getProduto().getId(), id -> new ProducaoAcc(o.getProduto().getNome(), o.getProduto().getUnidadeMedida()));
            acc.refugo = acc.refugo.add(a.getQuantidadeRefugo());
            if (a.getOperacao().getSequencia() == ult) acc.boa = acc.boa.add(a.getQuantidadeBoa());
        }
        BigDecimal refugoPct = razao(refugoTotal.multiply(BigDecimal.valueOf(100)), boaTotal.add(refugoTotal));
        List<ProducaoProduto> producao = porProduto.entrySet().stream()
                .map(e -> new ProducaoProduto(e.getKey(), e.getValue().nome, e.getValue().unidade, e.getValue().boa, e.getValue().refugo))
                .sorted(Comparator.comparing(ProducaoProduto::boa).reversed()).toList();

        // ── OPs: concluídas no período (no prazo ou não) e abertas atrasadas hoje
        Map<Long, LocalDateTime> conclusao = new HashMap<>();
        for (OpEvento e : eventoRepo.conclusoesNoPeriodo(ini, fim)) conclusao.merge(e.getOrdemId(), e.getCreatedAt(), (x, y) -> x.isAfter(y) ? x : y);
        long noPrazo = 0;
        for (var e : conclusao.entrySet()) {
            OrdemProducao o = ordemRepo.findById(e.getKey()).orElse(null);
            if (o != null && !e.getValue().toLocalDate().isAfter(o.getFimPrevisto())) noPrazo++;
        }
        List<OrdemProducao> abertas = ordemRepo.listar(null).stream().filter(o -> OrdemProducao.ABERTAS.contains(o.getStatus())).toList();
        LocalDate hoje = LocalDate.now();
        long atrasadas = abertas.stream().filter(o -> o.getFimPrevisto().isBefore(hoje)).count();

        // ── paradas (recortadas ao período)
        List<Parada> paradas = paradaRepo.noPeriodo(ini, fim, null);
        Map<String, Object[]> porMotivo = new LinkedHashMap<>();
        Map<String, BigDecimal[]> porMaquina = new LinkedHashMap<>();
        BigDecimal perda = BigDecimal.ZERO;
        BigDecimal planejada = BigDecimal.ZERO;
        Map<Long, BigDecimal> planejadaPorMaquina = new HashMap<>();
        for (Parada p : paradas) {
            BigDecimal min = minutosDentro(p.getInicio(), p.getFim(), ini, fim);
            boolean plan = Boolean.TRUE.equals(p.getMotivo().getPlanejada());
            if (plan) planejada = planejada.add(min); else perda = perda.add(min);
            Object[] m = porMotivo.computeIfAbsent(p.getMotivo().getDescricao(),
                    k -> new Object[]{p.getMotivo().getCategoria(), plan, BigDecimal.ZERO, 0L});
            m[2] = ((BigDecimal) m[2]).add(min);
            m[3] = (Long) m[3] + 1;
            if (p.getMaquina() != null) {
                BigDecimal[] mq = porMaquina.computeIfAbsent(p.getMaquina().getCodigo(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                if (plan) {
                    mq[1] = mq[1].add(min);
                    planejadaPorMaquina.merge(p.getMaquina().getId(), min, BigDecimal::add);
                } else mq[0] = mq[0].add(min);
            }
        }
        List<ParadaMotivo> motivos = porMotivo.entrySet().stream()
                .map(e -> new ParadaMotivo(e.getKey(), (String) e.getValue()[0], (Boolean) e.getValue()[1], (BigDecimal) e.getValue()[2], (Long) e.getValue()[3]))
                .sorted(Comparator.comparing(ParadaMotivo::minutos).reversed()).toList();
        List<ParadaMaquina> maquinasParadas = porMaquina.entrySet().stream()
                .map(e -> new ParadaMaquina(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparing(ParadaMaquina::minutosPerda).reversed()).toList();

        // ── qualidade
        long aprovadas = 0, reprovadas = 0;
        for (Object[] r : inspecaoRepo.contagemNoPeriodo(ini, fim)) {
            if ("APROVADO".equals(r[0])) aprovadas = (Long) r[1]; else reprovadas = (Long) r[1];
        }

        // ── expedição (kg pela unidade do produto ou pela ficha técnica)
        List<Expedicao> carregadas = expedicaoRepo.carregadasNoPeriodo(ini, fim);
        BigDecimal kg = BigDecimal.ZERO;
        boolean kgCompleto = true;
        Map<Long, Optional<FichaTecnica>> fichas = new HashMap<>();
        for (Expedicao e : carregadas) {
            for (ExpedicaoItem i : e.getItens()) {
                FichaTecnica f = fichas.computeIfAbsent(i.getProduto().getId(), fichaRepo::findByProdutoId).orElse(null);
                BigDecimal q = PerdaService.emKg(i.getProduto(), i.getQuantidade(), f);
                if (q == null) kgCompleto = false; else kg = kg.add(q);
            }
        }

        return new Painel(de, ate, refugoPct, aps.size(), producao, conclusao.size(), noPrazo, atrasadas, abertas.size(),
                perda, planejada, motivos, maquinasParadas, aprovadas, reprovadas, ncRepo.listar("ABERTA").size(),
                loteRepo.listar("BLOQUEADO", null).size(), loteRepo.listar("QUARENTENA", null).size(),
                carregadas.size(), kg.setScale(2, RoundingMode.HALF_UP), kgCompleto,
                oee(aps, planejadaPorMaquina, de, ate, ini, fim));
    }

    private List<OeeMaquina> oee(List<Apontamento> aps, Map<Long, BigDecimal> planejadaPorMaquina, LocalDate de, LocalDate ate,
                                 LocalDateTime ini, LocalDateTime fim) {
        Map<Long, List<Apontamento>> porMaquina = new LinkedHashMap<>();
        for (Apontamento a : aps) if (a.getMaquina() != null) porMaquina.computeIfAbsent(a.getMaquina().getId(), k -> new ArrayList<>()).add(a);
        if (porMaquina.isEmpty()) return List.of();
        Map<Long, Maquina> comTurnos = new HashMap<>();
        for (Maquina m : maquinaRepo.ativasComTurnos()) comTurnos.put(m.getId(), m);

        List<OeeMaquina> r = new ArrayList<>();
        for (var e : porMaquina.entrySet()) {
            Maquina m = e.getValue().get(0).getMaquina();
            Maquina t = comTurnos.get(m.getId());
            List<String> motivos = new ArrayList<>();
            BigDecimal capacidade = t == null ? BigDecimal.ZERO : BigDecimal.valueOf(CargaService.capacidadeMinutos(t.getTurnos(), de, ate));
            BigDecimal planejado = capacidade.subtract(planejadaPorMaquina.getOrDefault(m.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            BigDecimal operando = BigDecimal.ZERO;
            BigDecimal padrao = BigDecimal.ZERO;
            BigDecimal boa = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            boolean semPadrao = false;
            for (Apontamento a : e.getValue()) {
                BigDecimal duracao = minutosDentro(a.getInicio(), a.getFim(), a.getInicio(), a.getFim().plusSeconds(1));
                BigDecimal dentro = minutosDentro(a.getInicio(), a.getFim(), ini, fim);
                BigDecimal fracao = duracao.signum() == 0 ? BigDecimal.ONE : dentro.divide(duracao, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
                BigDecimal trabalhado = duracao.subtract(a.getMinutosParado()).max(BigDecimal.ZERO).multiply(fracao);
                operando = operando.add(trabalhado);
                OpOperacao op = a.getOperacao();
                BigDecimal unit = op.getTempoPrevistoMin().subtract(op.getTempoSetupMin()).max(BigDecimal.ZERO)
                        .divide(a.getOrdem().getQuantidade(), 6, RoundingMode.HALF_UP);
                if (unit.signum() == 0) semPadrao = true;
                BigDecimal q = a.getQuantidadeBoa().add(a.getQuantidadeRefugo());
                padrao = padrao.add(q.multiply(unit).multiply(fracao));
                boa = boa.add(a.getQuantidadeBoa().multiply(fracao));
                total = total.add(q.multiply(fracao));
            }
            BigDecimal disp = razao(operando, planejado);
            BigDecimal perf = razao(padrao, operando);
            BigDecimal qual = razao(boa, total);
            if (planejado.signum() == 0) motivos.add("Máquina sem turnos no período (cadastre os turnos dela na estrutura).");
            if (e.getValue().size() < MIN_APONTAMENTOS) motivos.add("Menos de " + MIN_APONTAMENTOS + " apontamentos finalizados no período.");
            if (semPadrao) motivos.add("Há operação apontada sem tempo padrão no roteiro.");
            if (disp != null && disp.compareTo(new BigDecimal("1.05")) > 0) motivos.add("Tempo apontado maior que o tempo dos turnos: confira os turnos ou os apontamentos.");
            if (perf != null && perf.compareTo(PERFORMANCE_MAXIMA) > 0) motivos.add("Performance acima de 120%: o tempo padrão do roteiro parece desatualizado.");
            boolean confiavel = motivos.isEmpty() && disp != null && perf != null && qual != null;
            BigDecimal oee = confiavel ? disp.multiply(perf).multiply(qual).setScale(4, RoundingMode.HALF_UP) : null;
            r.add(new OeeMaquina(m.getId(), m.getCodigo(), m.getDescricao(), planejado, operando.setScale(2, RoundingMode.HALF_UP),
                    e.getValue().size(), disp, perf, qual, oee, confiavel, motivos));
        }
        r.sort(Comparator.comparing(OeeMaquina::codigo));
        return r;
    }

    private static final class ProducaoAcc {
        final String nome;
        final String unidade;
        BigDecimal boa = BigDecimal.ZERO;
        BigDecimal refugo = BigDecimal.ZERO;

        ProducaoAcc(String nome, String unidade) {
            this.nome = nome;
            this.unidade = unidade;
        }
    }
}
