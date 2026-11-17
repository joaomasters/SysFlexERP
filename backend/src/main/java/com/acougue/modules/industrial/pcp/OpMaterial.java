package com.acougue.modules.industrial.pcp;

import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Material previsto da OP, copiado da BOM na liberação e calculado para a quantidade da OP. */
@Entity
@Table(name = "ind_op_material")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OpMaterial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_id", nullable = false)
    private OrdemProducao ordem;
    @Column(nullable = false)
    private Integer sequencia;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @Column(name = "quantidade_liquida", nullable = false, precision = 16, scale = 6)
    private BigDecimal quantidadeLiquida;
    @Column(name = "perda_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal perdaPercentual;
    @Column(name = "quantidade_prevista", nullable = false, precision = 16, scale = 6)
    private BigDecimal quantidadePrevista;
    @Column(nullable = false, length = 5)
    private String unidade;
}
