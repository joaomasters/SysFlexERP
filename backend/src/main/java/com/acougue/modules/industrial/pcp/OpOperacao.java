package com.acougue.modules.industrial.pcp;

import com.acougue.modules.industrial.estrutura.CentroTrabalho;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.TipoMaquina;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Operação da OP, copiada do roteiro na liberação. A máquina é escolhida pelo PCP depois, dentro do centro. */
@Entity
@Table(name = "ind_op_operacao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OpOperacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_id", nullable = false)
    private OrdemProducao ordem;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquina_id")
    private Maquina maquina;
    @Column(name = "tempo_setup_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal tempoSetupMin;
    @Column(name = "tempo_previsto_min", nullable = false, precision = 14, scale = 2)
    private BigDecimal tempoPrevistoMin;
    @Column(columnDefinition = "TEXT")
    private String instrucoes;
    @Builder.Default
    @Column(nullable = false, length = 12)
    private String status = "PENDENTE";
}
