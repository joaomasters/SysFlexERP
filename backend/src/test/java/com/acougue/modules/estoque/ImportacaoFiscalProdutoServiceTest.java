package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.ImportacaoFiscalProdutoService.LinhaPlanilha;
import com.acougue.modules.estoque.ImportacaoFiscalProdutoService.Resultado;
import com.acougue.repository.ProdutoRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.acougue.modules.estoque.ImportacaoFiscalProdutoService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Planilha fiscal de produtos (SUPER_ADMIN)")
class ImportacaoFiscalProdutoServiceTest {

    @Mock ProdutoRepository produtoRepo;
    @InjectMocks ImportacaoFiscalProdutoService service;

    private Produto biju;
    private Produto picanha;

    private static void logar(boolean superAdmin) {
        UsuarioAutenticado u = new UsuarioAutenticado(1L, "admin", "Admin", "ADMIN", superAdmin, Map.of(), "tenant_005");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    @BeforeEach
    void preparar() {
        logar(true);
        biju = Produto.builder().id(1L).codigoInterno("P000668").nome("biju").unidadeMedida("UN").tipoProduto("MERCEARIA")
                .precoVenda(new BigDecimal("17")).origem("0").build();
        picanha = Produto.builder().id(2L).codigoInterno("P000001").nome("PICANHA").unidadeMedida("KG").tipoProduto("CORTE")
                .precoVenda(new BigDecimal("89.9")).ncm("02013000").cfop("5102").origem("0").csosn("102").build();
        when(produtoRepo.findByCodigoInterno(anyString())).thenReturn(Optional.empty());
        when(produtoRepo.findByCodigoInterno("P000668")).thenReturn(Optional.of(biju));
        when(produtoRepo.findByCodigoInterno("P000001")).thenReturn(Optional.of(picanha));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static LinhaPlanilha linha(int n, String codigo, String desc, String ncm, String cest, String cfop, String origem, String csosn) {
        return new LinhaPlanilha(n, codigo, desc, ncm, cest, cfop, origem, csosn);
    }

    @Test
    @DisplayName("prévia: diz o que muda e não grava nada")
    void previa() {
        Resultado r = service.importar(List.of(
                linha(2, "P000668", "biju de tapioca", "1903.00.00", "", "5102", "0", "102"),
                linha(3, "P000001", "", "02013000", "", "", "", "")), false);

        assertThat(r.aplicado()).isFalse();
        assertThat(r.alterados()).isEqualTo(1);
        assertThat(r.semAlteracao()).isEqualTo(1);
        assertThat(r.linhas().get(0).status()).isEqualTo(ALTERAR);
        assertThat(r.linhas().get(0).mensagem()).contains("NCM → 19030000").contains("Descrição → BIJU DE TAPIOCA");
        assertThat(biju.getNcm()).isNull();
        assertThat(biju.getNome()).isEqualTo("biju");
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("aplicar: grava só as linhas válidas; célula em branco mantém o valor atual")
    void aplicar() {
        Resultado r = service.importar(List.of(
                linha(2, "P000668", "", "1903.00.00", "", "5102", "", "102"),
                linha(3, "P000001", "", "", "", "", "", "500"),
                linha(4, "X999", "", "02013000", "", "", "", ""),
                linha(5, "P000668", "", "02013000", "", "", "", "")), true);

        assertThat(r.aplicado()).isTrue();
        assertThat(r.alterados()).isEqualTo(2);
        assertThat(r.erros()).isEqualTo(2);
        assertThat(biju.getNcm()).isEqualTo("19030000");
        assertThat(biju.getCsosn()).isEqualTo("102");
        assertThat(biju.getNome()).isEqualTo("biju");          // descrição em branco: mantida
        assertThat(picanha.getCsosn()).isEqualTo("500");
        assertThat(picanha.getNcm()).isEqualTo("02013000");    // NCM em branco: mantido
        assertThat(r.linhas().get(2).mensagem()).isEqualTo("Produto não encontrado.");
        assertThat(r.linhas().get(3).mensagem()).isEqualTo("Código repetido na planilha.");
        verify(produtoRepo, times(2)).save(any());
    }

    @Test
    @DisplayName("Excel tira o zero da esquerda: NCM de 7 e CEST de 6 dígitos voltam com o zero")
    void zeroDaEsquerda() {
        service.importar(List.of(linha(2, "P000668", null, "2013000", "100100", null, null, null)), true);
        assertThat(biju.getNcm()).isEqualTo("02013000");
        assertThat(biju.getCest()).isEqualTo("0100100");
    }

    @Test
    @DisplayName("valores inválidos viram erro da linha, sem gravar; avisa o que ainda falta para sair em nota")
    void invalidos() {
        Resultado r = service.importar(List.of(
                linha(2, "P000668", null, "123", null, null, null, null),
                linha(3, "P000001", null, null, null, "9102", null, null)), true);
        assertThat(r.erros()).isEqualTo(2);
        assertThat(r.linhas().get(0).mensagem()).contains("NCM");
        assertThat(r.linhas().get(1).mensagem()).contains("CFOP");
        verify(produtoRepo, never()).save(any());

        Resultado incompleto = service.importar(List.of(linha(2, "P000668", null, "02013000", null, null, null, null)), false);
        assertThat(incompleto.aindaIncompletos()).isEqualTo(1);
        assertThat(incompleto.linhas().get(0).mensagem()).contains("Ainda falta: CFOP, CSOSN");
    }

    @Test
    @DisplayName("só o SUPER_ADMIN pode importar; planilha vazia é recusada")
    void permissao() {
        assertThatThrownBy(() -> service.importar(List.of(), false)).isInstanceOf(BusinessException.class);
        logar(false);
        assertThatThrownBy(() -> service.importar(List.of(linha(2, "P000668", null, "02013000", null, null, null, null)), true))
                .isInstanceOf(AccessDeniedException.class);
        verify(produtoRepo, never()).save(any());
    }
}
