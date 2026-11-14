package com.acougue.modules.industrial.pcp;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Histórico de mudanças de status da OP. Só é inserido, nunca editado. */
@Entity
@Table(name = "ind_op_evento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OpEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ordem_id", nullable = false, updatable = false)
    private Long ordemId;
    @Column(name = "status_anterior", length = 12, updatable = false)
    private String statusAnterior;
    @Column(name = "status_novo", nullable = false, length = 12, updatable = false)
    private String statusNovo;
    @Column(columnDefinition = "TEXT", updatable = false)
    private String motivo;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
