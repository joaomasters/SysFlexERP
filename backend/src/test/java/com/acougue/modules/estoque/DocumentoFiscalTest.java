package com.acougue.modules.estoque;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoFiscalTest {

    @Test
    @DisplayName("CPF: aceita número válido, com ou sem máscara")
    void cpfValido() {
        assertThat(DocumentoFiscal.cpfValido("529.982.247-25")).isTrue();
        assertThat(DocumentoFiscal.cpfValido("52998224725")).isTrue();
    }

    @Test
    @DisplayName("CPF: recusa dígito errado, tamanho errado e sequências repetidas")
    void cpfInvalido() {
        assertThat(DocumentoFiscal.cpfValido("529.982.247-24")).isFalse();
        assertThat(DocumentoFiscal.cpfValido("5299822472")).isFalse();
        assertThat(DocumentoFiscal.cpfValido("111.111.111-11")).isFalse();
        assertThat(DocumentoFiscal.cpfValido(null)).isFalse();
    }

    @Test
    @DisplayName("CNPJ: aceita número válido, com ou sem máscara")
    void cnpjValido() {
        assertThat(DocumentoFiscal.cnpjValido("11.222.333/0001-81")).isTrue();
        assertThat(DocumentoFiscal.cnpjValido("11222333000181")).isTrue();
    }

    @Test
    @DisplayName("CNPJ: recusa dígito errado, tamanho errado e sequências repetidas")
    void cnpjInvalido() {
        assertThat(DocumentoFiscal.cnpjValido("11.222.333/0001-82")).isFalse();
        assertThat(DocumentoFiscal.cnpjValido("1122233300018")).isFalse();
        assertThat(DocumentoFiscal.cnpjValido("00.000.000/0000-00")).isFalse();
    }

    @Test
    @DisplayName("formatar: aplica máscara de CPF e de CNPJ")
    void formatar() {
        assertThat(DocumentoFiscal.formatar("52998224725")).isEqualTo("529.982.247-25");
        assertThat(DocumentoFiscal.formatar("11222333000181")).isEqualTo("11.222.333/0001-81");
    }
}