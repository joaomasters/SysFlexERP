package com.acougue.modules.fiscal.emissao.dto;

import java.time.LocalDateTime;

/** Uma NFC-e de venda, com o que o DANFE impresso precisa (emitente, chave, protocolo, QR Code). */
public record NfceDTO(
        Long id, Long vendaId, String status, String mensagem, String ambiente,
        short serie, int numero, String chave, String protocolo, LocalDateTime emitidaEm,
        String destinatarioDocumento, String urlConsulta, String qrCodeSvg,
        Emitente emitente,
        /** % aproximado de tributos para a linha da Lei 12.741/2012 no DANFE (nulo = não informado). */
        java.math.BigDecimal percentualTributos) {

    public record Emitente(String razaoSocial, String nomeFantasia, String cnpj, String inscricaoEstadual,
                           String endereco, String municipio) {}
}
