package com.acougue.modules.comissao.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Uma venda fechada pelo funcionário, com a comissão registrada nela. */
@Data
@Builder
public class VendaComissaoDTO {
    private Long vendaId;
    private String numeroCupom;
    private LocalDateTime dataVenda;
    private BigDecimal total;
    private BigDecimal percentualComissao;  // null = venda anterior ao módulo de comissão
    private BigDecimal valorComissao;
}
