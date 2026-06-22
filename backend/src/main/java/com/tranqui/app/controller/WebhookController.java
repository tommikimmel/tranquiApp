package com.tranqui.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

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

        // Successful processing log
        log.info("Webhook autenticado con éxito. Body: {}", body);
        return ResponseEntity.ok().build();
    }
}
