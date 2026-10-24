package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "fornecedores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Fornecedor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "tipo_pessoa", nullable = false, length = 5)
    @Builder.Default
    private String tipoPessoa = "PJ";

    @Column(name = "cpf_cnpj", length = 18)
    private String cpfCnpj;

    @Column(length = 20)
    private String telefone;

    @Column(length = 100)
    private String email;

    /** Pessoa de contato no fornecedor (vendedor, representante...). */
    @Column(length = 100)
    private String contato;

    @Column(columnDefinition = "TEXT")
    private String endereco;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    // Autoria vem do token, nunca do corpo (mesmo padrão de Cliente/Produto).
    @JsonIgnore
    @Column(name = "criado_por_id")
    private Long criadoPorId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String criadoPorNome;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}