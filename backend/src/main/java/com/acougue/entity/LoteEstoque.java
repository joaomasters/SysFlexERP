package com.acougue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um lote de estoque com validade conhecida: nasce numa entrada (recebimento, desossa...)
 * e vai sendo consumido em ordem FEFO por {@code EstoqueService}.
 *
 * Não é serializado direto na API — a consulta sai por {@code LoteValidadeDTO}.
 */
@Entity
@Table(name = "lote_estoque")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoteEstoque {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @Column(name = "data_validade", nullable = false)
    private LocalDate dataValidade;

    @Column(name = "quantidade_inicial", nullable = false, updatable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeInicial;

    @Column(name = "quantidade_atual", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeAtual;

    @Column(name = "documento_ref", length = 100)
    private String documentoRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
