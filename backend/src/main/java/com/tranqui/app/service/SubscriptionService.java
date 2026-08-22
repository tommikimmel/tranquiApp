package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private SubscriptionEventRepository eventRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Etapa 2 del paywall: crea el Preapproval de Mercado Pago (cobrado a la cuenta del admin) para
     * que este profesional pague/renueve su suscripción, y devuelve la URL de checkout a la que
     * hay que redirigirlo. No activa nada todavía — la activación real llega después, por
     * webhook (processMercadoPagoPreapprovalWebhook), cuando el profesional autoriza el pago.
     */
    @Transactional
    public String iniciarCheckout(Long professionalId, Long planId) {
        Usuario profesional = usuarioRepository.findById(professionalId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Profesional no encontrado"));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Plan no encontrado"));
        Subscription sub = subscriptionRepository.findByProfessionalId(professionalId)
                .orElseThrow(() -> new IllegalStateException("El profesional no tiene una suscripción registrada."));

        try {
            var preapproval = mercadoPagoService.crearSuscripcionPreapproval(profesional, plan, sub.getId());

            sub.setPlan(plan);
            sub.setMpPreapprovalId(preapproval.getId());
            sub.setBillingSource(BillingSource.MERCADOPAGO);
            sub.setAmountArs(plan.getPriceArs());
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);

            logEvent(sub.getId(), "MP_CHECKOUT_CREATED", "professional", String.valueOf(professionalId),
                    "Preapproval creado: " + preapproval.getId() + " para plan " + plan.getCode());

            return mercadoPagoService.checkoutUrlFor(preapproval);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creando checkout de Mercado Pago para profesional {}: {}", professionalId, e.getMessage(), e);
            throw new IllegalStateException("No se pudo iniciar el pago con Mercado Pago. Intentá de nuevo en unos minutos.", e);
        }
    }

    /**
     * Checks whether a professional has access to a particular system feature (Gate 1).
     */
    public boolean hasFeature(Long professionalId, String featureKey) {
        if (professionalId == null) return false;

        Usuario usuario = usuarioRepository.findById(professionalId).orElse(null);
        if (usuario == null) return false;

        // Admin has universal access
        if (usuario.getRol() == Rol.ADMIN) {
            return true;
        }

        // Check active subscription
        Optional<Subscription> subOpt = subscriptionRepository.findByProfessionalId(professionalId);
        if (subOpt.isEmpty()) {
            // Si el profesional no tiene suscripción aún (entorno de pruebas o profesionales existentes),
            // permitimos el acceso si su rol es psiquiatra/médico para recetas
            if (usuario.getRol() == Rol.PSIQUIATRA && "recetas_electronicas".equals(featureKey)) {
                return true;
            }
            return false;
        }

        Subscription sub = subOpt.get();
        if (sub.getStatus() != SubscriptionStatus.ACTIVE && sub.getStatus() != SubscriptionStatus.PAST_DUE) {
            return false;
        }

        Plan plan = sub.getPlan();
        if (plan == null) return false;

        return planFeatureRepository.existsByPlanIdAndFeatureKey(plan.getId(), featureKey);
    }

    /**
     * Paywall (Etapa 1): ¿este profesional tiene acceso real a la app en este momento?
     * Deliberadamente estricto y en tiempo real (no depende del cron diario de reconciliación,
     * que solo corre 1 vez por día y mira graceUntil, no currentPeriodEnd) — se evalúa en cada
     * request vía SubscriptionAccessFilter. Admin y roles no-profesionales (paciente, visitador)
     * nunca están sujetos a este gate.
     */
    public boolean isAccessAllowed(Long userId) {
        if (userId == null) return false;

        Usuario usuario = usuarioRepository.findById(userId).orElse(null);
        if (usuario == null) return false;
        if (usuario.getRol() != Rol.PSIQUIATRA) return true; // el paywall solo aplica a profesionales

        Subscription sub = subscriptionRepository.findByProfessionalId(userId).orElse(null);
        if (sub == null) return false; // sin fila de suscripción = sin acceso

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) return false;
        if (sub.getCurrentPeriodEnd() == null) return false; // ACTIVE sin período pagado = inconsistente, no dejamos pasar

        return sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now());
    }

    // Corre una sola vez por arranque real (idempotente: solo toca filas que nunca tuvieron
    // currentPeriodEnd, así que después de la primera pasada no vuelve a encontrar candidatos).
    // Migra a los profesionales que YA estaban verificados/usando la app antes de que existiera
    // este paywall, dándoles 14 días de acceso (ACTIVE) antes de que el filtro los bloquee, en
    // vez de cortarles el acceso de un día para el otro sin aviso. Los profesionales nuevos que
    // se registren de acá en más NO entran acá — arrancan en PENDING_VERIFICATION sin
    // currentPeriodEnd, así que el paywall los bloquea desde el primer momento hasta que paguen
    // (Etapa 2) o el admin les registre un pago manual.
    //
    // Cubre DOS casos, no solo uno: cuentas creadas antes de que existiera la tabla Subscription
    // (seed/DataInitializer, o cualquier alta anterior a este feature) no tienen NINGUNA fila —
    // a esas hay que crearles una desde cero, no solo completarles currentPeriodEnd. Se detectó
    // este caso en el primer deploy: TODOS los profesionales de producción no tenían fila de
    // suscripción y quedaron bloqueados porque la versión anterior de este método los saltaba.
    private static final int DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES = 14;

    @PostConstruct
    @Transactional
    public void migrateExistingVerifiedProfessionalsOnStartup() {
        try {
            List<Usuario> psiquiatras = usuarioRepository.findByRol(Rol.PSIQUIATRA);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime gracePeriodEnd = now.plusDays(DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES);
            int migrados = 0;

            for (Usuario u : psiquiatras) {
                if (!Boolean.TRUE.equals(u.getVerificadoAdmin())) continue;

                Subscription sub = subscriptionRepository.findByProfessionalId(u.getId()).orElse(null);
                if (sub != null && sub.getCurrentPeriodEnd() != null) continue; // ya tiene un período real (pago manual o migración previa)

                if (sub == null) {
                    // Rol=PSIQUIATRA implica médico en este sistema (no psicólogo) — "clinico" es
                    // el plan que incluye recetas_electronicas, así que migrarlos ahí evita
                    // sacarles una funcionalidad a la que ya tenían acceso libre antes del paywall.
                    Plan planMigracion = planRepository.findByCode("clinico").orElse(null);
                    if (planMigracion == null) {
                        log.error("Paywall: no se encontró el plan 'clinico' para migrar al profesional {} — se omite.", u.getId());
                        continue;
                    }
                    sub = Subscription.builder()
                            .professional(u)
                            .plan(planMigracion)
                            .status(SubscriptionStatus.ACTIVE)
                            .seats(1)
                            .billingSource(BillingSource.MANUAL_TRANSFER)
                            .amountArs(planMigracion.getPriceArs())
                            .currentPeriodStart(now)
                            .currentPeriodEnd(gracePeriodEnd)
                            .nextBillingDate(gracePeriodEnd)
                            .cancelAtPeriodEnd(false)
                            .build();
                    subscriptionRepository.save(sub);

                    logEvent(sub.getId(), "MIGRATION_GRACE_PERIOD_GRANTED", null, SubscriptionStatus.ACTIVE.name(),
                            "system", "startup",
                            "Profesional existente (sin fila de suscripción previa) migrado al paywall con " +
                                    DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES + " días de gracia, plan clinico. Vence: " + gracePeriodEnd);
                    migrados++;
                    continue;
                }

                SubscriptionStatus estadoPrevioMigracion = sub.getStatus();
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setCurrentPeriodStart(now);
                sub.setCurrentPeriodEnd(gracePeriodEnd);
                sub.setNextBillingDate(gracePeriodEnd);
                sub.setUpdatedAt(now);
                subscriptionRepository.save(sub);

                logEvent(sub.getId(), "MIGRATION_GRACE_PERIOD_GRANTED",
                        estadoPrevioMigracion != null ? estadoPrevioMigracion.name() : null, SubscriptionStatus.ACTIVE.name(),
                        "system", "startup",
                        "Profesional existente migrado al paywall con " + DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES +
                                " días de gracia. Vence: " + gracePeriodEnd);
                migrados++;
            }

            if (migrados > 0) {
                log.info("Paywall: {} profesional(es) existente(s) migrado(s) con {} días de gracia (vence {})",
                        migrados, DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES, gracePeriodEnd);
            }
        } catch (Exception e) {
            // Nunca debe tumbar el arranque del server por esto — si falla, simplemente esos
            // profesionales quedan sin migrar y se pueden migrar a mano después (o en el próximo restart).
            log.error("Error migrando profesionales existentes al paywall de suscripciones: {}", e.getMessage(), e);
        }
    }

    /**
     * Valida el Doble Gate (§7) para recetas electrónicas:
     * Gate 1: Plan contratado incluye 'recetas_electronicas'
     * Gate 2: Capacidad legal (médico / psiquiatra habilitado)
     */
    public void validarAccesoRecetas(Usuario prof) {
        if (prof == null) {
            throw new IllegalArgumentException("Usuario no autenticado");
        }

        if (prof.getRol() == Rol.ADMIN) {
            return;
        }

        // Gate 1: Plan
        if (!hasFeature(prof.getId(), "recetas_electronicas")) {
            throw new IllegalStateException("Tu plan actual no incluye recetas electrónicas oficiales. Podés actualizar tu suscripción al plan Clínico.");
        }

        // Gate 2: Capacidad legal independiente
        if (prof.getRol() != Rol.PSIQUIATRA && !prof.isCanPrescribe()) {
            throw new IllegalStateException("Tu matrícula profesional no se encuentra verificada y habilitada para prescribir medicamentos.");
        }
    }

    /**
     * Registra un pago manual (efectivo / transferencia / cortesía) desde el panel de admin.
     * Implementa protección contra doble submit vía idempotency_key (§8).
     */
    @Transactional
    public SubscriptionPayment registerManualPayment(
            Long adminId,
            Long professionalId,
            Long planId,
            BigDecimal amountArs,
            String method, // MANUAL_CASH | MANUAL_TRANSFER | COURTESY
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            String receiptReference,
            String notes,
            boolean emitInvoice,
            String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }

        // 1. Idempotencia: si ya existe el pago con esa clave, retornarlo sin duplicar
        Optional<SubscriptionPayment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Pago idempotente ya registrado con key {}", idempotencyKey);
            return existingPayment.get();
        }

        // 2. Precondición fiscal si se va a emitir factura
        Usuario prof = usuarioRepository.findById(professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado: " + professionalId));

        if (emitInvoice && !"COURTESY".equalsIgnoreCase(method)) {
            if (prof.getEffectiveTaxId() == null || prof.getEffectiveTaxId().isBlank()) {
                throw new IllegalStateException("No se puede emitir factura: el profesional no tiene CUIT/CUIL cargado");
            }
        }

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + planId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = periodStart != null ? periodStart : now;
        LocalDateTime end = periodEnd != null ? periodEnd : now.plusMonths(1);
        LocalDateTime grace = end.plusDays(7); // 7 días de ventana de gracia

        BillingSource source;
        try {
            source = BillingSource.valueOf(method.toUpperCase());
        } catch (Exception e) {
            source = BillingSource.MANUAL_TRANSFER;
        }

        // 3. Upsert suscripción
        Subscription subscription = subscriptionRepository.findByProfessionalId(prof.getId())
                .orElseGet(() -> Subscription.builder()
                        .professional(prof)
                        .createdAt(now)
                        .build());

        SubscriptionStatus estadoPrevioPagoManual = subscription.getStatus();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingSource(source);
        subscription.setAmountArs(amountArs != null ? amountArs : plan.getPriceArs());
        subscription.setCurrentPeriodStart(start);
        subscription.setCurrentPeriodEnd(end);
        subscription.setNextBillingDate(end);
        subscription.setGraceUntil(grace);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setUpdatedAt(now);

        subscription = subscriptionRepository.save(subscription);

        // 4. Insertar registro en subscription_payments
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .subscriptionId(subscription.getId())
                .professionalId(prof.getId())
                .idempotencyKey(idempotencyKey)
                .method(source.name())
                .status("APPROVED")
                .amountArs(amountArs != null ? amountArs : plan.getPriceArs())
                .paidAt(now)
                .periodStart(start)
                .periodEnd(end)
                .registeredByAdminId(adminId)
                .receiptReference(receiptReference)
                .notes(notes)
                .createdAt(now)
                .build();

        payment = paymentRepository.save(payment);

        // 5. Auditoría en subscription_events (Ley 25.326)
        logEvent(subscription.getId(), "MANUAL_PAYMENT_ACTIVATED",
                estadoPrevioPagoManual != null ? estadoPrevioPagoManual.name() : null, SubscriptionStatus.ACTIVE.name(),
                "ADMIN", String.valueOf(adminId),
                "Método: " + method + ", Monto: " + payment.getAmountArs() + ", Período hasta: " + end);

        // 6. Facturación ARCA si corresponde
        if (emitInvoice && !"COURTESY".equalsIgnoreCase(method)) {
            try {
                invoiceService.generateInvoiceForPayment(payment.getId());
            } catch (Exception e) {
                log.error("Error emitiendo factura ARCA para pago manual {}: {}", payment.getId(), e.getMessage(), e);
            }
        }

        return payment;
    }

    /**
     * Procesa cobro exitoso proveniente del Webhook de Mercado Pago (subscription_authorized_payment).
     */
    @Transactional
    public SubscriptionPayment processMercadoPagoPaymentWebhook(
            String mpPaymentId,
            String mpPreapprovalId,
            BigDecimal amount,
            String status
    ) {
        Optional<SubscriptionPayment> existing = paymentRepository.findByMpPaymentId(mpPaymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Subscription sub = subscriptionRepository.findByMpPreapprovalId(mpPreapprovalId)
                .orElse(null);

        if (sub == null) {
            log.warn("Webhook Mercado Pago recibido para preapproval desconocido: {}", mpPreapprovalId);
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextEnd = now.plusMonths(1);

        SubscriptionStatus estadoPrevioPagoMp = sub.getStatus();
        SubscriptionStatus estadoNuevoPagoMp = "approved".equalsIgnoreCase(status) ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PAST_DUE;
        sub.setStatus(estadoNuevoPagoMp);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(nextEnd);
        sub.setNextBillingDate(nextEnd);
        sub.setGraceUntil(nextEnd.plusDays(7));
        subscriptionRepository.save(sub);

        String idemKey = "MP-" + mpPaymentId;
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .subscriptionId(sub.getId())
                .professionalId(sub.getProfessional().getId())
                .mpPaymentId(mpPaymentId)
                .idempotencyKey(idemKey)
                .method("MERCADOPAGO")
                .status("approved".equalsIgnoreCase(status) ? "APPROVED" : "REJECTED")
                .amountArs(amount != null ? amount : sub.getAmountArs())
                .paidAt(now)
                .periodStart(now)
                .periodEnd(nextEnd)
                .receiptReference("MP ID " + mpPaymentId)
                .createdAt(now)
                .build();

        payment = paymentRepository.save(payment);

        logEvent(sub.getId(), "MP_SUBSCRIPTION_PAYMENT",
                estadoPrevioPagoMp != null ? estadoPrevioPagoMp.name() : null, estadoNuevoPagoMp.name(),
                "MP_WEBHOOK", mpPaymentId,
                "Estado: " + status + ", Monto: " + payment.getAmountArs());

        if ("APPROVED".equalsIgnoreCase(payment.getStatus())) {
            try {
                invoiceService.generateInvoiceForPayment(payment.getId());
            } catch (Exception e) {
                log.error("Error emitiendo factura ARCA para pago MP {}: {}", payment.getId(), e.getMessage());
            }
        }

        return payment;
    }

    /**
     * Actualiza el estado de preapproval de Mercado Pago (authorized, paused, cancelled).
     */
    @Transactional
    public void processMercadoPagoPreapprovalWebhook(String mpPreapprovalId, String status) {
        Subscription sub = subscriptionRepository.findByMpPreapprovalId(mpPreapprovalId).orElse(null);
        if (sub == null) return;

        SubscriptionStatus oldStatus = sub.getStatus();
        SubscriptionStatus newStatus = switch (status.toLowerCase()) {
            case "authorized" -> SubscriptionStatus.ACTIVE;
            case "paused" -> SubscriptionStatus.PAST_DUE;
            case "cancelled" -> SubscriptionStatus.CANCELLED;
            default -> oldStatus;
        };

        sub.setStatus(newStatus);
        if (newStatus == SubscriptionStatus.CANCELLED) {
            sub.setCancelledAt(LocalDateTime.now());
        }
        // Activa el período de acceso acá mismo en vez de esperar al webhook separado
        // subscription_authorized_payment (que confirma el primer cobro, pero puede tardar más en
        // llegar) — así isAccessAllowed() desbloquea al profesional apenas autoriza, no cuando MP
        // termina de procesar el cargo. Si currentPeriodEnd ya está vigente (ej. venía de la
        // migración de gracia), no lo pisa para no acortarle el acceso que ya tenía.
        if (newStatus == SubscriptionStatus.ACTIVE
                && (sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isBefore(LocalDateTime.now()))) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime periodEnd = now.plusMonths(1);
            sub.setCurrentPeriodStart(now);
            sub.setCurrentPeriodEnd(periodEnd);
            sub.setNextBillingDate(periodEnd);
            sub.setGraceUntil(periodEnd.plusDays(7));
        }
        subscriptionRepository.save(sub);

        logEvent(sub.getId(), "MP_PREAPPROVAL_STATUS_CHANGED", oldStatus.name(), newStatus.name(),
                "MP_WEBHOOK", mpPreapprovalId,
                "Cambio de estado: " + oldStatus + " -> " + newStatus);
    }

    public void logEvent(Long subscriptionId, String eventType, String actorType, String actorId, String payloadJson) {
        logEvent(subscriptionId, eventType, null, null, actorType, actorId, payloadJson);
    }

    /**
     * Variante para eventos que sí representan una transición de estado de la suscripción
     * (ej. ACTIVE -> SUSPENDED), para que el panel de admin pueda mostrar "de dónde a dónde"
     * pasó la suscripción en cada evento del historial de auditoría.
     */
    public void logEvent(Long subscriptionId, String eventType, String previousStatus, String newStatus,
                          String actorType, String actorId, String payloadJson) {
        SubscriptionEvent event = SubscriptionEvent.builder()
                .subscriptionId(subscriptionId)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .actorType(actorType)
                .actorId(actorId)
                .payloadJson(payloadJson)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    public Optional<Subscription> getSubscriptionForProfessional(Long professionalId) {
        return subscriptionRepository.findByProfessionalId(professionalId);
    }

    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    /**
     * Retorna métricas clave para el panel de administración:
     * - Suscriptores activos por plan
     * - MRR (Monthly Recurring Revenue)
     * - Acumulado rodante 12 meses vs Tope de Monotributo Categoría A / B
     */
    public Map<String, Object> getAdminOverviewStats() {
        List<Subscription> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
        long totalActive = activeSubs.size();

        // "En período de gracia" = suscripciones PAST_DUE: el cobro venció pero todavía tienen
        // acceso mientras corre la ventana de graceUntil (ver SubscriptionReconciliationScheduler,
        // que las suspende automáticamente cuando esa ventana vence).
        long totalEnGracia = subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE).size();

        BigDecimal mrr = activeSubs.stream()
                .map(Subscription::getAmountArs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        BigDecimal rolling12m = paymentRepository.sumApprovedPaymentsSince(twelveMonthsAgo);

        // Topes Monotributo vigentes agosto 2026 (§3)
        BigDecimal topeCatA = new BigDecimal("12009410.00");
        BigDecimal topeCatB = new BigDecimal("17595183.00");
        BigDecimal topeCatC = new BigDecimal("24670494.00");
        BigDecimal topeCatK = new BigDecimal("126610839.00");

        double percentCatA = rolling12m.divide(topeCatA, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
        boolean alerta80Porciento = percentCatA >= 80.0;

        // Nombres en español: son los que lee AdminSubscriptionsView.tsx en el frontend. Antes
        // estas claves no coincidían (ej. acá se mandaba "activeSubscribers" pero el frontend leía
        // "suscriptoresActivos"), por eso las cards de "Suscriptores Activos" y "En Período de
        // Gracia" siempre se veían vacías pese a que el backend sí tenía los datos.
        Map<String, Object> stats = new HashMap<>();
        stats.put("suscriptoresActivos", totalActive);
        stats.put("suscriptoresEnGracia", totalEnGracia);
        stats.put("mrr", mrr);
        stats.put("totalFacturado12Meses", rolling12m);
        stats.put("topeCategoriaA", topeCatA);
        stats.put("topeCatB", topeCatB);
        stats.put("topeCatK", topeCatK);
        stats.put("porcentajeUsoCategoriaA", Math.round(percentCatA * 10.0) / 10.0);
        stats.put("alertaMonotributo80", alerta80Porciento);

        return stats;
    }
}
