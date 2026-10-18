package com.acougue.modules.pagamento.provedor;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.ClienteHttpPagamento;
import com.acougue.modules.pagamento.ClienteHttpPagamento.Resposta;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mercado Pago Point (maquininhas Point Smart / Point Pro) via
 * "Point Integration API" — payment intents.
 *
 * Pré-requisitos (feitos uma vez, ver docs/MAQUININHAS.md):
 *  1. Access Token da conta (Suas integrações → Credenciais de produção).
 *  2. Maquininha vinculada à conta e em modo PDV (a tela de Maquininhas tem
 *     o botão "Ativar modo PDV", que chama ativarModoPdv()).
 *
 * Fluxo: cria o intent → a maquininha "acorda" pedindo o cartão → consultamos
 * o intent até FINISHED/CANCELED/ERROR → no FINISHED buscamos o pagamento
 * (/v1/payments) para saber se foi aprovado e pegar autorização/bandeira.
 */
@Component
@RequiredArgsConstructor
public class MercadoPagoPointProvedor implements ProvedorTerminalNuvem {

    static final String BASE = "https://api.mercadopago.com";

    private final ClienteHttpPagamento http;

    @Override
    public ProvedorPagamento provedor() {
        return ProvedorPagamento.MERCADO_PAGO;
    }

    @Override
    public RetornoProvedor iniciar(CobrancaTerminal c, String token) {
        Map<String, Object> pagamento = new LinkedHashMap<>();
        if ("CREDITO".equals(c.tipo())) {
            pagamento.put("type", "credit_card");
            pagamento.put("installments", c.parcelas());
            pagamento.put("installments_cost", "seller");
        } else {
            pagamento.put("type", "debit_card");
        }

        Map<String, Object> corpo = Map.of(
                "amount", c.valorCentavos(),
                "description", c.descricao(),
                "additional_info", Map.of(
                        "external_reference", c.referencia(),
                        "print_on_terminal", true),
                "payment", pagamento);

        Resposta r = http.enviar("POST",
                BASE + "/point/integration-api/devices/" + c.identificadorDispositivo() + "/payment-intents",
                headers(token, c.referencia()), corpo);

        if (r.status() == 409) {
            throw new BusinessException(
                    "A maquininha já tem uma cobrança pendente. Finalize ou cancele na própria maquininha e tente de novo.");
        }
        exigirSucesso(r, "criar a cobrança na maquininha");
        return RetornoProvedor.pendente(r.json().path("id").asText(), r.corpo());
    }

    @Override
    public RetornoProvedor consultar(String idExterno, String dispositivo, String token) {
        Resposta intent = http.enviar("GET",
                BASE + "/point/integration-api/payment-intents/" + idExterno, headers(token, null), null);
        exigirSucesso(intent, "consultar a cobrança");

        String pagamentoId = intent.json().path("payment").path("id").asText("");
        JsonNode pagamento = null;
        if (!pagamentoId.isBlank()) {
            Resposta p = http.enviar("GET", BASE + "/v1/payments/" + pagamentoId, headers(token, null), null);
            if (p.sucesso()) pagamento = p.json();
        }
        return interpretar(idExterno, intent.json(), pagamento);
    }

    /** Separado para teste: traduz intent + pagamento do MP para o nosso status. */
    static RetornoProvedor interpretar(String idExterno, JsonNode intent, JsonNode pagamento) {
        String bruto = pagamento != null ? pagamento.toString() : intent.toString();

        if (pagamento != null) {
            String status = pagamento.path("status").asText("");
            String nsu = pagamento.path("id").asText(null);
            String autorizacao = textoOuNull(pagamento.path("authorization_code"));
            String bandeira = textoOuNull(pagamento.path("payment_method_id"));
            switch (status) {
                case "approved":
                    return new RetornoProvedor(StatusTransacaoTerminal.APROVADA, idExterno,
                            nsu, autorizacao, bandeira, "Aprovado", bruto);
                case "rejected":
                    return new RetornoProvedor(StatusTransacaoTerminal.NEGADA, idExterno,
                            nsu, null, bandeira, "Negado: " + pagamento.path("status_detail").asText("recusado"), bruto);
                case "cancelled", "refunded", "charged_back":
                    return new RetornoProvedor(StatusTransacaoTerminal.CANCELADA, idExterno,
                            nsu, null, bandeira, "Pagamento " + status, bruto);
                default:
                    break; // pending / in_process: continua olhando o intent
            }
        }

        String estado = intent.path("state").asText("");
        return switch (estado) {
            case "CANCELED", "ABANDONED" -> new RetornoProvedor(StatusTransacaoTerminal.CANCELADA, idExterno,
                    null, null, null, "Cobrança cancelada na maquininha", bruto);
            case "ERROR" -> new RetornoProvedor(StatusTransacaoTerminal.ERRO, idExterno,
                    null, null, null, "A maquininha reportou erro na cobrança", bruto);
            default -> RetornoProvedor.pendente(idExterno, bruto); // OPEN, ON_TERMINAL, PROCESSING, PROCESSED, FINISHED sem pagamento ainda
        };
    }

    @Override
    public void cancelar(String idExterno, String dispositivo, String token) {
        Resposta r = http.enviar("DELETE",
                BASE + "/point/integration-api/devices/" + dispositivo + "/payment-intents/" + idExterno,
                headers(token, null), null);
        if (!r.sucesso()) {
            throw new BusinessException(
                    "O Mercado Pago não permitiu cancelar (a cobrança provavelmente já está na maquininha). " +
                    "Cancele direto na maquininha.");
        }
    }

    @Override
    public TesteConexao testarConexao(String token) {
        Resposta r = http.enviar("GET", BASE + "/users/me", headers(token, null), null);
        if (!r.sucesso()) {
            return new TesteConexao(false, "Mercado Pago recusou o Access Token (HTTP " + r.status() + ").");
        }
        return new TesteConexao(true, "Conectado à conta " + r.json().path("nickname").asText("?")
                + " (id " + r.json().path("id").asText("?") + ").");
    }

    @Override
    public List<DispositivoRemoto> listarDispositivos(String token) {
        Resposta r = http.enviar("GET", BASE + "/point/integration-api/devices?limit=50", headers(token, null), null);
        exigirSucesso(r, "listar as maquininhas");
        List<DispositivoRemoto> lista = new ArrayList<>();
        for (JsonNode d : r.json().path("devices")) {
            lista.add(new DispositivoRemoto(
                    d.path("id").asText(),
                    d.path("operating_mode").asText(""),
                    "POS " + d.path("pos_id").asText("-") + " · loja " + d.path("store_id").asText("-")));
        }
        return lista;
    }

    @Override
    public void ativarModoPdv(String dispositivo, String token) {
        Resposta r = http.enviar("PATCH", BASE + "/point/integration-api/devices/" + dispositivo,
                headers(token, null), Map.of("operating_mode", "PDV"));
        exigirSucesso(r, "ativar o modo PDV");
    }

    private static Map<String, String> headers(String token, String idempotencia) {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token);
        if (idempotencia != null) h.put("X-Idempotency-Key", idempotencia);
        return h;
    }

    private static void exigirSucesso(Resposta r, String acao) {
        if (r.sucesso() && r.json() != null) return;
        String detalhe = r.json() != null ? r.json().path("message").asText(r.corpo()) : r.corpo();
        throw new BusinessException("Mercado Pago: falha ao " + acao + " (HTTP " + r.status() + "): " + detalhe);
    }

    private static String textoOuNull(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() || n.asText().isBlank() ? null : n.asText();
    }
}
