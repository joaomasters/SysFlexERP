package com.acougue.modules.pagamento;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.modules.pdv.PixService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/*
 !!NÃO anotar com @ExigirPermissao — as adquirentes chamam sem usuário logado
 !!(permitAll em SecurityConfig). A empresa vem do parâmetro ?tenant=<código>,
 !!lido pelo TenantResolvingFilter.

 Segurança: o corpo da notificação nunca é tratado como verdade. Ele só diz
 QUAL cobrança mudou; o status é sempre reconsultado na API da adquirente com
 a credencial da própria empresa. Um webhook forjado, no máximo, provoca uma
 consulta extra.

 URLs para cadastrar nos painéis (troque <codigo> pelo código da empresa):
   Mercado Pago: https://<backend>/api/pagamento/webhook/mercadopago?tenant=<codigo>
   Stone:        https://<backend>/api/pagamento/webhook/stone?tenant=<codigo>
   Veloce:       não precisa cadastrar; a URL vai em cada cobrança (ver VeloceSmartTefProvedor)
 */
@Slf4j
@RestController
@RequestMapping("/pagamento/webhook")
@RequiredArgsConstructor
public class WebhookPagamentoController {

    private final TransacaoTerminalService transacaoService;
    private final CredenciaisPagamentoService credenciais;
    private final PixService pixService;
    private final ObjectMapper objectMapper;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(
            @RequestParam(name = "data.id", required = false) String dataIdQuery,
            @RequestParam(name = "type", required = false) String tipoQuery,
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) Map<String, Object> corpo) {

        Map<String, Object> payload = corpo != null ? corpo : Map.of();
        String dataIdCorpo = payload.get("data") instanceof Map<?, ?> data && data.get("id") != null
                ? data.get("id").toString() : null;
        String dataId = dataIdQuery != null ? dataIdQuery : dataIdCorpo;

        Optional<String> segredo = credenciais.webhookSecret(ProvedorPagamento.MERCADO_PAGO);
        if (segredo.isPresent() && !AssinaturaWebhookMercadoPago.valida(assinatura, requestId, dataId, segredo.get())) {
            log.warn("Webhook Mercado Pago com assinatura inválida (data.id={})", dataId);
            return ResponseEntity.status(401).build();
        }

        String tipo = tipoQuery != null ? tipoQuery : String.valueOf(payload.getOrDefault("type", ""));
        if ("payment".equals(tipo) && dataId != null) {
            pixService.verificarStatus(dataId);
        }

        // Notificações da Point podem trazer o id do payment intent em data.id ou na raiz.
        Set<String> candidatos = new LinkedHashSet<>();
        if (dataId != null) candidatos.add(dataId);
        if (payload.get("id") != null) candidatos.add(payload.get("id").toString());
        candidatos.forEach(id -> transacaoService.processarNotificacao(ProvedorPagamento.MERCADO_PAGO, id));

        return ResponseEntity.ok().build();
    }

    /**
     * Veloce Smart TEF: POST com todos os atributos do pagamento, para a url_retorno enviada em
     * cada cobrança. Aceita JSON ou formulário, porque o manual não diz qual dos dois.
     */
    @PostMapping("/veloce")
    public ResponseEntity<Void> veloce(HttpServletRequest req) {
        Map<String, String> dados = new java.util.HashMap<>();
        req.getParameterMap().forEach((k, v) -> { if (v.length > 0) dados.put(k, v[0]); });
        try {
            String corpo = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!corpo.isBlank() && corpo.trim().startsWith("{")) {
                JsonNode n = objectMapper.readTree(corpo);
                JsonNode p = n.has("result") ? n.path("result") : n;
                p.fields().forEachRemaining(e -> dados.put(e.getKey(), e.getValue().asText()));
            }
        } catch (Exception e) {
            log.warn("Webhook Veloce com corpo ilegível: {}", e.getMessage());
        }
        String idTabela = dados.get("id_tbl_pagamento");
        String pagamentoId = dados.get("pagamento_id");
        if (idTabela != null && pagamentoId != null) {
            transacaoService.processarNotificacao(ProvedorPagamento.VELOCE, idTabela + ":" + pagamentoId);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stone")
    public ResponseEntity<Void> stone(@RequestBody(required = false) Map<String, Object> corpo) {
        // Evento de pedido: { "type": "order.paid", "data": { "id": "or_..." } }
        if (corpo != null && corpo.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            transacaoService.processarNotificacao(ProvedorPagamento.STONE, data.get("id").toString());
        }
        return ResponseEntity.ok().build();
    }
}
