package com.acougue.modules.fiscal.emissao.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** "CPF na nota?": CPF ou CNPJ do consumidor, com ou sem pontuação. Vazio = não identificado. */
@Data
public class EmitirNfceDTO {

    @Size(max = 20)
    private String documento;
}
