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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Preço de custo editável na alteração do produto (antes era ignorado). */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoService — preço de custo na alteração")
class ProdutoServiceCustoTest {

    @Mock ProdutoRepository produtoRepo;
    @Mock FornecedorRepository fornecedorRepo;
    @Mock ItemPendenteBalancaService itemPendenteBalancaService;
    @InjectMocks ProdutoService service;

    private Produto produto(String custo) {
        return Produto.builder().id(1L).nome("Picanha").unidadeMedida("KG").tipoProduto("BOVINO")
                .precoVenda(new BigDecimal("79.90")).precoCusto(custo == null ? null : new BigDecimal(custo))
                .codigoInterno("P000001").ativo(true).build();
    }

    private void existente(String custo) {
        when(produtoRepo.findById(1L)).thenReturn(Optional.of(produto(custo)));
        lenient().when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("grava o custo corrigido pelo usuário")
    void gravaCusto() {
        existente("45.0000");
        Produto salvo = service.atualizar(1L, produto("52.30"));
        assertThat(salvo.getPrecoCusto()).isEqualByComparingTo("52.30");
    }

    @Test
    @DisplayName("sem custo no corpo, mantém o custo atual (custo médio das entradas)")
    void semCustoMantem() {
        existente("45.0000");
        Produto salvo = service.atualizar(1L, produto(null));
        assertThat(salvo.getPrecoCusto()).isEqualByComparingTo("45.0000");
    }

    @Test
    @DisplayName("custo negativo é recusado com mensagem clara")
    void recusaNegativo() {
        existente("45.0000");
        assertThatThrownBy(() -> service.atualizar(1L, produto("-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O preço de custo não pode ser negativo.");
    }
}
