package com.acougue.modules.estoque;

import com.acougue.entity.MovimentacaoEstoque;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.repository.LoteEstoqueRepository;
import com.acougue.repository.MovimentacaoEstoqueRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstoqueService")
class EstoqueServiceTest {

    @Mock ProdutoRepository      produtoRepo;
    @Mock MovimentacaoEstoqueRepository movRepo;
    @Mock LoteEstoqueRepository loteRepo;

    @InjectMocks EstoqueService service;

    private Produto produto;

    @BeforeEach
    void setUp() {
        produto = Produto.builder()
                .id(1L)
                .nome("Picanha")
                .unidadeMedida("KG")
                .estoqueAtual(new BigDecimal("10.000"))
                .precoCusto(new BigDecimal("50.0000"))
                .precoVenda(new BigDecimal("89.90"))
                .build();
    }



    @Test
    @DisplayName("entrada: incrementa estoqueAtual corretamente")
    void entrada_incrementaEstoque() {
        service.entrada(produto, new BigDecimal("5.000"), new BigDecimal("50.00"),
                "ENTRADA_NF", "NF-001", 1L);

        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("15.0000");
        verify(produtoRepo).save(produto);
    }

    @Test
    @DisplayName("entrada: atualiza custo médio ponderado")
    void entrada_atualizaCustoMedio() {



        service.entrada(produto, new BigDecimal("5.000"), new BigDecimal("60.00"),
                "ENTRADA_NF", "NF-001", 1L);

        assertThat(produto.getPrecoCusto())
                .isEqualByComparingTo(new BigDecimal("53.3333"));
    }

    @Test
    @DisplayName("entrada: registra movimentação no repositório")
    void entrada_registraMovimentacao() {
        service.entrada(produto, new BigDecimal("3.000"), new BigDecimal("50.00"),
                "ENTRADA_NF", "NF-001", 1L);

        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movRepo).save(captor.capture());
        assertThat(captor.getValue().getTipoMovimentacao()).isEqualTo("ENTRADA_NF");
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo("3.000");
    }

    @Test
    @DisplayName("entrada: lança BusinessException para quantidade zero")
    void entrada_lancaExcecao_quantidadeZero() {
        assertThatThrownBy(() ->
                service.entrada(produto, BigDecimal.ZERO, new BigDecimal("50.00"),
                        "ENTRADA_NF", "NF-001", 1L)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("positiva");

        verifyNoInteractions(produtoRepo, movRepo);
    }

    @Test
    @DisplayName("entrada: lança BusinessException para quantidade negativa")
    void entrada_lancaExcecao_quantidadeNegativa() {
        assertThatThrownBy(() ->
                service.entrada(produto, new BigDecimal("-1.000"), new BigDecimal("50.00"),
                        "ENTRADA_NF", "NF-001", 1L)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("entrada: custo médio não é alterado quando custoUnitario é zero")
    void entrada_naAlteraCusto_quandoCustoZero() {
        BigDecimal custoOriginal = produto.getPrecoCusto();
        service.entrada(produto, new BigDecimal("5.000"), BigDecimal.ZERO,
                "AJUSTE", "ADJ-001", 1L);
        assertThat(produto.getPrecoCusto()).isEqualByComparingTo(custoOriginal);
    }



    @Test
    @DisplayName("saida: decrementa estoqueAtual corretamente")
    void saida_decrementaEstoque() {
        service.saida(produto, new BigDecimal("3.000"), "SAIDA_VENDA", "VENDA#1", 1L);

        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("7.0000");
        verify(produtoRepo).save(produto);
    }

    @Test
    @DisplayName("saida: registra movimentação no repositório")
    void saida_registraMovimentacao() {
        service.saida(produto, new BigDecimal("2.000"), "SAIDA_VENDA", "VENDA#1", 1L);

        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movRepo).save(captor.capture());
        assertThat(captor.getValue().getTipoMovimentacao()).isEqualTo("SAIDA_VENDA");
    }

    @Test
    @DisplayName("saida: lança BusinessException quando estoque é insuficiente")
    void saida_lancaExcecao_estoqueInsuficiente() {
        assertThatThrownBy(() ->
                service.saida(produto, new BigDecimal("15.000"), "SAIDA_VENDA", "VENDA#1", 1L)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente");

        verifyNoInteractions(movRepo);
    }

    @Test
    @DisplayName("saida: lança BusinessException para quantidade zero")
    void saida_lancaExcecao_quantidadeZero() {
        assertThatThrownBy(() ->
                service.saida(produto, BigDecimal.ZERO, "SAIDA_VENDA", "VENDA#1", 1L)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("positiva");
    }

    @Test
    @DisplayName("saida: exatamente o estoque disponível é permitido (limite)")
    void saida_permiteQuantidadeExataDoEstoque() {
        assertThatCode(() ->
                service.saida(produto, new BigDecimal("10.000"), "SAIDA_VENDA", "VENDA#1", 1L)
        ).doesNotThrowAnyException();

        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("0.0000");
    }



    @Test
    @DisplayName("ajuste positivo: incrementa estoque")
    void ajustePositivo_incrementaEstoque() {
        service.ajuste(produto, new BigDecimal("2.000"), "AJUSTE_POSITIVO", "INV-001", 1L);
        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("12.0000");
    }

    @Test
    @DisplayName("ajuste negativo: decrementa estoque sem ir abaixo de zero")
    void ajusteNegativo_naoVaiAbaixoDeZero() {

        service.ajuste(produto, new BigDecimal("50.000"), "AJUSTE_NEGATIVO", "INV-001", 1L);
        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("0.0000");
    }
}
