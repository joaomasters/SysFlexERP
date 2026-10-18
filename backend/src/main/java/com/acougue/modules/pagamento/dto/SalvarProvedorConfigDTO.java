package com.acougue.modules.pagamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Segredos: null ou vazio = manter o que já está salvo (a tela nunca recebe o
 * valor atual, então não tem como reenviá-lo). Para apagar, use as flags limpar*.
 */
@Data
public class SalvarProvedorConfigDTO {

    @NotNull
    private Boolean ativo;

    @NotNull
    @Pattern(regexp = "SANDBOX|PRODUCAO", message = "Ambiente deve ser SANDBOX ou PRODUCAO")
    private String ambiente;

    @Size(max = 100)
    private String codigoEstabelecimento;

    @Size(max = 2000)
    private String observacoes;

    @Size(max = 500)
    private String accessToken;

    @Size(max = 500)
    private String webhookSecret;

    private boolean limparAccessToken;
    private boolean limparWebhookSecret;
}
