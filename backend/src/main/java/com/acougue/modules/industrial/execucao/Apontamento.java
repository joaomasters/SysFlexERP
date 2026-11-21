package com.acougue.modules.industrial.execucao;

import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sessão de trabalho de um operador numa operação da OP: início, pausas (paradas ligadas a ele), fim e
 * quantidades. A {@code chave} vem do cliente e torna o início idempotente (o tablet pode reenviar sem duplicar).
 */
@Entity
@Table(name = "ind_apontamento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Apontamento {
    public static final List<String> ABERTOS = List.of("EM_ANDAMENTO", "PAUSADO");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40, updatable = false)
    private String chave;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_id", nullable = false, updatable = false)
    private OrdemProducao ordem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacao_id", nullable = false, updatable = false)
    private OpOperacao operacao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquina_id", updatable = false)
    private Maquina maquina;
    @Column(name = "operador_id", nullable = false, updatable = false)
    private Long operadorId;
    @Builder.Default
    @Column(nullable = false, length = 12)
    private String status = "EM_ANDAMENTO";
    @Column(nullable = false, updatable = false)
    private LocalDateTime inicio;
    private LocalDateTime fim;
    @Builder.Default
    @Column(name = "minutos_parado", nullable = false, precision = 12, scale = 2)
    private BigDecimal minutosParado = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "quantidade_boa", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidadeBoa = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "quantidade_refugo", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidadeRefugo = BigDecimal.ZERO;
    @Column(name = "motivo_refugo", length = 200)
    private String motivoRefugo;
    @Column(length = 30)
    private String lote;
    @Builder.Default
    @Column(name = "concluiu_operacao", nullable = false)
    private Boolean concluiuOperacao = false;
    @Builder.Default
    @Column(name = "entrada_estoque", nullable = false)
    private Boolean entradaEstoque = false;
    @Column(name = "chave_finalizacao", length = 40)
    private String chaveFinalizacao;
    @Column(columnDefinition = "TEXT")
    private String observacao;
    @Column(name = "estornado_em")
    private LocalDateTime estornadoEm;
    @Column(name = "estornado_por_id")
    private Long estornadoPorId;
    @Column(name = "motivo_estorno", columnDefinition = "TEXT")
    private String motivoEstorno;
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean aberto() {
        return ABERTOS.contains(status);
    }
}
