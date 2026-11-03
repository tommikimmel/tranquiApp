package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.mp.MercadoPagoAdminApi;
import com.tranqui.pagos.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.pagos.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de administración para suscripciones (/api/admin/subscriptions),
 * restringidos a usuarios con rol ADMIN.
 */
@RestController
@RequestMapping("/api/admin/subscriptions")
public class AdminSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(AdminSubscriptionController.class);

    private final Suscripciones suscripciones;
    private final Planes planes;
    private final PagosSuscripcion pagosSuscripcion;
    private final EventosSuscripcion eventosSuscripcion;
    private final Suscriptores suscriptores;
    private final SuscripcionService suscripcionService;
    private final AvisoSuscripcionPublisher avisoSuscripcionPublisher;
    private final MercadoPagoAdminApi mercadoPagoAdminApi;
    private final SubscriptionReconciliationScheduler reconciliationScheduler;

    public AdminSubscriptionController(
            Suscripciones suscripciones,
            Planes planes,
            PagosSuscripcion pagosSuscripcion,
            EventosSuscripcion eventosSuscripcion,
            Suscriptores suscriptores,
            SuscripcionService suscripcionService,
            AvisoSuscripcionPublisher avisoSuscripcionPublisher,
            MercadoPagoAdminApi mercadoPagoAdminApi,
            SubscriptionReconciliationScheduler reconciliationScheduler
    ) {
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.pagosSuscripcion = pagosSuscripcion;
        this.eventosSuscripcion = eventosSuscripcion;
        this.suscriptores = suscriptores;
        this.suscripcionService = suscripcionService;
        this.avisoSuscripcionPublisher = avisoSuscripcionPublisher;
        this.mercadoPagoAdminApi = mercadoPagoAdminApi;
        this.reconciliationScheduler = reconciliationScheduler;
    }

    private UsuarioJwt.Usuario validarAdmin(HttpServletRequest request) {
        UsuarioJwt.Usuario u = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!"ADMIN".equals(u.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return u;
    }

    /**
     * Resumen de métricas administrativas, MRR y estado de topes de Monotributo.
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(HttpServletRequest request) {
        validarAdmin(request);
        return ResponseEntity.ok(suscripcionService.getAdminOverviewStats());
    }

    /**
     * Listado completo de suscripciones con estado y profesional.
     */
    @GetMapping("/list")
    public ResponseEntity<List<Dtos.SubscriptionResponseDto>> getAllSubscriptions(HttpServletRequest request) {
        validarAdmin(request);
        List<Suscripciones.Suscripcion> subs = suscripciones.todas();
        List<Dtos.SubscriptionResponseDto> dtos = subs.stream().map(sub -> {
            Suscriptores.Suscriptor prof = suscriptores.porId(sub.professionalId()).orElse(null);
            Planes.Plan plan = planes.porId(sub.planId()).orElse(null);
            List<String> features = plan != null
                    ? planes.featuresPorPlanId(plan.id()).stream().map(Planes.PlanFeature::featureKey).toList()
                    : List.of();

            Dtos.PlanResponseDto planDto = plan != null ? new Dtos.PlanResponseDto(
                    plan.id(),
                    plan.code(),
                    plan.name(),
                    plan.description(),
                    plan.priceArs(),
                    plan.priceUsdRef(),
                    plan.priceArsAnual(),
                    plan.billingPeriod(),
                    plan.minSeats(),
                    plan.requiresPrescriber(),
                    features,
                    plan.isActive()
            ) : null;

            return new Dtos.SubscriptionResponseDto(
                    sub.id(),
                    prof != null ? prof.id() : null,
                    prof != null ? prof.nombreLegalEfectivo() : "-",
                    prof != null ? prof.email() : "-",
                    planDto,
                    sub.status(),
                    sub.seats(),
                    sub.billingSource(),
                    sub.billingCycle(),
                    sub.amountArs(),
                    sub.currentPeriodStart(),
                    sub.currentPeriodEnd(),
                    sub.nextBillingDate(),
                    sub.graceUntil(),
                    sub.cancelAtPeriodEnd(),
                    features
            );
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Alta manual de suscripción y registro de pago.
     */
    @PostMapping("/manual-payment")
    public ResponseEntity<?> registerManualPayment(HttpServletRequest request, @RequestBody Dtos.ManualPaymentRequestDto req) {
        UsuarioJwt.Usuario u = validarAdmin(request);
        Long adminId = suscriptores.porEmail(u.email()).map(Suscriptores.Suscriptor::id).orElse(1L);

        try {
            boolean emitFactura = req.emitInvoice() != null ? req.emitInvoice() : true;
            PagosSuscripcion.PagoSuscripcion pago = suscripcionService.registerManualPayment(
                    adminId,
                    req.professionalId(),
                    req.planId(),
                    req.amountArs(),
                    req.method(),
                    req.periodStart(),
                    req.periodEnd(),
                    req.receiptReference(),
                    req.notes(),
                    emitFactura,
                    req.idempotencyKey()
            );

            Dtos.SubscriptionPaymentDto dto = new Dtos.SubscriptionPaymentDto(
                    pago.id(),
                    pago.subscriptionId(),
                    pago.professionalId(),
                    pago.mpPaymentId(),
                    pago.idempotencyKey(),
                    pago.method(),
                    pago.status(),
                    pago.amountArs(),
                    pago.paidAt(),
                    pago.periodStart(),
                    pago.periodEnd(),
                    pago.invoiceId(),
                    pago.registeredByAdminId(),
                    pago.receiptReference(),
                    pago.notes(),
                    pago.createdAt()
            );

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Catálogo completo de planes para el panel de administración (incluyendo inactivos).
     */
    @GetMapping("/plans")
    public ResponseEntity<List<Dtos.PlanResponseDto>> getAllPlans(HttpServletRequest request) {
        validarAdmin(request);
        List<Planes.Plan> plans = planes.todos();
        List<Dtos.PlanResponseDto> dtos = plans.stream().map(p -> new Dtos.PlanResponseDto(
                p.id(),
                p.code(),
                p.name(),
                p.description(),
                p.priceArs(),
                p.priceUsdRef(),
                p.priceArsAnual(),
                p.billingPeriod(),
                p.minSeats(),
                p.requiresPrescriber(),
                planes.featuresPorPlanId(p.id()).stream().map(Planes.PlanFeature::featureKey).toList(),
                p.isActive()
        )).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Actualiza el precio base, precio USD ref y detalles de un plan del catálogo.
     */
    @PutMapping("/plans/{id}")
    @Transactional
    public ResponseEntity<?> updatePlan(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body
    ) {
        validarAdmin(request);
        Planes.Plan plan = planes.porId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan con ID " + id + " no encontrado"));

        BigDecimal priceArs = body.containsKey("priceArs") && body.get("priceArs") != null
                ? new BigDecimal(body.get("priceArs").toString()) : plan.priceArs();
        BigDecimal priceUsdRef = body.containsKey("priceUsdRef") && body.get("priceUsdRef") != null
                ? new BigDecimal(body.get("priceUsdRef").toString()) : plan.priceUsdRef();
        BigDecimal priceArsAnual = body.containsKey("priceArsAnual") && body.get("priceArsAnual") != null
                ? new BigDecimal(body.get("priceArsAnual").toString()) : plan.priceArsAnual();
        String name = body.containsKey("name") && body.get("name") != null
                ? body.get("name").toString().trim() : plan.name();
        String description = body.containsKey("description") && body.get("description") != null
                ? body.get("description").toString().trim() : plan.description();
        Boolean isActive = body.containsKey("isActive") && body.get("isActive") != null
                ? Boolean.valueOf(body.get("isActive").toString()) : plan.isActive();

        Planes.Plan actualizado = new Planes.Plan(
                plan.id(), plan.code(), name, description, priceArs, priceUsdRef, priceArsAnual,
                plan.billingPeriod(), plan.minSeats(), plan.mpPreapprovalPlanId(), plan.requiresPrescriber(),
                isActive, plan.effectiveFrom(), plan.effectiveTo()
        );
        planes.actualizar(actualizado);

        log.info("Plan {} ({}) actualizado por admin: precio ARS {}, USD ref {}",
                actualizado.code(), actualizado.name(), actualizado.priceArs(), actualizado.priceUsdRef());
        return ResponseEntity.ok(actualizado);
    }

    /**
     * Historial de eventos de auditoría de todas las suscripciones.
     */
    @GetMapping("/events")
    public ResponseEntity<List<Dtos.SubscriptionEventDto>> getAllSubscriptionEvents(HttpServletRequest request) {
        validarAdmin(request);
        List<Dtos.SubscriptionEventDto> dtos = eventosSuscripcion.todosOrdenados().stream().map(e ->
                new Dtos.SubscriptionEventDto(
                        e.id(), e.subscriptionId(), e.eventType(), e.previousStatus(), e.newStatus(),
                        e.actorType(), e.actorId(), e.payloadJson(), e.createdAt()
                )
        ).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Historial de eventos de auditoría de una suscripción específica.
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<Dtos.SubscriptionEventDto>> getSubscriptionEvents(HttpServletRequest request, @PathVariable("id") Long id) {
        validarAdmin(request);
        List<Dtos.SubscriptionEventDto> dtos = eventosSuscripcion.porSuscripcionId(id).stream().map(e ->
                new Dtos.SubscriptionEventDto(
                        e.id(), e.subscriptionId(), e.eventType(), e.previousStatus(), e.newStatus(),
                        e.actorType(), e.actorId(), e.payloadJson(), e.createdAt()
                )
        ).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Modificación manual del estado de una suscripción por el administrador.
     */
    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateSubscriptionStatus(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body
    ) {
        UsuarioJwt.Usuario u = validarAdmin(request);
        String newStatusStr = body != null ? body.get("status") : null;
        if (newStatusStr == null || newStatusStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'status' es obligatorio"));
        }

        Suscripciones.Suscripcion sub = suscripciones.porId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suscripción no encontrada"));

        String oldStatus = sub.status();
        String newStatus = newStatusStr.toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        boolean cancelAtPeriodEnd = sub.cancelAtPeriodEnd();
        LocalDateTime cancelledAt = sub.cancelledAt();

        if ("CANCELLED".equals(newStatus)) {
            cancelAtPeriodEnd = true;
            cancelledAt = now;
            if (sub.mpPreapprovalId() != null && !sub.mpPreapprovalId().isBlank()) {
                try {
                    mercadoPagoAdminApi.cancelarPreapproval(sub.mpPreapprovalId());
                } catch (Exception e) {
                    log.error("No se pudo cancelar el preapproval {} en Mercado Pago (suscripción #{}): {}",
                            sub.mpPreapprovalId(), sub.id(), e.getMessage(), e);
                }
            }
            try {
                String nombrePlan = planes.porId(sub.planId()).map(Planes.Plan::name).orElse("Profesional");
                avisoSuscripcionPublisher.publicar(sub, nombrePlan, SuscripcionAvisoData.CANCELACION_CONFIRMADA,
                        null, sub.currentPeriodEnd(), null, null, null, null);
            } catch (Exception e) {
                // ignore
            }
        } else if ("SUSPENDED".equals(newStatus)) {
            try {
                String nombrePlan = planes.porId(sub.planId()).map(Planes.Plan::name).orElse("Profesional");
                avisoSuscripcionPublisher.publicar(sub, nombrePlan, SuscripcionAvisoData.SUSPENSION,
                        null, null, null, null, null, null);
            } catch (Exception e) {
                // ignore
            }
        }

        Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                sub.id(), sub.version(), sub.professionalId(), sub.planId(), newStatus,
                sub.seats(), sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(),
                sub.billingCycle(), sub.nextBillingDate(), sub.currentPeriodStart(),
                sub.currentPeriodEnd(), sub.graceUntil(), cancelAtPeriodEnd, cancelledAt,
                sub.renewalReminderSent(), sub.createdAt(), now
        );
        suscripciones.actualizar(actualizada);

        suscripcionService.logEvent(sub.id(), "ADMIN_STATUS_CHANGE", oldStatus, newStatus,
                "ADMIN", u.email(), "Estado modificado manualmente por " + u.email());

        return ResponseEntity.ok(Map.of("id", sub.id(), "status", newStatus));
    }

    /**
     * Ejecuta el cron de reconciliación bajo demanda.
     */
    @PostMapping("/reconciliation/run")
    public ResponseEntity<?> triggerReconciliation(HttpServletRequest request) {
        validarAdmin(request);
        reconciliationScheduler.runDailyReconciliation();
        return ResponseEntity.ok(Map.of("message", "Reconciliación diaria ejecutada correctamente"));
    }
}
