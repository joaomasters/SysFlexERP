package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nota_fiscal_saida")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotaFiscalSaida {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_nf", length = 50)
    private String numeroNf;

    @Column(name = "serie_nf", length = 5)
    @Builder.Default
    private String serieNf = "1";

    @Column(name = "chave_nf", length = 44)
    private String chaveNf;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "natureza_operacao", nullable = false, length = 200)
    @Builder.Default
    private String naturezaOperacao = "VENDA DE MERCADORIAS";

    @Column(name = "data_emissao")
    @CreationTimestamp
    private LocalDateTime dataEmissao;

    @Column(name = "valor_produtos", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorProdutos = BigDecimal.ZERO;

    @Column(name = "valor_desconto", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Builder.Default
    private String status = "PENDENTE";

    @Column(name = "xml_nf", columnDefinition = "TEXT")
    private String xmlNf;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    // Quem lançou a nota. Nunca serializado: só os NOMES saem no JSON, e só para administradores.
    @JsonIgnore
    @Column(name = "usuario_criacao_id")
    private Long usuarioCriacaoId;

    // Quem efetivou a saída (virou EMITIDA = baixa de estoque).
    @JsonIgnore
    @Column(name = "usuario_emissao_id")
    private Long usuarioEmissaoId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String usuarioCriacaoNome;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String usuarioEmissaoNome;

    @OneToMany(mappedBy = "nota", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaFiscalSaidaItem> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
