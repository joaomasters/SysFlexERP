package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dados industriais de um produto (um por produto). Estende o cadastro de produtos sem alterar a tabela produtos.
 * Os fatores de peso são referência técnica informada pela engenharia: o sistema não converte unidades sozinho.
 */
@Entity
@Table(name = "ind_ficha_tecnica")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FichaTecnica {
    public static final List<String> TIPOS_ITEM = List.of("MATERIA_PRIMA", "SEMIACABADO", "ACABADO", "EMBALAGEM", "INSUMO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;
    @Column(name = "tipo_item", nullable = false, length = 15)
    private String tipoItem;
    @Column(name = "codigo_desenho", length = 40)
    private String codigoDesenho;
    @Column(name = "revisao_desenho", length = 10)
    private String revisaoDesenho;
    @Column(name = "peso_unitario_kg", precision = 14, scale = 6)
    private BigDecimal pesoUnitarioKg;
    @Column(name = "peso_linear_kg_m", precision = 14, scale = 6)
    private BigDecimal pesoLinearKgM;
    @Column(name = "comprimento_mm", precision = 12, scale = 3)
    private BigDecimal comprimentoMm;
    @Column(name = "lote_minimo", precision = 14, scale = 4)
    private BigDecimal loteMinimo;
    @Column(name = "lote_multiplo", precision = 14, scale = 4)
    private BigDecimal loteMultiplo;
    @Column(name = "prazo_producao_dias")
    private Integer prazoProducaoDias;
    @Builder.Default
    @Column(name = "controla_lote", nullable = false)
    private Boolean controlaLote = true;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
