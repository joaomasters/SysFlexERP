package com.acougue.modules.industrial.qualidade;

import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Plano de inspeção do produto (no máximo um ativo por produto). Produto com plano ativo tem os lotes
 * produzidos em QUARENTENA até a inspeção; sem plano, o lote nasce LIBERADO.
 * As características e faixas são cadastradas pela empresa.
 */
@Entity
@Table(name = "ind_plano_inspecao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlanoInspecao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String descricao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @Builder.Default
    @Column(nullable = false)
    private Integer revisao = 1;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
    @Column(name = "atualizado_por_id")
    private Long atualizadoPorId;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OrderBy("ordem ASC")
    @OneToMany(mappedBy = "plano", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Caracteristica> caracteristicas = new ArrayList<>();

    /** Característica a inspecionar: MEDIDA (número dentro da faixa) ou ATRIBUTO (conforme / não conforme). */
    @Entity(name = "PlanoCaracteristica")
    @Table(name = "ind_plano_caracteristica")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Caracteristica {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "plano_id", nullable = false)
        private PlanoInspecao plano;
        @Builder.Default
        @Column(nullable = false)
        private Short ordem = 0;
        @Column(nullable = false, length = 30)
        private String codigo;
        @Column(nullable = false, length = 100)
        private String nome;
        @Column(nullable = false, length = 8)
        private String tipo;
        @Column(length = 15)
        private String unidade;
        @Column(precision = 18, scale = 6)
        private BigDecimal minimo;
        @Column(precision = 18, scale = 6)
        private BigDecimal maximo;
        @Builder.Default
        @Column(nullable = false)
        private Boolean obrigatorio = true;
    }
}
