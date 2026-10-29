package com.tranqui.pagos.suscripciones;

/**
 * Punto de extensión para la facturación ARCA de las suscripciones (Fase 8, S2b/S2c).
 * En S2b la implementación por defecto es no-op ({@link FacturacionSuscripcionesNoop}).
 * En S2c se reemplaza por la emisión real de facturas y notas de crédito con CAE.
 */
public interface FacturacionSuscripciones {

    /**
     * Emite la factura electrónica para un pago de suscripción aprobado.
     * Se invoca tras la confirmación de la transacción (after commit).
     */
    void emitirFactura(Long paymentId, String contexto);

    /**
     * Reintenta emitir facturas para pagos aprobados mayores a 24 horas sin factura emitida.
     * Invocado por el cron diario de reconciliación.
     */
    void reconciliarPagosSinFactura();

    /**
     * Verifica la existencia de facturas en estado FAILED y emite las alertas correspondientes.
     * Invocado por el cron diario de reconciliación.
     */
    void verificarFacturasFallidas();
}
