package com.acougue.modules.fiscal.emissao.dto;

import java.time.LocalDateTime;
import java.util.List;

/** O que a tela recebe: tudo, menos os segredos (CSC, certificado e senha), que só dizem se existem. */
public record ConfiguracaoFiscalDTO(
        boolean emitirNfce, boolean emitirNfe, String ambiente, String uf, String provedor,
        String razaoSocial, String nomeFantasia, String cnpj, String inscricaoEstadual, short regimeTributario,
        String logradouro, String numeroEndereco, String complemento, String bairro,
        String codigoMunicipio, String municipio, String cep, String telefone,
        short nfceSerie, int nfceProximoNumero, short nfeSerie, int nfeProximoNumero,
        String cscId, boolean cscConfigurado,
        boolean certificadoConfigurado, String certificadoTitular, String certificadoCnpj,
        LocalDateTime certificadoValidade,
        boolean criptoConfigurada,
        List<Provedor> provedores,
        List<String> pendenciasNfce, List<String> pendenciasNfe,
        long produtosSemNcm) {

    public record Provedor(String codigo, String rotulo) {}
}
