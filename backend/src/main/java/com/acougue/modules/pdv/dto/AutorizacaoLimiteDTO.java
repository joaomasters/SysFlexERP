package com.acougue.modules.pdv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Credencial de administrador que autoriza o fiado acima do limite de crédito. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutorizacaoLimiteDTO {

    @NotBlank
    @Size(max = 50)
    private String login;

    @NotBlank
    @Size(max = 100)
    @ToString.Exclude   // a senha nunca pode aparecer em log/toString
    private String senha;
}