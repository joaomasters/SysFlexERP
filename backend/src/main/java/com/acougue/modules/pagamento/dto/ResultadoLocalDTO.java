package com.acougue.modules.pagamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Resultado informado pelo PDV para terminais AGENTE_LOCAL (resposta do agente
 * TEF) ou MANUAL (o operador digita o comprovante).
 */
@Data
public class ResultadoLocalDTO {

    @NotNull
    private Boolean aprovada;

    @Size(max = 50)
    private String nsu;

    @Size(max = 50)
    private String autorizacao;

    @Size(max = 30)
    private String bandeira;

    @Size(max = 500)
    private String mensagem;

    /** JSON cru devolvido pelo agente, guardado para suporte. */
    @Size(max = 20000)
    private String respostaBruta;
}
