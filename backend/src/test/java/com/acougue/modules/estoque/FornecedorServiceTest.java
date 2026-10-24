package com.acougue.modules.estoque;

import com.acougue.entity.Fornecedor;
import com.acougue.exception.BusinessException;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FornecedorServiceTest {

    @Mock FornecedorRepository fornecedorRepo;
    @Mock ProdutoRepository produtoRepo;
    @InjectMocks FornecedorService service;

    private Fornecedor fornecedor(Long id, String nome) {
        return Fornecedor.builder().id(id).nome(nome).tipoPessoa("PJ").ativo(true).build();
    }

    @Test
    @DisplayName("criar: grava com o usuário do token, ativo e sem aceitar id do corpo")
    void criarGravaAutoria() {
        when(fornecedorRepo.buscarPorNomeExato("Frigorífico Sul")).thenReturn(Optional.empty());
        when(fornecedorRepo.save(any(Fornecedor.class))).thenAnswer(i -> i.getArgument(0));

        Fornecedor dados = fornecedor(99L, "  Frigorífico Sul  ");
        dados.setAtivo(false);
        Fornecedor salvo = service.criar(dados, 7L);

        assertThat(salvo.getId()).isNull();
        assertThat(salvo.getNome()).isEqualTo("Frigorífico Sul");
        assertThat(salvo.getAtivo()).isTrue();
        assertThat(salvo.getCriadoPorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("criar: nome já cadastrado (ignorando caixa) é recusado")
    void criarNomeDuplicado() {
        when(fornecedorRepo.buscarPorNomeExato("frigorífico sul")).thenReturn(Optional.of(fornecedor(1L, "Frigorífico Sul")));

        assertThatThrownBy(() -> service.criar(fornecedor(null, "frigorífico sul"), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um fornecedor");
        verify(fornecedorRepo, never()).save(any());
    }

    @Test
    @DisplayName("criar: nome em branco é recusado")
    void criarSemNome() {
        assertThatThrownBy(() -> service.criar(fornecedor(null, "   "), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nome");
    }

    @Test
    @DisplayName("atualizar: renomear propaga o novo nome para os produtos vinculados")
    void atualizarRenomeiaProdutos() {
        Fornecedor existente = fornecedor(5L, "ABC Distribuidora");
        when(fornecedorRepo.findById(5L)).thenReturn(Optional.of(existente));
        when(fornecedorRepo.buscarPorNomeExato("ABC Alimentos")).thenReturn(Optional.empty());
        when(fornecedorRepo.save(any(Fornecedor.class))).thenAnswer(i -> i.getArgument(0));

        service.atualizar(5L, fornecedor(null, "ABC Alimentos"));

        verify(produtoRepo).atualizarNomeFornecedor(5L, "ABC Alimentos");
    }

    @Test
    @DisplayName("atualizar: manter o nome não mexe nos produtos")
    void atualizarSemRenomearNaoMexeNosProdutos() {
        Fornecedor existente = fornecedor(5L, "ABC Distribuidora");
        when(fornecedorRepo.findById(5L)).thenReturn(Optional.of(existente));
        when(fornecedorRepo.buscarPorNomeExato("ABC Distribuidora")).thenReturn(Optional.of(existente));
        when(fornecedorRepo.save(any(Fornecedor.class))).thenAnswer(i -> i.getArgument(0));

        Fornecedor dados = fornecedor(null, "ABC Distribuidora");
        dados.setTelefone("(43) 3333-4444");
        Fornecedor salvo = service.atualizar(5L, dados);

        assertThat(salvo.getTelefone()).isEqualTo("(43) 3333-4444");
        verify(produtoRepo, never()).atualizarNomeFornecedor(anyLong(), anyString());
    }

    @Test
    @DisplayName("atualizar: não pode ficar com o nome de OUTRO fornecedor")
    void atualizarParaNomeDeOutro() {
        when(fornecedorRepo.findById(5L)).thenReturn(Optional.of(fornecedor(5L, "ABC")));
        when(fornecedorRepo.buscarPorNomeExato("XYZ")).thenReturn(Optional.of(fornecedor(8L, "XYZ")));

        assertThatThrownBy(() -> service.atualizar(5L, fornecedor(null, "XYZ")))
                .isInstanceOf(BusinessException.class);
        verify(produtoRepo, never()).atualizarNomeFornecedor(anyLong(), anyString());
    }

    @Test
    @DisplayName("inativar e reativar alternam o flag ativo")
    void inativarReativar() {
        Fornecedor f = fornecedor(5L, "ABC");
        when(fornecedorRepo.findById(5L)).thenReturn(Optional.of(f));

        service.inativar(5L);
        assertThat(f.getAtivo()).isFalse();

        service.reativar(5L);
        assertThat(f.getAtivo()).isTrue();
    }
}