package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "perdas_estoque")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PerdasEstoque {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "custo_unitario", precision = 12, scale = 4)
    private BigDecimal custoUnitario;

    @Column(name = "custo_total", precision = 12, scale = 2)
    private BigDecimal custoTotal;

    @Column(nullable = false, length = 50)
    private String motivo;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    // Quem lançou a perda. Nunca serializado: só o NOME sai no JSON, e só para administradores.
    @JsonIgnore
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String usuarioNome;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
