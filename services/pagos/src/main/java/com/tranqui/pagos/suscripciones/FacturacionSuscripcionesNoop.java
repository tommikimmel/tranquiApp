package com.tranqui.pagos.suscripciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación no-op de {@link FacturacionSuscripciones} para S2b.
 * No realiza ninguna acción; la emisión real y la numeración con ARCA se integran en S2c.
 */
@Component
public class FacturacionSuscripcionesNoop implements FacturacionSuscripciones {

    private static final Logger log = LoggerFactory.getLogger(FacturacionSuscripcionesNoop.class);

    @Override
    public void emitirFactura(Long paymentId, String contexto) {
        log.debug("FacturacionSuscripcionesNoop: emitirFactura omitida para pago {} ({}) - pendiente de S2c", paymentId, contexto);
    }

    @Override
    public void reconciliarPagosSinFactura() {
        log.debug("FacturacionSuscripcionesNoop: reconciliarPagosSinFactura omitida - pendiente de S2c");
    }

    @Override
    public void verificarFacturasFallidas() {
        log.debug("FacturacionSuscripcionesNoop: verificarFacturasFallidas omitida - pendiente de S2c");
    }
}
