package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_prof", columnList = "professional_id"),
        @Index(name = "idx_subscription_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professional_id", nullable = false)
    private Usuario professional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status;

    @Builder.Default
    @Column(name = "seats", nullable = false)
    private Integer seats = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_source", nullable = false, length = 30)
    private BillingSource billingSource;

    @Column(name = "mp_preapproval_id", length = 100)
    private String mpPreapprovalId;

    @Column(name = "amount_ars", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountArs;

    // "monthly" | "annual" — ciclo de facturación realmente contratado en el checkout vigente.
    // Los webhooks de Mercado Pago (processMercadoPagoPreapprovalWebhook /
    // processMercadoPagoPaymentWebhook) lo leen para saber si el próximo período dura 1 o 12
    // meses; antes de que existiera este campo, ambos asumían siempre 1 mes.
    @Builder.Default
    @Column(name = "billing_cycle", length = 20)
    private String billingCycle = "monthly";

    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "grace_until")
    private LocalDateTime graceUntil;

    @Builder.Default
    @Column(name = "cancel_at_period_end", nullable = false)
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Whether SubscriptionReconciliationScheduler#checkExpiringManualSubscriptions already sent
    // the "tu suscripción vence pronto" email for the CURRENT currentPeriodEnd — that check re-runs
    // daily and would otherwise re-match (and re-email) the same subscription every day of its
    // 7-day warning window. Reset to false whenever a payment extends currentPeriodEnd (see
    // SubscriptionService#registerManualPayment / activarPagoSuscripcion), so the next billing
    // cycle gets its own warning.
    @Builder.Default
    @Column(name = "aviso_vencimiento_enviado", nullable = false)
    private Boolean avisoVencimientoEnviado = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
