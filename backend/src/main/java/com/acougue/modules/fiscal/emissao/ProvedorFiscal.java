package com.acougue.modules.fiscal.emissao;

/**
 * Quem conversa com a SEFAZ (um serviço de API fiscal, ou um emissor próprio no futuro).
 * Como as adquirentes em ProvedorTerminalNuvem: cada implementação é um @Component e o
 * ConfiguracaoFiscalService descobre todas sozinho. Sem nenhuma, a emissão não pode ser ligada.
 *
 * Cancelamento, consulta e inutilização entram aqui junto com o primeiro provedor de verdade.
 */
public interface ProvedorFiscal {

    /** Gravado em configuracao_fiscal.provedor. */
    String codigo();

    String rotulo();

    /** Provedor de teste, que não fala com a SEFAZ: nunca pode emitir em produção. */
    default boolean simulado() {
        return false;
    }

    /**
     * Assina, transmite e devolve o resultado da SEFAZ. Recusa da SEFAZ volta como
     * RetornoEmissao rejeitado; falha de comunicação pode lançar exceção.
     */
    RetornoEmissao emitirNfce(NfceEmissao nota, CredencialFiscal credencial);
}
