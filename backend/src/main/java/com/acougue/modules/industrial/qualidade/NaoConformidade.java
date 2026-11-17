package com.acougue.modules.industrial.qualidade;

import com.acougue.modules.industrial.pcp.OrdemProducao;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Não conformidade. Aberta por inspeção reprovada ou manualmente; com lote, o lote fica bloqueado.
 * O encerramento exige a disposição (usar como está, retrabalhar, sucatear), a causa e a ação corretiva.
 */
@Entity
@Table(name = "ind_nao_conformidade")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NaoConformidade {
    public static final List<String> ORIGENS = List.of("INSPECAO", "PROCESSO", "CLIENTE", "INTERNA");
    public static final List<String> DISPOSICOES = List.of("USAR_COMO_ESTA", "RETRABALHAR", "SUCATEAR", "SEM_LOTE");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20, updatable = false)
    private String numero;
    @Column(nullable = false, length = 10, updatable = false)
    private String origem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", updatable = false)
    private Lote lote;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordem_id", updatable = false)
    private OrdemProducao ordem;
    @Column(name = "inspecao_id", updatable = false)
    private Long inspecaoId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = "ABERTA";
    @Column(length = 16)
    private String disposicao;
    @Column(columnDefinition = "TEXT")
    private String causa;
    @Column(name = "acao_corretiva", columnDefinition = "TEXT")
    private String acaoCorretiva;
    @Column(name = "aberta_por_id", updatable = false)
    private Long abertaPorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "encerrada_por_id")
    private Long encerradaPorId;
    @Column(name = "encerrada_em")
    private LocalDateTime encerradaEm;
    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long versao = 0L;
}
