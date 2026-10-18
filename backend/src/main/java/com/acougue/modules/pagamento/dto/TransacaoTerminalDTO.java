package com.acougue.modules.pagamento.dto;

import com.acougue.entity.ModoIntegracaoTerminal;
import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.entity.TransacaoTerminal;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransacaoTerminalDTO {
    private Long id;
    private Long terminalId;
    private String terminalNome;
    private Long vendaId;
    private ProvedorPagamento provedor;
    private ModoIntegracaoTerminal modoIntegracao;
    private String tipo;
    private int parcelas;
    private BigDecimal valor;
    private StatusTransacaoTerminal status;
    private String nsu;
    private String autorizacao;
    private String bandeira;
    private String mensagem;
    /** Só em AGENTE_LOCAL: para onde o navegador do caixa deve mandar a cobrança. */
    private String agenteUrl;
    private LocalDateTime createdAt;

    public static TransacaoTerminalDTO de(TransacaoTerminal t) {
        return TransacaoTerminalDTO.builder()
                .id(t.getId())
                .terminalId(t.getTerminal().getId())
                .terminalNome(t.getTerminal().getNome())
                .vendaId(t.getVendaId())
                .provedor(t.getProvedor())
                .modoIntegracao(t.getModoIntegracao())
                .tipo(t.getTipo())
                .parcelas(t.getParcelas())
                .valor(t.getValor())
                .status(t.getStatus())
                .nsu(t.getNsu())
                .autorizacao(t.getAutorizacao())
                .bandeira(t.getBandeira())
                .mensagem(t.getMensagem())
                .agenteUrl(t.getModoIntegracao() == ModoIntegracaoTerminal.AGENTE_LOCAL
                        ? t.getTerminal().getAgenteUrl() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
