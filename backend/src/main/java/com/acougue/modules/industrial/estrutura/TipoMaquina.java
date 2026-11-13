package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

/** Tipo de máquina e os parâmetros técnicos dele. Máquinas de naturezas diferentes (forno, prensa,
 * serra) não compartilham parâmetros: cada tipo define os seus. */
@Entity
@Table(name = "ind_tipo_maquina")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TipoMaquina {
    public static final List<String> CATEGORIAS = List.of("FORNO", "PRENSA_EXTRUSAO", "CORTE", "TRATAMENTO_TERMICO",
            "CNC", "ACABAMENTO", "EMBALAGEM", "OUTRO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(nullable = false, length = 25)
    private String categoria;
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
    @Builder.Default
    @OneToMany(mappedBy = "tipoMaquina", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC, id ASC")
    private List<TipoMaquinaParametro> parametros = new ArrayList<>();
}
