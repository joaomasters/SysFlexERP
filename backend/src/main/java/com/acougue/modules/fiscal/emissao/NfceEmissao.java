package com.acougue.modules.fiscal.emissao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tudo o que um provedor precisa para montar o XML de uma NFC-e (modelo 65), já com os
 * valores fechados: soma de valorBruto − soma de desconto dos itens = total.
 */
public record NfceEmissao(
        String ambiente,             // HOMOLOGACAO | PRODUCAO
        String uf,
        Emitente emitente,
        short serie,
        int numero,
        LocalDateTime dataEmissao,
        String destinatarioDocumento, // CPF (11) ou CNPJ (14); null = consumidor não identificado
        List<Item> itens,
        List<Pagamento> pagamentos,
        BigDecimal troco,
        BigDecimal total,
        Long vendaId) {

    public record Emitente(String cnpj, String inscricaoEstadual, String razaoSocial, String nomeFantasia,
                           short regimeTributario, String logradouro, String numero, String complemento,
                           String bairro, String codigoMunicipio, String municipio, String cep, String telefone) {}

    public record Item(String codigo, String ean, String descricao, String ncm, String cest, String cfop,
                       String origem, String csosn, String unidade, BigDecimal quantidade,
                       BigDecimal valorUnitario, BigDecimal valorBruto, BigDecimal desconto) {}

    /** formaPagamento = código tPag da SEFAZ (01 dinheiro, 03 crédito, 04 débito, 17 PIX...). */
    public record Pagamento(String formaPagamento, BigDecimal valor) {}
}
