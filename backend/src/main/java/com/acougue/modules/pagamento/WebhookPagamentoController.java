package com.acougue.modules.pagamento;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.modules.pdv.PixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
 */
@Slf4j
@RestController
@RequestMapping("/pagamento/webhook")
@RequiredArgsConstructor
public class WebhookPagamentoController {

    private final TransacaoTerminalService transacaoService;
    private final CredenciaisPagamentoService credenciais;
    private final PixService pixService;

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

    @PostMapping("/stone")
    public ResponseEntity<Void> stone(@RequestBody(required = false) Map<String, Object> corpo) {
        // Evento de pedido: { "type": "order.paid", "data": { "id": "or_..." } }
        if (corpo != null && corpo.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            transacaoService.processarNotificacao(ProvedorPagamento.STONE, data.get("id").toString());
        }
        return ResponseEntity.ok().build();
    }
}
