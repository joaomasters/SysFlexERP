package com.acougue.modules.pagamento.dto;

import com.acougue.entity.ModoIntegracaoTerminal;
import com.acougue.entity.ProvedorPagamento;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SalvarTerminalDTO {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 80)
    private String nome;

    @NotNull(message = "Adquirente é obrigatória")
    private ProvedorPagamento provedor;

    @NotNull(message = "Modo de integração é obrigatório")
    private ModoIntegracaoTerminal modoIntegracao;

    @Size(max = 100)
    private String identificadorDispositivo;

    @Size(max = 200)
    private String agenteUrl;

    private boolean aceitaDebito = true;
    private boolean aceitaCredito = true;

    @Min(1) @Max(18)
    private int maxParcelas = 12;

    private boolean ativo = true;
}
