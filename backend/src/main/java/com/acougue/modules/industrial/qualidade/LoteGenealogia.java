package com.acougue.modules.industrial.qualidade;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** De qual lote (pai) um lote (filho) recebeu quantidade, numa divisão ou numa união. Só inserção. */
@Entity
@Table(name = "ind_lote_genealogia")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoteGenealogia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false, updatable = false)
    private Lote lote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_pai_id", nullable = false, updatable = false)
    private Lote lotePai;
    @Column(nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal quantidade;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
