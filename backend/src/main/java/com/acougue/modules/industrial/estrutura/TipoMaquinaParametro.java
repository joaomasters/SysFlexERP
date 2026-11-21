package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Parâmetro técnico de um tipo de máquina (ex.: temperatura máxima em °C, de 0 a 650). */
@Entity
@Table(name = "ind_tipo_maquina_parametro")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TipoMaquinaParametro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_maquina_id", nullable = false)
    private TipoMaquina tipoMaquina;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(length = 15)
    private String unidade;
    @Builder.Default
    @Column(name = "tipo_valor", nullable = false, length = 6)
    private String tipoValor = "NUMERO";
    @Column(precision = 18, scale = 6)
    private BigDecimal minimo;
    @Column(precision = 18, scale = 6)
    private BigDecimal maximo;
    @Builder.Default
    @Column(nullable = false)
    private Boolean obrigatorio = false;
    @Builder.Default
    @Column(nullable = false)
    private Short ordem = 0;

    public boolean numerico() {
        return "NUMERO".equals(tipoValor);
    }
}
