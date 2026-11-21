package com.acougue.modules.industrial.qualidade;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lote de produto fabricado, com saldo e situação de qualidade. Nasce na produção (última operação da OP),
 * numa divisão ou numa união. Só um lote LIBERADO pode ser reservado e expedido.
 * O saldo do lote é rastreabilidade: o estoque físico do produto continua em produtos.estoque_atual.
 */
@Entity
@Table(name = "ind_lote")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lote {
    public static final List<String> SITUACOES = List.of("QUARENTENA", "LIBERADO", "BLOQUEADO", "ESTORNADO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordem_id", updatable = false)
    private OrdemProducao ordem;
    @Column(name = "apontamento_id", updatable = false)
    private Long apontamentoId;
    @Column(nullable = false, length = 10, updatable = false)
    private String origem;
    @Column(name = "quantidade_inicial", nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal quantidadeInicial;
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal saldo;
    @Column(nullable = false, length = 12)
    private String situacao;
    @Column(name = "motivo_situacao", columnDefinition = "TEXT")
    private String motivoSituacao;
    @Column(name = "situacao_por_id")
    private Long situacaoPorId;
    @Column(name = "situacao_em")
    private LocalDateTime situacaoEm;
    @Column(name = "criado_por_id", updatable = false)
    private Long criadoPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;
}
