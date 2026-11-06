package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * O que falta para a empresa poder emitir cada modelo. Enquanto houver pendência, o modelo
 * não pode ser ligado e o PDV segue no cupom não fiscal.
 */
public record PendenciasFiscais(List<String> nfce, List<String> nfe) {

    public static PendenciasFiscais calcular(ConfiguracaoFiscal c, boolean criptoConfigurada, boolean provedorDisponivel) {
        List<String> comuns = new ArrayList<>();
        if (!criptoConfigurada) {
            comuns.add("O servidor está sem PAGAMENTO_CRIPTO_CHAVE: sem ela não dá para guardar o certificado.");
        }
        if (vazio(c.getRazaoSocial())) comuns.add("Informe a razão social.");
        if (!DocumentosFiscais.cnpjValido(c.getCnpj())) comuns.add("Informe um CNPJ válido.");
        if (!DocumentosFiscais.inscricaoEstadualPrValida(c.getInscricaoEstadual())) {
            comuns.add("Informe uma Inscrição Estadual do Paraná válida (10 dígitos).");
        }
        if (vazio(c.getLogradouro()) || vazio(c.getNumeroEndereco()) || vazio(c.getBairro()) || vazio(c.getMunicipio())) {
            comuns.add("Complete o endereço: logradouro, número, bairro e município.");
        }
        if (c.getCodigoMunicipio() == null || !c.getCodigoMunicipio().matches("41[0-9]{5}")) {
            comuns.add("Informe o código IBGE do município (7 dígitos, começa com 41 no Paraná).");
        }
        if (c.getCep() == null || !c.getCep().matches("8[0-7][0-9]{6}")) {
            comuns.add("Informe um CEP do Paraná (80000-000 a 87999-999).");
        }
        if (c.getCertificadoCripto() == null) {
            comuns.add("Envie o certificado digital A1 (.pfx) da empresa.");
        } else {
            if (c.getCertificadoValidade() != null && c.getCertificadoValidade().isBefore(LocalDateTime.now())) {
                comuns.add("O certificado digital venceu. Envie o certificado renovado.");
            }
            // O certificado da matriz assina pelas filiais: basta a raiz (8 primeiros) do CNPJ bater
            if (c.getCertificadoCnpj() != null && c.getCnpj() != null
                    && !c.getCertificadoCnpj().regionMatches(0, c.getCnpj(), 0, 8)) {
                comuns.add("O certificado é de outro CNPJ (" + c.getCertificadoCnpj() + ").");
            }
        }
        if (!provedorDisponivel) {
            comuns.add("Ainda não há provedor de comunicação com a SEFAZ configurado no sistema.");
        }

        List<String> nfce = new ArrayList<>(comuns);
        if (vazio(c.getCscId()) || c.getCscTokenCripto() == null) {
            nfce.add("Informe o CSC (identificador e código) gerado no portal da SEFAZ-PR.");
        }
        return new PendenciasFiscais(List.copyOf(nfce), List.copyOf(comuns));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
