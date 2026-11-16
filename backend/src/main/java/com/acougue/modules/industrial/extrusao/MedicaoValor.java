package com.acougue.modules.industrial.extrusao;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Valor medido de um parâmetro, com a faixa da receita copiada no momento da medição. */
@Entity
@Table(name = "ind_medicao_valor")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MedicaoValor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicao_id", nullable = false)
    private Medicao medicao;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(length = 15)
    private String unidade;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal valor;
    @Column(precision = 18, scale = 6)
    private BigDecimal alvo;
    @Column(precision = 18, scale = 6)
    private BigDecimal minimo;
    @Column(precision = 18, scale = 6)
    private BigDecimal maximo;
    @Column(name = "dentro_faixa", nullable = false)
    private Boolean dentroFaixa;
}
