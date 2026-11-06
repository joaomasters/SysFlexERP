package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Uma NFC-e (modelo 65) ou NF-e (modelo 55): emitida, tentada, cancelada ou inutilizada (ver V36). */
@Entity
@Table(name = "documento_fiscal")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentoFiscal {

    public static final short MODELO_NFE = 55;
    public static final short MODELO_NFCE = 65;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Short modelo;

    @Column(nullable = false)
    private Short serie;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 12)
    private String ambiente;

    @Builder.Default
    @Column(nullable = false, length = 15)
    private String status = "PENDENTE";

    @Column(length = 44)
    private String chave;

    @Column(length = 20)
    private String protocolo;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Builder.Default
    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "venda_id")
    private Long vendaId;

    @Column(name = "nota_fiscal_saida_id")
    private Long notaFiscalSaidaId;

    @Column(name = "destinatario_documento", length = 14)
    private String destinatarioDocumento;

    @Column(name = "destinatario_nome", length = 150)
    private String destinatarioNome;

    @JsonIgnore // grande; baixado à parte quando for preciso
    @Column(columnDefinition = "TEXT")
    private String xml;

    @Column(length = 30)
    private String provedor;

    @Column(name = "id_externo", length = 100)
    private String idExterno;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "emitida_em")
    private LocalDateTime emitidaEm;

    @Column(name = "cancelada_em")
    private LocalDateTime canceladaEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void criar() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void atualizar() {
        updatedAt = LocalDateTime.now();
    }
}
