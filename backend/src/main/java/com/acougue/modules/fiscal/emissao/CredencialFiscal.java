package com.acougue.modules.fiscal.emissao;

/** Segredos da empresa, já decifrados só para esta emissão. Nunca vão para log nem para a tela. */
public record CredencialFiscal(byte[] certificadoPfx, String certificadoSenha, String cscId, String cscToken) {

    @Override
    public String toString() {
        return "CredencialFiscal[cscId=" + cscId + "]";
    }
}
