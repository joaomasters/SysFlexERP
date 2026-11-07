package com.acougue.modules.fiscal.emissao;

/**
 * Quem conversa com a SEFAZ (um serviço de API fiscal, ou um emissor próprio no futuro).
 * Como as adquirentes em ProvedorTerminalNuvem: cada implementação é um @Component e o
 * ConfiguracaoFiscalService descobre todas sozinho. Sem nenhuma, a emissão não pode ser ligada.
 *
 * A emissão de verdade (emitir, consultar, cancelar, inutilizar) entra aqui junto com o
 * primeiro provedor, quando ele for escolhido.
 */
public interface ProvedorFiscal {

    /** Gravado em configuracao_fiscal.provedor. */
    String codigo();

    String rotulo();
}
