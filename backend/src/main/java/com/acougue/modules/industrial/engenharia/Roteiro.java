package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Roteiro de fabricação, versionado como a BOM (RASCUNHO → ATIVO → OBSOLETO). */
@Entity
@Table(name = "ind_roteiro")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Roteiro {
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
    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;
    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "ativado_por_id")
    private Long ativadoPorId;
    @Column(name = "ativado_em")
    private LocalDateTime ativadoEm;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OrderBy("sequencia ASC")
    @OneToMany(mappedBy = "roteiro", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoteiroOperacao> operacoes = new ArrayList<>();
}
