package com.tranqui.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;

/**
 * Validates the authenticity of incoming Mercado Pago webhook calls.
 *
 * When mercadopago.enabled=false (local/dev/tests) we keep the old lenient
 * check so the built-in "simulate webhook" dev tool and existing tests keep
 * working without a real signed request. When enabled=true (a real
 * integration is configured) we enforce Mercado Pago's real HMAC-SHA256
 * signature scheme, since real money is at stake.
 *
 * Docs: https://www.mercadopago.com.ar/developers/es/docs/your-integrations/notifications/webhooks#editor_5
 */
@Service
public class MercadoPagoWebhookValidator {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookValidator.class);

    @Value("${mercadopago.enabled:false}")
    private boolean isEnabled;

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        if (!isEnabled) {
            // Legacy/dev behavior: accept the mock-webhook simulator and only reject requests
            // explicitly marked as invalid in tests/tooling. The "test-signature" bypass must
            // never be reachable once a real integration is configured (isEnabled=true) —
            // otherwise anyone could mark any turno as paid for free via the public webhook.
            if (xSignature != null && xSignature.equals("test-signature")) {
                log.info("Simulación de webhook detectada (test-signature). Omitiendo validación.");
                return true;
            }
            return xSignature != null && !xSignature.equals("invalid-signature") && !xSignature.contains("fake");
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("MERCADOPAGO_WEBHOOK_SECRET no está configurado; rechazando webhook por seguridad.");
            return false;
        }
        if (xSignature == null || xRequestId == null || dataId == null) {
            log.warn("Webhook de Mercado Pago sin los headers/parámetros requeridos para validar la firma.");
            return false;
        }

        Map<String, String> parts = parseSignatureHeader(xSignature);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) {
            return false;
        }

        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        String expected = hmacHex(manifest, webhookSecret);
        return constantTimeEquals(expected, v1);
    }

    private Map<String, String> parseSignatureHeader(String header) {
        Map<String, String> result = new HashMap<>();
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }

    private String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la firma del webhook", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
