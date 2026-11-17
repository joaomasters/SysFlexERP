package com.acougue.modules.industrial.qualidade;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Extrato do lote: cada variação de saldo (positiva ou negativa) e o documento que a causou. Só inserção. */
@Entity
@Table(name = "ind_lote_movimento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoteMovimento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "lote_id", nullable = false, updatable = false)
    private Long loteId;
    @Column(nullable = false, length = 20, updatable = false)
    private String tipo;
    @Column(nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal quantidade;
    @Column(length = 50, updatable = false)
    private String documento;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
