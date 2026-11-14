package com.acougue.modules.industrial.pcp;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.Roteiro;
import com.acougue.modules.industrial.engenharia.RoteiroRepository;
import com.acougue.modules.industrial.estrutura.CentroTrabalho;
import com.acougue.modules.industrial.estrutura.CentroTrabalhoRepository;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.estrutura.Turno;
import com.acougue.modules.industrial.pcp.PcpDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Planejamento básico: carga das OPs abertas × capacidade de cada centro de trabalho num período.
 * Capacidade = soma, por máquina não inativa do centro, dos minutos dos turnos dela em cada dia do período.
 * Carga = tempo previsto das operações (congeladas na OP liberada, ou estimadas pelo roteiro ativo na planejada),
 * rateado pelos dias da OP que caem no período. É uma visão de apoio: não reprograma nada sozinha.
 */
@Service
@RequiredArgsConstructor
public class CargaService {

    static final int DIAS_MAXIMOS = 92;

    private final OrdemProducaoRepository ordemRepo;
    private final RoteiroRepository roteiroRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final MaquinaRepository maquinaRepo;

    /** Minutos de trabalho de uma máquina em [de, ate], somando os turnos ativos dela em cada dia da semana marcado. */
    static long capacidadeMinutos(Collection<Turno> turnos, LocalDate de, LocalDate ate) {
        long total = 0;
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            int dia = d.getDayOfWeek().getValue() - 1; // diasSemana começa na segunda
            for (Turno t : turnos) {
                if (Boolean.TRUE.equals(t.getAtivo()) && t.getDiasSemana().charAt(dia) == '1') {
                    total += Turno.minutos(t.getHoraInicio(), t.getHoraFim());
                }
            }
        }
        return total;
    }

    /** Parte do tempo de uma OP que cai em [de, ate], proporcional aos dias. */
    static BigDecimal rateio(BigDecimal minutos, LocalDate inicioOp, LocalDate fimOp, LocalDate de, LocalDate ate) {
        LocalDate ini = inicioOp.isAfter(de) ? inicioOp : de;
        LocalDate fim = fimOp.isBefore(ate) ? fimOp : ate;
        if (fim.isBefore(ini)) return BigDecimal.ZERO;
        long dentro = ChronoUnit.DAYS.between(ini, fim) + 1;
        long total = ChronoUnit.DAYS.between(inicioOp, fimOp) + 1;
        return minutos.multiply(BigDecimal.valueOf(dentro)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public CargaOut carga(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) throw new BusinessException("O fim do período não pode ser antes do início.");
        if (ChronoUnit.DAYS.between(de, ate) + 1 > DIAS_MAXIMOS) {
            throw new BusinessException("Consulte no máximo " + DIAS_MAXIMOS + " dias por vez.");
        }

        Map<Long, CentroTrabalho> centros = new LinkedHashMap<>();
        centroRepo.listarTodos().stream().filter(CentroTrabalho::getAtivo).forEach(c -> centros.put(c.getId(), c));
        Map<Long, Long> capacidade = new HashMap<>();
        Map<Long, Integer> maquinas = new HashMap<>();
        for (Maquina m : maquinaRepo.ativasComTurnos()) {
            Long c = m.getCentroTrabalho().getId();
            capacidade.merge(c, capacidadeMinutos(m.getTurnos(), de, ate), Long::sum);
            maquinas.merge(c, 1, Integer::sum);
        }

        Map<Long, List<CargaOp>> cargas = new HashMap<>();
        List<String> semRoteiro = new ArrayList<>();
        for (OrdemProducao o : ordemRepo.noPeriodo(OrdemProducao.ABERTAS, de, ate)) {
            if ("PLANEJADA".equals(o.getStatus())) {
                Optional<Roteiro> roteiro = roteiroRepo.ativoDoProduto(o.getProduto().getId());
                if (roteiro.isEmpty()) { semRoteiro.add(o.getNumero()); continue; }
                roteiro.get().getOperacoes().forEach(op -> somar(cargas, op.getCentroTrabalho().getId(), o,
                        OrdemProducaoService.tempoPrevisto(op.getTempoSetupMin(), op.getTempoPorUnidadeMin(), o.getQuantidade()),
                        true, de, ate));
            } else {
                o.getOperacoes().stream().filter(op -> !"CONCLUIDA".equals(op.getStatus()))
                        .forEach(op -> somar(cargas, op.getCentroTrabalho().getId(), o, op.getTempoPrevistoMin(), false, de, ate));
            }
        }

        List<CargaCentro> resultado = new ArrayList<>();
        for (CentroTrabalho c : centros.values()) {
            List<CargaOp> ops = cargas.getOrDefault(c.getId(), List.of());
            BigDecimal carga = ops.stream().map(CargaOp::minutos).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cap = BigDecimal.valueOf(capacidade.getOrDefault(c.getId(), 0L));
            BigDecimal ocupacao = cap.signum() == 0 ? null
                    : carga.multiply(BigDecimal.valueOf(100)).divide(cap, 1, RoundingMode.HALF_UP);
            resultado.add(new CargaCentro(c.getId(), c.getCodigo(), c.getNome(), maquinas.getOrDefault(c.getId(), 0), cap,
                    carga, ocupacao, ops));
        }
        return new CargaOut(de, ate, resultado, semRoteiro);
    }

    private static void somar(Map<Long, List<CargaOp>> cargas, Long centroId, OrdemProducao o, BigDecimal minutos,
                              boolean estimada, LocalDate de, LocalDate ate) {
        BigDecimal parte = rateio(minutos, o.getInicioPrevisto(), o.getFimPrevisto(), de, ate);
        if (parte.signum() == 0) return;
        List<CargaOp> lista = cargas.computeIfAbsent(centroId, k -> new ArrayList<>());
        // a mesma OP pode passar duas vezes pelo centro: soma numa linha só
        for (int i = 0; i < lista.size(); i++) {
            CargaOp x = lista.get(i);
            if (x.ordemId().equals(o.getId())) {
                lista.set(i, new CargaOp(x.ordemId(), x.numero(), x.produtoNome(), x.status(), x.minutos().add(parte), estimada));
                return;
            }
        }
        lista.add(new CargaOp(o.getId(), o.getNumero(), o.getProduto().getNome(), o.getStatus(), parte, estimada));
    }
}
