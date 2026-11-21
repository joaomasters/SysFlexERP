package com.acougue.modules.fiscal.emissao;

/**
 * Segredos da empresa, já decifrados só para esta operação. Nunca vão para log nem para a tela.
 * Provedor que assina por conta própria (API de terceiro) usa só o tokenApi; o simulado e um
 * emissor próprio usam certificado e CSC.
 */
public record CredencialFiscal(byte[] certificadoPfx, String certificadoSenha, String cscId, String cscToken, String tokenApi) {

    public CredencialFiscal(byte[] certificadoPfx, String certificadoSenha, String cscId, String cscToken) {
        this(certificadoPfx, certificadoSenha, cscId, cscToken, null);
    }

    @Override
    public String toString() {
        return "CredencialFiscal[cscId=" + cscId + ", token=" + (tokenApi == null ? "não" : "sim") + "]";
    }
}
