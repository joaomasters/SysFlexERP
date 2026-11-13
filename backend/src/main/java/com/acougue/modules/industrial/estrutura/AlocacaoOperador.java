package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** Operador (usuário do sistema) alocado a um centro de trabalho em um turno, com vigência. */
@Entity
@Table(name = "ind_alocacao_operador")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlocacaoOperador {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centro_trabalho_id", nullable = false)
    private CentroTrabalho centroTrabalho;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;
    @Column(length = 60)
    private String funcao;
    @Column(name = "vigente_de", nullable = false)
    private LocalDate vigenteDe;
    @Column(name = "vigente_ate")
    private LocalDate vigenteAte;
}
