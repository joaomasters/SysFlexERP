package com.acougue.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "produtos", indexes = {
        @Index(name = "idx_produto_codigo_balanca", columnList = "codigo_balanca"),
        @Index(name = "idx_produto_ean13", columnList = "ean13")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Produto {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_interno", unique = true, nullable = false, length = 20)
    private String codigoInterno;

    @Column(name = "codigo_balanca")
    private Integer codigoBalanca;

    @Column(length = 13)
    private String ean13;

    @Column(nullable = false, length = 150)
    @NotBlank
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "unidade_medida", nullable = false, length = 5)
    @NotBlank
    private String unidadeMedida;

    @Column(name = "tipo_produto", nullable = false, length = 20)
    @NotBlank
    private String tipoProduto;

    @Column(length = 100)
    private String marca;

    @Column(length = 150)
    private String fornecedor;

    @Column(name = "preco_custo", precision = 12, scale = 4)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 4)
    @NotNull
    @DecimalMin("0.0001")
    private BigDecimal precoVenda;

    @Column(name = "estoque_atual", precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal estoqueAtual = BigDecimal.ZERO;

    @Column(name = "estoque_minimo", precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "tem_ficha_desossa")
    @Builder.Default
    private Boolean temFichaDesossa = false;

    @Builder.Default
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}