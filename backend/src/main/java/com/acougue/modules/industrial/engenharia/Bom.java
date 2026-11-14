package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrutura de produto (BOM), versionada. Só o RASCUNHO é editável; a versão ATIVA é a vigente
 * (no máximo uma por produto, garantido também por índice único no banco) e a OBSOLETA fica de histórico.
 */
@Entity
@Table(name = "ind_bom")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;
    @Column(nullable = false, updatable = false)
    private Integer versao;
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = "RASCUNHO";
    /** Quantidade do produto que esta estrutura fabrica (as quantidades dos itens são para esta base). */
    @Column(name = "quantidade_base", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidadeBase;
    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;
    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "ativada_por_id")
    private Long ativadaPorId;
    @Column(name = "ativada_em")
    private LocalDateTime ativadaEm;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OrderBy("sequencia ASC")
    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomItem> itens = new ArrayList<>();
}
