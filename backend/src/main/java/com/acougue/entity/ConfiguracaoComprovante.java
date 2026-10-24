package com.acougue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Cabeçalho, rodapé e opções do cupom não fiscal da empresa (uma linha por empresa, id = 1). */
@Entity
@Table(name = "configuracao_comprovante")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfiguracaoComprovante {

    public static final short ID_UNICO = 1;

    @Id
    private Short id;

    @Column(name = "nome_fantasia", nullable = false, length = 120)
    private String nomeFantasia;

    @Column(length = 20)
    private String cnpj;

    @Column(length = 150)
    private String endereco;

    @Column(length = 80)
    private String bairro;

    @Column(length = 80)
    private String cidade;

    @Column(length = 30)
    private String telefone;

    @Column(name = "mensagem_rodape", length = 300)
    private String mensagemRodape;

    @Builder.Default
    @Column(name = "largura_papel_mm", nullable = false)
    private Short larguraPapelMm = 80;

    @Builder.Default
    @Column(nullable = false)
    private Short vias = 1;

    @Builder.Default
    @Column(name = "imprimir_automatico", nullable = false)
    private Boolean imprimirAutomatico = false;

    @Builder.Default
    @Column(name = "mostrar_aviso_nao_fiscal", nullable = false)
    private Boolean mostrarAvisoNaoFiscal = true;

    @Builder.Default
    @Column(name = "mostrar_operador", nullable = false)
    private Boolean mostrarOperador = true;

    @Builder.Default
    @Column(name = "mostrar_cliente", nullable = false)
    private Boolean mostrarCliente = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist @PreUpdate
    void tocar() {
        updatedAt = LocalDateTime.now();
    }
}
