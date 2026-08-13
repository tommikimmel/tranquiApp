package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.util.EncryptionUtil;
import com.tranqui.app.service.MercadoPagoOAuthService;
import com.tranqui.app.service.MercadoPagoWebhookValidator;
import com.tranqui.app.service.PagoWebhookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private PagoWebhookHandler pagoWebhookHandler;

    @Autowired
    private MercadoPagoWebhookValidator webhookValidator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private MercadoPagoOAuthService oauthService;

    @PostMapping("/webhook")
    public ResponseEntity<?> receiveWebhook(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestBody String body) {

        log.info("Recibido Webhook de Mercado Pago. Firma: {}", signature);

        if (!webhookValidator.isValid(signature, requestId, dataId)) {
            log.warn("Firma de Webhook de Mercado Pago no válida. Rechazando petición.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Firma inválida");
        }

        log.info("Webhook autenticado con éxito. Body: {}", body);

        try {
            JsonNode node = objectMapper.readTree(body);
            String externalReference = null;
            String transactionId = null;
            String status = null;

            // Caso 1: Es un webhook real de Mercado Pago (tipo = payment)
            if (node.has("type") && "payment".equals(node.get("type").asText()) 
                    && node.has("data") && node.get("data").has("id")) {
                
                String paymentIdStr = node.get("data").get("id").asText();
                Long paymentId = Long.parseLong(paymentIdStr);
                String mpUserId = node.has("user_id") ? node.get("user_id").asText() : null;

                log.info("Procesando webhook real de Mercado Pago. Payment ID: {}, User ID (Seller): {}", paymentId, mpUserId);

                if (mpUserId != null) {
                    Usuario medico = usuarioRepository.findByMpUserId(mpUserId).orElse(null);
                    if (medico != null) {
                        String accessToken = oauthService.obtenerAccessTokenValido(medico);

                        PaymentClient paymentClient = new PaymentClient();
                        MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
                        Payment payment = paymentClient.get(paymentId, options);

                        externalReference = payment.getExternalReference();
                        transactionId = String.valueOf(payment.getId());
                        status = payment.getStatus();
                        log.info("Obtenidos detalles de pago real de Mercado Pago. Ref: {}, Status: {}", externalReference, status);
                    } else {
                        log.error("Médico con mp_user_id {} no encontrado en la base de datos.", mpUserId);
                    }
                } else {
                    log.error("El webhook real no contiene 'user_id' para identificar al médico.");
                }
            }
            // Caso 2: Es un mock webhook del simulador local
            else if (node.has("external_reference")) {
                externalReference = node.get("external_reference").asText();
                transactionId = node.has("transaction_id") ? node.get("transaction_id").asText() : "mock-tx-123";
                status = "approved"; // El simulador local siempre aprueba
                log.info("Procesando webhook simulado localmente. Ref: {}", externalReference);
            }

            // Si pudimos obtener la referencia externa y el estado es aprobado, procesamos
            if (externalReference != null && "approved".equals(status)) {
                if (externalReference.startsWith("doc-")) {
                    Long solicitudId = Long.parseLong(externalReference.substring(4));
                    pagoWebhookHandler.procesarAprobacionConcepto(solicitudId, transactionId);
                } else {
                    try {
                        Long turnoId = Long.parseLong(externalReference);
                        pagoWebhookHandler.procesarAprobacionTurno(turnoId, transactionId);
                    } catch (NumberFormatException e) {
                        log.error("Referencia externa de webhook no válida: {}", externalReference);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error al procesar el webhook de pago", e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Fallback reconciliation for when Mercado Pago's async webhook is delayed or never arrives
     * at all — real-world webhook delivery isn't guaranteed, and relying on it exclusively used
     * to leave a paid turno stuck showing "pending" in "Mis Turnos" forever, with nothing to ever
     * re-check it. Public/unauthenticated like /turnos/{id}/abandonar-pago, since booking doesn't
     * require a logged-in session — safe because the payment is re-verified against Mercado
     * Pago's own API (using the médico's token) rather than trusted from the client, and the
     * fetched payment's externalReference must match the turno being confirmed.
     *
     * Two ways to reach it:
     *  - With paymentId: called right after the patient is redirected back from Checkout Pro,
     *    using the payment_id Mercado Pago appends to the return URL — a direct, cheap lookup.
     *  - Without paymentId: called for a turno that's ALREADY sitting as "pending" (e.g. every
     *    time the patient opens "Mis Turnos") — searches Mercado Pago by external_reference
     *    instead, since there's no known payment_id to look up directly. This is what lets a
     *    turno that got stuck pending before this reconciliation existed (or because the return
     *    redirect never happened — tab closed, connection dropped) self-heal on its own.
     */
    @PostMapping("/verificar")
    public ResponseEntity<?> verificarPago(
            @RequestParam String externalReference,
            @RequestParam(required = false) Long paymentId) {
        try {
            Long turnoId = Long.parseLong(externalReference);
            Turno turno = turnoRepository.findById(turnoId).orElse(null);
            if (turno == null) {
                return ResponseEntity.notFound().build();
            }
            if (turno.getEstado() == com.tranqui.app.model.EstadoTurno.CONFIRMADO) {
                return ResponseEntity.ok().build();
            }

            Usuario medico = turno.getMedico();
            String accessToken = oauthService.obtenerAccessTokenValido(medico);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("No se pudo verificar el pago.");
            }

            MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
            Payment payment = (paymentId != null)
                    ? new PaymentClient().get(paymentId, options)
                    : buscarPagoAprobadoPorExternalReference(externalReference, options);

            if (payment == null || !externalReference.equals(payment.getExternalReference())) {
                log.warn("Verificación manual de pago: no se encontró un pago de Mercado Pago que corresponda al turno {}.", turnoId);
                return ResponseEntity.status(HttpStatus.CONFLICT).body("El pago no corresponde a este turno.");
            }
            if ("approved".equals(payment.getStatus())) {
                pagoWebhookHandler.procesarAprobacionTurno(turnoId, String.valueOf(payment.getId()));
            }
            return ResponseEntity.ok().build();
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referencia externa inválida.");
        } catch (Exception e) {
            log.error("Error al verificar manualmente el pago (externalReference={}, paymentId={})", externalReference, paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Prefers the most recently created matching payment, and among those an "approved" one if
    // there is one (a patient can retry a failed/rejected attempt and pay successfully on a
    // second try, leaving more than one payment under the same external_reference).
    private Payment buscarPagoAprobadoPorExternalReference(String externalReference, MPRequestOptions options) throws Exception {
        Map<String, Object> filters = new HashMap<>();
        filters.put("external_reference", externalReference);
        filters.put("sort", "date_created");
        filters.put("criteria", "desc");
        MPSearchRequest searchRequest = MPSearchRequest.builder().filters(filters).build();

        MPResultsResourcesPage<Payment> page = new PaymentClient().search(searchRequest, options);
        List<Payment> results = page != null ? page.getResults() : null;
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.stream()
                .filter(p -> "approved".equals(p.getStatus()))
                .findFirst()
                .orElse(results.get(0));
    }
}
