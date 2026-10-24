package com.acougue.modules.comprovante.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ConfiguracaoComprovanteDTO {

    @NotBlank(message = "Informe o nome que sai no cupom")
    @Size(max = 120)
    private String nomeFantasia;

    @Size(max = 20)
    private String cnpj;

    @Size(max = 150)
    private String endereco;

    @Size(max = 80)
    private String bairro;

    @Size(max = 80)
    private String cidade;

    @Size(max = 30)
    private String telefone;

    @Size(max = 300)
    private String mensagemRodape;

    @NotNull
    private Short larguraPapelMm;

    @NotNull @Min(1) @Max(3)
    private Short vias;

    private boolean imprimirAutomatico;
    private boolean mostrarAvisoNaoFiscal = true;
    private boolean mostrarOperador = true;
    private boolean mostrarCliente = true;
}
