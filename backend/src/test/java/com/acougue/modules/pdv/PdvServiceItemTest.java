package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.EanBalancaParser;
import com.acougue.modules.balanca.dto.EanParseResult;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.pagamento.TransacaoTerminalService;
import com.acougue.modules.pdv.dto.ItemVendaDTO;
import com.acougue.repository.*;
import com.acougue.security.AutorizacaoAdminService;
import com.acougue.security.auditoria.AuditoriaService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Itens da comanda: valor calculado no servidor e troca de quantidade. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdvService — itens da comanda")
class PdvServiceItemTest {

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

    private final Venda venda = Venda.builder().id(1L).status("ABERTA").desconto(BigDecimal.ZERO).build();
    private final Produto espeto = Produto.builder().id(5L).nome("Espeto de Queijo").unidadeMedida("UN")
            .precoVenda(new BigDecimal("9.00")).build();
    private final Produto picanha = Produto.builder().id(6L).nome("Picanha").unidadeMedida("KG")
            .precoVenda(new BigDecimal("69.90")).build();

    private void vendaAberta() {
        when(vendaRepo.findById(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ItensVenda salvo() {
        ArgumentCaptor<ItensVenda> c = ArgumentCaptor.forClass(ItensVenda.class);
        verify(itensRepo).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("ignora o valor total mandado pela tela: calcula quantidade × preço")
    void totalDaTelaIgnorado() {
        vendaAberta();
        when(produtoRepo.findById(5L)).thenReturn(Optional.of(espeto));
        ItemVendaDTO dto = ItemVendaDTO.builder().produtoId(5L).quantidade(new BigDecimal("2"))
                .totalItem(new BigDecimal("0.01")).tipoEntrada("MANUAL").build();

        service.adicionarItem(1L, dto);

        assertThat(salvo().getTotalItem()).isEqualByComparingTo("18.00");
    }

    @Test
    @DisplayName("etiqueta de balança é relida no servidor: peso e valor vêm da etiqueta, não da tela")
    void etiquetaRelida() {
        vendaAberta();
        String ean = "2000800012345";
        when(produtoRepo.findById(6L)).thenReturn(Optional.of(picanha));
        when(produtoRepo.findByCodigoBalanca(anyInt())).thenReturn(Optional.of(picanha));
        when(eanParser.parse(ean, picanha.getPrecoVenda())).thenReturn(EanParseResult.builder()
                .pesoKg(new BigDecimal("1.250")).valorTotal(new BigDecimal("87.38")).build());
        ItemVendaDTO dto = ItemVendaDTO.builder().produtoId(6L).quantidade(new BigDecimal("0.001"))
                .totalItem(new BigDecimal("0.01")).ean13Lido(ean).tipoEntrada("BARCODE").build();

        service.adicionarItem(1L, dto);

        ItensVenda item = salvo();
        assertThat(item.getQuantidade()).isEqualByComparingTo("1.250");
        assertThat(item.getTotalItem()).isEqualByComparingTo("87.38");
    }

    @Test
    @DisplayName("produto por unidade não aceita quantidade quebrada")
    void unidadeInteira() {
        vendaAberta();
        when(produtoRepo.findById(5L)).thenReturn(Optional.of(espeto));
        ItemVendaDTO dto = ItemVendaDTO.builder().produtoId(5L).quantidade(new BigDecimal("1.5")).tipoEntrada("MANUAL").build();

        assertThatThrownBy(() -> service.adicionarItem(1L, dto))
                .isInstanceOf(BusinessException.class).hasMessageContaining("número inteiro");
    }

    @Test
    @DisplayName("troca a quantidade e mantém o desconto já dado")
    void alteraQuantidade() {
        vendaAberta();
        ItensVenda item = ItensVenda.builder().id(9L).venda(venda).produto(espeto).quantidade(BigDecimal.ONE)
                .precoUnitario(new BigDecimal("9.00")).descontoItem(new BigDecimal("1.00"))
                .totalItem(new BigDecimal("8.00")).tipoEntrada("BARCODE").ean13Lido("7891234567895").build();
        when(itensRepo.findById(9L)).thenReturn(Optional.of(item));
        when(itensRepo.findByVendaId(1L)).thenReturn(List.of(item));

        Venda resultado = service.alterarQuantidadeItem(1L, 9L, new BigDecimal("3"));

        assertThat(item.getQuantidade()).isEqualByComparingTo("3");
        assertThat(item.getTotalItem()).isEqualByComparingTo("26.00"); // 27,00 − 1,00
        assertThat(resultado.getTotal()).isEqualByComparingTo("26.00");
    }

    @Test
    @DisplayName("não troca o peso de etiqueta da balança")
    void etiquetaNaoMuda() {
        vendaAberta();
        ItensVenda item = ItensVenda.builder().id(9L).venda(venda).produto(picanha)
                .ean13Lido("2000800012345").build();
        when(itensRepo.findById(9L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.alterarQuantidadeItem(1L, 9L, new BigDecimal("2")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("etiqueta");
    }

    @Test
    @DisplayName("não mexe em item de outra comanda (trocar ou excluir)")
    void itemDeOutraVenda() {
        vendaAberta();
        Venda outra = Venda.builder().id(2L).status("ABERTA").build();
        ItensVenda alheio = ItensVenda.builder().id(9L).venda(outra).produto(espeto).build();
        when(itensRepo.findById(9L)).thenReturn(Optional.of(alheio));

        assertThatThrownBy(() -> service.alterarQuantidadeItem(1L, 9L, new BigDecimal("2")))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.removerItem(1L, 9L))
                .isInstanceOf(EntityNotFoundException.class);
        verify(itensRepo, never()).delete(any());
    }
}
