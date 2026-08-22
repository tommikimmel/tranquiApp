package com.tranqui.app.repository;

import com.tranqui.app.model.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    Optional<SubscriptionPayment> findByIdempotencyKey(String idempotencyKey);
    Optional<SubscriptionPayment> findByMpPaymentId(String mpPaymentId);
    List<SubscriptionPayment> findBySubscriptionIdOrderByPaidAtDesc(Long subscriptionId);
    List<SubscriptionPayment> findByProfessionalIdOrderByPaidAtDesc(Long professionalId);

    // Payments older than 24h with status APPROVED that have no invoice (check 1 of §11)
    @Query("SELECT p FROM SubscriptionPayment p WHERE p.status = 'APPROVED' AND p.invoiceId IS NULL AND p.method != 'COURTESY' AND p.paidAt < :cutoff")
    List<SubscriptionPayment> findApprovedPaymentsWithoutInvoiceOlderThan(@Param("cutoff") LocalDateTime cutoff);

    // Rolling 12-month billing calculation for Monotributo category meter (§3 & §11)
    @Query("SELECT COALESCE(SUM(p.amountArs), 0) FROM SubscriptionPayment p WHERE p.status = 'APPROVED' AND p.paidAt >= :twelveMonthsAgo")
    BigDecimal sumApprovedPaymentsSince(@Param("twelveMonthsAgo") LocalDateTime twelveMonthsAgo);
}
