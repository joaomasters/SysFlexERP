package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.EanBalancaParser;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.pagamento.TransacaoTerminalService;
import com.acougue.repository.*;
import com.acougue.security.AutorizacaoAdminService;
import com.acougue.security.auditoria.AuditoriaService;
import jakarta.persistence.EntityNotFoundException;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Desconto em um item da comanda aberta. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdvService — desconto no item")
class PdvServiceDescontoItemTest {

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

    private Venda venda(String status) {
        return Venda.builder().id(1L).status(status).desconto(BigDecimal.ZERO).build();
    }

    private ItensVenda item(Venda venda, String total, String desconto) {
        return ItensVenda.builder().id(9L).venda(venda)
                .produto(Produto.builder().id(5L).nome("Picanha").build())
                .totalItem(new BigDecimal(total)).descontoItem(new BigDecimal(desconto)).build();
    }

    private void preparar(Venda venda, ItensVenda item) {
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        when(itensRepo.findById(9L)).thenReturn(Optional.of(item));
        lenient().when(itensRepo.findByVendaId(1L)).thenReturn(List.of(item));
        lenient().when(vendaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("reduz o total do item e o total da venda, e registra na auditoria")
    void aplicaDesconto() {
        Venda venda = venda("ABERTA");
        ItensVenda item = item(venda, "59.42", "0");
        preparar(venda, item);

        Venda resultado = service.aplicarDescontoItem(1L, 9L, new BigDecimal("9.42"));

        assertThat(item.getDescontoItem()).isEqualByComparingTo("9.42");
        assertThat(item.getTotalItem()).isEqualByComparingTo("50.00");
        assertThat(resultado.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(resultado.getTotal()).isEqualByComparingTo("50.00");
        verify(auditoriaService).registrar(eq("PDV"), eq("EDITAR"), contains("Picanha"));
    }

    @Test
    @DisplayName("um novo desconto substitui o anterior, sem acumular")
    void substituiDescontoAnterior() {
        Venda venda = venda("ABERTA");
        ItensVenda item = item(venda, "50.00", "10.00");
        preparar(venda, item);

        service.aplicarDescontoItem(1L, 9L, new BigDecimal("5.00"));

        assertThat(item.getDescontoItem()).isEqualByComparingTo("5.00");
        assertThat(item.getTotalItem()).isEqualByComparingTo("55.00");
    }

    @Test
    @DisplayName("zero remove o desconto e devolve o valor cheio")
    void zeroRemoveDesconto() {
        Venda venda = venda("ABERTA");
        ItensVenda item = item(venda, "50.00", "10.00");
        preparar(venda, item);

        service.aplicarDescontoItem(1L, 9L, BigDecimal.ZERO);

        assertThat(item.getDescontoItem()).isEqualByComparingTo("0");
        assertThat(item.getTotalItem()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("recusa desconto igual ou maior que o valor do item, contando o desconto anterior")
    void recusaDescontoDoValorInteiro() {
        Venda venda = venda("ABERTA");
        ItensVenda item = item(venda, "50.00", "10.00");
        preparar(venda, item);

        assertThatThrownBy(() -> service.aplicarDescontoItem(1L, 9L, new BigDecimal("60.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("menor que o valor do item");

        assertThat(item.getTotalItem()).isEqualByComparingTo("50.00");
        verify(itensRepo, never()).save(any());
    }

    @Test
    @DisplayName("recusa desconto negativo, que aumentaria o preço")
    void recusaDescontoNegativo() {
        Venda venda = venda("ABERTA");
        preparar(venda, item(venda, "50.00", "0"));

        assertThatThrownBy(() -> service.aplicarDescontoItem(1L, 9L, new BigDecimal("-1.00")))
                .isInstanceOf(BusinessException.class);
        verify(itensRepo, never()).save(any());
    }

    @Test
    @DisplayName("não mexe em item de outra venda")
    void recusaItemDeOutraVenda() {
        Venda outra = Venda.builder().id(2L).status("ABERTA").build();
        Venda venda = venda("ABERTA");
        preparar(venda, item(outra, "50.00", "0"));

        assertThatThrownBy(() -> service.aplicarDescontoItem(1L, 9L, BigDecimal.ONE))
                .isInstanceOf(EntityNotFoundException.class);
        verify(itensRepo, never()).save(any());
    }

    @Test
    @DisplayName("não aceita desconto em venda já fechada")
    void recusaVendaFechada() {
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda("FECHADA")));

        assertThatThrownBy(() -> service.aplicarDescontoItem(1L, 9L, BigDecimal.ONE))
                .isInstanceOf(EntityNotFoundException.class);
        verify(itensRepo, never()).save(any());
    }
}
