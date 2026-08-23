package com.tranqui.app.controller;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.*;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.InvoiceService;
import com.tranqui.app.service.PlanService;
import com.tranqui.app.service.SubscriptionReconciliationScheduler;
import com.tranqui.app.service.SubscriptionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PlanService planService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SubscriptionReconciliationScheduler reconciliationScheduler;

    /**
     * Resumen de métricas administrativas, MRR y estado de topes de Monotributo (§3)
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(subscriptionService.getAdminOverviewStats());
    }

    /**
     * Listado completo de suscripciones con estado y profesional
     */
    @GetMapping("/list")
    public ResponseEntity<List<SubscriptionResponseDto>> getAllSubscriptions() {
        List<Subscription> subs = subscriptionService.getAllSubscriptions();
        List<SubscriptionResponseDto> dtos = subs.stream().map(sub -> {
            Usuario prof = sub.getProfessional();
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

            return SubscriptionResponseDto.builder()
                    .id(sub.getId())
                    .professionalId(prof != null ? prof.getId() : null)
                    .professionalName(prof != null ? prof.getEffectiveLegalName() : "-")
                    .professionalEmail(prof != null ? prof.getEmail() : "-")
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
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Alta manual de suscripción y registro de pago en efectivo / transferencia / cortesía (§8)
     */
    @PostMapping("/manual-payment")
    public ResponseEntity<?> registerManualPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ManualPaymentRequestDto req
    ) {
        Usuario admin = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Admin no encontrado"));

        try {
            boolean emitFactura = req.getEmitInvoice() != null ? req.getEmitInvoice() : true;
            SubscriptionPayment payment = subscriptionService.registerManualPayment(
                    admin.getId(),
                    req.getProfessionalId(),
                    req.getPlanId(),
                    req.getAmountArs(),
                    req.getMethod(),
                    req.getPeriodStart(),
                    req.getPeriodEnd(),
                    req.getReceiptReference(),
                    req.getNotes(),
                    emitFactura,
                    req.getIdempotencyKey()
            );

            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Listado de todas las facturas y comprobantes emitidos
     */
    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponseDto>> getAllInvoices() {
        List<Invoice> invoices = invoiceService.getAllInvoices();
        List<InvoiceResponseDto> dtos = invoices.stream().map(inv -> InvoiceResponseDto.builder()
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
                .build()
        ).toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Emisión de Nota de Crédito C para anular una Factura C ya emitida (§10.5)
     */
    @PostMapping("/invoices/{id}/credit-note")
    public ResponseEntity<?> emitirNotaDeCredito(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreditNoteRequestDto req
    ) {
        Usuario admin = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Admin no encontrado"));

        try {
            Invoice nc = invoiceService.emitirNotaDeCredito(id, req.getReason() != null ? req.getReason() : "Anulación administrativa", admin.getId());
            return ResponseEntity.ok(nc);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Autowired
    private com.tranqui.app.repository.SubscriptionEventRepository subscriptionEventRepository;

    @Autowired
    private com.tranqui.app.repository.SubscriptionRepository subscriptionRepository;

    /**
     * Catálogo completo de planes para el panel de admin — a diferencia de
     * /api/subscriptions/plans (público, solo activos), esto incluye los planes ocultos
     * (isActive=false) para que el admin pueda verlos y reactivarlos. Sin este endpoint, un plan
     * desactivado desaparecía de la pestaña "Planes y Precios Base" sin forma de volver atrás.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponseDto>> getAllPlans() {
        List<Plan> plans = planService.getAllPlans();
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
     * Actualiza el precio base, precio USD ref y detalles de un plan del catálogo
     */
    @PutMapping("/plans/{id}")
    public ResponseEntity<?> updatePlan(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        java.math.BigDecimal priceArs = body.containsKey("priceArs") && body.get("priceArs") != null
                ? new java.math.BigDecimal(body.get("priceArs").toString()) : null;
        java.math.BigDecimal priceUsdRef = body.containsKey("priceUsdRef") && body.get("priceUsdRef") != null
                ? new java.math.BigDecimal(body.get("priceUsdRef").toString()) : null;
        java.math.BigDecimal priceArsAnual = body.containsKey("priceArsAnual") && body.get("priceArsAnual") != null
                ? new java.math.BigDecimal(body.get("priceArsAnual").toString()) : null;
        String name = body.containsKey("name") && body.get("name") != null ? body.get("name").toString() : null;
        String description = body.containsKey("description") && body.get("description") != null ? body.get("description").toString() : null;
        Boolean isActive = body.containsKey("isActive") && body.get("isActive") != null
                ? Boolean.valueOf(body.get("isActive").toString()) : null;

        Plan updated = planService.updatePlan(id, priceArs, priceUsdRef, priceArsAnual, name, description, isActive);
        return ResponseEntity.ok(updated);
    }

    /**
     * Historial de eventos de auditoría de todas las suscripciones
     */
    @GetMapping("/events")
    public ResponseEntity<List<SubscriptionEvent>> getAllSubscriptionEvents() {
        return ResponseEntity.ok(subscriptionEventRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * Historial de eventos de auditoría de una suscripción específica
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<SubscriptionEvent>> getSubscriptionEvents(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionEventRepository.findBySubscriptionIdOrderByCreatedAtDesc(id));
    }

    /**
     * Modificación manual del estado de una suscripción por el administrador
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateSubscriptionStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body
    ) {
        String newStatusStr = body.get("status");
        if (newStatusStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'status' es obligatorio"));
        }
        SubscriptionStatus newStatus = SubscriptionStatus.valueOf(newStatusStr);
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Suscripción no encontrada"));

        SubscriptionStatus oldStatus = sub.getStatus();
        sub.setStatus(newStatus);
        sub.setUpdatedAt(java.time.LocalDateTime.now());
        subscriptionRepository.save(sub);

        subscriptionService.logEvent(sub.getId(), "ADMIN_STATUS_CHANGE", oldStatus.name(), newStatus.name(),
                "ADMIN", userDetails != null ? userDetails.getUsername() : "admin",
                "Estado modificado manualmente por " + (userDetails != null ? userDetails.getUsername() : "admin"));

        // No devolver la entidad JPA cruda: su relación lazy "professional" es un proxy de
        // Hibernate que Jackson no puede serializar (rompe la respuesta a mitad de camino, con
        // ERR_HTTP2_PROTOCOL_ERROR del lado del browser en vez de un error prolijo). El frontend
        // no lee el body de esta respuesta — solo le importa que la request haya sido exitosa.
        return ResponseEntity.ok(Map.of("id", sub.getId(), "status", sub.getStatus().name()));
    }

    /**
     * Ejecuta el cron de reconciliación bajo demanda
     */
    @PostMapping("/reconciliation/run")
    public ResponseEntity<?> triggerReconciliation() {
        reconciliationScheduler.runDailyReconciliation();
        return ResponseEntity.ok(Map.of("message", "Reconciliación diaria ejecutada correctamente"));
    }
}
