package com.acougue.modules.fiscal.emissao;

import com.acougue.repository.VendaRepository;
import com.acougue.security.auditoria.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/*
 Extrato de faturamento do mês para o contador (base do PGDAS e conferência com as maquininhas):
 por dia, as vendas fechadas no PDV, quanto entrou em cada forma de pagamento e quanto saiu com
 NFC-e autorizada. Vendas canceladas não entram. As formas sempre fecham com o total (ver ratear).
 */
@Service
@RequiredArgsConstructor
public class FaturamentoMensalService {

    /** Ordem das colunas; forma nova que aparecer nos pagamentos entra no fim. */
    static final String NAO_INFORMADO = "NAO_INFORMADO";
    static final List<String> FORMAS = List.of("DINHEIRO", "DEBITO", "CREDITO", "PIX", "VOUCHER", "CHEQUE", "FIADO", "CONVENIO");

    private final VendaRepository vendas;
    private final AuditoriaService auditoria;

    public record Dia(LocalDate data, long vendas, BigDecimal total, long vendasComNfce, BigDecimal totalComNfce,
                      Map<String, BigDecimal> porForma) {}

    public record Extrato(YearMonth mes, List<String> formas, List<Dia> dias, Dia totais) {}

    @Transactional   // não readOnly: a auditoria grava na mesma transação
    public Extrato extrato(YearMonth mes) {
        LocalDateTime de = mes.atDay(1).atStartOfDay();
        LocalDateTime ate = de.plusMonths(1);

        Map<Long, Map<String, BigDecimal>> pagos = new HashMap<>();
        Set<String> formas = new LinkedHashSet<>(FORMAS);
        for (Object[] p : vendas.pagamentosFechadas(de, ate)) {
            String forma = String.valueOf(p[1]).toUpperCase(Locale.ROOT);
            formas.add(forma);
            pagos.computeIfAbsent((Long) p[0], k -> new LinkedHashMap<>()).merge(forma, valor(p[2]), BigDecimal::add);
        }

        Map<LocalDate, Acumulado> porDia = new TreeMap<>();
        for (Object[] v : vendas.resumoFechadas(de, ate)) {
            LocalDate dia = ((LocalDateTime) v[1]).toLocalDate();
            Acumulado a = porDia.computeIfAbsent(dia, d -> new Acumulado());
            BigDecimal total = valor(v[2]);
            a.vendas++;
            a.total = a.total.add(total);
            if ("AUTORIZADA".equals(v[4])) {
                a.comNfce++;
                a.totalComNfce = a.totalComNfce.add(total);
            }
            ratear(a, total, pagos.getOrDefault((Long) v[0], Map.of()));
        }
        if (porDia.values().stream().anyMatch(a -> a.porForma.containsKey(NAO_INFORMADO))) formas.add(NAO_INFORMADO);

        List<String> colunas = List.copyOf(formas);
        List<Dia> dias = new ArrayList<>();
        Acumulado geral = new Acumulado();
        porDia.forEach((dia, a) -> {
            dias.add(a.paraDia(dia, colunas));
            geral.vendas += a.vendas;
            geral.total = geral.total.add(a.total);
            geral.comNfce += a.comNfce;
            geral.totalComNfce = geral.totalComNfce.add(a.totalComNfce);
            a.porForma.forEach(geral::somar);
        });
        Dia totais = geral.paraDia(null, colunas);
        auditoria.registrar("FISCAL", "EXTRATO_MES", "Extrato de faturamento de " + mes + ": " + totais.vendas()
                + " venda(s), R$ " + totais.total());
        return new Extrato(mes, colunas, dias, totais);
    }

    /**
     * O que a venda trouxe em cada forma, fechando com o total: o que foi pago a mais é troco e sai do
     * dinheiro (ou da maior forma, se não houve dinheiro); o que faltar fica em "não informado".
     */
    static void ratear(Acumulado a, BigDecimal total, Map<String, BigDecimal> pagamentos) {
        Map<String, BigDecimal> formas = new LinkedHashMap<>(pagamentos);
        BigDecimal pago = formas.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sobra = pago.subtract(total);
        if (sobra.signum() > 0) {
            String deQuem = formas.containsKey("DINHEIRO") ? "DINHEIRO"
                    : formas.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            formas.merge(deQuem, sobra.negate(), BigDecimal::add);
        } else if (sobra.signum() < 0) {
            formas.merge(NAO_INFORMADO, sobra.negate(), BigDecimal::add);
        }
        formas.forEach(a::somar);
    }

    private static BigDecimal valor(Object o) {
        return o == null ? BigDecimal.ZERO : (BigDecimal) o;
    }

    static final class Acumulado {
        long vendas;
        long comNfce;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalComNfce = BigDecimal.ZERO;
        final Map<String, BigDecimal> porForma = new HashMap<>();

        void somar(String forma, BigDecimal valor) {
            porForma.merge(forma, valor, BigDecimal::add);
        }

        Dia paraDia(LocalDate data, List<String> colunas) {
            Map<String, BigDecimal> formas = new LinkedHashMap<>();
            for (String c : colunas) formas.put(c, porForma.getOrDefault(c, BigDecimal.ZERO));
            return new Dia(data, vendas, total, comNfce, totalComNfce, formas);
        }
    }
}
