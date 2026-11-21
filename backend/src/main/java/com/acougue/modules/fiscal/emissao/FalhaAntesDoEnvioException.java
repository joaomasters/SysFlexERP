package com.acougue.modules.fiscal.emissao;

/**
 * O provedor fiscal garante que a nota NÃO chegou a ser transmitida (XML inválido, certificado
 * que não abre, provedor recusou a requisição antes de falar com a SEFAZ). Só nesse caso a
 * tentativa vira ERRO; qualquer outra falha é resultado desconhecido.
 */
public class FalhaAntesDoEnvioException extends RuntimeException {

    public FalhaAntesDoEnvioException(String mensagem) {
        super(mensagem);
    }

    public FalhaAntesDoEnvioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
