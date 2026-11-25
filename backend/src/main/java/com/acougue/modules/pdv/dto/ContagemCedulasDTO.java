package com.acougue.modules.pdv.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Cédulas e moedas contadas pelo operador (abertura ou fechamento do caixa). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContagemCedulasDTO {

    @Valid
    private List<Item> cedulas = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull
        private BigDecimal valor;

        @NotNull
        @Min(0)
        private Integer quantidade;
    }
}
