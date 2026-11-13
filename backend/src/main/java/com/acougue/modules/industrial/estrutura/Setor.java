package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;

/** Setor produtivo de uma unidade (ex.: Extrusão, Tratamento térmico, Expedição). */
@Entity
@Table(name = "ind_setor")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Setor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nome;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
}
