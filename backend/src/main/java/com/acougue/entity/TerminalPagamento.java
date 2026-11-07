package com.acougue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Uma maquininha física e a forma como o PDV conversa com ela. */
@Entity
@Table(name = "terminal_pagamento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TerminalPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvedorPagamento provedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_integracao", nullable = false, length = 20)
    private ModoIntegracaoTerminal modoIntegracao;

    @Column(name = "identificador_dispositivo", length = 100)
    private String identificadorDispositivo;

    @Column(name = "agente_url", length = 200)
    private String agenteUrl;

    @Builder.Default
    @Column(name = "aceita_debito", nullable = false)
    private Boolean aceitaDebito = true;

    @Builder.Default
    @Column(name = "aceita_credito", nullable = false)
    private Boolean aceitaCredito = true;

    /** PIX com QR na tela da maquininha — só Veloce Smart TEF em API_NUVEM. */
    @Builder.Default
    @Column(name = "aceita_pix", nullable = false)
    private Boolean aceitaPix = false;

    /** Voucher (vale-alimentação/refeição) — Veloce Smart TEF em API_NUVEM, ou modo MANUAL. */
    @Builder.Default
    @Column(name = "aceita_voucher", nullable = false)
    private Boolean aceitaVoucher = false;

    @Builder.Default
    @Column(name = "max_parcelas", nullable = false)
    private Integer maxParcelas = 12;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
