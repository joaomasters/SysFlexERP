package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Componente de uma BOM, na unidade de medida do próprio componente. */
@Entity
@Table(name = "ind_bom_item")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BomItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id", nullable = false)
    private Bom bom;
    @Column(nullable = false)
    private Integer sequencia;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "componente_id", nullable = false)
    private Produto componente;
    @Column(nullable = false, precision = 14, scale = 6)
    private BigDecimal quantidade;
    @Builder.Default
    @Column(name = "perda_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal perdaPercentual = BigDecimal.ZERO;
    @Column(length = 200)
    private String observacao;
}
