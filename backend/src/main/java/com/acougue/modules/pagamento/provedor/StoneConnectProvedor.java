package com.acougue.modules.pagamento.provedor;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.ClienteHttpPagamento;
import com.acougue.modules.pagamento.ClienteHttpPagamento.Resposta;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stone Connect — maquininhas Stone (POS Android) recebendo pedidos pela API
 * de pedidos da Pagar.me/Stone (core v5) com "poi_payment_settings".
 *
 * Credencial: Secret Key da conta (sk_test_... no sandbox, sk_... em produção),
 * liberada pelo time comercial da Stone junto com a habilitação do Stone Connect.
 * O identificador do dispositivo é o NÚMERO DE SÉRIE da POS.
 *
 * ⚠ Antes de ir para produção, homologue com a Stone: o contrato de
 * poi_payment_settings pode ter campos obrigatórios extras dependendo do
 * plano contratado. Toda a montagem do pedido está em montarPedido().
 *
 * Maquininha Stone via TEF no computador (sem Stone Connect) usa o modo
 * AGENTE_LOCAL, não esta classe.
 */
@Component
@RequiredArgsConstructor
public class StoneConnectProvedor implements ProvedorTerminalNuvem {

    static final String BASE = "https://api.pagar.me/core/v5";

    private final ClienteHttpPagamento http;

    @Override
    public ProvedorPagamento provedor() {
        return ProvedorPagamento.STONE;
    }

    @Override
    public RetornoProvedor iniciar(CobrancaTerminal c, String secretKey) {
        Resposta r = http.enviar("POST", BASE + "/orders", headers(secretKey, c.referencia()), montarPedido(c));
        exigirSucesso(r, "criar o pedido na maquininha");
        return RetornoProvedor.pendente(r.json().path("id").asText(), r.corpo());
    }

    static Map<String, Object> montarPedido(CobrancaTerminal c) {
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("type", "CREDITO".equals(c.tipo()) ? "credit" : "debit");
        setup.put("installments", "CREDITO".equals(c.tipo()) ? c.parcelas() : 1);
        setup.put("installment_type", "merchant");

        Map<String, Object> poi = new LinkedHashMap<>();
        poi.put("visible", true);
        poi.put("devices_serial_number", List.of(c.identificadorDispositivo()));
        poi.put("print_order_receipt", false);
        poi.put("display_name", c.descricao());
        poi.put("payment_setup", setup);

        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("code", c.referencia());
        pedido.put("closed", false);
        pedido.put("customer", Map.of(
                "name", "Consumidor final",
                "email", "consumidor@sysflex.com.br",
                "type", "individual"));
        pedido.put("items", List.of(Map.of(
                "amount", c.valorCentavos(),
                "description", c.descricao(),
                "quantity", 1,
                "code", c.referencia())));
        pedido.put("poi_payment_settings", poi);
        return pedido;
    }

    @Override
    public RetornoProvedor consultar(String idExterno, String dispositivo, String secretKey) {
        Resposta r = http.enviar("GET", BASE + "/orders/" + idExterno, headers(secretKey, null), null);
        exigirSucesso(r, "consultar o pedido");
        return interpretar(idExterno, r.json());
    }

    /** Separado para teste: traduz o pedido da Stone para o nosso status. */
    static RetornoProvedor interpretar(String idExterno, JsonNode pedido) {
        String bruto = pedido.toString();
        JsonNode charges = pedido.path("charges");
        JsonNode transacao = charges.isArray() && !charges.isEmpty()
                ? charges.get(charges.size() - 1).path("last_transaction")
                : pedido.path("__sem_transacao__");

        String nsu = textoOuNull(transacao.path("acquirer_nsu"));
        String autorizacao = textoOuNull(transacao.path("acquirer_auth_code"));
        String bandeira = textoOuNull(transacao.path("card").path("brand"));

        return switch (pedido.path("status").asText("")) {
            case "paid" -> new RetornoProvedor(StatusTransacaoTerminal.APROVADA, idExterno,
                    nsu, autorizacao, bandeira, "Aprovado", bruto);
            case "canceled" -> new RetornoProvedor(StatusTransacaoTerminal.CANCELADA, idExterno,
                    nsu, null, bandeira, "Pedido cancelado", bruto);
            case "failed" -> new RetornoProvedor(StatusTransacaoTerminal.NEGADA, idExterno,
                    nsu, null, bandeira, "Negado: " + transacao.path("acquirer_message").asText("recusado"), bruto);
            default -> RetornoProvedor.pendente(idExterno, bruto);
        };
    }

    @Override
    public void cancelar(String idExterno, String dispositivo, String secretKey) {
        Resposta r = http.enviar("PATCH", BASE + "/orders/" + idExterno + "/closed",
                headers(secretKey, null), Map.of("status", "canceled"));
        if (!r.sucesso()) {
            throw new BusinessException("A Stone não permitiu cancelar o pedido. Cancele direto na maquininha.");
        }
    }

    @Override
    public TesteConexao testarConexao(String secretKey) {
        Resposta r = http.enviar("GET", BASE + "/orders?size=1", headers(secretKey, null), null);
        if (!r.sucesso()) {
            return new TesteConexao(false, "Stone recusou a Secret Key (HTTP " + r.status() + ").");
        }
        String ambiente = secretKey.startsWith("sk_test_") ? "sandbox" : "produção";
        return new TesteConexao(true, "Secret Key válida (" + ambiente + ").");
    }

    private static Map<String, String> headers(String secretKey, String idempotencia) {
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Basic " + basic);
        if (idempotencia != null) h.put("Idempotency-Key", idempotencia);
        return h;
    }

    private static void exigirSucesso(Resposta r, String acao) {
        if (r.sucesso() && r.json() != null) return;
        String detalhe = r.json() != null ? r.json().path("message").asText(r.corpo()) : r.corpo();
        throw new BusinessException("Stone: falha ao " + acao + " (HTTP " + r.status() + "): " + detalhe);
    }

    private static String textoOuNull(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() || n.asText().isBlank() ? null : n.asText();
    }
}
