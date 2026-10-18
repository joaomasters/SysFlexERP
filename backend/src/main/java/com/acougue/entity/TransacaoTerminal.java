package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uma cobrança enviada a uma maquininha. Provedor e modo são gravados como
 * snapshot: mudar a configuração do terminal depois não reescreve o histórico.
 */
@Entity
@Table(name = "transacao_terminal")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransacaoTerminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private TerminalPagamento terminal;

    @Column(name = "venda_id")
    private Long vendaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvedorPagamento provedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_integracao", nullable = false, length = 20)
    private ModoIntegracaoTerminal modoIntegracao;

    /** DEBITO | CREDITO — mesmo vocabulário de pagamentos_venda.forma_pagamento. */
    @Column(nullable = false, length = 10)
    private String tipo;

    @Builder.Default
    @Column(nullable = false)
    private Integer parcelas = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTransacaoTerminal status = StatusTransacaoTerminal.PENDENTE;

    @Column(name = "id_externo", length = 100)
    private String idExterno;

    @Column(length = 50)
    private String nsu;

    @Column(length = 50)
    private String autorizacao;

    @Column(length = 30)
    private String bandeira;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @JsonIgnore
    @Column(name = "resposta_bruta", columnDefinition = "TEXT")
    private String respostaBruta;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finalizada_em")
    private LocalDateTime finalizadaEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (createdAt == null) createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Move para um status final, registrando quando. Uma vez final, não muda mais. */
    public void finalizar(StatusTransacaoTerminal novoStatus, String mensagem) {
        if (status.isFinal()) return;
        this.status = novoStatus;
        this.mensagem = mensagem;
        this.finalizadaEm = LocalDateTime.now();
    }
}
