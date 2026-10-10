package com.acougue.modules.comissao.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DefinirPercentualDTO {
    @NotNull(message = "Percentual é obrigatório")
    @DecimalMin(value = "0.00", message = "Percentual não pode ser negativo")
    @DecimalMax(value = "100.00", message = "Percentual não pode passar de 100%")
    @Digits(integer = 3, fraction = 2, message = "Percentual aceita no máximo 2 casas decimais")
    private BigDecimal percentual;
}
