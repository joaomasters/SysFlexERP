package com.acougue.modules.fiscal.emissao;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pedido de inutilização de uma faixa de números (V46). Nasce DESCONHECIDO e confirmado no banco
 * antes da transmissão; vira HOMOLOGADA ou REJEITADA com a resposta. Sem resposta, continua
 * DESCONHECIDO e a faixa não pode ser pedida de novo sem conferir.
 */
@Entity
@Table(name = "fiscal_inutilizacao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inutilizacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Short modelo;
    @Column(nullable = false)
    private Short serie;
    @Column(name = "numero_inicial", nullable = false)
    private Integer numeroInicial;
    @Column(name = "numero_final", nullable = false)
    private Integer numeroFinal;
    @Column(nullable = false)
    private Short ano;
    @Column(nullable = false, length = 12)
    private String ambiente;
    @Column(nullable = false, length = 255)
    private String justificativa;
    @Column(nullable = false, length = 15)
    private String status;
    @Column(length = 20)
    private String protocolo;
    private String mensagem;
    @Column(length = 30)
    private String provedor;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public boolean cobre(int numero) {
        return numero >= numeroInicial && numero <= numeroFinal;
    }
}
