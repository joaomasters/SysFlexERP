package com.acougue.modules.pagamento.dto;

import com.acougue.entity.ProvedorPagamento;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Configuração de uma adquirente como a tela enxerga: segredos só como "configurado sim/não". */
@Data
@Builder
public class ProvedorConfigDTO {
    private ProvedorPagamento provedor;
    private String rotulo;
    private boolean ativo;
    private String ambiente;
    private String codigoEstabelecimento;
    private String observacoes;
    private boolean credencialConfigurada;
    private boolean webhookSecretConfigurado;
    /** false = só AGENTE_LOCAL/MANUAL (ex: Sicredi) */
    private boolean suportaApiNuvem;
    /** false = servidor sem PAGAMENTO_CRIPTO_CHAVE; a tela avisa e bloqueia salvar segredos */
    private boolean criptoConfigurada;
    private LocalDateTime updatedAt;
}
