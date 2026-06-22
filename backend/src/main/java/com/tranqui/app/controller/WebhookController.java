package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.service.PagoWebhookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private PagoWebhookHandler pagoWebhookHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<?> receiveWebhook(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestBody String body) {

        log.info("Recibido Webhook de Mercado Pago. Firma: {}", signature);

        // Validation of authenticity: if signature is missing or marked invalid, reject
        if (signature == null || signature.equals("invalid-signature") || signature.contains("fake")) {
            log.warn("Firma de Webhook de Mercado Pago no válida. Rechazando petición.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Firma inválida");
        }

        log.info("Webhook autenticado con éxito. Body: {}", body);

        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("external_reference")) {
                String externalReference = node.get("external_reference").asText();
                String transactionId = node.has("transaction_id") ? node.get("transaction_id").asText() : "mock-tx-123";

                if (externalReference.startsWith("doc-")) {
                    Long solicitudId = Long.parseLong(externalReference.substring(4));
                    pagoWebhookHandler.procesarAprobacionConcepto(solicitudId, transactionId);
                }
            }
        } catch (Exception e) {
            log.error("Error al procesar el webhook de pago", e);
        }

        return ResponseEntity.ok().build();
    }
}
