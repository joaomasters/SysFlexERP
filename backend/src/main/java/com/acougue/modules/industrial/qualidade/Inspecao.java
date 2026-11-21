package com.acougue.modules.industrial.qualidade;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Inspeção de um lote contra o plano (revisão e faixas copiadas no momento). Só inserção. */
@Entity
@Table(name = "ind_inspecao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inspecao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, updatable = false)
    private String chave;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false, updatable = false)
    private Lote lote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id", nullable = false, updatable = false)
    private PlanoInspecao plano;
    @Column(name = "plano_revisao", nullable = false, updatable = false)
    private Integer planoRevisao;
    @Column(nullable = false, length = 10, updatable = false)
    private String resultado;
    @Column(updatable = false)
    private Integer amostra;
    @Column(columnDefinition = "TEXT", updatable = false)
    private String observacao;
    @Column(name = "inspetor_id", updatable = false)
    private Long inspetorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "inspecao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Valor> valores = new ArrayList<>();

    @Entity(name = "InspecaoValor")
    @Table(name = "ind_inspecao_valor")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Valor {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "inspecao_id", nullable = false)
        private Inspecao inspecao;
        @Column(nullable = false, length = 30)
        private String codigo;
        @Column(nullable = false, length = 100)
        private String nome;
        @Column(nullable = false, length = 8)
        private String tipo;
        @Column(length = 15)
        private String unidade;
        @Column(name = "valor_numero", precision = 18, scale = 6)
        private BigDecimal valorNumero;
        @Column(precision = 18, scale = 6)
        private BigDecimal minimo;
        @Column(precision = 18, scale = 6)
        private BigDecimal maximo;
        @Column(nullable = false)
        private Boolean conforme;
    }
}
