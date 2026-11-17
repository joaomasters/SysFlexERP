package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Fornecedor;
import com.acougue.entity.Produto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lote de matéria-prima (corrida do fornecedor), com certificado e composição real. Entra em QUARENTENA;
 * só um lote LIBERADO pode ser consumido. A liberação confere a composição contra a faixa da liga.
 */
@Entity
@Table(name = "ind_lote_mp")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoteMp {
    public static final List<String> STATUS = List.of("QUARENTENA", "LIBERADO", "BLOQUEADO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liga_id", nullable = false)
    private Liga liga;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;
    @Column(length = 60)
    private String certificado;
    @Column(name = "data_recebimento", nullable = false)
    private LocalDate dataRecebimento;
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;
    @Column(name = "diametro_mm", precision = 8, scale = 2)
    private BigDecimal diametroMm;
    @Column(name = "comprimento_mm", precision = 10, scale = 2)
    private BigDecimal comprimentoMm;
    @Builder.Default
    @Column(nullable = false, length = 12)
    private String status = "QUARENTENA";
    @Column(name = "motivo_status", columnDefinition = "TEXT")
    private String motivoStatus;
    @Column(name = "status_por_id")
    private Long statusPorId;
    @Column(name = "status_em")
    private LocalDateTime statusEm;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;

    @Builder.Default
    @OrderBy("elemento ASC")
    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LoteMpComposicao> composicao = new ArrayList<>();
}
