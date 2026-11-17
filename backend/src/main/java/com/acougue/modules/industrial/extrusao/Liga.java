package com.acougue.modules.industrial.extrusao;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Liga metálica (ex.: 6063, 6061) com a faixa de composição de cada elemento, informada pela empresa. */
@Entity
@Table(name = "ind_liga")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Liga {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String descricao;
    @Column(length = 60)
    private String norma;
    @Column(name = "densidade_g_cm3", precision = 6, scale = 3)
    private BigDecimal densidadeGCm3;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    @Builder.Default
    @OrderBy("elemento ASC")
    @OneToMany(mappedBy = "liga", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigaElemento> elementos = new ArrayList<>();
}
