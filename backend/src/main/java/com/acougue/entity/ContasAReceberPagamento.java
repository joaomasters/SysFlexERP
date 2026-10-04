package com.acougue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uma linha do histórico de pagamentos de uma conta: um pagamento (parcial ou
 * total), com o saldo ANTES e DEPOIS dele gravados no momento em que aconteceu.
 *
 * Registro imutável (só INSERT, nunca UPDATE/DELETE) — por isso as colunas são
 * {@code updatable = false} e não há setters de negócio sendo usados depois da criação.
 * Também por isso não é serializada direto na API: o histórico sai por DTO
 * ({@code HistoricoContaDTO}) e só para administradores.
 *
 * origem: PAGAMENTO | MIGRACAO | TRANSFERENCIA
 *   (ver comentário da migration V18 para o significado de cada uma)
 *
 * usuarioId fica sem relação JPA de propósito (igual a LogAuditoria): é só a
 * referência de quem registrou; NULL para origens que não foram feitas por um usuário.
 */
@Entity
@Table(name = "contas_a_receber_pagamento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContasAReceberPagamento {

    public static final String ORIGEM_PAGAMENTO = "PAGAMENTO";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false, updatable = false)
    private ContasAReceber conta;

    @Column(name = "data_pagamento", nullable = false, updatable = false)
    private LocalDateTime dataPagamento;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "saldo_anterior", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal saldoPosterior;

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Builder.Default
    @Column(nullable = false, updatable = false, length = 20)
    private String origem = ORIGEM_PAGAMENTO;
}
