package com.tranqui.pagos.facturacion;

import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.SecuenciasFactura;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final Facturas facturas;
    private final SecuenciasFactura secuenciasFactura;
    private final PagosSuscripcion pagosSuscripcion;
    private final Suscripciones suscripciones;
    private final Planes planes;
    private final Suscriptores suscriptores;
    private final ArcaWsfeService arcaWsfeService;
    private final ArcaInvoicePdfService pdfService;
    private final ArcaConfig arcaConfig;

    public InvoiceService(
            Facturas facturas,
            SecuenciasFactura secuenciasFactura,
            PagosSuscripcion pagosSuscripcion,
            Suscripciones suscripciones,
            Planes planes,
            Suscriptores suscriptores,
            ArcaWsfeService arcaWsfeService,
            ArcaInvoicePdfService pdfService,
            ArcaConfig arcaConfig
    ) {
        this.facturas = facturas;
        this.secuenciasFactura = secuenciasFactura;
        this.pagosSuscripcion = pagosSuscripcion;
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.suscriptores = suscriptores;
        this.arcaWsfeService = arcaWsfeService;
        this.pdfService = pdfService;
        this.arcaConfig = arcaConfig;
    }

    /**
     * Genera la Factura C electrónica ante ARCA para un pago registrado (alta manual o Mercado Pago).
     * Garantiza numeración correlativa estricta y serializada sin huecos ni duplicados.
     */
    @Transactional
    public synchronized Facturas.Factura generateInvoiceForPayment(Long paymentId) {
        PagosSuscripcion.PagoSuscripcion payment = pagosSuscripcion.porId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + paymentId));

        if (payment.invoiceId() != null) {
            Optional<Facturas.Factura> existing = facturas.porId(payment.invoiceId());
            if (existing.isPresent()) {
                log.info("Factura ya existente para paymentId {}: Factura C #{}", paymentId, existing.get().cbteNumero());
                return existing.get();
            }
        }

        if ("COURTESY".equalsIgnoreCase(payment.method())) {
            log.info("Pago de cortesía - no requiere emisión de factura electrónica");
            return null;
        }

        if (!arcaConfig.isEnabled()) {
            log.info("Facturación ARCA todavía no está habilitada (arca.enabled=false) — se omite la emisión para el pago {}.", paymentId);
            return null;
        }

        Suscriptores.Suscriptor prof = suscriptores.porId(payment.professionalId())
                .orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado"));

        Suscripciones.Suscripcion subscription = payment.subscriptionId() != null
                ? suscripciones.porId(payment.subscriptionId()).orElse(null)
                : null;
        Planes.Plan plan = (subscription != null && subscription.planId() != null)
                ? planes.porId(subscription.planId()).orElse(null)
                : null;

        // Validar datos fiscales mínimos del receptor
        String taxIdStr = prof.taxIdEfectivo();
        if (taxIdStr == null || taxIdStr.replaceAll("\\D", "").isEmpty()) {
            throw new IllegalStateException("El profesional no tiene CUIT/CUIL/DNI configurado para facturar");
        }

        long cleanDocNro = Long.parseLong(taxIdStr.replaceAll("\\D", ""));
        int docTipo = (cleanDocNro > 99999999L) ? 80 : 96; // 80 = CUIT, 96 = DNI
        int condicionIva = prof.condicionIvaEfectiva();
        String receptorNombre = prof.nombreLegalEfectivo();

        int ptoVta = arcaConfig.getPuntoVenta() != null ? arcaConfig.getPuntoVenta() : 1;
        int cbteTipo = 11; // 11 = Factura C

        // Obtener siguiente número correlativo con lock pesimista
        long nextNumero = secuenciasFactura.proximoNumero(ptoVta, cbteTipo);

        LocalDate now = LocalDate.now();
        LocalDate fchDesde = payment.periodStart() != null ? payment.periodStart().toLocalDate() : now;
        LocalDate fchHasta = payment.periodEnd() != null ? payment.periodEnd().toLocalDate() : now.plusMonths(1);

        Facturas.Factura factura = new Facturas.Factura(
                null,
                payment.id(),
                cbteTipo,
                ptoVta,
                nextNumero,
                null,
                null,
                docTipo,
                cleanDocNro,
                receptorNombre,
                condicionIva,
                payment.amountArs(),
                "PES",
                BigDecimal.ONE,
                now,
                null,
                null,
                "PENDING",
                1,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null
        );

        Long invoiceId = facturas.insertarNuevo(factura);

        // Solicitar CAE ante ARCA WSFEv1
        try {
            ArcaWsfeService.SolicitudCaeRequest req = ArcaWsfeService.SolicitudCaeRequest.builder()
                    .puntoVenta(ptoVta)
                    .cbteTipo(cbteTipo)
                    .cbteNumero(nextNumero)
                    .docTipo(docTipo)
                    .docNro(cleanDocNro)
                    .importeTotal(payment.amountArs())
                    .fechaEmision(now)
                    .fechaServDesde(fchDesde)
                    .fechaServHasta(fchHasta)
                    .fechaVtoPago(now.plusDays(10))
                    .condicionIvaReceptorId(condicionIva)
                    .build();

            ArcaWsfeService.ArcaCaeResponse caeResp = arcaWsfeService.solicitarCae(req);

            if (caeResp.isAprobado()) {
                String periodDesc = "Período " + fchDesde.toString() + " al " + fchHasta.toString();
                Facturas.Factura tempParaPdf = new Facturas.Factura(
                        invoiceId, payment.id(), cbteTipo, ptoVta, nextNumero,
                        caeResp.getCae(), caeResp.getCaeFchVto(), docTipo, cleanDocNro,
                        receptorNombre, condicionIva, payment.amountArs(), "PES", BigDecimal.ONE,
                        now, null, null, "ISSUED", 1, null,
                        caeResp.getRawRequestXml(), caeResp.getRawResponseXml(), null,
                        LocalDateTime.now(), LocalDateTime.now()
                );
                byte[] pdfBytes = pdfService.renderInvoicePdf(tempParaPdf, plan, periodDesc);
                String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                String pdfUrl = "/api/subscriptions/invoices/" + invoiceId + "/pdf";

                facturas.actualizarResultadoCae(invoiceId, caeResp.getCae(), caeResp.getCaeFchVto(),
                        nextNumero, pdfUrl, pdfBase64, "ISSUED", null,
                        caeResp.getRawRequestXml(), caeResp.getRawResponseXml(), LocalDateTime.now(), 1);

                pagosSuscripcion.actualizarFacturaId(payment.id(), invoiceId);

                log.info("Factura C #{} emitida exitosamente con CAE {}", nextNumero, caeResp.getCae());
            } else {
                String lastError = String.join("; ", caeResp.getErrores());
                facturas.actualizarResultadoCae(invoiceId, null, null,
                        nextNumero, null, null, "FAILED", lastError,
                        caeResp.getRawRequestXml(), caeResp.getRawResponseXml(), null, 1);
                log.error("Rechazo de factura ARCA: {}", lastError);
            }
        } catch (Exception e) {
            String lastError = "Error de comunicación ARCA: " + e.getMessage();
            facturas.actualizarResultadoCae(invoiceId, null, null,
                    nextNumero, null, null, "FAILED", lastError,
                    null, null, null, 1);
            log.error("Fallo al autorizar comprobante con ARCA: {}", e.getMessage(), e);
        }

        return facturas.porId(invoiceId).orElse(null);
    }

    /**
     * Emite una Nota de Crédito C (cbte_tipo 13) para anular o rectificar una Factura C ya emitida con CAE.
     */
    @Transactional
    public synchronized Facturas.Factura emitirNotaDeCredito(Long originalInvoiceId, String reason, Long adminId) {
        if (!arcaConfig.isEnabled()) {
            throw new IllegalStateException("La facturación electrónica ARCA todavía no está habilitada.");
        }

        Facturas.Factura original = facturas.porId(originalInvoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Factura original no encontrada"));

        if (!"ISSUED".equals(original.status())) {
            throw new IllegalStateException("Solo se puede emitir Nota de Crédito sobre comprobantes en estado ISSUED");
        }
        if (original.cbteTipo() == null || original.cbteTipo() != 11) {
            throw new IllegalStateException("El comprobante original debe ser una Factura C (tipo 11)");
        }

        int ptoVta = original.puntoVenta();
        int ncTipo = 13; // 13 = Nota de Crédito C

        long nextNumero = secuenciasFactura.proximoNumero(ptoVta, ncTipo);
        LocalDate now = LocalDate.now();

        Facturas.Factura nc = new Facturas.Factura(
                null,
                original.paymentId(),
                ncTipo,
                ptoVta,
                nextNumero,
                null,
                null,
                original.receptorDocTipo(),
                original.receptorDocNro(),
                original.receptorNombre(),
                original.receptorCondicionIva(),
                original.importeTotal(),
                "PES",
                BigDecimal.ONE,
                now,
                null,
                null,
                "PENDING",
                1,
                null,
                null,
                null,
                original.id(),
                LocalDateTime.now(),
                null
        );

        Long ncId = facturas.insertarNuevo(nc);

        try {
            ArcaWsfeService.SolicitudCaeRequest req = ArcaWsfeService.SolicitudCaeRequest.builder()
                    .puntoVenta(ptoVta)
                    .cbteTipo(ncTipo)
                    .cbteNumero(nextNumero)
                    .docTipo(original.receptorDocTipo())
                    .docNro(original.receptorDocNro())
                    .importeTotal(original.importeTotal())
                    .fechaEmision(now)
                    .fechaServDesde(original.fechaEmision())
                    .fechaServHasta(original.fechaEmision())
                    .fechaVtoPago(now.plusDays(10))
                    .condicionIvaReceptorId(original.receptorCondicionIva())
                    .comprobanteAsociadoNumero(original.cbteNumero())
                    .comprobanteAsociadoTipo(11)
                    .comprobanteAsociadoPtoVta(ptoVta)
                    .build();

            ArcaWsfeService.ArcaCaeResponse caeResp = arcaWsfeService.solicitarCae(req);

            if (caeResp.isAprobado()) {
                Facturas.Factura tempParaPdf = new Facturas.Factura(
                        ncId, original.paymentId(), ncTipo, ptoVta, nextNumero,
                        caeResp.getCae(), caeResp.getCaeFchVto(),
                        original.receptorDocTipo(), original.receptorDocNro(),
                        original.receptorNombre(), original.receptorCondicionIva(),
                        original.importeTotal(), "PES", BigDecimal.ONE, now,
                        null, null, "ISSUED", 1, null,
                        caeResp.getRawRequestXml(), caeResp.getRawResponseXml(),
                        original.id(), LocalDateTime.now(), LocalDateTime.now()
                );

                byte[] pdfBytes = pdfService.renderInvoicePdf(tempParaPdf, null,
                        "Anulación / Crédito de Factura C #" + original.cbteNumero() + " (" + reason + ")");
                String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                String pdfUrl = "/api/subscriptions/invoices/" + ncId + "/pdf";

                facturas.actualizarResultadoCae(ncId, caeResp.getCae(), caeResp.getCaeFchVto(),
                        nextNumero, pdfUrl, pdfBase64, "ISSUED", null,
                        caeResp.getRawRequestXml(), caeResp.getRawResponseXml(), LocalDateTime.now(), 1);

                facturas.actualizarEstado(original.id(), "VOIDED");

                log.info("Nota de Crédito C #{} emitida exitosamente para anular Factura C #{}", nextNumero, original.cbteNumero());
            } else {
                String lastError = String.join("; ", caeResp.getErrores());
                facturas.actualizarResultadoCae(ncId, null, null,
                        nextNumero, null, null, "FAILED", lastError,
                        caeResp.getRawRequestXml(), caeResp.getRawResponseXml(), null, 1);
                log.error("Rechazo de Nota de Crédito ARCA: {}", lastError);
            }
        } catch (Exception e) {
            String lastError = "Error de comunicación ARCA: " + e.getMessage();
            facturas.actualizarResultadoCae(ncId, null, null,
                    nextNumero, null, null, "FAILED", lastError,
                    null, null, null, 1);
            log.error("Fallo al emitir Nota de Crédito con ARCA: {}", e.getMessage(), e);
        }

        return facturas.porId(ncId).orElse(null);
    }

    public List<Facturas.Factura> getAllInvoices() {
        return facturas.todasOrdenadas();
    }

    public Optional<Facturas.Factura> getInvoiceById(Long id) {
        return facturas.porId(id);
    }

    public List<Facturas.Factura> getInvoicesForProfessional(Long professionalId) {
        List<PagosSuscripcion.PagoSuscripcion> payments = pagosSuscripcion.porProfesionalId(professionalId);
        List<Long> paymentIds = payments.stream().map(PagosSuscripcion.PagoSuscripcion::id).toList();
        if (paymentIds.isEmpty()) return List.of();
        return facturas.porPagoIds(paymentIds);
    }
}
