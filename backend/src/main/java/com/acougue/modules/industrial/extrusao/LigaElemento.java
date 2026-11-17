package com.acougue.modules.industrial.extrusao;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Faixa de um elemento químico na liga, em % de massa. Mínimo ou máximo podem ficar em branco. */
@Entity
@Table(name = "ind_liga_elemento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LigaElemento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liga_id", nullable = false)
    private Liga liga;
    @Column(nullable = false, length = 4)
    private String elemento;
    @Column(precision = 7, scale = 4)
    private BigDecimal minimo;
    @Column(precision = 7, scale = 4)
    private BigDecimal maximo;
}
