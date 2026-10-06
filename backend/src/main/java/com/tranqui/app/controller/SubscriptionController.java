package com.tranqui.app.controller;

import com.tranqui.app.model.Invoice;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CheckoutRequestDto;
import com.tranqui.app.model.dto.InvoiceResponseDto;
import com.tranqui.app.model.dto.PlanResponseDto;
import com.tranqui.app.model.dto.SubscriptionResponseDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.InvoiceService;
import com.tranqui.app.service.PlanService;
import com.tranqui.app.service.SubscriptionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    @Autowired
    private PlanService planService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Catálogo público de planes y precios
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponseDto>> getActivePlans() {
        List<Plan> plans = planService.getAllActivePlans();
        List<PlanResponseDto> dtos = plans.stream().map(p -> PlanResponseDto.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .priceArs(p.getPriceArs())
                .priceUsdRef(p.getPriceUsdRef())
                .priceArsAnual(p.getPriceArsAnual())
                .billingPeriod(p.getBillingPeriod())
                .minSeats(p.getMinSeats())
                .requiresPrescriber(p.getRequiresPrescriber())
                .features(planService.getFeatureKeysForPlan(p.getId()))
                .isActive(p.getIsActive())
                .build()
        ).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Suscripción del profesional autenticado
     */
    @GetMapping("/my-subscription")
    @PreAuthorize("hasAnyRole('PSIQUIATRA', 'ADMIN', 'PACIENTE')")
    public ResponseEntity<SubscriptionResponseDto> getMySubscription(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario prof = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Subscription sub = subscriptionService.getSubscriptionForProfessional(prof.getId()).orElse(null);
        if (sub == null) {
            return ResponseEntity.ok(null);
        }

        Plan plan = sub.getPlan();
        List<String> features = plan != null ? planService.getFeatureKeysForPlan(plan.getId()) : List.of();

        PlanResponseDto planDto = plan != null ? PlanResponseDto.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .priceArs(plan.getPriceArs())
                .priceUsdRef(plan.getPriceUsdRef())
                .priceArsAnual(plan.getPriceArsAnual())
                .billingPeriod(plan.getBillingPeriod())
                .minSeats(plan.getMinSeats())
                .requiresPrescriber(plan.getRequiresPrescriber())
                .features(features)
                .isActive(plan.getIsActive())
                .build() : null;

        SubscriptionResponseDto response = SubscriptionResponseDto.builder()
                .id(sub.getId())
                .professionalId(prof.getId())
                .professionalName(prof.getEffectiveLegalName())
                .professionalEmail(prof.getEmail())
                .plan(planDto)
                .status(sub.getStatus())
                .seats(sub.getSeats())
                .billingSource(sub.getBillingSource())
                .billingCycle(sub.getBillingCycle())
                .amountArs(sub.getAmountArs())
                .currentPeriodStart(sub.getCurrentPeriodStart())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .nextBillingDate(sub.getNextBillingDate())
                .graceUntil(sub.getGraceUntil())
                .cancelAtPeriodEnd(sub.getCancelAtPeriodEnd())
                .activeFeatures(features)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Etapa 2 del paywall: crea el checkout de Mercado Pago (cuenta del admin) para que el
     * profesional autenticado pague/renueve el plan elegido. Deliberadamente vive bajo
     * /api/subscriptions/ (ver SubscriptionAccessFilter.BYPASS_PREFIXES) para que un profesional
     * bloqueado por falta de pago pueda igual usarlo — es justamente la salida del bloqueo.
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<?> iniciarCheckout(@AuthenticationPrincipal UserDetails userDetails, @RequestBody CheckoutRequestDto body) {
        if (body.getPlanId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "planId es obligatorio"));
        }
        Usuario prof = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        try {
            String checkoutUrl = subscriptionService.iniciarCheckout(prof.getId(), body.getPlanId(), body.getBillingCycle());
            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cancela la renovación automática de la suscripción del profesional autenticado: corta el
     * débito recurrente en Mercado Pago, pero conserva el acceso hasta currentPeriodEnd (ver
     * SubscriptionService.cancelarSuscripcionPorProfesional). Vive bajo /api/subscriptions/ a
     * propósito — mismo motivo que /checkout: un profesional bloqueado por falta de pago igual
     * tiene que poder cancelar.
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<?> cancelarMiSuscripcion(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario prof = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        try {
            Subscription sub = subscriptionService.cancelarSuscripcionPorProfesional(prof.getId());
            return ResponseEntity.ok(Map.of(
                    "message", "Suscripción cancelada exitosamente. Mantendrás acceso a todas las funcionalidades hasta el fin del período abonado.",
                    "status", sub.getStatus().name(),
                    "cancelAtPeriodEnd", Boolean.TRUE.equals(sub.getCancelAtPeriodEnd()),
                    "currentPeriodEnd", sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd().toString() : ""
            ));
        } catch (IllegalStateException e) {
            // Mercado Pago no confirmó la cancelación del débito: no marcamos nada como cancelado.
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Historial de facturas electrónicas del profesional autenticado
     */
    @GetMapping("/my-invoices")
    @PreAuthorize("hasAnyRole('PSIQUIATRA', 'ADMIN', 'PACIENTE')")
    public ResponseEntity<List<InvoiceResponseDto>> getMyInvoices(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario prof = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        List<Invoice> invoices = invoiceService.getInvoicesForProfessional(prof.getId());
        List<InvoiceResponseDto> dtos = invoices.stream().map(this::mapInvoiceToDto).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Descarga de comprobante PDF oficial ARCA (Factura C / Nota de Crédito C)
     */
    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id) {
        Invoice invoice = invoiceService.getInvoiceById(id)
                .orElseThrow(() -> new EntityNotFoundException("Comprobante no encontrado"));

        if (invoice.getPdfBase64() == null || invoice.getPdfBase64().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdfBytes = Base64.getDecoder().decode(invoice.getPdfBase64());
        String filename = (invoice.getCbteTipo() == 13 ? "NotaCreditoC_" : "FacturaC_") +
                String.format("%05d-%08d", invoice.getPuntoVenta(), invoice.getCbteNumero()) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private InvoiceResponseDto mapInvoiceToDto(Invoice inv) {
        return InvoiceResponseDto.builder()
                .id(inv.getId())
                .paymentId(inv.getPaymentId())
                .cbteTipo(inv.getCbteTipo())
                .cbteTipoNombre(inv.getCbteTipo() == 13 ? "Nota de Crédito C" : "Factura C")
                .puntoVenta(inv.getPuntoVenta())
                .cbteNumero(inv.getCbteNumero())
                .cae(inv.getCae())
                .caeVencimiento(inv.getCaeVencimiento())
                .receptorNombre(inv.getReceptorNombre())
                .receptorDocNro(inv.getReceptorDocNro())
                .receptorCondicionIva(inv.getReceptorCondicionIva())
                .importeTotal(inv.getImporteTotal())
                .fechaEmision(inv.getFechaEmision())
                .pdfUrl(inv.getPdfUrl())
                .status(inv.getStatus())
                .lastError(inv.getLastError())
                .comprobanteAsociadoId(inv.getComprobanteAsociadoId())
                .issuedAt(inv.getIssuedAt())
                .build();
    }
}
