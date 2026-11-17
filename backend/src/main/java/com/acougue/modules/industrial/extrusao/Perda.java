package com.acougue.modules.industrial.extrusao;

import com.acougue.modules.industrial.pcp.OrdemProducao;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Perda metálica registrada na OP (kg). Não movimenta estoque: serve ao rendimento e à análise de perdas. */
@Entity
@Table(name = "ind_perda")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Perda {
    public static final List<String> TIPOS = List.of("TOPO_TARUGO", "PONTA_PERFIL", "RETALHO", "SUCATA_PROCESSO", "OUTRO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, updatable = false)
    private String chave;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_id", nullable = false, updatable = false)
    private OrdemProducao ordem;
    @Column(name = "apontamento_id", updatable = false)
    private Long apontamentoId;
    @Column(nullable = false, length = 16, updatable = false)
    private String tipo;
    @Column(name = "quantidade_kg", nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal quantidadeKg;
    @Column(columnDefinition = "TEXT", updatable = false)
    private String observacao;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Builder.Default
    @Column(nullable = false)
    private Boolean estornada = false;
    @Column(name = "estornada_em")
    private LocalDateTime estornadaEm;
    @Column(name = "estornada_por_id")
    private Long estornadaPorId;
    @Column(name = "motivo_estorno", columnDefinition = "TEXT")
    private String motivoEstorno;
}
