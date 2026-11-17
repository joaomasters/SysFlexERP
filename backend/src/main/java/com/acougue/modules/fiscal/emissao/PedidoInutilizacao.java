package com.acougue.modules.fiscal.emissao;

/** O que o provedor precisa para pedir a inutilização à SEFAZ. ano = 2 dígitos do ano dos números. */
public record PedidoInutilizacao(String ambiente, String uf, String cnpj, short modelo, short serie,
                                 int numeroInicial, int numeroFinal, int ano, String justificativa) {

    /** status: HOMOLOGADA ou REJEITADA. */
    public record Retorno(String status, String protocolo, String mensagem) {}
}
