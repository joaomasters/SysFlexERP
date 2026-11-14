package com.acougue.modules.industrial.engenharia;

import com.acougue.modules.industrial.estrutura.CentroTrabalho;
import com.acougue.modules.industrial.estrutura.TipoMaquina;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Operação do roteiro: onde é feita (centro de trabalho e, opcionalmente, tipo de máquina) e os tempos padrão. */
@Entity
@Table(name = "ind_roteiro_operacao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoteiroOperacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roteiro_id", nullable = false)
    private Roteiro roteiro;
    @Column(nullable = false)
    private Integer sequencia;
    @Column(nullable = false, length = 120)
    private String descricao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centro_trabalho_id", nullable = false)
    private CentroTrabalho centroTrabalho;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_maquina_id")
    private TipoMaquina tipoMaquina;
    @Builder.Default
    @Column(name = "tempo_setup_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal tempoSetupMin = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "tempo_por_unidade_min", nullable = false, precision = 12, scale = 6)
    private BigDecimal tempoPorUnidadeMin = BigDecimal.ZERO;
    @Column(columnDefinition = "TEXT")
    private String instrucoes;
}
