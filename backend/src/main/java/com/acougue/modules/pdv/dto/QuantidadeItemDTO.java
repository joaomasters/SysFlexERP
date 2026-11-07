package com.acougue.modules.pdv.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Nova quantidade de um item da comanda (unidades ou kg). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuantidadeItemDTO {

    @NotNull(message = "Informe a quantidade.")
    private BigDecimal quantidade;
}
