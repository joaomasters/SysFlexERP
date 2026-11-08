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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O PLU da balança só pode estar em um produto ativo (issue #77). */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoService — PLU único")
class ProdutoServicePluTest {

    @Mock ProdutoRepository produtoRepo;
    @Mock FornecedorRepository fornecedorRepo;
    @Mock ItemPendenteBalancaService itemPendenteBalancaService;
    @InjectMocks ProdutoService service;

    private Produto produto(Long id, String nome, Integer plu, boolean ativo) {
        return Produto.builder().id(id).nome(nome).unidadeMedida("KG").tipoProduto("CORTE")
                .precoVenda(new BigDecimal("43.99")).codigoInterno("P00000" + (id == null ? 9 : id))
                .codigoBalanca(plu).ativo(ativo).build();
    }

    @Test
    @DisplayName("salvar: recusa PLU já usado e diz em qual produto")
    void salvarRecusaDuplicado() {
        when(produtoRepo.findFirstByCodigoBalancaAndAtivoTrueAndIdNot(90, 0L))
                .thenReturn(Optional.of(produto(1L, "ESTOQUE", 90, true)));

        assertThatThrownBy(() -> service.salvar(produto(null, "DUPLICADO", 90, true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PLU 90")
                .hasMessageContaining("ESTOQUE");
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("salvar: PLU livre passa; produto sem PLU nem consulta")
    void salvarLivreOuSemPlu() {
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        service.salvar(produto(null, "Novo", 91, true), 1L);
        service.salvar(produto(null, "Sem balança", null, true), 1L);

        verify(produtoRepo).findFirstByCodigoBalancaAndAtivoTrueAndIdNot(91, 0L);
        verify(produtoRepo, never()).findFirstByCodigoBalancaAndAtivoTrueAndIdNot(org.mockito.ArgumentMatchers.isNull(), anyLong());
    }

    @Test
    @DisplayName("atualizar: recusa trocar para o PLU de outro produto")
    void atualizarRecusaDuplicado() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, "Posta", 80, true)));
        when(produtoRepo.findFirstByCodigoBalancaAndAtivoTrueAndIdNot(90, 2L))
                .thenReturn(Optional.of(produto(1L, "ESTOQUE", 90, true)));

        assertThatThrownBy(() -> service.atualizar(2L, produto(2L, "Posta", 90, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PLU 90");
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("atualizar: PLU igual ao atual não trava a edição de duplicados antigos")
    void atualizarMesmoPlu() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, "Posta", 90, true)));
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        service.atualizar(2L, produto(2L, "Posta", 90, true));

        verify(produtoRepo, never()).findFirstByCodigoBalancaAndAtivoTrueAndIdNot(anyInt(), anyLong());
    }

    @Test
    @DisplayName("atualizar: reativar produto confere se o PLU foi reutilizado")
    void reativarConfere() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, "Posta", 90, false)));
        when(produtoRepo.findFirstByCodigoBalancaAndAtivoTrueAndIdNot(90, 2L))
                .thenReturn(Optional.of(produto(1L, "ESTOQUE", 90, true)));

        assertThatThrownBy(() -> service.atualizar(2L, produto(2L, "Posta", 90, true)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("atualizar: tirar o PLU (balança desmarcada) é permitido")
    void atualizarRemovePlu() {
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(produto(2L, "Posta", 90, true)));
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        Produto salvo = service.atualizar(2L, produto(2L, "Posta", null, true));

        assertThat(salvo.getCodigoBalanca()).isNull();
    }
}