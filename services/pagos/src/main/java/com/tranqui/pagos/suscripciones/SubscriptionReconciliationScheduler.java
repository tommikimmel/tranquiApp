package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.CargaInicialSuscripciones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tareas programadas de reconciliación de suscripciones y alertas.
 * Corren únicamente cuando {@code pagos.suscripciones.habilitada=true} y la carga inicial ha finalizado.
 */
@Service
@Transactional
public class SubscriptionReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciliationScheduler.class);

    private final Suscripciones suscripciones;
    private final PagosSuscripcion pagosSuscripcion;
    private final Planes planes;
    private final Suscriptores suscriptores;
    private final SuscripcionService suscripcionService;
    private final AvisoSuscripcionPublisher avisoSuscripcionPublisher;
    private final FacturacionSuscripciones facturacionSuscripciones;
    private final CargaInicialSuscripciones cargaInicialSuscripciones;
    private final boolean habilitada;

    public SubscriptionReconciliationScheduler(
            Suscripciones suscripciones,
            PagosSuscripcion pagosSuscripcion,
            Planes planes,
            Suscriptores suscriptores,
            SuscripcionService suscripcionService,
            AvisoSuscripcionPublisher avisoSuscripcionPublisher,
            FacturacionSuscripciones facturacionSuscripciones,
            CargaInicialSuscripciones cargaInicialSuscripciones,
            @Value("${pagos.suscripciones.habilitada:false}") boolean habilitada
    ) {
        this.suscripciones = suscripciones;
        this.pagosSuscripcion = pagosSuscripcion;
        this.planes = planes;
        this.suscriptores = suscriptores;
        this.suscripcionService = suscripcionService;
        this.avisoSuscripcionPublisher = avisoSuscripcionPublisher;
        this.facturacionSuscripciones = facturacionSuscripciones;
        this.cargaInicialSuscripciones = cargaInicialSuscripciones;
        this.habilitada = habilitada;
    }

    private boolean puedeEjecutar() {
        return habilitada && cargaInicialSuscripciones.listo();
    }

    /**
     * Cron diario de reconciliación y alertas: todos los días a las 04:00 AM.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void runDailyReconciliation() {
        if (!puedeEjecutar()) {
            log.info("Reconciliación diaria omitida: suscripciones no habilitadas o carga inicial pendiente.");
            return;
        }
        log.info("Iniciando job diario de reconciliación de suscripciones...");

        checkUpcomingRenewals();
        checkCancelledSubscriptionsPeriodEnd();
        facturacionSuscripciones.reconciliarPagosSinFactura();
        facturacionSuscripciones.verificarFacturasFallidas();
        checkExpiredActiveSubscriptions();
        checkExpiringManualSubscriptions();
        checkMonotributoThreshold();

        log.info("Job diario de reconciliación completado exitosamente.");
    }

    /**
     * Verificación horaria de preavisos y cancelaciones cumplidas, más disparo al arrancar.
     */
    @Scheduled(cron = "0 0 * * * ?")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void runHourlyAlertsCheck() {
        if (!puedeEjecutar()) {
            return;
        }
        try {
            log.info("Ejecutando verificación de preavisos de renovación y cancelaciones diferidas...");
            checkUpcomingRenewals();
            checkCancelledSubscriptionsPeriodEnd();
        } catch (Exception e) {
            log.error("Error durante la verificación horaria de alertas de suscripción: {}", e.getMessage(), e);
        }
    }

    /**
     * Preaviso de cobro/renovación a 3 días.
     */
    public void checkUpcomingRenewals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysAhead = now.plusDays(3).plusHours(6);
        List<Suscripciones.Suscripcion> upcoming = suscripciones.proximasRenovacionesSinAviso(now, threeDaysAhead);
        log.info("Verificando suscripciones próximas a renovar (ventana 3 días). Candidatas: {}", upcoming.size());

        for (Suscripciones.Suscripcion sub : upcoming) {
            try {
                Suscriptores.Suscriptor prof = suscriptores.porId(sub.professionalId()).orElse(null);
                if (prof != null && prof.email() != null) {
                    Planes.Plan plan = planes.porId(sub.planId()).orElse(null);
                    String planName = plan != null ? plan.name() : "Profesional";
                    LocalDateTime fechaAviso = sub.nextBillingDate() != null ? sub.nextBillingDate() : sub.currentPeriodEnd();

                    avisoSuscripcionPublisher.publicar(sub, planName, SuscripcionAvisoData.PREAVISO_RENOVACION, sub.amountArs(),
                            fechaAviso, null, sub.billingCycle(), null, null);

                    Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                            sub.id(), sub.version(), sub.professionalId(), sub.planId(), sub.status(), sub.seats(),
                            sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(), sub.billingCycle(),
                            sub.nextBillingDate(), sub.currentPeriodStart(), sub.currentPeriodEnd(), sub.graceUntil(),
                            sub.cancelAtPeriodEnd(), sub.cancelledAt(), true, sub.createdAt(), LocalDateTime.now()
                    );
                    suscripciones.actualizar(actualizada);

                    suscripcionService.logEvent(sub.id(), "RENEWAL_PREAVISO_EMAIL_SENT",
                            "SYSTEM", "CRON",
                            "Preaviso de renovación a 3 días enviado a " + prof.email());
                }
            } catch (Exception e) {
                log.error("Error al enviar preaviso de renovación para suscripción #{}: {}", sub.id(), e.getMessage());
            }
        }
    }

    /**
     * Suscripciones canceladas cuyo período pagado ha concluido: pasan a CANCELLED.
     */
    @Transactional
    public void checkCancelledSubscriptionsPeriodEnd() {
        LocalDateTime now = LocalDateTime.now();
        List<Suscripciones.Suscripcion> expiredCancelled = suscripciones.canceladasVencidas(now);
        for (Suscripciones.Suscripcion sub : expiredCancelled) {
            try {
                String previo = sub.status();
                Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                        sub.id(), sub.version(), sub.professionalId(), sub.planId(), "CANCELLED", sub.seats(),
                        sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(), sub.billingCycle(),
                        sub.nextBillingDate(), sub.currentPeriodStart(), sub.currentPeriodEnd(), sub.graceUntil(),
                        false, sub.cancelledAt(), sub.renewalReminderSent(), sub.createdAt(), now
                );
                suscripciones.actualizar(actualizada);

                suscripcionService.logEvent(sub.id(), "SUBSCRIPTION_ACCESS_EXPIRED_AFTER_CANCELLATION",
                        previo, "CANCELLED",
                        "SYSTEM", "CRON",
                        "Período pagado finalizado tras cancelación previa. Cuenta bloqueada.");

                Suscriptores.Suscriptor prof = suscriptores.porId(sub.professionalId()).orElse(null);
                if (prof != null && prof.email() != null) {
                    Planes.Plan plan = planes.porId(sub.planId()).orElse(null);
                    String planName = plan != null ? plan.name() : "Profesional";
                    avisoSuscripcionPublisher.publicar(actualizada, planName, SuscripcionAvisoData.SUSPENSION, null, null, null, null, null, null);
                }
            } catch (Exception e) {
                log.error("Error al procesar cancelación diferida de suscripción #{}: {}", sub.id(), e.getMessage());
            }
        }
    }

    /**
     * Suscripciones activas vencidas pasada la ventana de gracia: pasan a SUSPENDED.
     */
    @Transactional
    public void checkExpiredActiveSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Suscripciones.Suscripcion> expired = suscripciones.activasVencidasConGracia(now);
        for (Suscripciones.Suscripcion sub : expired) {
            try {
                String estadoPrevio = sub.status();
                Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                        sub.id(), sub.version(), sub.professionalId(), sub.planId(), "SUSPENDED", sub.seats(),
                        sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(), sub.billingCycle(),
                        sub.nextBillingDate(), sub.currentPeriodStart(), sub.currentPeriodEnd(), sub.graceUntil(),
                        sub.cancelAtPeriodEnd(), sub.cancelledAt(), sub.renewalReminderSent(), sub.createdAt(), now
                );
                suscripciones.actualizar(actualizada);

                suscripcionService.logEvent(sub.id(), "SUBSCRIPTION_SUSPENDED_GRACE_EXPIRED",
                        estadoPrevio, "SUSPENDED",
                        "SYSTEM", "CRON",
                        "Suscripción suspendida tras vencer la ventana de gracia.");

                Suscriptores.Suscriptor prof = suscriptores.porId(sub.professionalId()).orElse(null);
                if (prof != null && prof.email() != null) {
                    Planes.Plan plan = planes.porId(sub.planId()).orElse(null);
                    String planName = plan != null ? plan.name() : "Profesional";
                    avisoSuscripcionPublisher.publicar(actualizada, planName, SuscripcionAvisoData.SUSPENSION, null, null, null, null, null, null);
                }
            } catch (Exception e) {
                log.error("Error al procesar suspensión de suscripción #{}: {}", sub.id(), e.getMessage());
            }
        }
    }

    /**
     * Altas manuales próximas a vencer (aviso preventivo a 7, 3 y 1 día).
     */
    @Transactional
    public void checkExpiringManualSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDays = now.plusDays(7);
        List<Suscripciones.Suscripcion> expiring = suscripciones.manualesPorVencer(now, sevenDays);
        for (Suscripciones.Suscripcion sub : expiring) {
            try {
                if (sub.currentPeriodEnd() != null) {
                    long dias = ChronoUnit.DAYS.between(now.toLocalDate(), sub.currentPeriodEnd().toLocalDate());
                    if (dias == 7 || dias == 3 || dias == 1) {
                        Planes.Plan plan = planes.porId(sub.planId()).orElse(null);
                        String planName = plan != null ? plan.name() : "Profesional";
                        avisoSuscripcionPublisher.publicar(sub, planName, SuscripcionAvisoData.VENCIMIENTO_MANUAL, null,
                                sub.currentPeriodEnd(), null, null, null, (int) dias);
                    }
                }
            } catch (Exception e) {
                log.error("Error enviando aviso manual para suscripción #{}: {}", sub.id(), e.getMessage());
            }
        }
    }

    /**
     * Control de facturación acumulada rodante 12 meses vs Tope Categoría A Monotributo.
     */
    public void checkMonotributoThreshold() {
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        BigDecimal sum12m = pagosSuscripcion.sumaAprobadosDesde(twelveMonthsAgo);
        BigDecimal topeCatA = new BigDecimal("12009410.00");
        BigDecimal umbral80 = topeCatA.multiply(new BigDecimal("0.80"));

        if (sum12m.compareTo(umbral80) >= 0) {
            log.warn("ALERTA MONOTRIBUTO: La facturación rodante de los últimos 12 meses ($ {}) superó el 80% del tope de Categoría A ($ {})",
                    sum12m, topeCatA);
        }
    }
}
