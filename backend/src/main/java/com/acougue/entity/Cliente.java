package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cliente {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "cpf_cnpj", length = 18)
    private String cpfCnpj;

    @Column(name = "tipo_pessoa", length = 5)
    @Builder.Default
    private String tipoPessoa = "PF";

    @Column(length = 20)
    private String telefone;

    @Column(length = 100)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String endereco;

    @Column(name = "tipo_cliente", length = 20)
    @Builder.Default
    private String tipoCliente = "VAREJO";

    @Column(name = "limite_credito", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "saldo_fiado_atual", precision = 12, scale = 2)
    private BigDecimal saldoFiadoAtual = BigDecimal.ZERO;

    @Builder.Default
    private Boolean ativo = true;

    // Quem cadastrou o cliente. @JsonIgnore também impede o cliente da API de forjar esse valor
    // (Cliente é usado como @RequestBody) — o service sempre grava o usuário logado.
    @JsonIgnore
    @Column(name = "criado_por_id")
    private Long criadoPorId;

    // Preenchido pelo controller via IdentificacaoUsuario — só para administradores.
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String criadoPorNome;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}