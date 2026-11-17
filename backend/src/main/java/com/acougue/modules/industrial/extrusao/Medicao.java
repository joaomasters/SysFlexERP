package com.acougue.modules.industrial.extrusao;

import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Medição de processo numa OP, contra uma receita. A origem separa o valor digitado (MANUAL) do lido por
 * equipamento (SENSOR). Fora da faixa não bloqueia nada nem comanda máquina: só marca e avisa.
 */
@Entity
@Table(name = "ind_medicao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Medicao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, updatable = false)
    private String chave;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_id", nullable = false, updatable = false)
    private OrdemProducao ordem;
    @Column(name = "apontamento_id", updatable = false)
    private Long apontamentoId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquina_id", updatable = false)
    private Maquina maquina;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receita_id", nullable = false, updatable = false)
    private Receita receita;
    @Column(name = "receita_revisao", nullable = false, updatable = false)
    private Integer receitaRevisao;
    @Column(nullable = false, length = 20, updatable = false)
    private String etapa;
    @Builder.Default
    @Column(nullable = false, length = 8, updatable = false)
    private String origem = "MANUAL";
    @Column(name = "medido_em", nullable = false, updatable = false)
    private LocalDateTime medidoEm;
    @Column(name = "fora_especificacao", nullable = false, updatable = false)
    private Boolean foraEspecificacao;
    @Column(columnDefinition = "TEXT", updatable = false)
    private String observacao;
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "medicao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicaoValor> valores = new ArrayList<>();
}
