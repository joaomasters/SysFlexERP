package com.acougue.modules.industrial.extrusao;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Parâmetro da receita, com alvo e faixa (qualquer um pode ficar em branco). */
@Entity
@Table(name = "ind_receita_parametro")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceitaParametro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receita_id", nullable = false)
    private Receita receita;
    @Builder.Default
    @Column(nullable = false)
    private Short ordem = 0;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(length = 15)
    private String unidade;
    @Column(precision = 18, scale = 6)
    private BigDecimal alvo;
    @Column(precision = 18, scale = 6)
    private BigDecimal minimo;
    @Column(precision = 18, scale = 6)
    private BigDecimal maximo;
    @Builder.Default
    @Column(nullable = false)
    private Boolean obrigatorio = true;
}
