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

        checkPaymentsWithoutInvoice();
        checkFailedInvoices();
        checkExpiredActiveSubscriptions();
        checkExpiringManualSubscriptions();
        checkMonotributoThreshold();

        log.info("Job diario de reconciliación completado exitosamente.");
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
