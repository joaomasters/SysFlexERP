package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "tipo_pessoa", nullable = false, length = 5)
    @Builder.Default
    private String tipoPessoa = "PJ";

    @Column(name = "cpf_cnpj", length = 18)
    private String cpfCnpj;

    @Column(length = 20)
    private String telefone;

    @Size(max = 100, message = "E-mail deve ter no máximo 100 caracteres")
    @Column(length = 100)
    private String email;

    /** Pessoa de contato no fornecedor (vendedor, representante...). */
    @Size(max = 100, message = "Contato deve ter no máximo 100 caracteres")
    @Column(length = 100)
    private String contato;

    @Size(max = 255, message = "Endereço deve ter no máximo 255 caracteres")
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