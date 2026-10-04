package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
import com.acougue.entity.ContasAReceber;
import com.acougue.entity.ContasAReceberPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.ContasAReceberPagamentoRepository;
import com.acougue.repository.ContasAReceberRepository;
import com.acougue.repository.FaturamentoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FaturamentoService.registrarPagamento (Contas a Receber + histórico)")
class RegistroPagamentoReceberTest {

    @Mock FaturamentoClienteRepository       faturamentoRepo;
    @Mock ContasAReceberRepository           contasRepo;
    @Mock ClienteRepository                  clienteRepo;
    @Mock ContasAReceberPagamentoRepository  pagamentoRepo;

    private FaturamentoService service;
    private ContasAReceber conta;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        service = new FaturamentoService(faturamentoRepo, contasRepo, clienteRepo, pagamentoRepo);
        conta = ContasAReceber.builder()
                .id(1L)
                .cliente(Cliente.builder().id(10L).nome("João").build())
                .valor(bd("5000.00"))
                .build();
    }

    private void contaEncontrada() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));
        when(contasRepo.save(any(ContasAReceber.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("exemplo da task: dívida 5.000 paga em 1.000 + 3.000 + 1.000 → histórico com saldo antes/depois e usuário")
    void pagamentosParciaisFormamHistoricoCompleto() {
        contaEncontrada();

        service.registrarPagamento(1L, bd("1000"), 7L);
        assertThat(conta.getStatus()).isEqualTo("PARCIAL");
        service.registrarPagamento(1L, bd("3000"), 8L);
        assertThat(conta.getStatus()).isEqualTo("PARCIAL");
        service.registrarPagamento(1L, bd("1000"), 7L);

        assertThat(conta.getStatus()).isEqualTo("PAGO");
        assertThat(conta.getValorPago()).isEqualByComparingTo("5000.00");

        ArgumentCaptor<ContasAReceberPagamento> captor = ArgumentCaptor.forClass(ContasAReceberPagamento.class);
        verify(pagamentoRepo, times(3)).save(captor.capture());
        List<ContasAReceberPagamento> linhas = captor.getAllValues();

        assertThat(linhas).extracting(l -> l.getValor().setScale(2).toPlainString())
                .containsExactly("1000.00", "3000.00", "1000.00");
        assertThat(linhas).extracting(l -> l.getSaldoAnterior().setScale(2).toPlainString())
                .containsExactly("5000.00", "4000.00", "1000.00");
        assertThat(linhas).extracting(l -> l.getSaldoPosterior().setScale(2).toPlainString())
                .containsExactly("4000.00", "1000.00", "0.00");
        assertThat(linhas).extracting(ContasAReceberPagamento::getUsuarioId).containsExactly(7L, 8L, 7L);
        assertThat(linhas).allSatisfy(l -> {
            assertThat(l.getOrigem()).isEqualTo("PAGAMENTO");
            assertThat(l.getDataPagamento()).isNotNull();
            assertThat(l.getConta()).isSameAs(conta);
        });
    }

    @Test
    @DisplayName("valor acima do saldo é rejeitado e nada é gravado")
    void rejeitaValorAcimaDoSaldo() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));

        assertThatThrownBy(() -> service.registrarPagamento(1L, bd("5000.01"), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maior que o saldo");

        verifyNoInteractions(pagamentoRepo);
        verify(contasRepo, never()).save(any());
        assertThat(conta.getValorPago()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("valor nulo, zero ou negativo é rejeitado")
    void rejeitaValorInvalido() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));

        assertThatThrownBy(() -> service.registrarPagamento(1L, null, 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.registrarPagamento(1L, BigDecimal.ZERO, 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.registrarPagamento(1L, bd("-10"), 7L)).isInstanceOf(BusinessException.class);
        // 0,004 arredonda para 0,00
        assertThatThrownBy(() -> service.registrarPagamento(1L, bd("0.004"), 7L)).isInstanceOf(BusinessException.class);

        verifyNoInteractions(pagamentoRepo);
    }

    @Test
    @DisplayName("conta quitada, cancelada ou agrupada em fechamento não recebe pagamento")
    void rejeitaContaEmStatusTerminal() {
        when(contasRepo.buscarParaAtualizar(1L)).thenReturn(Optional.of(conta));

        for (String status : List.of("PAGO", "CANCELADO", "AGRUPADO")) {
            conta.setStatus(status);
            assertThatThrownBy(() -> service.registrarPagamento(1L, bd("10"), 7L))
                    .as("status " + status)
                    .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(pagamentoRepo);
    }
}
