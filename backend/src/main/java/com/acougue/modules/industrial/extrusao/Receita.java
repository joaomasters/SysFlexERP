package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.estrutura.TipoMaquina;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Receita de processo para uma liga num tipo de máquina, numa etapa (aquecimento, extrusão, tratamento…).
 * Os parâmetros e as faixas são cadastrados pela empresa; o sistema não traz valor nenhum de processo.
 * Cada alteração sobe a revisão; as medições guardam a revisão e a faixa vigentes quando foram feitas.
 */
@Entity
@Table(name = "ind_receita")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Receita {
    public static final List<String> ETAPAS = List.of("AQUECIMENTO", "EXTRUSAO", "RESFRIAMENTO", "TRATAMENTO_TERMICO", "OUTRA");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String descricao;
    @Column(nullable = false, length = 20)
    private String etapa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liga_id", nullable = false)
    private Liga liga;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_maquina_id", nullable = false)
    private TipoMaquina tipoMaquina;
    /** Opcional: receita específica de um perfil. Sem produto, vale para qualquer produto da liga. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;
    @Builder.Default
    @Column(nullable = false)
    private Integer revisao = 1;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
    @Column(name = "atualizado_por_id")
    private Long atualizadoPorId;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OrderBy("ordem ASC")
    @OneToMany(mappedBy = "receita", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceitaParametro> parametros = new ArrayList<>();
}
