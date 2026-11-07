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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FornecedorServiceDocumentoTest {

    private static final String CNPJ = "11.222.333/0001-81";

    @Mock FornecedorRepository fornecedorRepo;
    @Mock ProdutoRepository produtoRepo;
    @InjectMocks FornecedorService service;

    private Fornecedor f(Long id, String nome, String tipo, String doc) {
        return Fornecedor.builder().id(id).nome(nome).tipoPessoa(tipo).cpfCnpj(doc).ativo(true).build();
    }

    @Test
    @DisplayName("criar: CNPJ inválido é recusado")
    void cnpjInvalido() {
        assertThatThrownBy(() -> service.criar(f(null, "Frigo", "PJ", "11.222.333/0001-82"), 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CNPJ inválido");
        verify(fornecedorRepo, never()).save(any());
    }

    @Test
    @DisplayName("criar: CPF inválido é recusado para pessoa física")
    void cpfInvalido() {
        assertThatThrownBy(() -> service.criar(f(null, "João", "PF", "111.111.111-11"), 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CPF inválido");
    }

    @Test
    @DisplayName("criar: documento já usado por outro fornecedor é recusado, citando o nome")
    void documentoDuplicado() {
        when(fornecedorRepo.buscarPorNomeExato(anyString())).thenReturn(Optional.empty());
        when(fornecedorRepo.buscarPorCpfCnpjDigitos("11222333000181"))
                .thenReturn(List.of(f(5L, "Frigorífico Sul", "PJ", CNPJ)));

        assertThatThrownBy(() -> service.criar(f(null, "Outro Nome", "PJ", "11222333000181"), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está cadastrado")
                .hasMessageContaining("Frigorífico Sul");
        verify(fornecedorRepo, never()).save(any());
    }

    @Test
    @DisplayName("criar: documento é gravado com máscara e sem documento continua permitido")
    void criarGravaFormatadoOuVazio() {
        when(fornecedorRepo.buscarPorNomeExato(anyString())).thenReturn(Optional.empty());
        when(fornecedorRepo.buscarPorCpfCnpjDigitos("11222333000181")).thenReturn(List.of());
        when(fornecedorRepo.save(any(Fornecedor.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.criar(f(null, "Com doc", "PJ", "11222333000181"), 1L).getCpfCnpj()).isEqualTo(CNPJ);
        assertThat(service.criar(f(null, "Sem doc", "PJ", "  "), 1L).getCpfCnpj()).isNull();
    }

    @Test
    @DisplayName("atualizar: sem mudar o documento não reconfere duplicidade (legado repetido segue editável)")
    void atualizarSemMudarDocumento() {
        Fornecedor existente = f(3L, "Antigo", "PJ", CNPJ);
        when(fornecedorRepo.findById(3L)).thenReturn(Optional.of(existente));
        when(fornecedorRepo.buscarPorNomeExato(anyString())).thenReturn(Optional.empty());
        when(fornecedorRepo.save(any(Fornecedor.class))).thenAnswer(i -> i.getArgument(0));

        Fornecedor salvo = service.atualizar(3L, f(null, "Antigo", "PJ", CNPJ));

        assertThat(salvo.getCpfCnpj()).isEqualTo(CNPJ);
        verify(fornecedorRepo, never()).buscarPorCpfCnpjDigitos(anyString());
    }

    @Test
    @DisplayName("atualizar: trocar para documento de outro fornecedor é recusado")
    void atualizarParaDocumentoDeOutro() {
        when(fornecedorRepo.findById(3L)).thenReturn(Optional.of(f(3L, "Antigo", "PJ", null)));
        when(fornecedorRepo.buscarPorNomeExato(anyString())).thenReturn(Optional.empty());
        when(fornecedorRepo.buscarPorCpfCnpjDigitos("11222333000181"))
                .thenReturn(List.of(f(5L, "Frigorífico Sul", "PJ", CNPJ)));

        assertThatThrownBy(() -> service.atualizar(3L, f(null, "Antigo", "PJ", CNPJ)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Frigorífico Sul");
    }
}