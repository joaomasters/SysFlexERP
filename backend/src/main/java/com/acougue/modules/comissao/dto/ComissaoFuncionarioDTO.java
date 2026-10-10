package com.acougue.modules.comissao.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Apuração de comissão de um funcionário no período. */
@Data
@Builder
public class ComissaoFuncionarioDTO {
    private Long usuarioId;
    private String nome;
    private String login;
    private boolean ativo;
    private BigDecimal percentualAtual;
    private long quantidadeVendas;
    private BigDecimal totalVendido;
    private BigDecimal totalComissao;
}
