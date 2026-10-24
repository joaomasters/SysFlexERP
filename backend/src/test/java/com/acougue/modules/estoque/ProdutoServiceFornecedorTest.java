package com.acougue.modules.estoque;

import com.acougue.entity.Fornecedor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Vínculo produto ↔ fornecedor cadastrado (dropdown do Novo Produto). */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceFornecedorTest {

    @Mock ProdutoRepository produtoRepo;
    @Mock FornecedorRepository fornecedorRepo;
    @Mock ItemPendenteBalancaService itemPendenteBalancaService;
    @InjectMocks ProdutoService service;

    private Produto novoProduto(Long fornecedorId, String fornecedorTexto) {
        return Produto.builder()
                .nome("Linguiça").unidadeMedida("UN").tipoProduto("INDUSTRIALIZADO")
                .precoVenda(new BigDecimal("12.50"))
                .codigoInterno("P000001")
                .fornecedorId(fornecedorId).fornecedor(fornecedorTexto)
                .build();
    }

    private Fornecedor fornecedor(Long id, String nome, boolean ativo) {
        return Fornecedor.builder().id(id).nome(nome).ativo(ativo).build();
    }

    @Test
    @DisplayName("salvar: o nome do fornecedor vem do cadastro, não do texto enviado")
    void nomeVemDoCadastro() {
        when(fornecedorRepo.findById(3L)).thenReturn(Optional.of(fornecedor(3L, "Sadia Distribuidora", true)));
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        Produto salvo = service.salvar(novoProduto(3L, "texto forjado"), 1L);

        assertThat(salvo.getFornecedorId()).isEqualTo(3L);
        assertThat(salvo.getFornecedor()).isEqualTo("Sadia Distribuidora");
    }

    @Test
    @DisplayName("salvar: sem fornecedorId, o texto livre é descartado")
    void semFornecedor() {
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        Produto salvo = service.salvar(novoProduto(null, "digitado à mão"), 1L);

        assertThat(salvo.getFornecedorId()).isNull();
        assertThat(salvo.getFornecedor()).isNull();
    }

    @Test
    @DisplayName("salvar: fornecedor inativo não pode ser vinculado a produto novo")
    void fornecedorInativoRecusado() {
        when(fornecedorRepo.findById(3L)).thenReturn(Optional.of(fornecedor(3L, "Antigo", false)));

        assertThatThrownBy(() -> service.salvar(novoProduto(3L, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("salvar: fornecedor inexistente é recusado")
    void fornecedorInexistente() {
        when(fornecedorRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salvar(novoProduto(99L, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("atualizar: produto que já era de um fornecedor depois inativado continua editável")
    void edicaoMantemFornecedorInativoJaVinculado() {
        Produto existente = novoProduto(3L, "Antigo");
        existente.setId(10L);
        when(produtoRepo.findById(10L)).thenReturn(Optional.of(existente));
        when(fornecedorRepo.findById(3L)).thenReturn(Optional.of(fornecedor(3L, "Antigo", false)));
        when(produtoRepo.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        Produto dados = novoProduto(3L, null);
        dados.setAtivo(true);
        Produto salvo = service.atualizar(10L, dados);

        assertThat(salvo.getFornecedorId()).isEqualTo(3L);
        assertThat(salvo.getFornecedor()).isEqualTo("Antigo");
    }

    @Test
    @DisplayName("atualizar: trocar para um fornecedor inativo diferente é recusado")
    void trocaParaInativoRecusada() {
        Produto existente = novoProduto(3L, "Atual");
        existente.setId(10L);
        when(produtoRepo.findById(10L)).thenReturn(Optional.of(existente));
        when(fornecedorRepo.findById(4L)).thenReturn(Optional.of(fornecedor(4L, "Outro", false)));

        Produto dados = novoProduto(4L, null);
        dados.setAtivo(true);

        assertThatThrownBy(() -> service.atualizar(10L, dados))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
    }
}