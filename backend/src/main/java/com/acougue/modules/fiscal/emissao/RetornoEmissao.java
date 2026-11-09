package com.acougue.modules.fiscal.emissao;

import java.time.LocalDateTime;

/** Resultado da SEFAZ para uma nota. status segue documento_fiscal.status (AUTORIZADA, REJEITADA...). */
public record RetornoEmissao(String status, String chave, String protocolo, String mensagem,
                             String urlQrcode, String urlConsulta, String xml, String idExterno,
                             LocalDateTime autorizadaEm) {

    public static RetornoEmissao erro(String mensagem) {
        return new RetornoEmissao("ERRO", null, null, mensagem, null, null, null, null, null);
    }

    public boolean autorizada() {
        return "AUTORIZADA".equals(status);
    }
}
