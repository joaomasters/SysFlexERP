package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Máquina ou equipamento produtivo. Toda mudança de status fica em ind_maquina_evento. */
@Entity
@Table(name = "ind_maquina")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Maquina {
    public static final List<String> STATUS = List.of("DISPONIVEL", "EM_OPERACAO", "PARADA", "EM_MANUTENCAO", "INATIVA");
    public static final List<String> UNIDADES_CAPACIDADE = List.of("KG_H", "T_H", "PECAS_H", "M_MIN", "M_H", "CICLOS_H", "KG_CARGA");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 150)
    private String descricao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_maquina_id", nullable = false)
    private TipoMaquina tipoMaquina;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centro_trabalho_id", nullable = false)
    private CentroTrabalho centroTrabalho;
    @Column(precision = 14, scale = 4)
    private BigDecimal capacidade;
    @Column(name = "unidade_capacidade", length = 10)
    private String unidadeCapacidade;
    @Builder.Default
    @Column(nullable = false, length = 15)
    private String status = "DISPONIVEL";
    @Column(name = "custo_hora", precision = 14, scale = 4)
    private BigDecimal custoHora;
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal horimetro = BigDecimal.ZERO;
    @Column(length = 80)
    private String fabricante;
    @Column(length = 80)
    private String modelo;
    @Column(name = "numero_serie", length = 60)
    private String numeroSerie;
    @Column(name = "ano_fabricacao")
    private Short anoFabricacao;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    /** Bloqueio otimista: duas pessoas mudando a mesma máquina ao mesmo tempo não se sobrescrevem. */
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;

    @Builder.Default
    @OneToMany(mappedBy = "maquina", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaquinaParametro> parametros = new ArrayList<>();

    /** Calendário de disponibilidade: os turnos em que a máquina trabalha. */
    @Builder.Default
    @ManyToMany
    @JoinTable(name = "ind_maquina_turno", joinColumns = @JoinColumn(name = "maquina_id"),
            inverseJoinColumns = @JoinColumn(name = "turno_id"))
    private Set<Turno> turnos = new HashSet<>();
}
