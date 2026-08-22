package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_payments", indexes = {
        @Index(name = "idx_sub_payment_sub_id", columnList = "subscription_id"),
        @Index(name = "idx_sub_payment_mp_id", columnList = "mp_payment_id", unique = true),
        @Index(name = "idx_sub_payment_idem_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    // Idempotency key is NOT NULL for all payments (admin manual and MP) to avoid double charges
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // CASH | TRANSFER | MERCADOPAGO | COURTESY
    @Column(name = "method", nullable = false, length = 30)
    private String method;

    // APPROVED | REJECTED | PENDING
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "amount_ars", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountArs;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "registered_by_admin_id")
    private Long registeredByAdminId;

    @Column(name = "receipt_reference", length = 150)
    private String receiptReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (paidAt == null) paidAt = LocalDateTime.now();
    }
}
