package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.CredencialCripto;

import java.util.Base64;

/** Decifra só o que o provedor usa: certificado e CSC (assinatura local) ou o token da API. */
final class Credenciais {

    private Credenciais() {}

    static CredencialFiscal de(ConfiguracaoFiscal c, ProvedorFiscal provedor, String ambiente, CredencialCripto cripto) {
        String token = null;
        if (provedor.exigeToken()) {
            String cifrado = "PRODUCAO".equals(ambiente) ? c.getProvedorTokenProducaoCripto() : c.getProvedorTokenHomologacaoCripto();
            if (cifrado == null) {
                throw new BusinessException("Falta o token de " + ("PRODUCAO".equals(ambiente) ? "produção" : "homologação")
                        + " do provedor fiscal na configuração.");
            }
            token = cripto.decifrar(cifrado);
        }
        if (!provedor.usaCertificadoLocal()) {
            return new CredencialFiscal(null, null, null, null, token);
        }
        if (c.getCertificadoCripto() == null) throw new BusinessException("Envie o certificado digital A1 da empresa.");
        return new CredencialFiscal(
                Base64.getDecoder().decode(cripto.decifrar(c.getCertificadoCripto())),
                cripto.decifrar(c.getCertificadoSenhaCripto()),
                c.getCscId(), c.getCscTokenCripto() == null ? null : cripto.decifrar(c.getCscTokenCripto()), token);
    }
}
