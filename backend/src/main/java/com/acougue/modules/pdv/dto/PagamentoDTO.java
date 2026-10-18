package com.acougue.modules.pdv.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoDTO {

    @NotBlank
    private String formaPagamento;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal valor;

    // ── Cartão ──────────────────────────────────────────────────
    // Aliases: nomes que a tela antiga do PDV enviava (e o backend descartava).
    @Size(max = 50)
    @JsonAlias("stoneNsu")
    private String nsu;

    @Size(max = 50)
    @JsonAlias("stoneAuth")
    private String autorizacao;

    @Min(1) @Max(18)
    private Integer parcelas;

    @Size(max = 30)
    private String bandeira;

    /**
     * Cartão passado em maquininha integrada: o backend confere que a transação
     * está APROVADA, é desta venda, do mesmo tipo e valor — e copia NSU,
     * autorização e bandeira dela (os campos acima são ignorados nesse caso).
     */
    private Long transacaoTerminalId;

    // ── PIX ─────────────────────────────────────────────────────
    @Size(max = 100)
    @JsonAlias("pixMpId")
    private String referenciaExterna;

    // ── Cheque ──────────────────────────────────────────────────
    @Size(max = 60)
    private String chequeBanco;

    @Size(max = 30)
    private String chequeNumero;

    @Size(max = 150)
    private String chequeTitular;
}
