package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.exception.LimiteCreditoExcedidoException;
import com.acougue.modules.balanca.EanBalancaParser;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.pagamento.TransacaoTerminalService;
import com.acougue.modules.pdv.dto.AutorizacaoLimiteDTO;
import com.acougue.modules.pdv.dto.FecharVendaDTO;
import com.acougue.modules.pdv.dto.PagamentoDTO;
import com.acougue.repository.*;
import com.acougue.security.AutorizacaoAdminService;
import com.acougue.security.auditoria.AuditoriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Trava de limite de crédito no fechamento de venda com pagamento FIADO. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdvService — limite de crédito do fiado")
class PdvServiceLimiteFiadoTest {

    @Mock VendaRepository            vendaRepo;
    @Mock ItensVendaRepository       itensRepo;
    @Mock PagamentoVendaRepository   pagRepo;
    @Mock ProdutoRepository          produtoRepo;
    @Mock ClienteRepository          clienteRepo;
    @Mock CaixaRepository            caixaRepo;
    @Mock ContasAReceberRepository   contasRepo;
    @Mock UsuarioRepository          usuarioRepo;
    @Mock EstoqueService             estoqueService;
    @Mock EanBalancaParser           eanParser;
    @Mock CaixaService               caixaService;
    @Mock TransacaoTerminalService   transacaoTerminalService;
    @Mock AutorizacaoAdminService    autorizacaoAdminService;
    @Mock AuditoriaService           auditoriaService;

    @InjectMocks PdvService service;

    private static Cliente cliente(String limite) {
        return Cliente.builder().id(7L).nome("Carlos Ramos").tipoCliente("FIADO")
                .limiteCredito(new BigDecimal(limite)).build();
    }

    private static Venda venda(Cliente c, String total) {
        return Venda.builder().id(31L).status("ABERTA").operadorId(10L).cliente(c)
                .total(new BigDecimal(total)).desconto(BigDecimal.ZERO).build();
    }

    private static PagamentoDTO pag(String forma, String valor) {
        PagamentoDTO p = new PagamentoDTO();
        p.setFormaPagamento(forma);
        p.setValor(new BigDecimal(valor));
        return p;
    }

    private static FecharVendaDTO fechar(AutorizacaoLimiteDTO aut, PagamentoDTO... pagamentos) {
        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(31L);
        dto.setPagamentos(List.of(pagamentos));
        dto.setAutorizacaoLimite(aut);
        return dto;
    }

    /** Venda de R$ total com cliente de limite dado e saldo em aberto dado (stubs do que a trava lê). */
    private Venda preparar(String limite, String saldoAberto, String total) {
        Cliente c = cliente(limite);
        Venda v = venda(c, total);
        when(vendaRepo.findById(31L)).thenReturn(Optional.of(v));
        when(clienteRepo.buscarParaAtualizar(7L)).thenReturn(Optional.of(c));
        when(contasRepo.saldoAberto(7L)).thenReturn(new BigDecimal(saldoAberto));
        return v;
    }

    private void permitirFechamento() {
        when(vendaRepo.save(any(Venda.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("dentro do limite: fecha sem pedir autorização")
    void dentroDoLimite() {
        preparar("1000.00", "200.00", "300.00");
        permitirFechamento();

        Venda fechada = service.fecharVenda(fechar(null, pag("FIADO", "300.00")));

        assertThat(fechada.getStatus()).isEqualTo("FECHADA");
        verify(contasRepo).save(any(ContasAReceber.class));
        verifyNoInteractions(autorizacaoAdminService);
    }

    @Test
    @DisplayName("exatamente no limite: ainda passa (só trava quando ULTRAPASSA)")
    void exatamenteNoLimite() {
        preparar("1000.00", "700.00", "300.00");
        permitirFechamento();

        Venda fechada = service.fecharVenda(fechar(null, pag("FIADO", "300.00")));

        assertThat(fechada.getStatus()).isEqualTo("FECHADA");
        verifyNoInteractions(autorizacaoAdminService);
    }

    @Test
    @DisplayName("acima do limite sem autorização: recusa com os valores e não grava nada")
    void acimaDoLimiteSemAutorizacao() {
        preparar("1000.00", "800.00", "300.00");

        assertThatThrownBy(() -> service.fecharVenda(fechar(null, pag("FIADO", "300.00"))))
                .isInstanceOf(LimiteCreditoExcedidoException.class)
                .satisfies(e -> {
                    LimiteCreditoExcedidoException ex = (LimiteCreditoExcedidoException) e;
                    assertThat(ex.getClienteNome()).isEqualTo("Carlos Ramos");
                    assertThat(ex.getLimite()).isEqualByComparingTo("1000.00");
                    assertThat(ex.getSaldoAtual()).isEqualByComparingTo("800.00");
                    assertThat(ex.getValorFiado()).isEqualByComparingTo("300.00");
                    assertThat(ex.getExcedente()).isEqualByComparingTo("100.00");
                });

        verify(pagRepo, never()).save(any());
        verify(contasRepo, never()).save(any());
        verify(estoqueService, never()).saida(any(), any(), anyString(), anyString(), any());
        verify(vendaRepo, never()).save(any());
    }

    @Test
    @DisplayName("é BusinessException (o tratamento genérico continua valendo)")
    void ehBusinessException() {
        preparar("1000.00", "1000.00", "10.00");

        assertThatThrownBy(() -> service.fecharVenda(fechar(null, pag("FIADO", "10.00"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("acima do limite com administrador válido: fecha, lança o fiado e audita")
    void acimaDoLimiteComAutorizacao() {
        Venda v = preparar("1000.00", "800.00", "300.00");
        permitirFechamento();
        Usuario admin = Usuario.builder().id(1L).nome("Administrador").login("admin").build();
        when(autorizacaoAdminService.validar(10L, "admin", "segredo")).thenReturn(admin);

        Venda fechada = service.fecharVenda(fechar(new AutorizacaoLimiteDTO("admin", "segredo"),
                pag("FIADO", "300.00")));

        assertThat(fechada.getStatus()).isEqualTo("FECHADA");
        assertThat(fechada).isSameAs(v);
        verify(contasRepo).save(any(ContasAReceber.class));
        verify(auditoriaService).registrar(org.mockito.ArgumentMatchers.eq("PDV"),
                org.mockito.ArgumentMatchers.eq("CRIAR"),
                org.mockito.ArgumentMatchers.contains("autorizado por Administrador"));
    }

    @Test
    @DisplayName("credencial inválida: propaga o erro e nada é gravado")
    void credencialInvalida() {
        preparar("1000.00", "800.00", "300.00");
        when(autorizacaoAdminService.validar(10L, "admin", "errada"))
                .thenThrow(new BusinessException("Login ou senha de administrador inválidos."));

        assertThatThrownBy(() -> service.fecharVenda(fechar(new AutorizacaoLimiteDTO("admin", "errada"),
                pag("FIADO", "300.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválidos");

        verify(pagRepo, never()).save(any());
        verify(contasRepo, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("autorização enviada sem necessidade é ignorada (não valida credencial à toa)")
    void autorizacaoSemNecessidadeIgnorada() {
        preparar("1000.00", "0.00", "100.00");
        permitirFechamento();

        service.fecharVenda(fechar(new AutorizacaoLimiteDTO("admin", "x"), pag("FIADO", "100.00")));

        verifyNoInteractions(autorizacaoAdminService);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("cliente sem limite cadastrado (0): não trava e nem consulta o saldo")
    void semLimiteCadastrado() {
        Cliente c = cliente("0.00");
        Venda v = venda(c, "5000.00");
        when(vendaRepo.findById(31L)).thenReturn(Optional.of(v));
        when(clienteRepo.buscarParaAtualizar(7L)).thenReturn(Optional.of(c));
        permitirFechamento();

        Venda fechada = service.fecharVenda(fechar(null, pag("FIADO", "5000.00")));

        assertThat(fechada.getStatus()).isEqualTo("FECHADA");
        verify(contasRepo, never()).saldoAberto(any());
    }

    @Test
    @DisplayName("várias linhas de FIADO na mesma venda somam para o limite")
    void somaLinhasDeFiado() {
        preparar("1000.00", "500.00", "600.00");

        assertThatThrownBy(() -> service.fecharVenda(fechar(null, pag("FIADO", "300.00"), pag("FIADO", "300.00"))))
                .isInstanceOf(LimiteCreditoExcedidoException.class)
                .satisfies(e -> assertThat(((LimiteCreditoExcedidoException) e).getExcedente())
                        .isEqualByComparingTo("100.00"));
    }

    @Test
    @DisplayName("pagamento misto: só a parte no FIADO conta para o limite")
    void soAParteFiadoConta() {
        preparar("1000.00", "950.00", "200.00");

        assertThatThrownBy(() -> service.fecharVenda(fechar(null, pag("DINHEIRO", "100.00"), pag("FIADO", "100.00"))))
                .isInstanceOf(LimiteCreditoExcedidoException.class)
                .satisfies(e -> {
                    LimiteCreditoExcedidoException ex = (LimiteCreditoExcedidoException) e;
                    assertThat(ex.getValorFiado()).isEqualByComparingTo("100.00");
                    assertThat(ex.getExcedente()).isEqualByComparingTo("50.00");
                });
    }

    @Test
    @DisplayName("venda sem fiado: não toca no cadastro do cliente")
    void vendaSemFiado() {
        Cliente c = cliente("1000.00");
        Venda v = venda(c, "50.00");
        when(vendaRepo.findById(31L)).thenReturn(Optional.of(v));
        permitirFechamento();

        service.fecharVenda(fechar(null, pag("DINHEIRO", "50.00")));

        verifyNoInteractions(clienteRepo);
        verify(contasRepo, never()).saldoAberto(any());
    }

    @Test
    @DisplayName("a mensagem traz os valores em reais")
    void mensagemEmReais() {
        LimiteCreditoExcedidoException ex = new LimiteCreditoExcedidoException("Carlos Ramos",
                new BigDecimal("1000.00"), new BigDecimal("800.00"), new BigDecimal("300.00"), new BigDecimal("100.00"));

        assertThat(ex.getMessage())
                .contains("Carlos Ramos")
                .contains("R$ 1.000,00")
                .contains("R$ 800,00")
                .contains("R$ 100,00")
                .contains("administrador");
    }
}