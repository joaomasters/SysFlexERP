package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.ItemPendenteBalancaService;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Estoque máximo e campos fiscais da nova tela de cadastro de produto. */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceCamposFiscaisTest {

    @Mock ProdutoRepository produtoRepo;
    @Mock FornecedorRepository fornecedorRepo;
    @Mock ItemPendenteBalancaService itemPendenteBalancaService;
    @InjectMocks ProdutoService service;

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    private Produto produto() {
        return Produto.builder()
                .nome("Picanha").unidadeMedida("KG").tipoProduto("CORTE")
                .precoVenda(new BigDecimal("89.90")).estoqueMinimo(new BigDecimal("5"))
                .ativo(true)
                .build();
    }

    private static Set<String> mensagens(Produto p) {
        return VALIDADOR.validate(p).stream().map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("salvar: grava estoque máximo e campos fiscais, com espaços e vazios limpos")
    void salvarGravaFiscal() {
        when(produtoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Produto p = produto();
        p.setEstoqueMaximo(new BigDecimal("50"));
        p.setNcm("02013000");
        p.setCest(" ");
        p.setCfop("5102");
        p.setOrigem("2");
        p.setCsosn("102");

        Produto salvo = service.salvar(p, 1L);

        assertThat(salvo.getEstoqueMaximo()).isEqualByComparingTo("50");
        assertThat(salvo.getNcm()).isEqualTo("02013000");
        assertThat(salvo.getCest()).isNull();
        assertThat(salvo.getCfop()).isEqualTo("5102");
        assertThat(salvo.getOrigem()).isEqualTo("2");
        assertThat(salvo.getCsosn()).isEqualTo("102");
    }

    @Test
    @DisplayName("salvar: origem em branco vira 0 (nacional)")
    void origemPadrao() {
        when(produtoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Produto p = produto();
        p.setOrigem(null);

        assertThat(service.salvar(p, 1L).getOrigem()).isEqualTo("0");
        assertThat(produto().getOrigem()).isEqualTo("0");
    }

    @Test
    @DisplayName("salvar e atualizar: estoque máximo menor que o mínimo é recusado")
    void estoqueMaximoMenorQueMinimo() {
        Produto p = produto();
        p.setEstoqueMaximo(new BigDecimal("2"));

        assertThatThrownBy(() -> service.salvar(p, 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("estoque máximo");

        Produto existente = produto();
        existente.setId(10L);
        when(produtoRepo.findById(10L)).thenReturn(Optional.of(existente));
        assertThatThrownBy(() -> service.atualizar(10L, p))
                .isInstanceOf(BusinessException.class).hasMessageContaining("estoque máximo");
        verify(produtoRepo, never()).save(any());
    }

    @Test
    @DisplayName("salvar: código interno informado é usado, e repetido é recusado dizendo em qual produto")
    void codigoInternoManual() {
        when(produtoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Produto p = produto();
        p.setCodigoInterno(" PIC-01 ");
        assertThat(service.salvar(p, 1L).getCodigoInterno()).isEqualTo("PIC-01");

        Produto outro = produto();
        outro.setNome("Alcatra");
        when(produtoRepo.findByCodigoInterno("ALC-01")).thenReturn(Optional.of(outro));
        Produto repetido = produto();
        repetido.setCodigoInterno("ALC-01");
        assertThatThrownBy(() -> service.salvar(repetido, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ALC-01").hasMessageContaining("Alcatra");
    }

    @Test
    @DisplayName("salvar: sem código interno, gera o automático pela sequência, pulando o que foi digitado à mão")
    void codigoInternoAutomatico() {
        when(produtoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(produtoRepo.proximoNumeroCodigoInterno()).thenReturn(42L, 43L);
        when(produtoRepo.findByCodigoInterno("P000042")).thenReturn(Optional.of(produto())); // digitado à mão antes
        when(produtoRepo.findByCodigoInterno("P000043")).thenReturn(Optional.empty());

        assertThat(service.salvar(produto(), 1L).getCodigoInterno()).isEqualTo("P000043");
    }

    @Test
    @DisplayName("atualizar: grava os campos fiscais e mantém o que não veio como estava antes")
    void atualizarFiscal() {
        Produto existente = produto();
        existente.setId(10L);
        existente.setNcm("02013000");
        existente.setCsosn("102");
        when(produtoRepo.findById(10L)).thenReturn(Optional.of(existente));
        when(produtoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Produto dados = produto();
        dados.setNcm("02023000");
        dados.setCfop("5405");
        dados.setOrigem("1");
        dados.setCsosn("500");
        dados.setCest("1700100");
        dados.setEstoqueMaximo(new BigDecimal("80"));

        Produto salvo = service.atualizar(10L, dados);

        assertThat(salvo.getNcm()).isEqualTo("02023000");
        assertThat(salvo.getCfop()).isEqualTo("5405");
        assertThat(salvo.getOrigem()).isEqualTo("1");
        assertThat(salvo.getCsosn()).isEqualTo("500");
        assertThat(salvo.getCest()).isEqualTo("1700100");
        assertThat(salvo.getEstoqueMaximo()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("validação: formatos iguais aos do formulário e do banco")
    void formatos() {
        Produto p = produto();
        assertThat(mensagens(p)).isEmpty();

        p.setNcm("0201300");
        p.setCest("12345678");
        p.setCfop("8102");
        p.setOrigem("9");
        p.setCsosn("104");
        p.setEstoqueMaximo(new BigDecimal("-1"));

        assertThat(mensagens(p)).contains(
                "O NCM deve ter 8 dígitos.", "O CEST deve ter 7 dígitos.", "Informe um CFOP válido (4 dígitos).",
                "Origem da mercadoria inválida.", "CSOSN inválido.", "O estoque máximo não pode ser negativo.");
    }
}
