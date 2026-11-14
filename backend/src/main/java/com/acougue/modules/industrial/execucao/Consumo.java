package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Consumo real de material numa OP. Cada registro é uma saída no estoque; o estorno devolve. */
@Entity
@Table(name = "ind_consumo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Consumo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, updatable = false)
    private String chave;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_id", nullable = false, updatable = false)
    private OrdemProducao ordem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;
    @Column(nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal quantidade;
    @Column(name = "custo_unitario", precision = 12, scale = 4, updatable = false)
    private BigDecimal custoUnitario;
    @Column(name = "lote_material", length = 40, updatable = false)
    private String loteMaterial;
    @Column(name = "apontamento_id", updatable = false)
    private Long apontamentoId;
    @Column(columnDefinition = "TEXT", updatable = false)
    private String observacao;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Builder.Default
    @Column(nullable = false)
    private Boolean estornado = false;
    @Column(name = "estornado_em")
    private LocalDateTime estornadoEm;
    @Column(name = "estornado_por_id")
    private Long estornadoPorId;
    @Column(name = "motivo_estorno", columnDefinition = "TEXT")
    private String motivoEstorno;
}
