package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventario_fisico")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventarioFisico {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ABERTO";

    @Column(columnDefinition = "TEXT")
    private String observacao;

    // Quem ABRIU o inventário. Nunca serializado: só os NOMES saem no JSON, e só para administradores.
    @JsonIgnore
    @Column(name = "usuario_id")
    private Long usuarioId;

    // Quem FECHOU o inventário (finalizou ou cancelou — o status diferencia).
    @JsonIgnore
    @Column(name = "fechado_por_id")
    private Long fechadoPorId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String usuarioNome;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String fechadoPorNome;

    @Column(name = "data_inicio")
    private LocalDateTime dataInicio;

    @Column(name = "data_fim")
    private LocalDateTime dataFim;

    @OneToMany(mappedBy = "inventario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InventarioFisicoItem> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
