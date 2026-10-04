package com.acougue.modules.financeiro.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Uma conta (dívida) com seus pagamentos — a unidade do relatório de histórico.
 *
 * valor / totalPago / saldo descrevem a conta HOJE (acumulado), independente do
 * filtro de período; já {@code pagamentos} traz só as linhas do período pedido.
 *
 * contraparte = nome do cliente (a receber) ou do fornecedor (a pagar).
 */
public record HistoricoContaDTO(
        Long contaId,
        String descricao,
        String contraparte,
        BigDecimal valor,
        BigDecimal totalPago,
        BigDecimal saldo,
        String status,
        List<PagamentoHistoricoDTO> pagamentos
) {}
