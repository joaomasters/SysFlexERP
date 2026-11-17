package com.acougue.modules.industrial.expedicao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.qualidade.Lote;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Item da expedição (produto e quantidade) e os lotes reservados para atendê-lo. */
@Entity
@Table(name = "ind_expedicao_item")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpedicaoItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expedicao_id", nullable = false)
    private Expedicao expedicao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Builder.Default
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reserva> reservas = new ArrayList<>();

    public BigDecimal reservado() {
        return reservas.stream().map(Reserva::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Quantidade de um lote separada para o item. A conferência confirma, lendo o código do lote. */
    @Entity(name = "ExpedicaoReserva")
    @Table(name = "ind_expedicao_reserva")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Reserva {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "item_id", nullable = false)
        private ExpedicaoItem item;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "lote_id", nullable = false)
        private Lote lote;
        @Column(nullable = false, precision = 14, scale = 4)
        private BigDecimal quantidade;
        @Builder.Default
        @Column(nullable = false)
        private Boolean conferida = false;
        @Column(name = "conferida_em")
        private LocalDateTime conferidaEm;
        @Column(name = "conferida_por_id")
        private Long conferidaPorId;
    }
}
