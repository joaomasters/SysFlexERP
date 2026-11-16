package com.acougue.modules.industrial.extrusao;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Composição real do lote, como consta no certificado do fornecedor (% em massa). */
@Entity
@Table(name = "ind_lote_mp_composicao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoteMpComposicao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteMp lote;
    @Column(nullable = false, length = 4)
    private String elemento;
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentual;
}
