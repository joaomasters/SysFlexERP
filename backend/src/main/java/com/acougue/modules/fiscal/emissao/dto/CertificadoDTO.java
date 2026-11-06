package com.acougue.modules.fiscal.emissao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CertificadoDTO {

    /** Conteúdo do arquivo .pfx em Base64 (um A1 tem poucos KB). */
    @NotBlank(message = "Escolha o arquivo do certificado.")
    @Size(max = 100_000, message = "Arquivo grande demais para um certificado A1.")
    private String arquivoBase64;

    @NotBlank(message = "Informe a senha do certificado.")
    @Size(max = 200)
    private String senha;
}
