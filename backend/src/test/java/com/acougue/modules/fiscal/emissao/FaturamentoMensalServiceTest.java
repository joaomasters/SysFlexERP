package com.acougue.modules.fiscal.emissao;

import com.acougue.repository.VendaRepository;
import com.acougue.security.auditoria.AuditoriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Extrato de faturamento do mês (contador)")
class FaturamentoMensalServiceTest {

    private final VendaRepository repo = mock(VendaRepository.class);
    private final FaturamentoMensalService service = new FaturamentoMensalService(repo, mock(AuditoriaService.class));

    private static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("soma por dia e forma de pagamento; troco sai do dinheiro; separa o que teve NFC-e")
    void extrato() {
        LocalDateTime dia1 = LocalDateTime.of(2026, 11, 3, 10, 0);
        LocalDateTime dia2 = LocalDateTime.of(2026, 11, 4, 18, 30);
        when(repo.resumoFechadas(any(), any())).thenReturn(List.of(
                new Object[]{1L, dia1, r("90.00"), r("10.00"), null},          // pagou 100 em dinheiro, troco 10
                new Object[]{2L, dia1, r("50.00"), r("0.00"), "AUTORIZADA"},   // débito, com NFC-e
                new Object[]{3L, dia2, r("30.00"), r("0.00"), "REJEITADA"}));  // metade pix, metade crédito
        when(repo.pagamentosFechadas(any(), any())).thenReturn(List.of(
                new Object[]{1L, "DINHEIRO", r("100.00")},
                new Object[]{2L, "DEBITO", r("50.00")},
                new Object[]{3L, "PIX", r("15.00")},
                new Object[]{3L, "CREDITO", r("15.00")}));

        FaturamentoMensalService.Extrato e = service.extrato(YearMonth.of(2026, 11));

        verify(repo).resumoFechadas(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(e.dias()).hasSize(2);
        FaturamentoMensalService.Dia d1 = e.dias().get(0);
        assertThat(d1.data()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(d1.vendas()).isEqualTo(2);
        assertThat(d1.total()).isEqualByComparingTo("140.00");
        assertThat(d1.porForma().get("DINHEIRO")).isEqualByComparingTo("90.00");
        assertThat(d1.porForma().get("DEBITO")).isEqualByComparingTo("50.00");
        assertThat(d1.vendasComNfce()).isEqualTo(1);
        assertThat(d1.totalComNfce()).isEqualByComparingTo("50.00");

        assertThat(e.totais().total()).isEqualByComparingTo("170.00");
        assertThat(e.totais().vendasComNfce()).isEqualTo(1);   // rejeitada não conta como nota
        assertThat(e.totais().porForma().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("170.00");               // as formas fecham com o total
        assertThat(e.formas()).startsWith("DINHEIRO", "DEBITO", "CREDITO", "PIX");
    }

    @Test
    @DisplayName("forma de pagamento nova aparece como coluna no fim")
    void formaNova() {
        when(repo.resumoFechadas(any(), any())).thenReturn(List.<Object[]>of(new Object[]{1L, LocalDateTime.of(2026, 11, 5, 9, 0), r("20.00"), null, null}));
        when(repo.pagamentosFechadas(any(), any())).thenReturn(List.<Object[]>of(new Object[]{1L, "boleto", r("20.00")}));
        FaturamentoMensalService.Extrato e = service.extrato(YearMonth.of(2026, 11));
        assertThat(e.formas()).last().isEqualTo("BOLETO");
        assertThat(e.totais().porForma().get("BOLETO")).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("troco gravado em venda no cartão não desconta; venda sem pagamento fica em 'não informado'")
    void fechaComOTotal() {
        LocalDateTime dia = LocalDateTime.of(2026, 11, 6, 12, 0);
        when(repo.resumoFechadas(any(), any())).thenReturn(List.of(
                new Object[]{1L, dia, r("78.50"), r("1.50"), null},   // cartão com "troco" gravado
                new Object[]{2L, dia, r("40.00"), r("0.00"), null})); // sem pagamento registrado
        when(repo.pagamentosFechadas(any(), any())).thenReturn(List.<Object[]>of(new Object[]{1L, "CREDITO", r("78.50")}));

        FaturamentoMensalService.Extrato e = service.extrato(YearMonth.of(2026, 11));

        assertThat(e.totais().porForma().get("CREDITO")).isEqualByComparingTo("78.50");
        assertThat(e.totais().porForma().get("NAO_INFORMADO")).isEqualByComparingTo("40.00");
        assertThat(e.formas()).contains("NAO_INFORMADO");
        assertThat(e.totais().porForma().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(e.totais().total());
    }
}
