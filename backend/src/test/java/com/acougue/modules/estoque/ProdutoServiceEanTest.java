package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.ItemPendenteBalancaService;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Um código de barras só pode estar em um produto ativo. */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceEanTest {

    private static final String EAN = "7891234567895";

    @Mock ProdutoRepository produtoRepo;
    @Mock FornecedorRepository fornecedorRepo;
    @Mock ItemPendenteBalancaService itemPendenteBalancaService;
    @InjectMocks ProdutoService service;

    private Produto produto(Long id, String ean, boolean ativo) {
        return Produto.builder().id(id)
                .nome("Kurupi").unidadeMedida("UN").tipoProduto("BEBIDA")
                .precoVenda(new BigDecimal("24.98")).codigoInterno("P000001")
                .ean13(ean).ativo(ativo)
                .build();
    }

    @Test
    @DisplayName("salvar: recusa código já usado e diz em qual produto")
    void salvarRecusaDuplicado() {
        when(produtoRepo.findFirstByEan13AndAtivoTrueAndIdNot(EAN, 0L))
                .thenReturn(Optional.of(produto(1L, EAN, true)));

        assertThatThrownBy(() -> service.salvar(produto(null, EAN, true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(EAN)
                .hasMessageContaining("Kurupi");
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("salvar: ignora espaços em volta do código na comparação")
    void salvarLimpaEspacos() {
        when(produtoRepo.findFirstByEan13AndAtivoTrueAndIdNot(EAN, 0L))
                .thenReturn(Optional.of(produto(1L, EAN, true)));

        assertThatThrownBy(() -> service.salvar(produto(null, " " + EAN + " ", true), 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("salvar: código em branco vira vazio e vários produtos podem ficar sem código")
    void salvarSemCodigo() {
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        Produto salvo = service.salvar(produto(null, "  ", true), 1L);

        assertThat(salvo.getEan13()).isNull();
        verify(produtoRepo, never()).findFirstByEan13AndAtivoTrueAndIdNot(anyString(), anyLong());
    }

    @Test
    @DisplayName("atualizar: recusa trocar para um código de outro produto")
    void atualizarRecusaDuplicado() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, "1111111111111", true)));
        when(produtoRepo.findFirstByEan13AndAtivoTrueAndIdNot(EAN, 2L))
                .thenReturn(Optional.of(produto(1L, EAN, true)));

        assertThatThrownBy(() -> service.atualizar(2L, produto(2L, EAN, true)))
                .isInstanceOf(BusinessException.class);
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("atualizar: código igual ao atual não trava a edição de duplicados antigos")
    void atualizarMesmoCodigo() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, EAN, true)));
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        service.atualizar(2L, produto(2L, EAN, true));

        verify(produtoRepo, never()).findFirstByEan13AndAtivoTrueAndIdNot(anyString(), anyLong());
    }

    @Test
    @DisplayName("atualizar: reativar produto confere se o código foi reutilizado")
    void reativarConfere() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, EAN, false)));
        when(produtoRepo.findFirstByEan13AndAtivoTrueAndIdNot(EAN, 2L))
                .thenReturn(Optional.of(produto(1L, EAN, true)));

        assertThatThrownBy(() -> service.atualizar(2L, produto(2L, EAN, true)))
                .isInstanceOf(BusinessException.class);
    }
}
