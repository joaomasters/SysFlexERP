package com.acougue.modules.estoque;

import com.acougue.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RegraValidade (máx. 3 anos a partir da entrada)")
class RegraValidadeTest {

    private static final LocalDate ENTRADA = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("o limite é a data de entrada + 3 anos")
    void limite() {
        assertThat(RegraValidade.limite(ENTRADA)).isEqualTo(LocalDate.of(2029, 10, 20));
    }

    @Test
    @DisplayName("entrada em 29/02 tem limite em 28/02 quando o ano de destino não é bissexto")
    void limiteDeEntradaEmAnoBissexto() {
        assertThat(RegraValidade.limite(LocalDate.of(2028, 2, 29))).isEqualTo(LocalDate.of(2031, 2, 28));
    }

    @Test
    @DisplayName("aceita validade no próprio limite de 3 anos")
    void aceitaNoLimite() {
        assertThatCode(() -> RegraValidade.validarLimite(LocalDate.of(2029, 10, 20), ENTRADA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("aceita validade curta e validade já vencida (correção/entrada de item vencendo)")
    void aceitaDatasDentroDoLimite() {
        assertThatCode(() -> RegraValidade.validarLimite(LocalDate.of(2026, 10, 21), ENTRADA)).doesNotThrowAnyException();
        assertThatCode(() -> RegraValidade.validarLimite(LocalDate.of(2026, 10, 1), ENTRADA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("barra validade um dia acima do limite")
    void barraUmDiaAcima() {
        assertThatThrownBy(() -> RegraValidade.validarLimite(LocalDate.of(2029, 10, 21), ENTRADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("21/10/2029")
                .hasMessageContaining("3 anos")
                .hasMessageContaining("20/10/2029");
    }

    @Test
    @DisplayName("barra datas absurdas como 12/12/2500")
    void barraDataAbsurda() {
        assertThatThrownBy(() -> RegraValidade.validarLimite(LocalDate.of(2500, 12, 12), ENTRADA))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validade nula é permitida (quem exige a data é o chamador)")
    void nulaPermitida() {
        assertThatCode(() -> RegraValidade.validarLimite(null, ENTRADA)).doesNotThrowAnyException();
    }
}