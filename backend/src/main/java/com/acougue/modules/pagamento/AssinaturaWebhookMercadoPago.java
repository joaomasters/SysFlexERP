package com.acougue.modules.pagamento;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida o header x-signature dos webhooks do Mercado Pago.
 *
 *   x-signature: ts=1704908010,v1=<hmac hex>
 *   manifesto:   id:<data.id>;request-id:<x-request-id>;ts:<ts>;
 *   v1 = HMAC-SHA256(manifesto, assinatura secreta do webhook)
 *
 * A "assinatura secreta" é gerada no painel do MP (Suas integrações →
 * Webhooks) e cadastrada na tela Maquininhas → Mercado Pago.
 */
public final class AssinaturaWebhookMercadoPago {

    private AssinaturaWebhookMercadoPago() {}

    public static boolean valida(String xSignature, String xRequestId, String dataId, String segredo) {
        if (xSignature == null || segredo == null) return false;
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("ts".equals(kv[0])) ts = kv[1];
            if ("v1".equals(kv[0])) v1 = kv[1];
        }
        if (ts == null || v1 == null) return false;

        StringBuilder manifesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // O MP pede o id em minúsculas quando é alfanumérico
            manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifesto.append("request-id:").append(xRequestId).append(';');
        }
        manifesto.append("ts:").append(ts).append(';');

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = HexFormat.of().formatHex(mac.doFinal(manifesto.toString().getBytes(StandardCharsets.UTF_8)));
            // comparação em tempo constante
            return MessageDigest.isEqual(
                    esperado.getBytes(StandardCharsets.UTF_8),
                    v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
