package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recebimento_mercadoria")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecebimentoMercadoria {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_nf", length = 50)
    private String numeroNf;

    @Column(name = "serie_nf", length = 5)
    @Builder.Default
    private String serieNf = "1";

    @Column(name = "chave_nf", length = 44)
    private String chaveNf;

    @Column(nullable = false, length = 200)
    private String fornecedor;

    @Column(name = "xml_nf", columnDefinition = "TEXT")
    private String xmlNf;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_recebimento")
    @CreationTimestamp
    private LocalDateTime dataRecebimento;

    @Column(name = "valor_total", precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Builder.Default
    private String status = "CONFERIDO";

    @Column(columnDefinition = "TEXT")
    private String observacao;

    // Quem registrou a entrada. Nunca serializado: só o NOME sai no JSON, e só para administradores.
    @JsonIgnore
    @Column(name = "usuario_id")
    private Long usuarioId;

    // Preenchido pelo controller via IdentificacaoUsuario — só para administradores.
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String usuarioNome;

    @OneToMany(mappedBy = "recebimento", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecebimentoItem> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
