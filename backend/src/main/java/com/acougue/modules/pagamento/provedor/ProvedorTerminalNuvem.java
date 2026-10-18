package com.acougue.modules.pagamento.provedor;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adquirente com API em nuvem: o servidor manda a cobrança para a maquininha
 * pela internet e depois consulta o resultado. Para adicionar uma adquirente
 * nova, basta implementar esta interface como @Component — o
 * TransacaoTerminalService descobre todas as implementações sozinho.
 *
 * Implementações NUNCA confiam em dados vindos de webhook: o webhook só avisa
 * "mudou algo", e o status real é sempre buscado de novo via consultar().
 */
public interface ProvedorTerminalNuvem {

    ProvedorPagamento provedor();

    /** Envia a cobrança para a maquininha. Retorna normalmente PENDENTE + idExterno. */
    RetornoProvedor iniciar(CobrancaTerminal cobranca, String credencial);

    RetornoProvedor consultar(String idExterno, String identificadorDispositivo, String credencial);

    /** Cancela uma cobrança ainda não paga. Lança BusinessException se a adquirente recusar. */
    void cancelar(String idExterno, String identificadorDispositivo, String credencial);

    TesteConexao testarConexao(String credencial);

    default List<DispositivoRemoto> listarDispositivos(String credencial) {
        throw new BusinessException(provedor().getRotulo() + " não permite listar dispositivos pela API.");
    }

    default void ativarModoPdv(String identificadorDispositivo, String credencial) {
        throw new BusinessException(provedor().getRotulo() + " não exige ativação de modo PDV.");
    }

    record CobrancaTerminal(
            Long transacaoId,
            Long vendaId,
            String identificadorDispositivo,
            String tipo,          // DEBITO | CREDITO
            int parcelas,
            BigDecimal valor
    ) {
        /** Referência única da cobrança na adquirente — também serve de chave de idempotência. */
        public String referencia() {
            return "SYSFLEX-TT-" + transacaoId;
        }

        public long valorCentavos() {
            return valor.movePointRight(2).longValueExact();
        }

        public String descricao() {
            return vendaId != null ? "Venda #" + vendaId : "Venda SysFlex";
        }
    }

    record RetornoProvedor(
            StatusTransacaoTerminal status,
            String idExterno,
            String nsu,
            String autorizacao,
            String bandeira,
            String mensagem,
            String respostaBruta
    ) {
        public static RetornoProvedor pendente(String idExterno, String respostaBruta) {
            return new RetornoProvedor(StatusTransacaoTerminal.PENDENTE, idExterno, null, null, null, null, respostaBruta);
        }
    }

    record DispositivoRemoto(String id, String modoOperacao, String descricao) {}

    record TesteConexao(boolean ok, String mensagem) {}
}
