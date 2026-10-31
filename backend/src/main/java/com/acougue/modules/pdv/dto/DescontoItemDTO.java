package com.acougue.modules.pdv.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Desconto em reais sobre o valor do item. Zero remove o desconto. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DescontoItemDTO {

    @NotNull
    @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
    private BigDecimal valor;
}
