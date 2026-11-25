package com.acougue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Quantidade contada de uma cédula/moeda na abertura ou no fechamento de um caixa. */
@Entity
@Table(name = "caixa_cedulas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaixaCedula {

    public static final String ABERTURA = "ABERTURA";
    public static final String FECHAMENTO = "FECHAMENTO";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caixa_id", nullable = false)
    private Long caixaId;

    @Column(nullable = false, length = 12)
    private String momento;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal denominacao;

    @Column(nullable = false)
    private Integer quantidade;
}
