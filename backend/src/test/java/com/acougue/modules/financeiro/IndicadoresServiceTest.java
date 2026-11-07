package com.acougue.modules.financeiro;

import com.acougue.entity.*;
import com.acougue.modules.financeiro.dto.IndicadoresDTO.*;
import com.acougue.repository.CaixaRepository;
import com.acougue.repository.ContasAReceberRepository;
import com.acougue.repository.ItensVendaRepository;
import com.acougue.repository.VendaRepository;
import com.acougue.security.IdentificacaoUsuario;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Relatórios de indicadores")
class IndicadoresServiceTest {

    private final VendaRepository vendaRepo = mock(VendaRepository.class);
    private final ItensVendaRepository itensRepo = mock(ItensVendaRepository.class);
    private final CaixaRepository caixaRepo = mock(CaixaRepository.class);
    private final ContasAReceberRepository contasRepo = mock(ContasAReceberRepository.class);
    private final IdentificacaoUsuario identificacao = mock(IdentificacaoUsuario.class);
    private final IndicadoresService service = new IndicadoresService(vendaRepo, itensRepo, caixaRepo, contasRepo, identificacao);

    private final LocalDateTime inicio = LocalDateTime.of(2026, 11, 1, 0, 0);
    private final LocalDateTime fim = LocalDateTime.of(2026, 11, 30, 23, 59);

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static Venda venda(String status, String total, LocalDateTime quando, Long operador) {
        return Venda.builder().status(status).total(new BigDecimal(total)).desconto(BigDecimal.ZERO)
                .dataVenda(quando).operadorId(operador).build();
    }

    private static void logar(boolean administrador) {
        UsuarioAutenticado u = new UsuarioAutenticado(1L, "x", "X", administrador ? "SUPER_ADMIN" : "CAIXA",
                administrador, Map.of(), "tenant_a");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @Test
    @DisplayName("horários: agrupa vendas fechadas por hora e dia da semana, ignora canceladas")
    void horarios() {
        LocalDateTime sexta10h = LocalDateTime.of(2026, 11, 6, 10, 15);
        when(vendaRepo.findByPeriodo(inicio, fim)).thenReturn(List.of(
                venda("FECHADA", "30.00", sexta10h, 1L),
                venda("FECHADA", "20.00", sexta10h.plusMinutes(20), 1L),
                venda("CANCELADA", "99.00", sexta10h, 1L)));

        Horarios h = service.horarios(inicio, fim);

        assertThat(h.porHora()).hasSize(24);
        assertThat(h.porHora().get(10).quantidade()).isEqualTo(2);
        assertThat(h.porHora().get(10).total()).isEqualByComparingTo("50.00");
        assertThat(h.porDiaSemana().get(4)).satisfies(f -> {  // 5 = sexta
            assertThat(f.chave()).isEqualTo(5);
            assertThat(f.quantidade()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("curva ABC: ordena por faturamento e classifica pelo acumulado (80% / 95%)")
    void curvaAbc() {
        when(itensRepo.vendasPorProduto(inicio, fim)).thenReturn(List.of(
                new Object[]{3L, "Sal", "MERCEARIA", new BigDecimal("2"), new BigDecimal("5.00"), new BigDecimal("3.00")},
                new Object[]{1L, "Picanha", "CORTE", new BigDecimal("10"), new BigDecimal("80.00"), new BigDecimal("50.00")},
                new Object[]{2L, "Cerveja", "BEBIDA", new BigDecimal("5"), new BigDecimal("15.00"), new BigDecimal("10.00")}));

        List<ProdutoAbc> abc = service.curvaAbc(inicio, fim);

        assertThat(abc).extracting(ProdutoAbc::nome).containsExactly("Picanha", "Cerveja", "Sal");
        assertThat(abc).extracting(ProdutoAbc::classe).containsExactly("A", "B", "C");
        assertThat(abc.get(0).participacao()).isEqualByComparingTo("80.00");
        assertThat(abc.get(0).percentualMargem()).isEqualByComparingTo("37.50");
        assertThat(abc.get(2).acumulado()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("margem por tipo de produto, com participação no faturamento")
    void margemPorTipo() {
        when(itensRepo.vendasPorProduto(inicio, fim)).thenReturn(List.of(
                new Object[]{1L, "Picanha", "CORTE", BigDecimal.ONE, new BigDecimal("60.00"), new BigDecimal("40.00")},
                new Object[]{2L, "Alcatra", "CORTE", BigDecimal.ONE, new BigDecimal("20.00"), new BigDecimal("10.00")},
                new Object[]{3L, "Cerveja", "BEBIDA", BigDecimal.ONE, new BigDecimal("20.00"), new BigDecimal("15.00")}));

        List<MargemTipo> tipos = service.margemPorTipo(inicio, fim);

        assertThat(tipos.get(0).tipoProduto()).isEqualTo("CORTE");
        assertThat(tipos.get(0).margem()).isEqualByComparingTo("30.00");
        assertThat(tipos.get(0).participacao()).isEqualByComparingTo("80.00");
        assertThat(tipos.get(0).produtos()).isEqualTo(2);
    }

    @Test
    @DisplayName("operadores: total, ticket, descontos e canceladas; só para quem vê a identificação")
    void operadores() {
        logar(false);
        assertThatThrownBy(() -> service.operadores(inicio, fim)).isInstanceOf(AccessDeniedException.class);

        logar(true);
        LocalDateTime d = inicio.plusDays(1);
        when(vendaRepo.findByPeriodo(inicio, fim)).thenReturn(List.of(
                venda("FECHADA", "40.00", d, 7L), venda("FECHADA", "20.00", d, 7L), venda("CANCELADA", "10.00", d, 7L)));
        when(itensRepo.descontosPorOperador(inicio, fim)).thenReturn(List.<Object[]>of(new Object[]{7L, new BigDecimal("2.50")}));
        when(identificacao.nomesPorId(any())).thenReturn(Map.of(7L, "Maria"));

        Operador o = service.operadores(inicio, fim).get(0);

        assertThat(o.nome()).isEqualTo("Maria");
        assertThat(o.vendas()).isEqualTo(2);
        assertThat(o.ticketMedio()).isEqualByComparingTo("30.00");
        assertThat(o.descontos()).isEqualByComparingTo("2.50");
        assertThat(o.canceladas()).isEqualTo(1);
    }

    @Test
    @DisplayName("fechamento de caixa: diferença = contado − esperado")
    void fechamentos() {
        logar(true);
        Caixa c = Caixa.builder().id(3L).operadorId(7L).valorCalculado(new BigDecimal("500.00"))
                .valorFechamentoInformado(new BigDecimal("495.00")).dataFechamento(fim.minusDays(1)).build();
        when(caixaRepo.findByStatusAndDataFechamentoBetweenOrderByDataFechamentoDesc("FECHADO", inicio, fim)).thenReturn(List.of(c));
        when(identificacao.nomesPorId(any())).thenReturn(Map.of(7L, "Maria"));

        assertThat(service.fechamentosCaixa(inicio, fim).get(0).diferenca()).isEqualByComparingTo("-5.00");
    }

    @Test
    @DisplayName("inadimplência: faixas de atraso pelo saldo (valor − pago) e maiores devedores")
    void inadimplencia() {
        LocalDate hoje = LocalDate.of(2026, 11, 7);
        Cliente ana = Cliente.builder().id(1L).nome("Ana").build();
        Cliente bia = Cliente.builder().id(2L).nome("Bia").build();
        when(contasRepo.buscarEmAbertoParaInadimplencia()).thenReturn(List.of(
                conta(ana, "100.00", "40.00", hoje.plusDays(5)),       // a vencer: 60
                conta(ana, "50.00", "0", hoje.minusDays(10)),          // 1–30: 50
                conta(bia, "200.00", "0", hoje.minusDays(120))));      // >90: 200

        Inadimplencia r = service.inadimplencia(hoje);

        assertThat(r.totalEmAberto()).isEqualByComparingTo("310.00");
        assertThat(r.totalVencido()).isEqualByComparingTo("250.00");
        assertThat(r.faixas()).extracting(FaixaAtraso::valor)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("60"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200"));
        assertThat(r.maioresDevedores().get(0).nome()).isEqualTo("Bia");
        assertThat(r.maioresDevedores().get(1).vencido()).isEqualByComparingTo("50.00");
    }

    private static ContasAReceber conta(Cliente c, String valor, String pago, LocalDate vencimento) {
        return ContasAReceber.builder().cliente(c).valor(new BigDecimal(valor)).valorPago(new BigDecimal(pago))
                .dataVencimento(vencimento).status("ABERTO").build();
    }
}
