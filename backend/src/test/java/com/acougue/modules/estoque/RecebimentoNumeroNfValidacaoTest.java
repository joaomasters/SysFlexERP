package com.acougue.modules.estoque;

import com.acougue.modules.estoque.dto.RecebimentoDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Recebimento — Número da NF aceita só números")
class RecebimentoNumeroNfValidacaoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private int violacoes(String numeroNf) {
        var itens = List.of(new RecebimentoDTO.ItemDTO(1L, null, null, null));
        var dto = new RecebimentoDTO("Fornecedor", numeroNf, "1", null, null, null, null, null, itens);
        return validator.validate(dto).size();
    }

    @Test
    @DisplayName("números (com zeros à esquerda) e ausência de número passam")
    void aceita() {
        assertThat(violacoes("123")).isZero();
        assertThat(violacoes("000001")).isZero();
        assertThat(violacoes("123456789")).isZero();
        assertThat(violacoes(null)).isZero();
    }

    @Test
    @DisplayName("letras, símbolos, espaços, vazio e mais de 9 dígitos são recusados")
    void recusa() {
        assertThat(violacoes("12A4")).isEqualTo(1);
        assertThat(violacoes("NF-123")).isEqualTo(1);
        assertThat(violacoes("12 3")).isEqualTo(1);
        assertThat(violacoes("")).isEqualTo(1);
        assertThat(violacoes("1234567890")).isEqualTo(1);
    }
}