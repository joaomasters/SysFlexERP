package com.acougue.modules.fiscal.parametros;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Regime tributário com vigência (V46). fim nulo = vigência aberta. */
@Entity
@Table(name = "fiscal_enquadramento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Enquadramento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "regime_tributario", nullable = false)
    private Short regimeTributario;
    @Column(nullable = false)
    private LocalDate inicio;
    private LocalDate fim;
    @Column(length = 255)
    private String observacao;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public boolean vigenteEm(LocalDate dia) {
        return !inicio.isAfter(dia) && (fim == null || !fim.isBefore(dia));
    }
}
