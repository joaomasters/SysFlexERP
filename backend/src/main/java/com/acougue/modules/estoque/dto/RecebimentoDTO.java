package com.acougue.modules.estoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecebimentoDTO(
        @NotBlank String fornecedor,
        @Pattern(regexp = "\\d{1,9}", message = "O número da NF deve conter apenas números (até 9 dígitos).")
        String numeroNf,
        String serieNf,
        String chaveNf,
        LocalDate dataEmissao,
        BigDecimal valorTotal,
        String observacao,
        String xmlNf,
        @NotEmpty List<ItemDTO> itens
) {
    public record ItemDTO(
            Long produtoId,
            BigDecimal quantidade,
            BigDecimal custoUnitario,
            LocalDate dataValidade   // opcional: validade do lote recebido
    ) {}
}