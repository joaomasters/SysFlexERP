package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Centro de trabalho: onde as operações do roteiro acontecem e onde as máquinas ficam. */
@Entity
@Table(name = "ind_centro_trabalho")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CentroTrabalho {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_id", nullable = false)
    private Setor setor;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(name = "custo_hora", precision = 14, scale = 4)
    private BigDecimal custoHora;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
}
