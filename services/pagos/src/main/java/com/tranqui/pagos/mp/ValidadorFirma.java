package com.tranqui.pagos.mp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Firma del webhook de Mercado Pago, con las mismas reglas que MercadoPagoWebhookValidator del monolito:
 * con {@code mercadopago.enabled=true} exige el HMAC-SHA256 real; en local acepta el simulador
 * ({@code x-signature: test-signature}), y {@code payment.simulation.enabled} solo habilita ese literal.
 *
 * <p>Docs: https://www.mercadopago.com.ar/developers/es/docs/your-integrations/notifications/webhooks
 */
@Component
public class ValidadorFirma {

    private static final Logger log = LoggerFactory.getLogger(ValidadorFirma.class);

    private final boolean habilitado;
    private final String secreto;
    private final boolean simulacion;

    public ValidadorFirma(@Value("${mercadopago.enabled:false}") boolean habilitado,
                          @Value("${mercadopago.webhook-secret:}") String secreto,
                          @Value("${payment.simulation.enabled:false}") boolean simulacion) {
        this.habilitado = habilitado;
        this.secreto = secreto;
        this.simulacion = simulacion;
    }

    public boolean esValida(String xSignature, String xRequestId, String dataId) {
        if (simulacion && "test-signature".equals(xSignature)) {
            log.info("Simulación de webhook aceptada (payment.simulation.enabled=true).");
            return true;
        }
        if (!habilitado) {
            // Local: el simulador y los tests; solo se rechaza lo marcado como inválido.
            return xSignature != null && !xSignature.equals("invalid-signature") && !xSignature.contains("fake");
        }
        if (secreto == null || secreto.isBlank()) {
            log.error("MERCADOPAGO_WEBHOOK_SECRET no está configurado; se rechaza el webhook.");
            return false;
        }
        if (xSignature == null || xRequestId == null || dataId == null) {
            log.warn("Webhook de Mercado Pago sin los encabezados o parámetros para validar la firma.");
            return false;
        }
        Map<String, String> partes = new HashMap<>();
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length == 2) {
                partes.put(kv[0].trim(), kv[1].trim());
            }
        }
        String ts = partes.get("ts");
        String v1 = partes.get("v1");
        if (ts == null || v1 == null) {
            return false;
        }
        String manifiesto = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        return igualesEnTiempoConstante(hmac(manifiesto), v1);
    }

    private String hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la firma del webhook", e);
        }
    }

    private static boolean igualesEnTiempoConstante(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int resultado = 0;
        for (int i = 0; i < a.length(); i++) {
            resultado |= a.charAt(i) ^ b.charAt(i);
        }
        return resultado == 0;
    }
}
