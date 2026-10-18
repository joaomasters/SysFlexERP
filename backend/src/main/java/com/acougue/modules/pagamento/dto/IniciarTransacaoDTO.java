package com.acougue.modules.pagamento.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IniciarTransacaoDTO {

    @NotNull(message = "Selecione a maquininha")
    private Long terminalId;

    @NotNull(message = "Venda é obrigatória")
    private Long vendaId;

    @NotNull
    @Pattern(regexp = "DEBITO|CREDITO", message = "Tipo deve ser DEBITO ou CREDITO")
    private String tipo;

    @Min(1) @Max(18)
    private int parcelas = 1;

    @NotNull
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal valor;
}
