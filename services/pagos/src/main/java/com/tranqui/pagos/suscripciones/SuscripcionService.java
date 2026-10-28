package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.mp.MercadoPagoAdminApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lógica de negocio de suscripciones, traducción fiel de SubscriptionService y PlanService del monolito
 * a JDBC sobre las clases de datos de S2a.
 *
 * <p>NOTA: A diferencia del monolito, en este servicio NO hay siembra de planes (@PostConstruct) ni migración
 * de 14 días de gracia al arrancar: los planes y las suscripciones iniciales provienen de la carga inicial
 * (CargaInicialSuscripciones) efectuada durante la ventana de mantenimiento del corte.
 */
@Service
public class SuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);

    private final Suscripciones suscripciones;
    private final Planes planes;
    private final PagosSuscripcion pagosSuscripcion;
    private final EventosSuscripcion eventosSuscripcion;
    private final Suscriptores suscriptores;
    private final OutboxPublisher outboxPublisher;
    private final AvisoSuscripcionPublisher avisoSuscripcionPublisher;
    private final FacturacionSuscripciones facturacionSuscripciones;
    private final MercadoPagoAdminApi mercadoPagoAdminApi;

    public SuscripcionService(
            Suscripciones suscripciones,
            Planes planes,
            PagosSuscripcion pagosSuscripcion,
            EventosSuscripcion eventosSuscripcion,
            Suscriptores suscriptores,
            OutboxPublisher outboxPublisher,
            AvisoSuscripcionPublisher avisoSuscripcionPublisher,
            FacturacionSuscripciones facturacionSuscripciones,
            MercadoPagoAdminApi mercadoPagoAdminApi
    ) {
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.pagosSuscripcion = pagosSuscripcion;
        this.eventosSuscripcion = eventosSuscripcion;
        this.suscriptores = suscriptores;
        this.outboxPublisher = outboxPublisher;
        this.avisoSuscripcionPublisher = avisoSuscripcionPublisher;
        this.facturacionSuscripciones = facturacionSuscripciones;
        this.mercadoPagoAdminApi = mercadoPagoAdminApi;
    }

    /**
     * Inicia el checkout de Mercado Pago (preapproval recurrente) para un profesional.
     */
    @Transactional
    public String iniciarCheckout(Long professionalId, Long planId, String billingCycleInput) {
        Suscriptores.Suscriptor profesional = suscriptores.porId(professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado"));
        Planes.Plan plan = planes.porId(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado"));

        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(professionalId)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    Suscripciones.Suscripcion nueva = new Suscripciones.Suscripcion(
                            null, 0, professionalId, plan.id(), "PENDING_VERIFICATION", 1,
                            "MANUAL_TRANSFER", null, plan.priceArs(), "monthly", null, null,
                            null, null, false, null, false, now, now
                    );
                    Long id = suscripciones.insertarNuevo(nueva);
                    return suscripciones.porId(id).orElseThrow();
                });

        String billingCycle = "annual".equalsIgnoreCase(billingCycleInput) ? "annual" : "monthly";
        if ("annual".equals(billingCycle) && plan.priceArsAnual() == null) {
            throw new IllegalStateException("El plan " + plan.name() + " todavía no tiene precio anual configurado.");
        }
        BigDecimal montoElegido = "annual".equals(billingCycle) ? plan.priceArsAnual() : plan.priceArs();

        if (sub.mpPreapprovalId() != null && !sub.mpPreapprovalId().isBlank()) {
            try {
                log.info("Cancelando preapproval previo {} en Mercado Pago para profesional {}", sub.mpPreapprovalId(), professionalId);
                mercadoPagoAdminApi.cancelarPreapproval(sub.mpPreapprovalId());
            } catch (Exception e) {
                log.warn("No se pudo cancelar el preapproval previo {} en Mercado Pago: {}", sub.mpPreapprovalId(), e.getMessage());
            }
        }

        try {
            MercadoPagoAdminApi.PreapprovalMp preapproval = mercadoPagoAdminApi.crearPreapproval(
                    profesional.email(), plan.name(), montoElegido, "annual".equals(billingCycle), sub.id()
            );

            Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                    sub.id(), sub.version(), sub.professionalId(), plan.id(), sub.status(), sub.seats(),
                    "MERCADOPAGO", preapproval.id(), montoElegido, billingCycle, sub.nextBillingDate(),
                    sub.currentPeriodStart(), sub.currentPeriodEnd(), sub.graceUntil(), sub.cancelAtPeriodEnd(),
                    sub.cancelledAt(), sub.renewalReminderSent(), sub.createdAt(), LocalDateTime.now()
            );
            suscripciones.actualizar(actualizada);

            logEvent(sub.id(), "MP_CHECKOUT_CREATED", null, null, "professional", String.valueOf(professionalId),
                    "Preapproval creado: " + preapproval.id() + " para plan " + plan.code() + " (" + billingCycle + ")");

            return preapproval.initPoint();
        } catch (IllegalStateException e) {
            throw e;
        } catch (com.mercadopago.exceptions.MPApiException e) {
            Integer status = e.getApiResponse() != null ? e.getApiResponse().getStatusCode() : null;
            String detalle = e.getApiResponse() != null ? e.getApiResponse().getContent() : e.getMessage();
            log.error("Error de la API de Mercado Pago creando checkout para profesional {} (status {}): {}",
                    professionalId, status, detalle);
            String mensaje = (status != null && status == 429)
                    ? "Mercado Pago está limitando temporalmente los intentos de pago. Esperá unos minutos y volvé a intentar."
                    : "No se pudo iniciar el pago con Mercado Pago. Intentá de nuevo en unos minutos.";
            throw new IllegalStateException(mensaje, e);
        } catch (Exception e) {
            log.error("Error creando checkout de Mercado Pago para profesional {}: {}", professionalId, e.getMessage(), e);
            throw new IllegalStateException("No se pudo iniciar el pago con Mercado Pago. Intentá de nuevo en unos minutos.", e);
        }
    }

    /**
     * Evalúa si el profesional tiene habilitada una funcionalidad según su plan y profesión.
     */
    public boolean hasFeature(Long professionalId, String featureKey) {
        if (professionalId == null) return false;

        Suscriptores.Suscriptor usuario = suscriptores.porId(professionalId).orElse(null);
        if (usuario == null) return false;

        if ("ADMIN".equals(usuario.rol())) {
            return true;
        }

        Optional<Suscripciones.Suscripcion> subOpt = suscripciones.porProfesionalId(professionalId);
        if (subOpt.isEmpty()) {
            if ("recetas_electronicas".equals(featureKey) && esPsiquiatraOMedico(usuario)) {
                return true;
            }
            return false;
        }

        Suscripciones.Suscripcion sub = subOpt.get();
        boolean periodoVigente = sub.currentPeriodEnd() != null && sub.currentPeriodEnd().isAfter(LocalDateTime.now());
        boolean statusPermitido = "ACTIVE".equals(sub.status())
                || "PAST_DUE".equals(sub.status())
                || ("CANCELLED".equals(sub.status()) && periodoVigente);

        if (!statusPermitido) {
            return false;
        }

        if (sub.planId() == null) return false;

        return planes.existePorPlanIdYFeatureKey(sub.planId(), featureKey);
    }

    private boolean esPsiquiatraOMedico(Suscriptores.Suscriptor usuario) {
        String profession = usuario.profession();
        return profession == null || profession.isBlank() || !"psicologo".equalsIgnoreCase(profession.trim());
    }

    /**
     * Evalúa si el profesional tiene acceso al sistema según el paywall en tiempo real.
     */
    public boolean isAccessAllowed(Long userId) {
        if (userId == null) return false;

        Suscriptores.Suscriptor usuario = suscriptores.porId(userId).orElse(null);
        if (usuario == null) return false;
        if (!"PSIQUIATRA".equals(usuario.rol())) return true;

        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(userId).orElse(null);
        if (sub == null) return false;

        boolean periodoVigente = sub.currentPeriodEnd() != null && sub.currentPeriodEnd().isAfter(LocalDateTime.now());
        boolean statusValido = "ACTIVE".equals(sub.status())
                || ("CANCELLED".equals(sub.status()) && periodoVigente);

        if (!statusValido) return false;
        if (sub.currentPeriodEnd() == null) return false;

        return periodoVigente;
    }

    /**
     * Cancela la renovación recurrente de la suscripción a pedido del profesional.
     */
    @Transactional
    public Suscripciones.Suscripcion cancelarSuscripcionPorProfesional(Long professionalId) {
        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Suscripción no encontrada para el profesional: " + professionalId));

        if (Boolean.TRUE.equals(sub.cancelAtPeriodEnd())) {
            return sub;
        }

        LocalDateTime now = LocalDateTime.now();

        if (sub.mpPreapprovalId() != null && !sub.mpPreapprovalId().isBlank()
                && "MERCADOPAGO".equalsIgnoreCase(sub.billingSource())) {
            try {
                log.info("Cancelando suscripción en Mercado Pago (preapproval: {}) para profesional {}", sub.mpPreapprovalId(), professionalId);
                mercadoPagoAdminApi.cancelarPreapproval(sub.mpPreapprovalId());
            } catch (com.mercadopago.exceptions.MPApiException e) {
                String detalle = e.getApiResponse() != null ? e.getApiResponse().getContent() : e.getMessage();
                log.error("Error de la API de Mercado Pago cancelando preapproval {} para profesional {} (status {}): {}",
                        sub.mpPreapprovalId(), professionalId,
                        e.getApiResponse() != null ? e.getApiResponse().getStatusCode() : null, detalle);
                throw new IllegalStateException(
                        "No se pudo cancelar el cobro recurrente en Mercado Pago. Intentá de nuevo o escribinos a soporte.", e);
            } catch (Exception e) {
                log.error("Error cancelando preapproval {} en Mercado Pago para profesional {}: {}",
                        sub.mpPreapprovalId(), professionalId, e.getMessage(), e);
                throw new IllegalStateException(
                        "No se pudo cancelar el cobro recurrente en Mercado Pago. Intentá de nuevo o escribinos a soporte.", e);
            }
        }

        String estadoPrevio = sub.status();
        boolean periodoVigente = sub.currentPeriodEnd() != null && sub.currentPeriodEnd().isAfter(now);
        String nuevoEstado = periodoVigente ? "ACTIVE" : "CANCELLED";

        Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                sub.id(), sub.version(), sub.professionalId(), sub.planId(), nuevoEstado, sub.seats(),
                sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(), sub.billingCycle(),
                sub.nextBillingDate(), sub.currentPeriodStart(), sub.currentPeriodEnd(), sub.graceUntil(),
                true, now, sub.renewalReminderSent(), sub.createdAt(), now
        );
        suscripciones.actualizar(actualizada);
        sub = suscripciones.porId(sub.id()).orElse(actualizada);

        logEvent(sub.id(), "SUBSCRIPTION_CANCELLED_BY_PROFESSIONAL",
                estadoPrevio, sub.status(),
                "PROFESSIONAL", String.valueOf(professionalId),
                "Renovación cancelada por el profesional. Acceso habilitado hasta: " + sub.currentPeriodEnd());

        String nombrePlan = planes.porId(sub.planId()).map(Planes.Plan::name).orElse("Profesional");
        avisoSuscripcionPublisher.publicar(sub, nombrePlan, SuscripcionAvisoData.CANCELACION_CONFIRMADA, null,
                sub.currentPeriodEnd(), null, null, null, null);

        return sub;
    }

    /**
     * Registra un pago manual idempotente por clave.
     */
    @Transactional
    public PagosSuscripcion.PagoSuscripcion registerManualPayment(
            Long adminId,
            Long professionalId,
            Long planId,
            BigDecimal amountArs,
            String method,
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

        Optional<PagosSuscripcion.PagoSuscripcion> existente = pagosSuscripcion.porClaveIdempotencia(idempotencyKey);
        if (existente.isPresent()) {
            log.info("Pago idempotente ya registrado con key {}", idempotencyKey);
            return existente.get();
        }

        Suscriptores.Suscriptor prof = suscriptores.porId(professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado: " + professionalId));
        Planes.Plan plan = planes.porId(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + planId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = periodStart != null ? periodStart : now;
        LocalDateTime end = periodEnd != null ? periodEnd : now.plusMonths(1);
        LocalDateTime grace = end.plusDays(7);

        String source = method != null && !method.isBlank() ? method.toUpperCase() : "MANUAL_TRANSFER";

        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(prof.id()).orElse(null);
        String estadoPrevioPagoManual = sub != null ? sub.status() : null;

        if (sub == null) {
            Suscripciones.Suscripcion nueva = new Suscripciones.Suscripcion(
                    null, 0, prof.id(), plan.id(), "ACTIVE", 1, source, null,
                    amountArs != null ? amountArs : plan.priceArs(), "monthly", end,
                    start, end, grace, false, null, false, now, now
            );
            Long id = suscripciones.insertarNuevo(nueva);
            sub = suscripciones.porId(id).orElseThrow();
        } else {
            Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                    sub.id(), sub.version(), sub.professionalId(), plan.id(), "ACTIVE",
                    sub.seats() != null ? sub.seats() : 1, source, sub.mpPreapprovalId(),
                    amountArs != null ? amountArs : plan.priceArs(), sub.billingCycle(),
                    end, start, end, grace, false, null, false, sub.createdAt(), now
            );
            suscripciones.actualizar(actualizada);
            sub = suscripciones.porId(sub.id()).orElse(actualizada);
        }

        PagosSuscripcion.PagoSuscripcion pago = new PagosSuscripcion.PagoSuscripcion(
                null, sub.id(), prof.id(), null, idempotencyKey, source, "APPROVED",
                amountArs != null ? amountArs : plan.priceArs(), now, start, end, null,
                adminId, receiptReference, notes, now
        );
        Long pagoId = pagosSuscripcion.insertarNuevo(pago);
        pago = pagosSuscripcion.porId(pagoId).orElseThrow();

        logEvent(sub.id(), "MANUAL_PAYMENT_ACTIVATED",
                estadoPrevioPagoManual, "ACTIVE",
                "ADMIN", String.valueOf(adminId),
                "Método: " + method + ", Monto: " + pago.amountArs() + ", Período hasta: " + end);

        if (emitInvoice && !"COURTESY".equalsIgnoreCase(method)) {
            emitirFacturaTrasCommit(pago.id(), "pago manual");
        }

        if (!"COURTESY".equalsIgnoreCase(method)) {
            avisoSuscripcionPublisher.publicar(sub, plan.name(), SuscripcionAvisoData.PAGO_CONFIRMADO, pago.amountArs(),
                    start, end, null,
                    receiptReference != null && !receiptReference.isBlank() ? receiptReference : "Pago Manual " + method, null);
        }

        return pago;
    }

    /**
     * Emite la factura electrónica tras el commit de la transacción actual.
     */
    public void emitirFacturaTrasCommit(Long paymentId, String contexto) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            facturacionSuscripciones.emitirFactura(paymentId, contexto);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                facturacionSuscripciones.emitirFactura(paymentId, contexto);
            }
        });
    }

    /**
     * Procesa cobro exitoso proveniente del webhook subscription_authorized_payment.
     */
    @Transactional
    public PagosSuscripcion.PagoSuscripcion processMercadoPagoPaymentWebhook(
            String mpPaymentId,
            String mpPreapprovalId,
            BigDecimal amount,
            String status
    ) {
        Optional<PagosSuscripcion.PagoSuscripcion> existente = pagosSuscripcion.porMpPaymentId(mpPaymentId);
        if (existente.isPresent()) {
            return existente.get();
        }

        Suscripciones.Suscripcion sub = suscripciones.porMpPreapprovalId(mpPreapprovalId).orElse(null);
        if (sub == null) {
            log.warn("Webhook Mercado Pago recibido para preapproval desconocido: {}", mpPreapprovalId);
            return null;
        }

        return activarPagoSuscripcion(sub, mpPaymentId, amount, status);
    }

    /**
     * Procesa cobro exitoso proveniente del webhook payment genérico con external_reference sub-{id}.
     */
    @Transactional
    public PagosSuscripcion.PagoSuscripcion processMercadoPagoPaymentByExternalReference(
            String externalReference,
            String mpPaymentId,
            BigDecimal amount,
            String status
    ) {
        if (externalReference == null || !externalReference.startsWith("sub-")) {
            return null;
        }
        Long subscriptionId;
        try {
            subscriptionId = Long.parseLong(externalReference.substring(4));
        } catch (NumberFormatException e) {
            log.warn("external_reference de suscripción con formato inesperado: {}", externalReference);
            return null;
        }

        Optional<PagosSuscripcion.PagoSuscripcion> existente = pagosSuscripcion.porMpPaymentId(mpPaymentId);
        if (existente.isPresent()) {
            return existente.get();
        }

        Suscripciones.Suscripcion sub = suscripciones.porId(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Webhook de pago de Mercado Pago recibido para suscripción desconocida: {}", subscriptionId);
            return null;
        }

        return activarPagoSuscripcion(sub, mpPaymentId, amount, status);
    }

    private PagosSuscripcion.PagoSuscripcion activarPagoSuscripcion(Suscripciones.Suscripcion sub, String mpPaymentId, BigDecimal amount, String status) {
        LocalDateTime now = LocalDateTime.now();
        int meses = "annual".equalsIgnoreCase(sub.billingCycle()) ? 12 : 1;
        LocalDateTime nextEnd = now.plusMonths(meses);

        String estadoPrevioPagoMp = sub.status();
        String estadoNuevoPagoMp = "approved".equalsIgnoreCase(status) ? "ACTIVE" : "PAST_DUE";

        Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                sub.id(), sub.version(), sub.professionalId(), sub.planId(), estadoNuevoPagoMp,
                sub.seats() != null ? sub.seats() : 1, sub.billingSource(), sub.mpPreapprovalId(),
                sub.amountArs(), sub.billingCycle(), nextEnd, now, nextEnd, nextEnd.plusDays(7),
                sub.cancelAtPeriodEnd(), sub.cancelledAt(), false, sub.createdAt(), now
        );
        suscripciones.actualizar(actualizada);
        sub = suscripciones.porId(sub.id()).orElse(actualizada);

        String idemKey = "MP-" + mpPaymentId;
        BigDecimal montoFinal = amount != null ? amount : sub.amountArs();
        PagosSuscripcion.PagoSuscripcion pago = new PagosSuscripcion.PagoSuscripcion(
                null, sub.id(), sub.professionalId(), mpPaymentId, idemKey, "MERCADOPAGO",
                "approved".equalsIgnoreCase(status) ? "APPROVED" : "REJECTED",
                montoFinal, now, now, nextEnd, null, null, "MP ID " + mpPaymentId, null, now
        );
        Long pagoId = pagosSuscripcion.insertarNuevo(pago);
        pago = pagosSuscripcion.porId(pagoId).orElseThrow();

        logEvent(sub.id(), "MP_SUBSCRIPTION_PAYMENT",
                estadoPrevioPagoMp, estadoNuevoPagoMp,
                "MP_WEBHOOK", mpPaymentId,
                "Estado: " + status + ", Monto: " + pago.amountArs());

        String nombrePlan = planes.porId(sub.planId()).map(Planes.Plan::name).orElse("Profesional");

        if ("APPROVED".equalsIgnoreCase(pago.status())) {
            emitirFacturaTrasCommit(pago.id(), "pago MP");
            avisoSuscripcionPublisher.publicar(sub, nombrePlan, SuscripcionAvisoData.PAGO_CONFIRMADO, pago.amountArs(),
                    sub.currentPeriodStart(), sub.currentPeriodEnd(), null, pago.receiptReference(), null);
        } else {
            avisoSuscripcionPublisher.publicar(sub, nombrePlan, SuscripcionAvisoData.PAGO_FALLIDO, pago.amountArs(),
                    sub.graceUntil(), null, null, null, null);
        }

        return pago;
    }

    /**
     * Procesa cambios de estado recibidos por webhook subscription_preapproval.
     */
    @Transactional
    public void processMercadoPagoPreapprovalWebhook(String mpPreapprovalId, String status) {
        Suscripciones.Suscripcion sub = suscripciones.porMpPreapprovalId(mpPreapprovalId).orElse(null);
        if (sub == null) return;

        String oldStatus = sub.status();
        String newStatus = switch (status != null ? status.toLowerCase() : "") {
            case "authorized" -> "ACTIVE";
            case "paused" -> "PAST_DUE";
            case "cancelled" -> "CANCELLED";
            default -> oldStatus;
        };

        boolean cancelAtPeriodEnd = sub.cancelAtPeriodEnd();
        LocalDateTime cancelledAt = sub.cancelledAt();
        LocalDateTime currentPeriodStart = sub.currentPeriodStart();
        LocalDateTime currentPeriodEnd = sub.currentPeriodEnd();
        LocalDateTime nextBillingDate = sub.nextBillingDate();
        LocalDateTime graceUntil = sub.graceUntil();
        String finalStatus = newStatus;

        if ("CANCELLED".equals(newStatus)) {
            cancelAtPeriodEnd = true;
            cancelledAt = LocalDateTime.now();
            if (currentPeriodEnd != null && currentPeriodEnd.isAfter(LocalDateTime.now())) {
                finalStatus = "ACTIVE";
            }
            String nombrePlan = planes.porId(sub.planId()).map(Planes.Plan::name).orElse("Profesional");
            avisoSuscripcionPublisher.publicar(sub, nombrePlan, SuscripcionAvisoData.CANCELACION_CONFIRMADA, null,
                    currentPeriodEnd, null, null, null, null);
        }

        if ("ACTIVE".equals(finalStatus)
                && (currentPeriodEnd == null || currentPeriodEnd.isBefore(LocalDateTime.now()))) {
            LocalDateTime now = LocalDateTime.now();
            int meses = "annual".equalsIgnoreCase(sub.billingCycle()) ? 12 : 1;
            LocalDateTime periodEnd = now.plusMonths(meses);
            currentPeriodStart = now;
            currentPeriodEnd = periodEnd;
            nextBillingDate = periodEnd;
            graceUntil = periodEnd.plusDays(7);
        }

        Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                sub.id(), sub.version(), sub.professionalId(), sub.planId(), finalStatus,
                sub.seats() != null ? sub.seats() : 1, sub.billingSource(), sub.mpPreapprovalId(),
                sub.amountArs(), sub.billingCycle(), nextBillingDate, currentPeriodStart,
                currentPeriodEnd, graceUntil, cancelAtPeriodEnd, cancelledAt, sub.renewalReminderSent(),
                sub.createdAt(), LocalDateTime.now()
        );
        suscripciones.actualizar(actualizada);

        logEvent(sub.id(), "MP_PREAPPROVAL_STATUS_CHANGED", oldStatus, newStatus,
                "MP_WEBHOOK", mpPreapprovalId,
                "Cambio de estado: " + oldStatus + " -> " + newStatus);
    }

    /**
     * Registra un evento de auditoría y, si cambió el estado, publica cambio-estado.v2 por outbox.
     */
    @Transactional
    public void logEvent(Long subscriptionId, String eventType, String previousStatus, String newStatus,
                         String actorType, String actorId, String payloadJson) {
        EventosSuscripcion.Evento evento = new EventosSuscripcion.Evento(
                null, subscriptionId, eventType, previousStatus, newStatus, actorType, actorId, payloadJson, LocalDateTime.now()
        );
        eventosSuscripcion.insertarNuevo(evento);

        if (previousStatus != null && newStatus != null && !previousStatus.equals(newStatus)) {
            suscripciones.porId(subscriptionId).ifPresent(sub -> {
                LocalDate vencimiento = sub.currentPeriodEnd() != null ? sub.currentPeriodEnd().toLocalDate() : null;
                boolean acceso = isAccessAllowed(sub.professionalId());
                boolean puedeRecetar = hasFeature(sub.professionalId(), "recetas_electronicas");
                String planCode = planes.porId(sub.planId()).map(Planes.Plan::code).orElse(null);
                LocalDateTime periodoHasta = sub.currentPeriodEnd();
                LocalDateTime graciaHasta = sub.graceUntil();

                outboxPublisher.publicar(
                        Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2,
                        String.valueOf(sub.id()),
                        sub.aggregateVersion(),
                        new SuscripcionCambioEstadoV2Data(
                                sub.id(),
                                sub.professionalId(),
                                previousStatus,
                                newStatus,
                                vencimiento,
                                planCode,
                                acceso,
                                puedeRecetar,
                                periodoHasta,
                                graciaHasta
                        )
                );
            });
        }
    }

    public void logEvent(Long subscriptionId, String eventType, String actorType, String actorId, String payloadJson) {
        logEvent(subscriptionId, eventType, null, null, actorType, actorId, payloadJson);
    }

    /**
     * Métricas para el resumen de administración (overview).
     */
    public Map<String, Object> getAdminOverviewStats() {
        List<Suscripciones.Suscripcion> activeSubs = suscripciones.porEstado("ACTIVE");
        long totalActive = activeSubs.size();
        long totalEnGracia = suscripciones.porEstado("PAST_DUE").size();

        BigDecimal mrr = activeSubs.stream()
                .map(Suscripciones.Suscripcion::amountArs)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        BigDecimal rolling12m = pagosSuscripcion.sumaAprobadosDesde(twelveMonthsAgo);

        BigDecimal topeCatA = new BigDecimal("12009410.00");
        BigDecimal topeCatB = new BigDecimal("17595183.00");
        BigDecimal topeCatK = new BigDecimal("126610839.00");

        double percentCatA = rolling12m.divide(topeCatA, 4, RoundingMode.HALF_UP).doubleValue() * 100;
        boolean alerta80Porciento = percentCatA >= 80.0;

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
