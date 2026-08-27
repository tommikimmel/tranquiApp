package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.InvoiceRepository;
import com.tranqui.app.repository.InvoiceSequenceRepository;
import com.tranqui.app.repository.SubscriptionPaymentRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.arca.ArcaConfig;
import com.tranqui.app.service.arca.ArcaInvoicePdfService;
import com.tranqui.app.service.arca.ArcaWsfeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceSequenceRepository sequenceRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ArcaWsfeService arcaWsfeService;

    @Autowired
    private ArcaInvoicePdfService pdfService;

    @Autowired
    private ArcaConfig arcaConfig;

    @Autowired
    private ResendEmailService resendEmailService;

    /**
     * Genera la Factura C electrónica ante ARCA para un pago registrado (alta manual o Mercado Pago).
     * Garantiza numeración correlativa estricta y serializada sin huecos ni duplicados.
     */
    @Transactional
    public synchronized Invoice generateInvoiceForPayment(Long paymentId) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + paymentId));

        if (payment.getInvoiceId() != null) {
            Optional<Invoice> existing = invoiceRepository.findById(payment.getInvoiceId());
            if (existing.isPresent()) {
                log.info("Factura ya existente para paymentId {}: Factura C #{}", paymentId, existing.get().getCbteNumero());
                return existing.get();
            }
        }

        if ("COURTESY".equalsIgnoreCase(payment.getMethod())) {
            log.info("Pago de cortesía - no requiere emisión de factura electrónica");
            return null;
        }

        // La integración con ARCA (WSAA/WSFEv1) está codeada pero todavía no operativa — sin
        // certificados ni alta real ante ARCA (arca.enabled=false por defecto, sin overrides en
        // .env). Sin este guard, cada pago intentaba pedir CAE contra el servicio real igual,
        // fallando siempre y — peor — quemando un número de comprobante correlativo real en cada
        // intento fallido (la numeración se incrementa ANTES de llamar a ARCA), lo que rompe la
        // integridad de la numeración para cuando la facturación real se habilite.
        if (!arcaConfig.isEnabled()) {
            log.info("Facturación ARCA todavía no está habilitada (arca.enabled=false) — se omite la emisión para el pago {}.", paymentId);
            return null;
        }

        Usuario prof = usuarioRepository.findById(payment.getProfessionalId())
                .orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado"));

        Subscription subscription = subscriptionRepository.findById(payment.getSubscriptionId())
                .orElse(null);
        Plan plan = subscription != null ? subscription.getPlan() : null;

        // Validar datos fiscales mínimos del receptor
        String taxIdStr = prof.getEffectiveTaxId();
        if (taxIdStr == null || taxIdStr.replaceAll("\\D", "").isEmpty()) {
            throw new IllegalStateException("El profesional no tiene CUIT/CUIL/DNI configurado para facturar");
        }

        long cleanDocNro = Long.parseLong(taxIdStr.replaceAll("\\D", ""));
        int docTipo = (cleanDocNro > 99999999L) ? 80 : 96; // 80 = CUIT, 96 = DNI
        int condicionIva = prof.getEffectiveIvaConditionId();
        String receptorNombre = prof.getEffectiveLegalName();

        int ptoVta = arcaConfig.getPuntoVenta() != null ? arcaConfig.getPuntoVenta() : 1;
        int cbteTipo = 11; // 11 = Factura C

        // Obtener siguiente número correlativo con lock pesimista
        InvoiceSequence sequence = sequenceRepository.findByPuntoVentaAndCbteTipoForUpdate(ptoVta, cbteTipo)
                .orElseGet(() -> InvoiceSequence.builder()
                        .puntoVenta(ptoVta)
                        .cbteTipo(cbteTipo)
                        .lastNumber(0L)
                        .build());

        long nextNumero = sequence.getLastNumber() + 1;
        sequence.setLastNumber(nextNumero);
        sequenceRepository.save(sequence);

        LocalDate now = LocalDate.now();
        LocalDate fchDesde = payment.getPeriodStart() != null ? payment.getPeriodStart().toLocalDate() : now;
        LocalDate fchHasta = payment.getPeriodEnd() != null ? payment.getPeriodEnd().toLocalDate() : now.plusMonths(1);

        Invoice invoice = Invoice.builder()
                .paymentId(payment.getId())
                .cbteTipo(cbteTipo)
                .puntoVenta(ptoVta)
                .cbteNumero(nextNumero)
                .receptorDocTipo(docTipo)
                .receptorDocNro(cleanDocNro)
                .receptorNombre(receptorNombre)
                .receptorCondicionIva(condicionIva)
                .importeTotal(payment.getAmountArs())
                .moneda("PES")
                .cotizacion(BigDecimal.ONE)
                .fechaEmision(now)
                .status(InvoiceStatus.PENDING)
                .attempts(1)
                .createdAt(LocalDateTime.now())
                .build();

        invoice = invoiceRepository.save(invoice);

        // Solicitar CAE ante ARCA WSFEv1
        try {
            ArcaWsfeService.SolicitudCaeRequest req = ArcaWsfeService.SolicitudCaeRequest.builder()
                    .puntoVenta(ptoVta)
                    .cbteTipo(cbteTipo)
                    .cbteNumero(nextNumero)
                    .docTipo(docTipo)
                    .docNro(cleanDocNro)
                    .importeTotal(payment.getAmountArs())
                    .fechaEmision(now)
                    .fechaServDesde(fchDesde)
                    .fechaServHasta(fchHasta)
                    .fechaVtoPago(now.plusDays(10))
                    .condicionIvaReceptorId(condicionIva)
                    .build();

            ArcaWsfeService.ArcaCaeResponse caeResp = arcaWsfeService.solicitarCae(req);

            invoice.setArcaRequestJson(caeResp.getRawRequestXml());
            invoice.setArcaResponseJson(caeResp.getRawResponseXml());

            if (caeResp.isAprobado()) {
                invoice.setCae(caeResp.getCae());
                invoice.setCaeVencimiento(caeResp.getCaeFchVto());
                invoice.setStatus(InvoiceStatus.ISSUED);
                invoice.setIssuedAt(LocalDateTime.now());

                // Generar PDF y almacenar
                String periodDesc = "Período " + fchDesde.toString() + " al " + fchHasta.toString();
                byte[] pdfBytes = pdfService.renderInvoicePdf(invoice, plan, periodDesc);
                String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                invoice.setPdfBase64(pdfBase64);
                invoice.setPdfUrl("/api/subscriptions/invoices/" + invoice.getId() + "/pdf");

                payment.setInvoiceId(invoice.getId());
                paymentRepository.save(payment);

                log.info("Factura C #{} emitida exitosamente con CAE {}", nextNumero, caeResp.getCae());
            } else {
                invoice.setStatus(InvoiceStatus.FAILED);
                invoice.setLastError(String.join("; ", caeResp.getErrores()));
                log.error("Rechazo de factura ARCA: {}", invoice.getLastError());
            }
        } catch (Exception e) {
            invoice.setStatus(InvoiceStatus.FAILED);
            invoice.setLastError("Error de comunicación ARCA: " + e.getMessage());
            log.error("Fallo al autorizar comprobante con ARCA: {}", e.getMessage(), e);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Emite una Nota de Crédito C (cbte_tipo 13) para anular o rectificar una Factura C ya emitida con CAE.
     */
    @Transactional
    public synchronized Invoice emitirNotaDeCredito(Long originalInvoiceId, String reason, Long adminId) {
        Invoice original = invoiceRepository.findById(originalInvoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Factura original no encontrada"));

        if (original.getStatus() != InvoiceStatus.ISSUED) {
            throw new IllegalStateException("Solo se puede emitir Nota de Crédito sobre comprobantes en estado ISSUED");
        }
        if (original.getCbteTipo() != 11) {
            throw new IllegalStateException("El comprobante original debe ser una Factura C (tipo 11)");
        }

        int ptoVta = original.getPuntoVenta();
        int ncTipo = 13; // 13 = Nota de Crédito C

        InvoiceSequence sequence = sequenceRepository.findByPuntoVentaAndCbteTipoForUpdate(ptoVta, ncTipo)
                .orElseGet(() -> InvoiceSequence.builder()
                        .puntoVenta(ptoVta)
                        .cbteTipo(ncTipo)
                        .lastNumber(0L)
                        .build());

        long nextNumero = sequence.getLastNumber() + 1;
        sequence.setLastNumber(nextNumero);
        sequenceRepository.save(sequence);

        LocalDate now = LocalDate.now();

        Invoice nc = Invoice.builder()
                .paymentId(original.getPaymentId())
                .cbteTipo(ncTipo)
                .puntoVenta(ptoVta)
                .cbteNumero(nextNumero)
                .receptorDocTipo(original.getReceptorDocTipo())
                .receptorDocNro(original.getReceptorDocNro())
                .receptorNombre(original.getReceptorNombre())
                .receptorCondicionIva(original.getReceptorCondicionIva())
                .importeTotal(original.getImporteTotal())
                .moneda("PES")
                .cotizacion(BigDecimal.ONE)
                .fechaEmision(now)
                .status(InvoiceStatus.PENDING)
                .comprobanteAsociadoId(original.getId())
                .attempts(1)
                .createdAt(LocalDateTime.now())
                .build();

        nc = invoiceRepository.save(nc);

        try {
            ArcaWsfeService.SolicitudCaeRequest req = ArcaWsfeService.SolicitudCaeRequest.builder()
                    .puntoVenta(ptoVta)
                    .cbteTipo(ncTipo)
                    .cbteNumero(nextNumero)
                    .docTipo(original.getReceptorDocTipo())
                    .docNro(original.getReceptorDocNro())
                    .importeTotal(original.getImporteTotal())
                    .fechaEmision(now)
                    .fechaServDesde(original.getFechaEmision())
                    .fechaServHasta(original.getFechaEmision())
                    .fechaVtoPago(now.plusDays(10))
                    .condicionIvaReceptorId(original.getReceptorCondicionIva())
                    .comprobanteAsociadoNumero(original.getCbteNumero())
                    .comprobanteAsociadoTipo(11)
                    .comprobanteAsociadoPtoVta(ptoVta)
                    .build();

            ArcaWsfeService.ArcaCaeResponse caeResp = arcaWsfeService.solicitarCae(req);

            nc.setArcaRequestJson(caeResp.getRawRequestXml());
            nc.setArcaResponseJson(caeResp.getRawResponseXml());

            if (caeResp.isAprobado()) {
                nc.setCae(caeResp.getCae());
                nc.setCaeVencimiento(caeResp.getCaeFchVto());
                nc.setStatus(InvoiceStatus.ISSUED);
                nc.setIssuedAt(LocalDateTime.now());

                byte[] pdfBytes = pdfService.renderInvoicePdf(nc, null, "Anulación / Crédito de Factura C #" + original.getCbteNumero() + " (" + reason + ")");
                String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                nc.setPdfBase64(pdfBase64);
                nc.setPdfUrl("/api/subscriptions/invoices/" + nc.getId() + "/pdf");

                original.setStatus(InvoiceStatus.VOIDED);
                invoiceRepository.save(original);

                log.info("Nota de Crédito C #{} emitida exitosamente para anular Factura C #{}", nextNumero, original.getCbteNumero());
            } else {
                nc.setStatus(InvoiceStatus.FAILED);
                nc.setLastError(String.join("; ", caeResp.getErrores()));
                log.error("Rechazo de Nota de Crédito ARCA: {}", nc.getLastError());
            }
        } catch (Exception e) {
            nc.setStatus(InvoiceStatus.FAILED);
            nc.setLastError("Error de comunicación ARCA: " + e.getMessage());
            log.error("Fallo al emitir Nota de Crédito con ARCA: {}", e.getMessage(), e);
        }

        return invoiceRepository.save(nc);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAllOrderByCreatedAtDesc();
    }

    public Optional<Invoice> getInvoiceById(Long id) {
        return invoiceRepository.findById(id);
    }

    public List<Invoice> getInvoicesForProfessional(Long professionalId) {
        List<SubscriptionPayment> payments = paymentRepository.findByProfessionalIdOrderByPaidAtDesc(professionalId);
        List<Long> paymentIds = payments.stream().map(SubscriptionPayment::getId).toList();
        if (paymentIds.isEmpty()) return List.of();
        return invoiceRepository.findByPaymentIdIn(paymentIds);
    }
}
