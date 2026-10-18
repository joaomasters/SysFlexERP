package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos_venda")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagamentoVenda {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private String formaPagamento; 

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(length = 50)
    private String nsu;

    @Column(length = 50)
    private String autorizacao;

    // ── Cartão ──────────────────────────────────────────────────
    private Integer parcelas;

    @Column(length = 30)
    private String bandeira;

    @Column(length = 20)
    private String provedor;

    /** Preenchido quando o cartão passou por uma maquininha integrada (ver TransacaoTerminal). */
    @Column(name = "transacao_terminal_id", unique = true)
    private Long transacaoTerminalId;

    /** Id do pagamento no provedor (ex: PIX no Mercado Pago). */
    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    // ── Cheque ──────────────────────────────────────────────────
    @Column(name = "cheque_banco", length = 60)
    private String chequeBanco;

    @Column(name = "cheque_numero", length = 30)
    private String chequeNumero;

    @Column(name = "cheque_titular", length = 150)
    private String chequeTitular;

    @Column(name = "data_pagamento")
    @CreationTimestamp
    private LocalDateTime dataPagamento;
}
