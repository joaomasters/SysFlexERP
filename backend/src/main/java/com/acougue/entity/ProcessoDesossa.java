package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "processo_desossa")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessoDesossa {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_desossa_id", nullable = false)
    private FichaDesossa fichaDesossa;

    @Column(name = "quantidade_entrada", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeEntrada;

    @Column(name = "data_processo")
    @CreationTimestamp
    private LocalDateTime dataProcesso;

    // Quem executou o rateio. Nunca serializado: só o NOME sai no JSON, e só para administradores.
    @JsonIgnore
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String usuarioNome;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Builder.Default
    private String status = "CONCLUIDO";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recebimento_id")
    private RecebimentoMercadoria recebimento;

    @OneToMany(mappedBy = "processoDesossa", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessoDesossaResultado> resultados = new ArrayList<>();
}
