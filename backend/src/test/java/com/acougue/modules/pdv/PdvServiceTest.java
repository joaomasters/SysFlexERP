package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.EanBalancaParser;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.pagamento.TransacaoTerminalService;
import com.acougue.modules.pdv.dto.*;
import com.acougue.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdvService")
class PdvServiceTest {

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
    @Mock ApplicationEventPublisher  eventPublisher;
    @Mock CaixaService               caixaService;
    @Mock TransacaoTerminalService   transacaoTerminalService;

    @InjectMocks PdvService service;

    private Caixa caixaAberto;
    private Produto picanha;

    @BeforeEach
    void setUp() {
        caixaAberto = Caixa.builder()
                .id(1L).operadorId(10L).status("ABERTO")
                .valorAbertura(new BigDecimal("200.00")).build();

        picanha = Produto.builder()
                .id(5L).nome("Picanha").unidadeMedida("KG")
                .ean13("1234567890128")
                .precoVenda(new BigDecimal("89.90"))
                .precoCusto(new BigDecimal("45.00"))
                .estoqueAtual(new BigDecimal("20.000")).build();
    }

    

    @Test
    @DisplayName("abrirCaixa: cria caixa com status ABERTO")
    void abrirCaixa_criaCaixaAberto() {
        when(caixaRepo.findFirstByOperadorIdAndStatus(10L, "ABERTO")).thenReturn(Optional.empty());
        when(caixaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Caixa resultado = service.abrirCaixa(10L, new BigDecimal("200.00"));

        assertThat(resultado.getStatus()).isEqualTo("ABERTO");
        assertThat(resultado.getOperadorId()).isEqualTo(10L);
        assertThat(resultado.getValorAbertura()).isEqualByComparingTo("200.00");
        verify(caixaRepo).save(any(Caixa.class));
    }

    @Test
    @DisplayName("abrirCaixa: lança BusinessException quando operador já tem caixa aberto")
    void abrirCaixa_lancaExcecao_operadorJaTemCaixaAberto() {
        when(caixaRepo.findFirstByOperadorIdAndStatus(10L, "ABERTO"))
                .thenReturn(Optional.of(caixaAberto));

        assertThatThrownBy(() -> service.abrirCaixa(10L, new BigDecimal("200.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caixa aberto");

        verify(caixaRepo, never()).save(any());
    }

    

    @Test
    @DisplayName("fecharCaixa: muda status para FECHADO")
    void fecharCaixa_mudaStatusParaFechado() {
        when(caixaRepo.findById(1L)).thenReturn(Optional.of(caixaAberto));
        when(caixaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caixaService.calcularFechamento(1L)).thenReturn(
                FechamentoCaixaDetalhadoDTO.builder().saldoEsperado(new BigDecimal("350.00")).build());

        Caixa resultado = service.fecharCaixa(1L, new BigDecimal("350.00"));

        assertThat(resultado.getStatus()).isEqualTo("FECHADO");
        assertThat(resultado.getValorFechamentoInformado()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("fecharCaixa: lança BusinessException quando caixa já está fechado")
    void fecharCaixa_lancaExcecao_caixaJaFechado() {
        caixaAberto.setStatus("FECHADO");
        when(caixaRepo.findById(1L)).thenReturn(Optional.of(caixaAberto));

        assertThatThrownBy(() -> service.fecharCaixa(1L, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fechado");
    }

    @Test
    @DisplayName("fecharCaixa: lança EntityNotFoundException quando caixa não existe")
    void fecharCaixa_lancaExcecao_caixaNaoEncontrado() {
        when(caixaRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fecharCaixa(99L, BigDecimal.ZERO))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    

    @Test
    @DisplayName("abrirVenda: cria venda com status ABERTA vinculada ao caixa")
    void abrirVenda_criaVendaAberta() {
        AbrirVendaDTO dto = new AbrirVendaDTO();
        dto.setCaixaId(1L);
        dto.setOperadorId(10L);

        when(caixaRepo.findById(1L)).thenReturn(Optional.of(caixaAberto));
        when(vendaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venda venda = service.abrirVenda(dto);

        assertThat(venda.getStatus()).isEqualTo("ABERTA");
        assertThat(venda.getCaixa()).isEqualTo(caixaAberto);
        assertThat(venda.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("abrirVenda: lança BusinessException quando caixa está fechado")
    void abrirVenda_lancaExcecao_caixaFechado() {
        caixaAberto.setStatus("FECHADO");

        AbrirVendaDTO dto = new AbrirVendaDTO();
        dto.setCaixaId(1L);
        dto.setOperadorId(10L);

        when(caixaRepo.findById(1L)).thenReturn(Optional.of(caixaAberto));

        assertThatThrownBy(() -> service.abrirVenda(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Caixa está fechado");
    }

    

    @Test
    @DisplayName("fecharVenda: calcula troco corretamente")
    void fecharVenda_calculaTroco() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto).operadorId(10L)
                .total(new BigDecimal("50.00")).desconto(BigDecimal.ZERO).build();

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("DINHEIRO");
        pag.setValor(new BigDecimal("60.00"));
        dto.setPagamentos(List.of(pag));

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(itensRepo.findByVendaId(1L)).thenReturn(List.of());
        when(vendaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venda resultado = service.fecharVenda(dto);

        assertThat(resultado.getStatus()).isEqualTo("FECHADA");
        assertThat(resultado.getTroco()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("fecharVenda: grava snapshot da comissão do operador sobre o total")
    void fecharVenda_registraComissaoDoOperador() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto).operadorId(10L)
                .total(new BigDecimal("89.90")).desconto(BigDecimal.ZERO).build();

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("DINHEIRO");
        pag.setValor(new BigDecimal("89.90"));
        dto.setPagamentos(List.of(pag));

        Usuario operador = Usuario.builder().id(10L).percentualComissao(new BigDecimal("2.50")).build();

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(itensRepo.findByVendaId(1L)).thenReturn(List.of());
        when(usuarioRepo.findById(10L)).thenReturn(Optional.of(operador));
        when(vendaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venda resultado = service.fecharVenda(dto);

        assertThat(resultado.getPercentualComissao()).isEqualByComparingTo("2.50");
        assertThat(resultado.getValorComissao()).isEqualByComparingTo("2.25"); // 89,90 × 2,5% = 2,2475
    }

    @Test
    @DisplayName("fecharVenda: lança BusinessException quando pagamento é insuficiente")
    void fecharVenda_lancaExcecao_pagamentoInsuficiente() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto)
                .total(new BigDecimal("100.00")).desconto(BigDecimal.ZERO).build();

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("DINHEIRO");
        pag.setValor(new BigDecimal("80.00")); 
        dto.setPagamentos(List.of(pag));

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));

        assertThatThrownBy(() -> service.fecharVenda(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pagamento insuficiente");
    }

    @Test
    @DisplayName("fecharVenda: pagamento FIADO sem cliente lança BusinessException")
    void fecharVenda_fiado_semCliente_lancaExcecao() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto)
                .cliente(null) 
                .total(new BigDecimal("50.00")).desconto(BigDecimal.ZERO).build();

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("FIADO");
        pag.setValor(new BigDecimal("50.00"));
        dto.setPagamentos(List.of(pag));

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        
        

        assertThatThrownBy(() -> service.fecharVenda(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FIADO exige cliente");
    }

    @Test
    @DisplayName("fecharVenda: baixa estoque de cada item vendido")
    void fecharVenda_baixaEstoqueDosItens() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto).operadorId(10L)
                .total(new BigDecimal("89.90")).desconto(BigDecimal.ZERO).build();

        ItensVenda item = ItensVenda.builder()
                .id(1L).venda(venda).produto(picanha)
                .quantidade(new BigDecimal("1.000"))
                .totalItem(new BigDecimal("89.90")).build();

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("DINHEIRO");
        pag.setValor(new BigDecimal("90.00"));
        dto.setPagamentos(List.of(pag));

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(itensRepo.findByVendaId(1L)).thenReturn(List.of(item));
        when(vendaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.fecharVenda(dto);

        verify(estoqueService).saida(eq(picanha), eq(new BigDecimal("1.000")),
                eq("SAIDA_VENDA"), eq("VENDA#1"), eq(10L));
    }

    @Test
    @DisplayName("fecharVenda: cartão de maquininha copia NSU/autorização da transação aprovada, não da tela")
    void fecharVenda_cartaoIntegrado_usaDadosDaTransacao() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto).operadorId(10L)
                .total(new BigDecimal("50.00")).desconto(BigDecimal.ZERO).build();

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("CREDITO");
        pag.setValor(new BigDecimal("50.00"));
        pag.setTransacaoTerminalId(7L);
        pag.setNsu("FORJADO");

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);
        dto.setPagamentos(List.of(pag));

        TransacaoTerminal transacao = TransacaoTerminal.builder()
                .id(7L).vendaId(1L).tipo("CREDITO").parcelas(3).valor(new BigDecimal("50.00"))
                .status(StatusTransacaoTerminal.APROVADA).provedor(ProvedorPagamento.MERCADO_PAGO)
                .nsu("123456").autorizacao("A1B2C3").bandeira("visa").build();

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(transacaoTerminalService.validarParaPagamento(7L, 1L, "CREDITO", new BigDecimal("50.00")))
                .thenReturn(transacao);
        when(itensRepo.findByVendaId(1L)).thenReturn(List.of());
        when(vendaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.fecharVenda(dto);

        org.mockito.ArgumentCaptor<PagamentoVenda> salvo = org.mockito.ArgumentCaptor.forClass(PagamentoVenda.class);
        verify(pagRepo).save(salvo.capture());
        assertThat(salvo.getValue().getNsu()).isEqualTo("123456");
        assertThat(salvo.getValue().getAutorizacao()).isEqualTo("A1B2C3");
        assertThat(salvo.getValue().getParcelas()).isEqualTo(3);
        assertThat(salvo.getValue().getTransacaoTerminalId()).isEqualTo(7L);
        assertThat(salvo.getValue().getProvedor()).isEqualTo("MERCADO_PAGO");
    }

    @Test
    @DisplayName("fecharVenda: transação de cartão inválida impede o fechamento")
    void fecharVenda_cartaoIntegrado_transacaoInvalida() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto)
                .total(new BigDecimal("50.00")).desconto(BigDecimal.ZERO).build();

        PagamentoDTO pag = new PagamentoDTO();
        pag.setFormaPagamento("DEBITO");
        pag.setValor(new BigDecimal("50.00"));
        pag.setTransacaoTerminalId(7L);

        FecharVendaDTO dto = new FecharVendaDTO();
        dto.setVendaId(1L);
        dto.setPagamentos(List.of(pag));

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(transacaoTerminalService.validarParaPagamento(any(), any(), any(), any()))
                .thenThrow(new BusinessException("não está aprovada"));

        assertThatThrownBy(() -> service.fecharVenda(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não está aprovada");
        verify(pagRepo, never()).save(any());
    }

    

    @Test
    @DisplayName("cancelarVenda: muda status para CANCELADA")
    void cancelarVenda_mudaStatusParaCancelada() {
        Venda venda = Venda.builder()
                .id(1L).status("ABERTA").caixa(caixaAberto).build();

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(vendaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venda resultado = service.cancelarVenda(1L);

        assertThat(resultado.getStatus()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("cancelarVenda: lança EntityNotFoundException para venda já fechada")
    void cancelarVenda_lancaExcecao_vendaJaFechada() {
        Venda venda = Venda.builder()
                .id(1L).status("FECHADA").build();

        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));

        assertThatThrownBy(() -> service.cancelarVenda(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("já encerrada");
    }

    

    @Test
    @DisplayName("processarBarcode: EAN padrão retorna item com quantidade 1")
    void processarBarcode_eanPadrao_retornaQuantidade1() {
        when(produtoRepo.findByEan13("1234567890128")).thenReturn(Optional.of(picanha));

        ItemVendaDTO item = service.processarBarcode("1234567890128");

        assertThat(item.getProdutoId()).isEqualTo(5L);
        assertThat(item.getQuantidade()).isEqualByComparingTo("1");
        assertThat(item.getTotalItem()).isEqualByComparingTo("89.90");
        assertThat(item.getTipoEntrada()).isEqualTo("BARCODE");
    }

    @Test
    @DisplayName("processarBarcode: lança EntityNotFoundException para EAN desconhecido")
    void processarBarcode_eanDesconhecido_lancaExcecao() {
        when(produtoRepo.findByEan13("1234567890128")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processarBarcode("1234567890128"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
