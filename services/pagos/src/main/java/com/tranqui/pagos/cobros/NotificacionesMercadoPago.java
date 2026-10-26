package com.tranqui.pagos.cobros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.interno.ClienteInterno;
import com.tranqui.pagos.mp.MercadoPagoApi;
import com.tranqui.pagos.mp.Vinculacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * El webhook de Mercado Pago, ya autenticado. Mercado Pago avisa todo a la misma URL: los pagos de turnos y
 * documentos (cobrados con la cuenta de cada profesional) se procesan acá; las suscripciones (cobradas con la
 * cuenta de la plataforma) se le reenvían al monolito, que las atiende hasta la Fase 8 (decisión 2).
 *
 * <p>Cómo se reconoce cada aviso, igual que el webhook del monolito:
 * <ul>
 *   <li>{@code subscription_preapproval} y {@code subscription_authorized_payment}: suscripciones.</li>
 *   <li>{@code payment} con el {@code user_id} de un profesional vinculado: un cobro de acá. Con otro
 *       {@code user_id} (la plataforma) es un débito de suscripción.</li>
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

    public NotificacionesMercadoPago(CuentasMp cuentas, Vinculacion vinculacion, MercadoPagoApi mercadoPago,
                                     Aprobaciones aprobaciones, ClienteInterno interno) {
        this.cuentas = cuentas;
        this.vinculacion = vinculacion;
        this.mercadoPago = mercadoPago;
        this.aprobaciones = aprobaciones;
        this.interno = interno;
    }

    public Resultado procesar(String cuerpo, String dataId) throws Exception {
        JsonNode nodo = mapper.readTree(cuerpo);
        String tipo = nodo.has("type") ? nodo.get("type").asText() : (nodo.has("topic") ? nodo.get("topic").asText() : "");
        String accion = nodo.has("action") ? nodo.get("action").asText() : "";

        if (tipo.startsWith("subscription_") || accion.startsWith("subscription_")) {
            interno.reenviarSuscripcion(cuerpo, dataId);
            return Resultado.REENVIADO;
        }

        if ("payment".equals(tipo) && nodo.has("data") && nodo.get("data").has("id")) {
            long paymentId = Long.parseLong(nodo.get("data").get("id").asText());
            String mpUserId = nodo.has("user_id") ? nodo.get("user_id").asText() : null;
            Optional<CuentasMp.Cuenta> cuenta = mpUserId != null ? cuentas.porMpUserId(mpUserId) : Optional.empty();
            if (cuenta.isEmpty()) {
                // No es de un profesional: lo cobró la plataforma (un débito de suscripción).
                interno.reenviarSuscripcion(cuerpo, dataId);
                return Resultado.REENVIADO;
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
                interno.reenviarSuscripcion(cuerpo, dataId);
                return Resultado.REENVIADO;
            }
            // El simulador de local siempre aprueba.
            String transactionId = nodo.has("transaction_id") ? nodo.get("transaction_id").asText() : "mock-tx-123";
            log.info("Webhook simulado de local para {}", referencia);
            aprobaciones.aplicar(referencia, transactionId, "approved", null);
            return Resultado.PROCESADO;
        }

        log.warn("Webhook de Mercado Pago que no se reconoce; se ignora: {}", cuerpo);
        return Resultado.IGNORADO;
    }
}
