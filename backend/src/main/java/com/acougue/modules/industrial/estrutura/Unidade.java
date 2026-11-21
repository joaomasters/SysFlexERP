package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Unidade industrial (fábrica). Uma empresa pode ter várias. */
@Entity
@Table(name = "ind_unidade")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Unidade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(length = 60)
    private String cidade;
    @Column(length = 2)
    private String uf;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
