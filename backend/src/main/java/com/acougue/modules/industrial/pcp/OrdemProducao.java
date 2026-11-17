package com.acougue.modules.industrial.pcp;

import com.acougue.entity.Cliente;
import com.acougue.entity.Produto;
import com.acougue.modules.industrial.engenharia.Bom;
import com.acougue.modules.industrial.engenharia.Roteiro;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ordem de produção. Na liberação, a BOM e o roteiro ativos são copiados para ind_op_material e ind_op_operacao:
 * mudanças posteriores na engenharia não alteram uma OP já liberada.
 */
@Entity
@Table(name = "ind_ordem_producao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdemProducao {
    public static final List<String> STATUS =
            List.of("PLANEJADA", "LIBERADA", "EM_PRODUCAO", "PAUSADA", "CONCLUIDA", "ENCERRADA", "CANCELADA");
    /** Situações que ainda ocupam capacidade e materiais. */
    public static final List<String> ABERTAS = List.of("PLANEJADA", "LIBERADA", "EM_PRODUCAO", "PAUSADA");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20, updatable = false)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;
    @Builder.Default
    @Column(nullable = false, length = 12)
    private String status = "PLANEJADA";
    @Builder.Default
    @Column(nullable = false)
    private Short prioridade = 3;
    @Column(name = "inicio_previsto", nullable = false)
    private LocalDate inicioPrevisto;
    @Column(name = "fim_previsto", nullable = false)
    private LocalDate fimPrevisto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
    @Column(name = "pedido_referencia", length = 40)
    private String pedidoReferencia;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id")
    private Bom bom;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roteiro_id")
    private Roteiro roteiro;
    @Column(name = "liberada_em")
    private LocalDateTime liberadaEm;
    @Column(name = "liberada_por_id")
    private Long liberadaPorId;
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OrderBy("sequencia ASC")
    @OneToMany(mappedBy = "ordem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OpMaterial> materiais = new ArrayList<>();

    @Builder.Default
    @OrderBy("sequencia ASC")
    @OneToMany(mappedBy = "ordem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OpOperacao> operacoes = new ArrayList<>();
}
