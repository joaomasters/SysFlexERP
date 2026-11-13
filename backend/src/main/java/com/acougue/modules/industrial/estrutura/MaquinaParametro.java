package com.acougue.modules.industrial.estrutura;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Valor de um parâmetro técnico (do tipo da máquina) para uma máquina específica. */
@Entity
@Table(name = "ind_maquina_parametro")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaquinaParametro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maquina_id", nullable = false)
    private Maquina maquina;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parametro_id", nullable = false)
    private TipoMaquinaParametro parametro;
    @Column(name = "valor_numero", precision = 18, scale = 6)
    private BigDecimal valorNumero;
    @Column(name = "valor_texto", length = 200)
    private String valorTexto;
}
