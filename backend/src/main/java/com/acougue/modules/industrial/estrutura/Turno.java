package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.time.Duration;
import java.time.LocalTime;

/** Turno de trabalho. diasSemana: 7 posições de segunda a domingo, '1' = trabalha. */
@Entity
@Table(name = "ind_turno")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Turno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 60)
    private String nome;
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;
    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;
    @Builder.Default
    @Column(name = "dias_semana", nullable = false, length = 7)
    private String diasSemana = "1111100";
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    /** Duração em minutos; o turno que vira a meia-noite soma as horas do dia seguinte. */
    public static long minutos(LocalTime inicio, LocalTime fim) {
        long m = Duration.between(inicio, fim).toMinutes();
        return m > 0 ? m : m + 24 * 60;
    }
}
