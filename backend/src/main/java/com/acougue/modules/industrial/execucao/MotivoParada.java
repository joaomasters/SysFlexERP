package com.acougue.modules.industrial.execucao;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

/** Motivo de parada de máquina. "Planejada" separa paradas previstas (setup, refeição) das que são perda. */
@Entity
@Table(name = "ind_motivo_parada")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MotivoParada {
    public static final List<String> CATEGORIAS =
            List.of("SETUP", "MANUTENCAO", "FALTA_MATERIAL", "QUALIDADE", "OPERACIONAL", "EXTERNA", "OUTRO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String descricao;
    @Column(nullable = false, length = 15)
    private String categoria;
    @Builder.Default
    @Column(nullable = false)
    private Boolean planejada = false;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
}
