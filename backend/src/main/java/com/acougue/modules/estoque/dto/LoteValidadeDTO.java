package com.acougue.modules.estoque.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um lote com saldo e a situação de validade dele, para a tela de Controle de Validade e para o sino.
 *
 * situacao: VENCIDO (validade já passou) | VENCE_EM_BREVE (dentro da janela de alerta,
 * incluindo "vence hoje") | OK.
 * diasRestantes: negativo quando já venceu (ex: -2 = venceu há 2 dias).
 */
public record LoteValidadeDTO(
        Long loteId,
        Long produtoId,
        String produtoNome,
        String unidadeMedida,
        BigDecimal quantidadeAtual,
        LocalDate dataValidade,
        long diasRestantes,
        String situacao,
        String documentoRef
) {
    public static final String VENCIDO = "VENCIDO";
    public static final String VENCE_EM_BREVE = "VENCE_EM_BREVE";
    public static final String OK = "OK";
}
