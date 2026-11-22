package com.acougue.security.auditoria;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Um ticket de saída (venda do PDV) no relatório da Auditoria — inclusive os cancelados. */
@Data
@Builder
public class TicketSaidaDTO {
    private Long id;
    private String numeroCupom;
    private LocalDateTime dataVenda;
    /** ABERTA, FECHADA ou CANCELADA. */
    private String status;
    private Long operadorId;
    private String operadorNome;
    private String clienteNome;
    private BigDecimal subtotal;
    private BigDecimal desconto;
    private BigDecimal total;
    /** Ex.: "DINHEIRO, PIX". Vazio se a venda não chegou a ser paga. */
    private String formasPagamento;
    /** Motivo do cancelamento, quando informado. */
    private String observacao;
    private List<Item> itens;

    @Data
    @Builder
    public static class Item {
        private String produto;
        private String unidade;
        private BigDecimal quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal desconto;
        private BigDecimal total;
    }
}
