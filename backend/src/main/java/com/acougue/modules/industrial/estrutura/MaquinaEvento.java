package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Histórico operacional e de manutenção da máquina. Só é inserido, nunca editado. */
@Entity
@Table(name = "ind_maquina_evento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaquinaEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maquina_id", nullable = false, updatable = false)
    private Maquina maquina;
    @Column(nullable = false, length = 25, updatable = false)
    private String tipo;
    @Column(name = "status_anterior", length = 15, updatable = false)
    private String statusAnterior;
    @Column(name = "status_novo", length = 15, updatable = false)
    private String statusNovo;
    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal horimetro;
    @Column(precision = 14, scale = 2, updatable = false)
    private BigDecimal custo;
    @Column(updatable = false)
    private LocalDateTime inicio;
    @Column(updatable = false)
    private LocalDateTime fim;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String descricao;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
