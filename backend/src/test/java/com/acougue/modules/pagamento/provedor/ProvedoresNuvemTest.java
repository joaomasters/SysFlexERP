package com.acougue.modules.pagamento.provedor;

import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.CobrancaTerminal;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.RetornoProvedor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Provedores em nuvem — tradução das respostas das adquirentes")
class ProvedoresNuvemTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return om.readTree(s);
    }

    @Nested
    @DisplayName("Mercado Pago Point")
    class MercadoPago {

        @Test
        @DisplayName("intent finalizado com pagamento aprovado → APROVADA com autorização e bandeira")
        void aprovado() throws Exception {
            RetornoProvedor r = MercadoPagoPointProvedor.interpretar("i1",
                    json("{\"state\":\"FINISHED\",\"payment\":{\"id\":123}}"),
                    json("{\"id\":123,\"status\":\"approved\",\"authorization_code\":\"AB12\",\"payment_method_id\":\"master\"}"));

            assertThat(r.status()).isEqualTo(StatusTransacaoTerminal.APROVADA);
            assertThat(r.nsu()).isEqualTo("123");
            assertThat(r.autorizacao()).isEqualTo("AB12");
            assertThat(r.bandeira()).isEqualTo("master");
        }

        @Test
        @DisplayName("pagamento rejeitado → NEGADA com o motivo")
        void negado() throws Exception {
            RetornoProvedor r = MercadoPagoPointProvedor.interpretar("i1",
                    json("{\"state\":\"FINISHED\"}"),
                    json("{\"id\":1,\"status\":\"rejected\",\"status_detail\":\"cc_rejected_insufficient_amount\"}"));

            assertThat(r.status()).isEqualTo(StatusTransacaoTerminal.NEGADA);
            assertThat(r.mensagem()).contains("insufficient");
        }

        @Test
        @DisplayName("intent ainda na maquininha → PENDENTE; cancelado/abandonado → CANCELADA")
        void estadosDoIntent() throws Exception {
            assertThat(MercadoPagoPointProvedor.interpretar("i1", json("{\"state\":\"ON_TERMINAL\"}"), null).status())
                    .isEqualTo(StatusTransacaoTerminal.PENDENTE);
            assertThat(MercadoPagoPointProvedor.interpretar("i1", json("{\"state\":\"ABANDONED\"}"), null).status())
                    .isEqualTo(StatusTransacaoTerminal.CANCELADA);
            assertThat(MercadoPagoPointProvedor.interpretar("i1", json("{\"state\":\"ERROR\"}"), null).status())
                    .isEqualTo(StatusTransacaoTerminal.ERRO);
        }
    }

    @Nested
    @DisplayName("Stone Connect")
    class Stone {

        @Test
        @DisplayName("pedido pago → APROVADA com NSU, autorização e bandeira da última transação")
        void pago() throws Exception {
            RetornoProvedor r = StoneConnectProvedor.interpretar("or_1", json("""
                    {"id":"or_1","status":"paid","charges":[{"last_transaction":
                      {"acquirer_nsu":"55501","acquirer_auth_code":"X9","card":{"brand":"Visa"}}}]}
                    """));

            assertThat(r.status()).isEqualTo(StatusTransacaoTerminal.APROVADA);
            assertThat(r.nsu()).isEqualTo("55501");
            assertThat(r.autorizacao()).isEqualTo("X9");
            assertThat(r.bandeira()).isEqualTo("Visa");
        }

        @Test
        @DisplayName("pedido sem cobrança ainda → PENDENTE; failed → NEGADA")
        void pendenteENegado() throws Exception {
            assertThat(StoneConnectProvedor.interpretar("or_1", json("{\"status\":\"pending\"}")).status())
                    .isEqualTo(StatusTransacaoTerminal.PENDENTE);
            assertThat(StoneConnectProvedor.interpretar("or_1", json("{\"status\":\"failed\",\"charges\":[]}")).status())
                    .isEqualTo(StatusTransacaoTerminal.NEGADA);
        }

        @Test
        @DisplayName("pedido enviado à POS: valor em centavos, série do dispositivo e parcelas")
        @SuppressWarnings("unchecked")
        void montaPedido() {
            Map<String, Object> pedido = StoneConnectProvedor.montarPedido(new CobrancaTerminal(
                    7L, 1L, "SERIE123", "CREDITO", 3, new BigDecimal("45.90")));

            Map<String, Object> poi = (Map<String, Object>) pedido.get("poi_payment_settings");
            Map<String, Object> setup = (Map<String, Object>) poi.get("payment_setup");
            Map<String, Object> item = ((List<Map<String, Object>>) pedido.get("items")).get(0);

            assertThat(pedido.get("code")).isEqualTo("SYSFLEX-TT-7");
            assertThat(item.get("amount")).isEqualTo(4590L);
            assertThat(poi.get("devices_serial_number")).isEqualTo(List.of("SERIE123"));
            assertThat(setup.get("type")).isEqualTo("credit");
            assertThat(setup.get("installments")).isEqualTo(3);
        }
    }
}
