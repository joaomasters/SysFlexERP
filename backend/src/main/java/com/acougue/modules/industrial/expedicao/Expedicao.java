package com.acougue.modules.industrial.expedicao;

import com.acougue.entity.Cliente;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de expedição para um cliente. ABERTA (itens e reservas de lote) → CONFERIDA (todos os lotes
 * conferidos) → CARREGADA (baixa no estoque e no saldo dos lotes, uma vez só). Pode ser cancelada antes de carregar.
 */
@Entity
@Table(name = "ind_expedicao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Expedicao {
    public static final List<String> ATIVAS = List.of("ABERTA", "CONFERIDA");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20, updatable = false)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @Column(name = "pedido_referencia", length = 40)
    private String pedidoReferencia;
    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = "ABERTA";
    @Column(length = 120)
    private String transportadora;
    @Column(length = 10)
    private String placa;
    @Column(length = 120)
    private String motorista;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "carregada_em")
    private LocalDateTime carregadaEm;
    @Column(name = "carregada_por_id")
    private Long carregadaPorId;
    @Column(name = "nota_fiscal_id")
    private Long notaFiscalId;
    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;

    @Builder.Default
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "expedicao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpedicaoItem> itens = new ArrayList<>();
}
