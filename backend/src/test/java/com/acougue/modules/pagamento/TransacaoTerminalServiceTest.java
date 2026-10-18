package com.acougue.modules.pagamento;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.dto.IniciarTransacaoDTO;
import com.acougue.modules.pagamento.dto.ResultadoLocalDTO;
import com.acougue.modules.pagamento.dto.TransacaoTerminalDTO;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.RetornoProvedor;
import com.acougue.repository.PagamentoVendaRepository;
import com.acougue.repository.TransacaoTerminalRepository;
import com.acougue.repository.VendaRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransacaoTerminalService")
class TransacaoTerminalServiceTest {

    @Mock TransacaoTerminalRepository transacaoRepo;
    @Mock PagamentoVendaRepository pagamentoRepo;
    @Mock VendaRepository vendaRepo;
    @Mock TerminalPagamentoService terminalService;
    @Mock CredenciaisPagamentoService credenciais;
    @Mock ProvedorTerminalNuvem mercadoPago;

    private TransacaoTerminalService service;
    private Venda vendaAberta;

    @BeforeEach
    void setUp() {
        when(mercadoPago.provedor()).thenReturn(ProvedorPagamento.MERCADO_PAGO);
        service = new TransacaoTerminalService(transacaoRepo, pagamentoRepo, vendaRepo,
                terminalService, credenciais, List.of(mercadoPago));

        vendaAberta = Venda.builder().id(1L).status("ABERTA").total(new BigDecimal("100.00")).build();

        UsuarioAutenticado u = new UsuarioAutenticado(9L, "caixa", "Caixa", "CAIXA", false, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private TerminalPagamento terminal(ModoIntegracaoTerminal modo) {
        return TerminalPagamento.builder()
                .id(3L).nome("Caixa 1").provedor(ProvedorPagamento.MERCADO_PAGO).modoIntegracao(modo)
                .identificadorDispositivo("PAX_A910__SMARTPOS1").agenteUrl("http://localhost:12345")
                .maxParcelas(6).build();
    }

    private IniciarTransacaoDTO pedido(String tipo, int parcelas, String valor) {
        IniciarTransacaoDTO dto = new IniciarTransacaoDTO();
        dto.setTerminalId(3L);
        dto.setVendaId(1L);
        dto.setTipo(tipo);
        dto.setParcelas(parcelas);
        dto.setValor(new BigDecimal(valor));
        return dto;
    }

    private void salvarDevolvendoComId() {
        when(transacaoRepo.save(any())).thenAnswer(inv -> {
            TransacaoTerminal t = inv.getArgument(0);
            if (t.getId() == null) t.setId(50L);
            return t;
        });
    }

    @Test
    @DisplayName("iniciar (nuvem): envia para a adquirente e fica PENDENTE com o id externo")
    void iniciar_nuvem_ficaPendente() {
        when(terminalService.buscar(3L)).thenReturn(terminal(ModoIntegracaoTerminal.API_NUVEM));
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(vendaAberta));
        when(credenciais.credencialObrigatoria(ProvedorPagamento.MERCADO_PAGO)).thenReturn("TOKEN");
        salvarDevolvendoComId();
        when(mercadoPago.iniciar(any(), eq("TOKEN"))).thenReturn(RetornoProvedor.pendente("intent-1", "{}"));

        TransacaoTerminalDTO r = service.iniciar(pedido("CREDITO", 3, "80.00"));

        assertThat(r.getStatus()).isEqualTo(StatusTransacaoTerminal.PENDENTE);
        assertThat(r.getParcelas()).isEqualTo(3);
        verify(mercadoPago).iniciar(argThat(c ->
                c.valorCentavos() == 8000 && c.parcelas() == 3 && c.referencia().equals("SYSFLEX-TT-50")), eq("TOKEN"));
    }

    @Test
    @DisplayName("iniciar (nuvem): erro da adquirente vira transação ERRO, sem estourar exceção")
    void iniciar_nuvem_erroDaAdquirente() {
        when(terminalService.buscar(3L)).thenReturn(terminal(ModoIntegracaoTerminal.API_NUVEM));
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(vendaAberta));
        when(credenciais.credencialObrigatoria(any())).thenReturn("TOKEN");
        salvarDevolvendoComId();
        when(mercadoPago.iniciar(any(), any())).thenThrow(new BusinessException("maquininha offline"));

        TransacaoTerminalDTO r = service.iniciar(pedido("DEBITO", 1, "10.00"));

        assertThat(r.getStatus()).isEqualTo(StatusTransacaoTerminal.ERRO);
        assertThat(r.getMensagem()).contains("maquininha offline");
    }

    @Test
    @DisplayName("iniciar: débito ignora parcelas e crédito respeita o máximo do terminal")
    void iniciar_validaParcelas() {
        when(terminalService.buscar(3L)).thenReturn(terminal(ModoIntegracaoTerminal.MANUAL));

        assertThatThrownBy(() -> service.iniciar(pedido("CREDITO", 7, "10.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6 parcelas");
    }

    @Test
    @DisplayName("iniciar: valor acima do total da venda é recusado")
    void iniciar_valorAcimaDoTotal() {
        when(terminalService.buscar(3L)).thenReturn(terminal(ModoIntegracaoTerminal.MANUAL));
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(vendaAberta));

        assertThatThrownBy(() -> service.iniciar(pedido("DEBITO", 1, "100.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maior que o total");
        verify(transacaoRepo, never()).save(any());
    }

    @Test
    @DisplayName("iniciar: venda já fechada é recusada")
    void iniciar_vendaFechada() {
        vendaAberta.setStatus("FECHADA");
        when(terminalService.buscar(3L)).thenReturn(terminal(ModoIntegracaoTerminal.MANUAL));
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(vendaAberta));

        assertThatThrownBy(() -> service.iniciar(pedido("DEBITO", 1, "10.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não está mais aberta");
    }

    @Test
    @DisplayName("resultado local (manual): aprovação grava NSU e autorização")
    void resultadoLocal_manualAprovado() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).terminal(terminal(ModoIntegracaoTerminal.MANUAL))
                .modoIntegracao(ModoIntegracaoTerminal.MANUAL).provedor(ProvedorPagamento.SICREDI)
                .tipo("DEBITO").valor(BigDecimal.TEN).build();
        when(transacaoRepo.findByIdParaAtualizar(50L)).thenReturn(Optional.of(t));
        when(transacaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultadoLocalDTO dto = new ResultadoLocalDTO();
        dto.setAprovada(true);
        dto.setNsu(" 000123 ");
        dto.setAutorizacao("AUT9");

        TransacaoTerminalDTO r = service.registrarResultadoLocal(50L, dto);

        assertThat(r.getStatus()).isEqualTo(StatusTransacaoTerminal.APROVADA);
        assertThat(r.getNsu()).isEqualTo("000123");
        assertThat(t.getFinalizadaEm()).isNotNull();
    }

    @Test
    @DisplayName("resultado local: não vale para transação em nuvem")
    void resultadoLocal_recusadoEmNuvem() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).terminal(terminal(ModoIntegracaoTerminal.API_NUVEM))
                .modoIntegracao(ModoIntegracaoTerminal.API_NUVEM).build();
        when(transacaoRepo.findByIdParaAtualizar(50L)).thenReturn(Optional.of(t));

        ResultadoLocalDTO dto = new ResultadoLocalDTO();
        dto.setAprovada(true);

        assertThatThrownBy(() -> service.registrarResultadoLocal(50L, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("consultar (nuvem): aprovação da adquirente finaliza com NSU e bandeira")
    void consultar_nuvemAprovada() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).terminal(terminal(ModoIntegracaoTerminal.API_NUVEM))
                .modoIntegracao(ModoIntegracaoTerminal.API_NUVEM).provedor(ProvedorPagamento.MERCADO_PAGO)
                .tipo("CREDITO").valor(BigDecimal.TEN).idExterno("intent-1").build();
        when(transacaoRepo.findByIdParaAtualizar(50L)).thenReturn(Optional.of(t));
        when(credenciais.credencialObrigatoria(ProvedorPagamento.MERCADO_PAGO)).thenReturn("TOKEN");
        when(mercadoPago.consultar("intent-1", "PAX_A910__SMARTPOS1", "TOKEN")).thenReturn(new RetornoProvedor(
                StatusTransacaoTerminal.APROVADA, "intent-1", "999", "AUTH", "visa", "Aprovado", "{}"));

        TransacaoTerminalDTO r = service.consultar(50L);

        assertThat(r.getStatus()).isEqualTo(StatusTransacaoTerminal.APROVADA);
        assertThat(r.getNsu()).isEqualTo("999");
        assertThat(r.getBandeira()).isEqualTo("visa");
    }

    @Test
    @DisplayName("consultar (nuvem): falha de rede mantém PENDENTE")
    void consultar_falhaDeRedeMantemPendente() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).terminal(terminal(ModoIntegracaoTerminal.API_NUVEM))
                .modoIntegracao(ModoIntegracaoTerminal.API_NUVEM).provedor(ProvedorPagamento.MERCADO_PAGO)
                .idExterno("intent-1").build();
        when(transacaoRepo.findByIdParaAtualizar(50L)).thenReturn(Optional.of(t));
        when(credenciais.credencialObrigatoria(any())).thenReturn("TOKEN");
        when(mercadoPago.consultar(any(), any(), any())).thenThrow(new BusinessException("timeout"));

        assertThat(service.consultar(50L).getStatus()).isEqualTo(StatusTransacaoTerminal.PENDENTE);
    }

    @Test
    @DisplayName("cancelar: transação já aprovada não pode ser cancelada pelo sistema")
    void cancelar_aprovadaRecusa() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).terminal(terminal(ModoIntegracaoTerminal.MANUAL))
                .modoIntegracao(ModoIntegracaoTerminal.MANUAL).status(StatusTransacaoTerminal.APROVADA).build();
        when(transacaoRepo.findByIdParaAtualizar(50L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.cancelar(50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("estorno");
    }

    @Test
    @DisplayName("cancelar (nuvem): se o cliente pagou no meio do caminho, não cancela")
    void cancelar_nuvemPagaNoMeioDoCaminho() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).terminal(terminal(ModoIntegracaoTerminal.API_NUVEM))
                .modoIntegracao(ModoIntegracaoTerminal.API_NUVEM).provedor(ProvedorPagamento.MERCADO_PAGO)
                .idExterno("intent-1").build();
        when(transacaoRepo.findByIdParaAtualizar(50L)).thenReturn(Optional.of(t));
        when(credenciais.credencialObrigatoria(any())).thenReturn("TOKEN");
        when(mercadoPago.consultar(any(), any(), any())).thenReturn(new RetornoProvedor(
                StatusTransacaoTerminal.APROVADA, "intent-1", "1", "A", "visa", "Aprovado", "{}"));

        assertThatThrownBy(() -> service.cancelar(50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("acabou de pagar");
        verify(mercadoPago, never()).cancelar(any(), any(), any());
    }

    @Test
    @DisplayName("validarParaPagamento: recusa transação de outra venda, outro valor ou já usada")
    void validarParaPagamento_regras() {
        TransacaoTerminal t = TransacaoTerminal.builder().id(50L).vendaId(1L).tipo("DEBITO")
                .valor(new BigDecimal("30.00")).status(StatusTransacaoTerminal.APROVADA).build();
        when(transacaoRepo.findById(50L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.validarParaPagamento(50L, 2L, "DEBITO", new BigDecimal("30.00")))
                .hasMessageContaining("outra venda");
        assertThatThrownBy(() -> service.validarParaPagamento(50L, 1L, "DEBITO", new BigDecimal("31.00")))
                .hasMessageContaining("difere");
        assertThatThrownBy(() -> service.validarParaPagamento(50L, 1L, "CREDITO", new BigDecimal("30.00")))
                .hasMessageContaining("DEBITO");

        when(pagamentoRepo.existsByTransacaoTerminalId(50L)).thenReturn(true);
        assertThatThrownBy(() -> service.validarParaPagamento(50L, 1L, "DEBITO", new BigDecimal("30.00")))
                .hasMessageContaining("já foi usada");
    }
}
