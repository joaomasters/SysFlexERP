package com.acougue.modules.financeiro;

import com.acougue.entity.Caixa;
import com.acougue.entity.ContasAReceber;
import com.acougue.entity.Venda;
import com.acougue.modules.financeiro.dto.IndicadoresDTO.*;
import com.acougue.repository.CaixaRepository;
import com.acougue.repository.ContasAReceberRepository;
import com.acougue.repository.ItensVendaRepository;
import com.acougue.repository.VendaRepository;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.IdentificacaoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Relatórios de indicadores da tela Relatórios: horários de pico, curva ABC, margem por tipo,
 * operadores, fechamentos de caixa e inadimplência. Só vendas FECHADAS entram nos números de venda.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicadoresService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    // Corte usual da curva ABC: A até 80% do faturamento acumulado, B até 95%, C o resto
    private static final BigDecimal LIMITE_A = BigDecimal.valueOf(80);
    private static final BigDecimal LIMITE_B = BigDecimal.valueOf(95);

    private final VendaRepository vendaRepo;
    private final ItensVendaRepository itensRepo;
    private final CaixaRepository caixaRepo;
    private final ContasAReceberRepository contasReceberRepo;
    private final IdentificacaoUsuario identificacao;

    public Horarios horarios(LocalDateTime inicio, LocalDateTime fim) {
        List<Venda> vendas = fechadas(inicio, fim);
        Map<Integer, List<Venda>> porHora = vendas.stream().collect(Collectors.groupingBy(v -> v.getDataVenda().getHour()));
        Map<Integer, List<Venda>> porDia = vendas.stream()
                .collect(Collectors.groupingBy(v -> v.getDataVenda().getDayOfWeek().getValue()));
        return new Horarios(
                IntStream.range(0, 24).mapToObj(h -> faixa(h, porHora.get(h))).toList(),
                IntStream.rangeClosed(1, 7).mapToObj(d -> faixa(d, porDia.get(d))).toList());
    }

    public List<ProdutoAbc> curvaAbc(LocalDateTime inicio, LocalDateTime fim) {
        List<Object[]> linhas = new ArrayList<>(itensRepo.vendasPorProduto(inicio, fim));
        linhas.sort(Comparator.comparing((Object[] l) -> dec(l[4])).reversed());
        BigDecimal total = linhas.stream().map(l -> dec(l[4])).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProdutoAbc> resultado = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Object[] l : linhas) {
            BigDecimal faturamento = dec(l[4]);
            BigDecimal custo = dec(l[5]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal participacao = percentual(faturamento, total);
            // A classe vem do acumulado ANTES deste produto: o que atravessa o corte ainda é da classe de cima
            String classe = acumulado.compareTo(LIMITE_A) < 0 ? "A" : acumulado.compareTo(LIMITE_B) < 0 ? "B" : "C";
            acumulado = acumulado.add(participacao);
            BigDecimal margem = faturamento.subtract(custo);
            resultado.add(new ProdutoAbc((Long) l[0], (String) l[1], (String) l[2], dec(l[3]), faturamento, custo,
                    margem, percentual(margem, faturamento), participacao, acumulado.min(CEM), classe));
        }
        return resultado;
    }

    public List<MargemTipo> margemPorTipo(LocalDateTime inicio, LocalDateTime fim) {
        List<Object[]> linhas = itensRepo.vendasPorProduto(inicio, fim);
        BigDecimal total = linhas.stream().map(l -> dec(l[4])).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, List<Object[]>> porTipo = linhas.stream()
                .collect(Collectors.groupingBy(l -> l[2] == null ? "OUTROS" : (String) l[2]));
        return porTipo.entrySet().stream().map(e -> {
            BigDecimal faturamento = e.getValue().stream().map(l -> dec(l[4])).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal custo = e.getValue().stream().map(l -> dec(l[5])).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal margem = faturamento.subtract(custo);
            return new MargemTipo(e.getKey(), faturamento, custo, margem, percentual(margem, faturamento),
                    percentual(faturamento, total), e.getValue().size());
        }).sorted(Comparator.comparing(MargemTipo::faturamento).reversed()).toList();
    }

    /** Mostra quem vendeu: só para quem pode ver a identificação dos usuários (como a Auditoria). */
    public List<Operador> operadores(LocalDateTime inicio, LocalDateTime fim) {
        ContextoUsuario.exigirVisualizacaoIdentificacaoUsuarios();
        List<Venda> todas = vendaRepo.findByPeriodo(inicio, fim);
        Map<Long, BigDecimal> descontosItens = new HashMap<>();
        for (Object[] l : itensRepo.descontosPorOperador(inicio, fim)) {
            descontosItens.put((Long) l[0], dec(l[1]));
        }
        Map<Long, List<Venda>> porOperador = todas.stream()
                .filter(v -> v.getOperadorId() != null)
                .collect(Collectors.groupingBy(Venda::getOperadorId));
        Map<Long, String> nomes = identificacao.nomesPorId(porOperador.keySet());

        return porOperador.entrySet().stream().map(e -> {
            List<Venda> fechadas = e.getValue().stream().filter(v -> "FECHADA".equals(v.getStatus())).toList();
            long canceladas = e.getValue().stream().filter(v -> "CANCELADA".equals(v.getStatus())).count();
            BigDecimal total = soma(fechadas);
            BigDecimal descontoVenda = fechadas.stream().map(v -> v.getDesconto() == null ? BigDecimal.ZERO : v.getDesconto())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Operador(e.getKey(), nomes.getOrDefault(e.getKey(), identificacao.rotuloDesconhecido(e.getKey())),
                    fechadas.size(), total, media(total, fechadas.size()),
                    descontoVenda.add(descontosItens.getOrDefault(e.getKey(), BigDecimal.ZERO)), canceladas);
        }).sorted(Comparator.comparing(Operador::total).reversed()).toList();
    }

    /** Sobras e faltas: o que o operador contou na gaveta menos o que o sistema esperava. */
    public List<FechamentoCaixa> fechamentosCaixa(LocalDateTime inicio, LocalDateTime fim) {
        ContextoUsuario.exigirVisualizacaoIdentificacaoUsuarios();
        List<Caixa> caixas = caixaRepo.findByStatusAndDataFechamentoBetweenOrderByDataFechamentoDesc("FECHADO", inicio, fim);
        Map<Long, String> nomes = identificacao.nomesPorId(caixas.stream().map(Caixa::getOperadorId).toList());
        return caixas.stream().map(c -> {
            BigDecimal esperado = c.getValorCalculado();
            BigDecimal informado = c.getValorFechamentoInformado();
            BigDecimal diferenca = esperado != null && informado != null ? informado.subtract(esperado) : null;
            return new FechamentoCaixa(c.getId(),
                    c.getOperadorId() == null ? null : nomes.getOrDefault(c.getOperadorId(), identificacao.rotuloDesconhecido(c.getOperadorId())),
                    c.getDataAbertura(), c.getDataFechamento(), esperado, informado, diferenca);
        }).toList();
    }

    /** Contas a receber em aberto por idade do atraso, e os clientes que mais devem. */
    public Inadimplencia inadimplencia(LocalDate hoje) {
        List<ContasAReceber> contas = contasReceberRepo.buscarEmAbertoParaInadimplencia();
        Map<String, List<ContasAReceber>> porFaixa = new LinkedHashMap<>();
        for (String f : List.of("A vencer", "1 a 30 dias", "31 a 60 dias", "61 a 90 dias", "Mais de 90 dias")) {
            porFaixa.put(f, new ArrayList<>());
        }
        for (ContasAReceber c : contas) porFaixa.get(faixaAtraso(c, hoje)).add(c);

        List<FaixaAtraso> faixas = porFaixa.entrySet().stream()
                .map(e -> new FaixaAtraso(e.getKey(), e.getValue().size(), saldo(e.getValue()))).toList();
        BigDecimal totalEmAberto = saldo(contas);
        BigDecimal totalVencido = saldo(contas.stream().filter(c -> vencida(c, hoje)).toList());

        List<Devedor> devedores = contas.stream()
                .collect(Collectors.groupingBy(c -> c.getCliente().getId()))
                .values().stream().map(lista -> new Devedor(
                        lista.get(0).getCliente().getId(), lista.get(0).getCliente().getNome(), lista.size(),
                        saldo(lista), saldo(lista.stream().filter(c -> vencida(c, hoje)).toList()),
                        lista.stream().map(ContasAReceber::getDataVencimento).filter(Objects::nonNull)
                                .min(Comparator.naturalOrder()).orElse(null)))
                .sorted(Comparator.comparing(Devedor::valor).reversed())
                .limit(10)
                .toList();
        return new Inadimplencia(totalEmAberto, totalVencido, faixas, devedores);
    }

    // ── auxiliares ─────────────────────────────────────────────

    private List<Venda> fechadas(LocalDateTime inicio, LocalDateTime fim) {
        return vendaRepo.findByPeriodo(inicio, fim).stream()
                .filter(v -> "FECHADA".equals(v.getStatus()) && v.getDataVenda() != null).toList();
    }

    private static Faixa faixa(int chave, List<Venda> vendas) {
        return vendas == null ? new Faixa(chave, 0, BigDecimal.ZERO) : new Faixa(chave, vendas.size(), soma(vendas));
    }

    static String faixaAtraso(ContasAReceber c, LocalDate hoje) {
        if (!vencida(c, hoje)) return "A vencer";
        long dias = ChronoUnit.DAYS.between(c.getDataVencimento(), hoje);
        if (dias <= 30) return "1 a 30 dias";
        if (dias <= 60) return "31 a 60 dias";
        if (dias <= 90) return "61 a 90 dias";
        return "Mais de 90 dias";
    }

    private static boolean vencida(ContasAReceber c, LocalDate hoje) {
        return c.getDataVencimento() != null && c.getDataVencimento().isBefore(hoje);
    }

    private static BigDecimal saldo(List<ContasAReceber> contas) {
        return contas.stream()
                .map(c -> c.getValor().subtract(c.getValorPago() == null ? BigDecimal.ZERO : c.getValorPago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal soma(List<Venda> vendas) {
        return vendas.stream().map(v -> v.getTotal() == null ? BigDecimal.ZERO : v.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal media(BigDecimal total, int n) {
        return n == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO
                : parte.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dec(Object o) {
        return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
