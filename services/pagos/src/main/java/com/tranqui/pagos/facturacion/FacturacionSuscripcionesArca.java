package com.tranqui.pagos.facturacion;

import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.suscripciones.FacturacionSuscripciones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementación real de FacturacionSuscripciones (Fase 8, S2c).
 * Emite la factura electrónica con ARCA y maneja la reconciliación de pagos y facturas.
 */
@Component
@Primary
public class FacturacionSuscripcionesArca implements FacturacionSuscripciones {

    private static final Logger log = LoggerFactory.getLogger(FacturacionSuscripcionesArca.class);

    private final InvoiceService invoiceService;
    private final PagosSuscripcion pagosSuscripcion;
    private final Facturas facturas;

    public FacturacionSuscripcionesArca(
            InvoiceService invoiceService,
            PagosSuscripcion pagosSuscripcion,
            Facturas facturas
    ) {
        this.invoiceService = invoiceService;
        this.pagosSuscripcion = pagosSuscripcion;
        this.facturas = facturas;
    }

    @Override
    public void emitirFactura(Long paymentId, String contexto) {
        try {
            log.info("Emitiendo factura ARCA para pago ID {} ({})", paymentId, contexto);
            invoiceService.generateInvoiceForPayment(paymentId);
        } catch (Exception e) {
            log.error("Error al emitir factura para pago ID {} ({}): {}", paymentId, contexto, e.getMessage(), e);
        }
    }

    @Override
    public void reconciliarPagosSinFactura() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<PagosSuscripcion.PagoSuscripcion> unInvoiced = pagosSuscripcion.aprobadosSinFacturaAnterioresA(cutoff);
        if (!unInvoiced.isEmpty()) {
            log.warn("ALERTA: Se encontraron {} pagos aprobados sin factura mayores a 24 horas.", unInvoiced.size());
            for (PagosSuscripcion.PagoSuscripcion p : unInvoiced) {
                try {
                    log.info("Reintentando emisión de factura para pago ID {}", p.id());
                    invoiceService.generateInvoiceForPayment(p.id());
                } catch (Exception e) {
                    log.error("Error al reintentar factura para pago ID {}: {}", p.id(), e.getMessage());
                }
            }
        }
    }

    @Override
    public void verificarFacturasFallidas() {
        List<Facturas.Factura> failed = facturas.fallidas();
        if (!failed.isEmpty()) {
            log.warn("ALERTA: Existen {} facturas en estado FAILED que requieren atención administrativa.", failed.size());
        }
    }
}
