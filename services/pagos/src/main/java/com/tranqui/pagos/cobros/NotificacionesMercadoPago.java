package com.tranqui.pagos.cobros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.interno.ClienteInterno;
import com.tranqui.pagos.mp.MercadoPagoAdminApi;
import com.tranqui.pagos.mp.MercadoPagoApi;
import com.tranqui.pagos.mp.Vinculacion;
import com.tranqui.pagos.suscripciones.SuscripcionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * El webhook de Mercado Pago, ya autenticado. Mercado Pago avisa todo a la misma URL: los pagos de turnos y
 * documentos (cobrados con la cuenta de cada profesional) se procesan acá; las suscripciones (cobradas con la
 * cuenta de la plataforma) se procesan localmente cuando {@code pagos.suscripciones.habilitada=true}, y se
 * reenvían al monolito cuando está apagado (vuelta atrás).
 *
 * <p>Cómo se reconoce cada aviso, igual que el webhook del monolito:
 * <ul>
 *   <li>{@code subscription_preapproval} y {@code subscription_authorized_payment}: suscripciones.</li>
 *   <li>{@code payment} con el {@code user_id} de un profesional vinculado: un cobro de turnos/documentos.
 *       Con otro {@code user_id} (la plataforma): un débito de suscripción.</li>
 *   <li>Con {@code external_reference} en el cuerpo: el simulador de local ({@code sub-*} es de suscripción).</li>
 * </ul>
 */
@Service
public class NotificacionesMercadoPago {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesMercadoPago.class);

    /** Qué se hizo con el aviso; si no se pudo procesar, el controller responde 500 y Mercado Pago reintenta. */
    public enum Resultado { PROCESADO, REENVIADO, IGNORADO }

    private final ObjectMapper mapper = new ObjectMapper();
    private final CuentasMp cuentas;
    private final Vinculacion vinculacion;
    private final MercadoPagoApi mercadoPago;
    private final Aprobaciones aprobaciones;
    private final ClienteInterno interno;
    private final SuscripcionService suscripcionService;
    private final MercadoPagoAdminApi mercadoPagoAdminApi;
    private final boolean suscripcionesHabilitada;

    public NotificacionesMercadoPago(
            CuentasMp cuentas,
            Vinculacion vinculacion,
            MercadoPagoApi mercadoPago,
            Aprobaciones aprobaciones,
            ClienteInterno interno,
            SuscripcionService suscripcionService,
            MercadoPagoAdminApi mercadoPagoAdminApi,
            @Value("${pagos.suscripciones.habilitada:false}") boolean suscripcionesHabilitada
    ) {
        this.cuentas = cuentas;
        this.vinculacion = vinculacion;
        this.mercadoPago = mercadoPago;
        this.aprobaciones = aprobaciones;
        this.interno = interno;
        this.suscripcionService = suscripcionService;
        this.mercadoPagoAdminApi = mercadoPagoAdminApi;
        this.suscripcionesHabilitada = suscripcionesHabilitada;
    }

    public Resultado procesar(String cuerpo, String dataId) throws Exception {
        JsonNode nodo = mapper.readTree(cuerpo);
        String tipo = nodo.has("type") ? nodo.get("type").asText() : (nodo.has("topic") ? nodo.get("topic").asText() : "");
        String accion = nodo.has("action") ? nodo.get("action").asText() : "";

        if (tipo.startsWith("subscription_") || accion.startsWith("subscription_")) {
            if (!suscripcionesHabilitada) {
                interno.reenviarSuscripcion(cuerpo, dataId);
                return Resultado.REENVIADO;
            }

            if ("subscription_preapproval".equals(tipo) || accion.startsWith("subscription_preapproval")) {
                String preapprovalId = nodo.has("data") && nodo.get("data").has("id") ? nodo.get("data").get("id").asText() : dataId;
                String subStatus = nodo.has("status") ? nodo.get("status").asText() : null;

                if (subStatus == null && preapprovalId != null && !preapprovalId.isBlank()) {
                    try {
                        var mpSub = mercadoPagoAdminApi.obtenerPreapproval(preapprovalId);
                        if (mpSub != null && mpSub.status() != null) {
                            subStatus = mpSub.status();
                        }
                    } catch (Exception e) {
                        log.warn("No se pudo obtener estado de preapproval {} desde MP: {}", preapprovalId, e.getMessage());
                    }
                }
                if (subStatus == null) subStatus = "authorized";

                log.info("Procesando webhook subscription_preapproval: id={}, status={}", preapprovalId, subStatus);
                suscripcionService.processMercadoPagoPreapprovalWebhook(preapprovalId, subStatus);
                return Resultado.PROCESADO;
            }

            if ("subscription_authorized_payment".equals(tipo) || accion.startsWith("subscription_authorized_payment")) {
                String subPaymentId = nodo.has("data") && nodo.get("data").has("id") ? nodo.get("data").get("id").asText() : dataId;
                String preapprovalId = nodo.has("preapproval_id") ? nodo.get("preapproval_id").asText() : null;
                BigDecimal amount = nodo.has("transaction_amount") ? new BigDecimal(nodo.get("transaction_amount").asText()) : null;
                String payStatus = nodo.has("status") ? nodo.get("status").asText() : null;

                if (subPaymentId != null && !subPaymentId.isBlank()) {
                    try {
                        var detalle = mercadoPagoAdminApi.obtenerDetalleAuthorizedPayment(subPaymentId);
                        if (detalle != null) {
                            if (detalle.has("payment") && detalle.get("payment").hasNonNull("id")) {
                                subPaymentId = detalle.get("payment").get("id").asText();
                            }
                            if ((preapprovalId == null || preapprovalId.isBlank()) && detalle.has("preapproval_id")) {
                                preapprovalId = detalle.get("preapproval_id").asText();
                            }
                            if (amount == null && detalle.has("transaction_amount")) {
                                amount = new BigDecimal(detalle.get("transaction_amount").asText());
                            }
                            if (payStatus == null && detalle.has("status")) {
                                payStatus = detalle.get("status").asText();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("No se pudo consultar detalle de authorized_payment {} en MP: {}", subPaymentId, e.getMessage());
                    }
                }
                if (payStatus == null) payStatus = "approved";
                if (preapprovalId == null) preapprovalId = "";

                log.info("Procesando webhook subscription_authorized_payment: id={}, preapprovalId={}, status={}, amount={}",
                        subPaymentId, preapprovalId, payStatus, amount);
                suscripcionService.processMercadoPagoPaymentWebhook(subPaymentId, preapprovalId, amount, payStatus);
                return Resultado.PROCESADO;
            }
        }

        if ("payment".equals(tipo) && nodo.has("data") && nodo.get("data").has("id")) {
            long paymentId = Long.parseLong(nodo.get("data").get("id").asText());
            String mpUserId = nodo.has("user_id") ? nodo.get("user_id").asText() : null;
            Optional<CuentasMp.Cuenta> cuenta = mpUserId != null ? cuentas.porMpUserId(mpUserId) : Optional.empty();
            if (cuenta.isEmpty()) {
                // No es de un profesional: lo cobró la plataforma (un débito de suscripción).
                if (!suscripcionesHabilitada) {
                    interno.reenviarSuscripcion(cuerpo, dataId);
                    return Resultado.REENVIADO;
                }

                MercadoPagoAdminApi.PagoAdminMp pagoSuscripcion = null;
                try {
                    pagoSuscripcion = mercadoPagoAdminApi.obtenerPagoAdmin(paymentId);
                } catch (Exception e) {
                    log.debug("Payment {} no es legible con token admin: {}", paymentId, e.getMessage());
                }

                if (pagoSuscripcion != null && pagoSuscripcion.externalReference() != null
                        && pagoSuscripcion.externalReference().startsWith("sub-")) {
                    log.info("Webhook 'payment' identificado como cobro de suscripción. Ref: {}, Status: {}",
                            pagoSuscripcion.externalReference(), pagoSuscripcion.status());
                    suscripcionService.processMercadoPagoPaymentByExternalReference(
                            pagoSuscripcion.externalReference(),
                            String.valueOf(pagoSuscripcion.id()),
                            pagoSuscripcion.transactionAmount(),
                            pagoSuscripcion.status());
                    return Resultado.PROCESADO;
                }

                log.warn("Notificación de pago {} sin cuenta asociada ni referencia sub-*; se ignora.", paymentId);
                return Resultado.IGNORADO;
            }
            String token = vinculacion.accessTokenValido(cuenta.get().profesionalId()).orElseThrow();
            MercadoPagoApi.PagoMp pago = mercadoPago.obtenerPago(token, paymentId);
            log.info("Webhook de Mercado Pago: pago {} de {} en estado {}", pago.id(), pago.referencia(), pago.estado());
            aprobaciones.aplicar(pago.referencia(), String.valueOf(pago.id()), pago.estado(), pago.monto());
            return Resultado.PROCESADO;
        }

        if (nodo.has("external_reference")) {
            String referencia = nodo.get("external_reference").asText();
            if (referencia.startsWith("sub-")) {
                if (!suscripcionesHabilitada) {
                    interno.reenviarSuscripcion(cuerpo, dataId);
                    return Resultado.REENVIADO;
                }

                String transactionId = nodo.has("transaction_id") ? nodo.get("transaction_id").asText() : "mock-tx-123";
                BigDecimal amount = nodo.has("amount") ? new BigDecimal(nodo.get("amount").asText()) : null;
                String status = nodo.has("status") ? nodo.get("status").asText() : "approved";

                log.info("Webhook simulado de suscripción para {}", referencia);
                suscripcionService.processMercadoPagoPaymentByExternalReference(referencia, transactionId, amount, status);
                return Resultado.PROCESADO;
            }
            // El simulador de local siempre aprueba pagos de turnos/documentos.
            String transactionId = nodo.has("transaction_id") ? nodo.get("transaction_id").asText() : "mock-tx-123";
            log.info("Webhook simulado de local para {}", referencia);
            aprobaciones.aplicar(referencia, transactionId, "approved", null);
            return Resultado.PROCESADO;
        }

        log.warn("Webhook de Mercado Pago que no se reconoce; se ignora: {}", cuerpo);
        return Resultado.IGNORADO;
    }
}
