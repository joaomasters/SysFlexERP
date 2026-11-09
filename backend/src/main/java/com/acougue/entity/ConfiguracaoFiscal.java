package com.acougue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Emissão de NFC-e / NF-e da empresa (uma linha por empresa, id = 1). Desligada por padrão:
 * o PDV continua no cupom não fiscal até a empresa completar a configuração (ver V36).
 * Os campos *_cripto guardam segredos cifrados (CredencialCripto) e nunca saem na API.
 */
@Entity
@Table(name = "configuracao_fiscal")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfiguracaoFiscal {

    public static final short ID_UNICO = 1;

    @Id
    private Short id;

    @Builder.Default
    @Column(name = "emitir_nfce", nullable = false)
    private Boolean emitirNfce = false;

    @Builder.Default
    @Column(name = "emitir_nfe", nullable = false)
    private Boolean emitirNfe = false;

    @Builder.Default
    @Column(nullable = false, length = 12)
    private String ambiente = "HOMOLOGACAO";

    @Builder.Default
    @Column(nullable = false, length = 2)
    private String uf = "PR";

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String provedor = "NENHUM";

    @Column(name = "razao_social", length = 150)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 120)
    private String nomeFantasia;

    @Column(length = 14)
    private String cnpj;

    @Column(name = "inscricao_estadual", length = 14)
    private String inscricaoEstadual;

    @Builder.Default
    @Column(name = "regime_tributario", nullable = false)
    private Short regimeTributario = 1;

    @Column(length = 120)
    private String logradouro;

    @Column(name = "numero_endereco", length = 10)
    private String numeroEndereco;

    @Column(length = 60)
    private String complemento;

    @Column(length = 60)
    private String bairro;

    @Column(name = "codigo_municipio", length = 7)
    private String codigoMunicipio;

    @Column(length = 60)
    private String municipio;

    @Column(length = 8)
    private String cep;

    @Column(length = 14)
    private String telefone;

    @Builder.Default
    @Column(name = "nfce_serie", nullable = false)
    private Short nfceSerie = 1;

    @Builder.Default
    @Column(name = "nfce_proximo_numero", nullable = false)
    private Integer nfceProximoNumero = 1;

    @Builder.Default
    @Column(name = "nfe_serie", nullable = false)
    private Short nfeSerie = 1;

    @Builder.Default
    @Column(name = "nfe_proximo_numero", nullable = false)
    private Integer nfeProximoNumero = 1;

    /** SOB_DEMANDA: NFC-e só quando o cliente pede ("CPF na nota?"); TODAS: em toda venda. */
    @Builder.Default
    @Column(name = "nfce_modo", nullable = false, length = 15)
    private String nfceModo = "SOB_DEMANDA";

    @Column(name = "csc_id", length = 6)
    private String cscId;

    @Column(name = "csc_token_cripto", columnDefinition = "TEXT")
    private String cscTokenCripto;

    @Column(name = "certificado_cripto", columnDefinition = "TEXT")
    private String certificadoCripto;

    @Column(name = "certificado_senha_cripto", columnDefinition = "TEXT")
    private String certificadoSenhaCripto;

    @Column(name = "certificado_titular", length = 200)
    private String certificadoTitular;

    @Column(name = "certificado_cnpj", length = 14)
    private String certificadoCnpj;

    @Column(name = "certificado_validade")
    private LocalDateTime certificadoValidade;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    @PreUpdate
    void carimbar() {
        updatedAt = LocalDateTime.now();
    }
}
