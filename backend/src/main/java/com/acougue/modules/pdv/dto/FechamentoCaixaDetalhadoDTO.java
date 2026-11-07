package com.acougue.modules.pdv.dto;

import com.acougue.entity.SangriaCaixa;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FechamentoCaixaDetalhadoDTO {
    private Long caixaId;
    private BigDecimal valorAbertura;
    private BigDecimal totalSangria;
    private BigDecimal totalSuprimento;
    private BigDecimal totalVendas;
    private BigDecimal totalDinheiro;
    private BigDecimal totalCredito;
    private BigDecimal totalDebito;
    private BigDecimal totalPix;
    private BigDecimal totalFiado;
    private BigDecimal totalVoucher;
    private BigDecimal saldoEsperado;
    private int quantidadeVendas;
    private List<SangriaCaixa> movimentos;
}
