package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.ContasAReceberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** O saldo de fiado exibido em Clientes é calculado das contas a receber em aberto. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteService — saldo de fiado")
class ClienteServiceSaldoTest {

    @Mock ClienteRepository clienteRepo;
    @Mock ContasAReceberRepository contasRepo;
    @InjectMocks ClienteService service;

    private static Cliente cliente(long id, String nome) {
        return Cliente.builder().id(id).nome(nome).tipoCliente("FIADO")
                .limiteCredito(new BigDecimal("1000.00")).build();
    }

    @Test
    @DisplayName("listarAtivos: cada cliente recebe o saldo em aberto das suas contas")
    void listaComSaldo() {
        List<Cliente> clientes = new ArrayList<>(List.of(cliente(7L, "Carlos Ramos"), cliente(8L, "Maria"), cliente(9L, "Sem fiado")));
        when(clienteRepo.findByAtivoTrue()).thenReturn(clientes);
        when(contasRepo.saldosAbertosPorCliente()).thenReturn(List.of(
                new Object[]{7L, new BigDecimal("25.90")},
                new Object[]{8L, new BigDecimal("1300.5")}));

        List<Cliente> lista = service.listarAtivos();

        assertThat(lista.get(0).getSaldoFiadoAtual()).isEqualByComparingTo("25.90");
        assertThat(lista.get(1).getSaldoFiadoAtual()).isEqualByComparingTo("1300.50");
        assertThat(lista.get(2).getSaldoFiadoAtual()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("listarAtivos: aceita ids e valores em tipos numéricos diferentes do banco")
    void aceitaTiposNumericos() {
        List<Cliente> clientes = new ArrayList<>(List.of(cliente(7L, "Carlos Ramos")));
        when(clienteRepo.findByAtivoTrue()).thenReturn(clientes);
        when(contasRepo.saldosAbertosPorCliente()).thenReturn(List.<Object[]>of(new Object[]{7, 12.5}));

        assertThat(service.listarAtivos().get(0).getSaldoFiadoAtual()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("listarAtivos: sem clientes, nem consulta os saldos")
    void listaVazia() {
        when(clienteRepo.findByAtivoTrue()).thenReturn(new ArrayList<>());

        assertThat(service.listarAtivos()).isEmpty();
        verify(contasRepo, never()).saldosAbertosPorCliente();
    }

    @Test
    @DisplayName("buscarPorId: usa o saldo em aberto do próprio cliente")
    void buscarPorIdComSaldo() {
        when(clienteRepo.findById(7L)).thenReturn(Optional.of(cliente(7L, "Carlos Ramos")));
        when(contasRepo.saldoAberto(7L)).thenReturn(new BigDecimal("25.9"));

        assertThat(service.buscarPorId(7L).getSaldoFiadoAtual()).isEqualByComparingTo("25.90");
    }

    @Test
    @DisplayName("criar: cliente novo começa com saldo zero e não grava saldo vindo da requisição")
    void criarComecaZerado() {
        when(clienteRepo.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        Cliente dados = cliente(99L, "Novo");
        dados.setSaldoFiadoAtual(new BigDecimal("500.00"));

        Cliente salvo = service.criar(dados, 3L);

        assertThat(salvo.getId()).isNull();
        assertThat(salvo.getCriadoPorId()).isEqualTo(3L);
        assertThat(salvo.getSaldoFiadoAtual()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("atualizar: devolve o cliente com o saldo real; o limite é o único valor financeiro editável")
    void atualizarDevolveSaldoReal() {
        Cliente existente = cliente(7L, "Carlos Ramos");
        when(clienteRepo.findById(7L)).thenReturn(Optional.of(existente));
        when(clienteRepo.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(contasRepo.saldoAberto(7L)).thenReturn(new BigDecimal("25.90"));

        Cliente dados = cliente(7L, "Carlos Ramos");
        dados.setLimiteCredito(new BigDecimal("2000.00"));
        dados.setSaldoFiadoAtual(new BigDecimal("0.00")); // tentativa de zerar o saldo pela requisição

        Cliente salvo = service.atualizar(7L, dados);

        assertThat(salvo.getLimiteCredito()).isEqualByComparingTo("2000.00");
        assertThat(salvo.getSaldoFiadoAtual()).isEqualByComparingTo("25.90");
    }
}