package com.tranqui.app.service;

import com.tranqui.app.model.Invoice;
import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.SubscriptionPayment;
import com.tranqui.app.model.SubscriptionStatus;
import com.tranqui.app.repository.InvoiceRepository;
import com.tranqui.app.repository.SubscriptionPaymentRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubscriptionReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciliationScheduler.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ResendEmailService resendEmailService;

    /**
     * Cron diario de reconciliación y alertas (§11):
     * Se ejecuta todos los días a las 04:00 AM.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void runDailyReconciliation() {
        log.info("Iniciando job diario de reconciliación de suscripciones y facturación...");

        checkUpcomingRenewals();
        checkCancelledSubscriptionsPeriodEnd();
        checkPaymentsWithoutInvoice();
        checkFailedInvoices();
        checkExpiredActiveSubscriptions();
        checkExpiringManualSubscriptions();
        checkMonotributoThreshold();

        log.info("Job diario de reconciliación completado exitosamente.");
    }

    /**
     * Verificación horaria de preavisos y cancelaciones cumplidas,
     * para no esperar hasta las 04:00 AM del día siguiente.
     */
    @Scheduled(cron = "0 0 * * * ?")
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void runHourlyAlertsCheck() {
        log.info("Ejecutando verificación de preavisos de renovación y cancelaciones diferidas...");
        checkUpcomingRenewals();
        checkCancelledSubscriptionsPeriodEnd();
    }

    /**
     * Check 0a: Preaviso de cobro/renovación a 3 días.
     * Busca suscripciones activas cuya fecha de cobro ocurra en los próximos 3 días
     * y envía el email preventivo con la opción de cancelar antes del débito.
     */
    public void checkUpcomingRenewals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysAhead = now.plusDays(3).plusHours(6);
        List<Subscription> upcoming = subscriptionRepository.findUpcomingRenewalsNeedingNotice(now, threeDaysAhead);
        log.info("Verificando suscripciones próximas a renovar (ventana 3 días). Candidatas: {}", upcoming.size());

        for (Subscription sub : upcoming) {
            try {
                if (sub.getProfessional() != null && sub.getProfessional().getEmail() != null) {
                    resendEmailService.enviarPreavisoRenovacionSuscripcion(
                            sub.getProfessional().getEmail(),
                            sub.getProfessional().getNombre(),
                            sub.getPlan() != null ? sub.getPlan().getName() : "Profesional",
                            sub.getAmountArs(),
                            sub.getNextBillingDate() != null ? sub.getNextBillingDate() : sub.getCurrentPeriodEnd(),
                            sub.getBillingCycle()
                    );
                    sub.setRenewalReminderSent(true);
                    subscriptionRepository.save(sub);
                    subscriptionService.logEvent(sub.getId(), "RENEWAL_PREAVISO_EMAIL_SENT",
                            "SYSTEM", "CRON",
                            "Preaviso de renovación a 3 días enviado a " + sub.getProfessional().getEmail());
                    log.info("Email de preaviso de renovación (3 días) enviado a {}", sub.getProfessional().getEmail());
                }
            } catch (Exception e) {
                log.error("Error al enviar preaviso de renovación para suscripción #{}: {}", sub.getId(), e.getMessage());
            }
        }
    }

    /**
     * Check 0b: Suscripciones canceladas cuyo período pagado (currentPeriodEnd) ha concluido.
     * En ese momento exacto el acceso se suspende y se envía el mail correspondiente.
     */
    public void checkCancelledSubscriptionsPeriodEnd() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expiredCancelled = subscriptionRepository.findExpiredCancelledSubscriptions(now);
        for (Subscription sub : expiredCancelled) {
            log.info("Finalizando período de gracia de suscripción cancelada #{} (profesional {})",
                    sub.getId(), sub.getProfessional().getEmail());
            SubscriptionStatus previo = sub.getStatus();
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setCancelAtPeriodEnd(false);
            subscriptionRepository.save(sub);

            subscriptionService.logEvent(sub.getId(), "SUBSCRIPTION_ACCESS_EXPIRED_AFTER_CANCELLATION",
                    previo != null ? previo.name() : null, SubscriptionStatus.CANCELLED.name(),
                    "SYSTEM", "CRON",
                    "Período pagado finalizado tras cancelación previa. Cuenta bloqueada.");

            try {
                resendEmailService.enviarAvisoSuspensionSuscripcion(
                        sub.getProfessional().getEmail(),
                        sub.getProfessional().getNombre(),
                        sub.getPlan() != null ? sub.getPlan().getName() : "Profesional"
                );
            } catch (Exception e) {
                log.error("Error al enviar email de suspensión por fin de período a {}: {}", sub.getProfessional().getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Check 1: Payments sin invoice > 24 h
     */
    public void checkPaymentsWithoutInvoice() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<SubscriptionPayment> unInvoiced = paymentRepository.findApprovedPaymentsWithoutInvoiceOlderThan(cutoff);
        if (!unInvoiced.isEmpty()) {
            log.warn("ALERTA: Se encontraron {} pagos aprobados sin factura mayores a 24 horas.", unInvoiced.size());
            for (SubscriptionPayment p : unInvoiced) {
                try {
                    log.info("Reintentando emisión de factura para pago ID {}", p.getId());
                    invoiceService.generateInvoiceForPayment(p.getId());
                } catch (Exception e) {
                    log.error("Error al reintentar factura para pago ID {}: {}", p.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Check 2: Invoices en FAILED
     */
    public void checkFailedInvoices() {
        List<Invoice> failed = invoiceRepository.findFailedInvoices();
        if (!failed.isEmpty()) {
            log.warn("ALERTA: Existen {} facturas en estado FAILED que requieren atención administrativa.", failed.size());
        }
    }

    /**
     * Check 3: Subscriptions activas con next_billing_date vencida + gracia
     */
    public void checkExpiredActiveSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expired = subscriptionRepository.findExpiredActiveSubscriptions(now);
        for (Subscription sub : expired) {
            log.warn("Suspendiendo suscripción #{} de {} por vencimiento de gracia", sub.getId(), sub.getProfessional().getEmail());
            SubscriptionStatus estadoPrevio = sub.getStatus();
            sub.setStatus(SubscriptionStatus.SUSPENDED);
            subscriptionRepository.save(sub);
            subscriptionService.logEvent(sub.getId(), "SUBSCRIPTION_SUSPENDED_GRACE_EXPIRED",
                    estadoPrevio != null ? estadoPrevio.name() : null, SubscriptionStatus.SUSPENDED.name(),
                    "SYSTEM", "CRON",
                    "Suscripción suspendida tras vencer la ventana de gracia.");

            try {
                resendEmailService.enviarAvisoSuspensionSuscripcion(
                        sub.getProfessional().getEmail(),
                        sub.getProfessional().getNombre(),
                        sub.getPlan() != null ? sub.getPlan().getName() : "Profesional"
                );
            } catch (Exception e) {
                log.error("Error al enviar email de suspensión a {}: {}", sub.getProfessional().getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Check 4: Altas manuales con current_period_end < 7 días
     */
    public void checkExpiringManualSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDays = now.plusDays(7);
        List<Subscription> expiring = subscriptionRepository.findExpiringManualSubscriptions(now, sevenDays);
        for (Subscription sub : expiring) {
            log.info("Aviso preventivo: Suscripción manual #{} (profesional {}) vence el {}",
                    sub.getId(), sub.getProfessional().getEmail(), sub.getCurrentPeriodEnd());
            try {
                long dias = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), sub.getCurrentPeriodEnd().toLocalDate());
                // Un aviso a 7, 3 y 1 día del vencimiento. Este check corre una vez por día, así que
                // cada umbral matchea un solo día (con <= 1 el último aviso salía dos veces).
                if (dias == 7 || dias == 3 || dias == 1) {
                    resendEmailService.enviarAvisoVencimientoManualSuscripcion(
                            sub.getProfessional().getEmail(),
                            sub.getProfessional().getNombre(),
                            sub.getPlan() != null ? sub.getPlan().getName() : "Profesional",
                            sub.getCurrentPeriodEnd(),
                            (int) dias
                    );
                }
            } catch (Exception e) {
                log.error("Error enviando email de aviso manual a {}: {}", sub.getProfessional().getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Check 5: Acumulado rodante 12 meses vs Tope de Categoría Monotributo (§3)
     */
    public void checkMonotributoThreshold() {
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        BigDecimal sum12m = paymentRepository.sumApprovedPaymentsSince(twelveMonthsAgo);

        BigDecimal topeCatA = new BigDecimal("12009410.00");
        BigDecimal umbral80 = topeCatA.multiply(new BigDecimal("0.80"));

        if (sum12m.compareTo(umbral80) >= 0) {
            log.warn("⚠️ ALERTA MONOTRIBUTO: La facturación rodante de los últimos 12 meses ($ {}) superó el 80% del tope de Categoría A ($ {})",
                    sum12m, topeCatA);
        }
    }
}
