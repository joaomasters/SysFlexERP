package com.acougue.modules.financeiro;

import com.acougue.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Documento do cliente")
class ClienteDocumentoTest {

    @Test
    @DisplayName("aceita CPF, CNPJ numérico e alfanumérico; grava como veio, em maiúsculas")
    void aceitaValidos() {
        assertThat(ClienteService.documentoConferido("529.982.247-25", "PF")).isEqualTo("529.982.247-25");
        assertThat(ClienteService.documentoConferido("11222333000181", "PJ")).isEqualTo("11222333000181");
        assertThat(ClienteService.documentoConferido("12.abc.345/01de-35", "PJ")).isEqualTo("12.ABC.345/01DE-35");
        assertThat(ClienteService.documentoConferido("  ", "PF")).isNull();
        assertThat(ClienteService.documentoConferido(null, "PJ")).isNull();
    }

    @Test
    @DisplayName("recusa dígito errado e documento do tipo trocado")
    void recusaInvalidos() {
        assertThatThrownBy(() -> ClienteService.documentoConferido("529.982.247-24", "PF"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CPF inválido");
        assertThatThrownBy(() -> ClienteService.documentoConferido("12.ABC.345/01DE-36", "PJ"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CNPJ inválido");
        assertThatThrownBy(() -> ClienteService.documentoConferido("11222333000181", "PF"))
                .isInstanceOf(BusinessException.class);
    }
}
