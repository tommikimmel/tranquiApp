package com.tranqui.pagos.mp;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Operaciones de Mercado Pago con la cuenta de la plataforma (administrador):
 * creación y cancelación de preapprovals (suscripciones recurrentes) y consulta
 * de pagos recurrentes cobrados con {@code MP_ADMIN_ACCESS_TOKEN}.
 */
public interface MercadoPagoAdminApi {

    record PreapprovalMp(String id, String initPoint, String status) {
        public PreapprovalMp(String id, String initPoint) {
            this(id, initPoint, null);
        }
    }

    record PagoAdminMp(Long id, String status, String externalReference, BigDecimal transactionAmount) {}

    /**
     * Crea un Preapproval en Mercado Pago para la suscripción recurrente del profesional.
     */
    PreapprovalMp crearPreapproval(String email, String planName, BigDecimal monto, boolean esAnual, Long subscriptionId) throws Exception;

    /**
     * Cancela el cobro recurrente en Mercado Pago para no debitar períodos futuros.
     */
    void cancelarPreapproval(String preapprovalId) throws Exception;

    /**
     * Consulta el estado actual del Preapproval en la API de Mercado Pago.
     */
    PreapprovalMp obtenerPreapproval(String preapprovalId);

    /**
     * Consulta un pago cobrado a la cuenta de la plataforma.
     */
    PagoAdminMp obtenerPagoAdmin(Long paymentId) throws Exception;

    /**
     * Consulta la API REST /v1/authorized_payments/{id} para obtener el detalle de un cobro recurrente.
     */
    JsonNode obtenerDetalleAuthorizedPayment(String authorizedPaymentId);
}
