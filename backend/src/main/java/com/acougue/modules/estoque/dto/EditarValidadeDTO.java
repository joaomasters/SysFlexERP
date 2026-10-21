package com.acougue.modules.estoque.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

//Corpo do pedido de correção da validade de um lote já cadastrado
public record EditarValidadeDTO(
        @NotNull(message = "Informe a nova data de validade.") LocalDate dataValidade
) {}