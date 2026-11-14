package com.acougue.modules.industrial.execucao;

import com.acougue.modules.industrial.estrutura.Maquina;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Parada de máquina: pausa de um apontamento ou parada avulsa. Aberta enquanto {@code fim} for nulo. */
@Entity
@Table(name = "ind_parada")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Parada {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, updatable = false)
    private String chave;
    /** Nula só na pausa de um apontamento feito sem máquina (centro manual). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquina_id", updatable = false)
    private Maquina maquina;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motivo_id", nullable = false, updatable = false)
    private MotivoParada motivo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apontamento_id", updatable = false)
    private Apontamento apontamento;
    @Column(name = "ordem_id", updatable = false)
    private Long ordemId;
    @Column(nullable = false, updatable = false)
    private LocalDateTime inicio;
    private LocalDateTime fim;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "encerrada_por_id")
    private Long encerradaPorId;
}
