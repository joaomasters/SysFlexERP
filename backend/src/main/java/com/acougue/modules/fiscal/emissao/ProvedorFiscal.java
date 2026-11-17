package com.acougue.modules.fiscal.emissao;

import java.util.Optional;

/**
 * Quem conversa com a SEFAZ (um serviço de API fiscal, ou um emissor próprio no futuro).
 * Como as adquirentes em ProvedorTerminalNuvem: cada implementação é um @Component e o
 * ConfiguracaoFiscalService descobre todas sozinho. Sem nenhuma, a emissão não pode ser ligada.
 *
 * Contrato de falhas (ver docs/CONTABIL_FISCAL.md, "Semântica de estados"):
 * - recusa da SEFAZ volta como RetornoEmissao (REJEITADA/DENEGADA);
 * - {@link FalhaAntesDoEnvioException}: o provedor garante que nada foi transmitido → ERRO;
 * - qualquer outra exceção (timeout, conexão caída no meio) → resultado DESCONHECIDO: a nota
 *   pode ter sido autorizada, e só uma consulta pela chave resolve.
 *
 * Cancelamento entra aqui junto com o primeiro provedor de verdade.
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
     * Assina, transmite e devolve o resultado da SEFAZ. A chave de acesso vem pronta em
     * {@link NfceEmissao#chave()} (gravada antes do envio) e deve ser usada como está.
     */
    RetornoEmissao emitirNfce(NfceEmissao nota, CredencialFiscal credencial);

    /**
     * Situação atual da nota na SEFAZ, pela chave. Vazio = o provedor não sabe consultar
     * (aí o usuário confere no portal da SEFAZ). Falha de comunicação pode lançar exceção.
     */
    default Optional<RetornoEmissao> consultarNfce(String chave, String ambiente, CredencialFiscal credencial) {
        return Optional.empty();
    }

    /**
     * Pede à SEFAZ a inutilização da faixa. Vazio = o provedor não inutiliza. Recusa volta como
     * Retorno REJEITADA; falha de comunicação pode lançar exceção (resultado desconhecido).
     */
    default Optional<PedidoInutilizacao.Retorno> inutilizar(PedidoInutilizacao pedido, CredencialFiscal credencial) {
        return Optional.empty();
    }
}
