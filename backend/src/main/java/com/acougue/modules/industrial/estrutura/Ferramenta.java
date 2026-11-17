package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

/** Ferramenta, matriz ou dispositivo. A mesma matriz em outra revisão é outro registro. */
@Entity
@Table(name = "ind_ferramenta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ferramenta {
    public static final List<String> TIPOS = List.of("MATRIZ", "DISPOSITIVO", "FERRAMENTA_CORTE", "OUTRO");
    public static final List<String> STATUS = List.of("DISPONIVEL", "EM_USO", "EM_MANUTENCAO", "DESCARTADA");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 150)
    private String descricao;
    @Column(nullable = false, length = 20)
    private String tipo;
    @Column(length = 10)
    private String revisao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_maquina_id")
    private TipoMaquina tipoMaquina;
    @Column(name = "vida_util_ciclos")
    private Integer vidaUtilCiclos;
    @Builder.Default
    @Column(name = "ciclos_utilizados", nullable = false)
    private Integer ciclosUtilizados = 0;
    @Builder.Default
    @Column(nullable = false, length = 15)
    private String status = "DISPONIVEL";
    @Column(name = "custo_aquisicao", precision = 14, scale = 2)
    private BigDecimal custoAquisicao;
    @Column(columnDefinition = "TEXT")
    private String observacao;
}
