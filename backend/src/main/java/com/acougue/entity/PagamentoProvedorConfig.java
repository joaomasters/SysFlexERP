package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Credenciais de uma adquirente para a empresa (tenant) atual.
 * Os campos *Cripto guardam o segredo cifrado (ver CredencialCripto) e
 * nunca saem na API — a tela recebe só se "está configurado" ou não.
 */
@Entity
@Table(name = "pagamento_provedor_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagamentoProvedorConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProvedorPagamento provedor;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = false;

    /** SANDBOX | PRODUCAO */
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String ambiente = "SANDBOX";

    @JsonIgnore
    @Column(name = "access_token_cripto", columnDefinition = "TEXT")
    private String accessTokenCripto;

    @JsonIgnore
    @Column(name = "webhook_secret_cripto", columnDefinition = "TEXT")
    private String webhookSecretCripto;

    @Column(name = "codigo_estabelecimento", length = 100)
    private String codigoEstabelecimento;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist @PreUpdate
    void tocar() {
        updatedAt = LocalDateTime.now();
    }
}
