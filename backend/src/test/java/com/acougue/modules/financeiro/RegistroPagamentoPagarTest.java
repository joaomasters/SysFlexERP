package com.acougue.modules.financeiro;

import com.acougue.entity.ContasPagar;
import com.acougue.entity.ContasPagarPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ContasPagarPagamentoRepository;
import com.acougue.repository.ContasPagarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContasPagarService.pagar (Contas a Pagar + histórico)")
class RegistroPagamentoPagarTest {

    @Mock ContasPagarRepository           contasRepo;
    @Mock ContasPagarPagamentoRepository  pagamentoRepo;

    private ContasPagarService service;
    private ContasPagar conta;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        service = new ContasPagarService(contasRepo, pagamentoRepo);
        conta = ContasPagar.builder()
                .id(1L).descricao("Compra de bovino").fornecedor("Frigorífico X")
                .valor(bd("5000.00")).dataVencimento(LocalDate.now().plusDays(10))
                .build();
    }

    @Test
    @DisplayName("exemplo da task: conta de 5.000 paga em 1.000 + 3.000 + 1.000 → histórico completo")
    void pagamentosParciaisFormamHistoricoCompleto() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));
        when(contasRepo.save(any(ContasPagar.class))).thenAnswer(i -> i.getArgument(0));

        service.pagar(1L, bd("1000"), 7L);
        assertThat(conta.getStatus()).isEqualTo("PARCIAL");
        assertThat(conta.getDataPagamento()).isNull();
        service.pagar(1L, bd("3000"), 8L);
        service.pagar(1L, bd("1000"), 7L);

        assertThat(conta.getStatus()).isEqualTo("PAGO");
        assertThat(conta.getDataPagamento()).isEqualTo(LocalDate.now());
        assertThat(conta.getValorPago()).isEqualByComparingTo("5000.00");

        ArgumentCaptor<ContasPagarPagamento> captor = ArgumentCaptor.forClass(ContasPagarPagamento.class);
        verify(pagamentoRepo, times(3)).save(captor.capture());
        List<ContasPagarPagamento> linhas = captor.getAllValues();

        assertThat(linhas).extracting(l -> l.getSaldoAnterior().setScale(2).toPlainString())
                .containsExactly("5000.00", "4000.00", "1000.00");
        assertThat(linhas).extracting(l -> l.getSaldoPosterior().setScale(2).toPlainString())
                .containsExactly("4000.00", "1000.00", "0.00");
        assertThat(linhas).extracting(ContasPagarPagamento::getUsuarioId).containsExactly(7L, 8L, 7L);
    }

    @Test
    @DisplayName("valor acima do saldo, nulo ou <= 0 é rejeitado sem gravar nada")
    void rejeitaValoresInvalidos() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));

        assertThatThrownBy(() -> service.pagar(1L, bd("5000.01"), 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.pagar(1L, null, 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.pagar(1L, BigDecimal.ZERO, 7L)).isInstanceOf(BusinessException.class);

        verifyNoInteractions(pagamentoRepo);
        verify(contasRepo, never()).save(any());
    }

    @Test
    @DisplayName("conta paga ou cancelada não aceita novo pagamento")
    void rejeitaContaPagaOuCancelada() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));
        for (String status : List.of("PAGO", "CANCELADO")) {
            conta.setStatus(status);
            assertThatThrownBy(() -> service.pagar(1L, bd("10"), 7L)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(pagamentoRepo);
    }
}
