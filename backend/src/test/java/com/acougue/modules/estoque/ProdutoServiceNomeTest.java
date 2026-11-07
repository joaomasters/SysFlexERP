package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.ItemPendenteBalancaService;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Espaços no nome do produto: limpos ao salvar; nome só com espaços é recusado com mensagem clara. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoService — espaços no nome")
class ProdutoServiceNomeTest {

    @Mock ProdutoRepository produtoRepo;
    @Mock FornecedorRepository fornecedorRepo;
    @Mock ItemPendenteBalancaService itemPendenteBalancaService;
    @InjectMocks ProdutoService service;

    private Produto produto(String nome) {
        return Produto.builder().nome(nome).unidadeMedida("KG").tipoProduto("BOVINO")
                .precoVenda(new BigDecimal("43.99")).codigoInterno("P000001").ativo(true).build();
    }

    @Test
    @DisplayName("normalizarNome: tira espaços das pontas e junta os repetidos")
    void normaliza() {
        assertThat(ProdutoService.normalizarNome("  Picanha   Maturada ")).isEqualTo("Picanha Maturada");
        assertThat(ProdutoService.normalizarNome("Posta\tVermelha")).isEqualTo("Posta Vermelha");
    }

    @Test
    @DisplayName("normalizarNome: vazio, nulo ou só espaços é recusado com mensagem em português")
    void recusaVazio() {
        for (String nome : new String[]{null, "", "   ", "\t \n"}) {
            assertThatThrownBy(() -> ProdutoService.normalizarNome(nome))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Informe a descrição do produto.");
        }
    }

    @Test
    @DisplayName("salvar: grava o nome já limpo")
    void salvarGravaLimpo() {
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        service.salvar(produto("  Posta   Vermelha  "), 1L);

        ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepo).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("Posta Vermelha");
    }

    @Test
    @DisplayName("salvar: nome só com espaços não chega ao banco")
    void salvarRecusaSoEspacos() {
        assertThatThrownBy(() -> service.salvar(produto("     "), 1L))
                .isInstanceOf(BusinessException.class);
        verify(produtoRepo, never()).save(any());
    }
}