package com.tranqui.app.repository;

import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByProfessionalId(Long professionalId);
    Optional<Subscription> findByMpPreapprovalId(String mpPreapprovalId);
    List<Subscription> findByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.professional.id = :profId ORDER BY s.createdAt DESC")
    List<Subscription> findAllByProfessionalIdOrderByCreatedAtDesc(@Param("profId") Long profId);

    // Active subscriptions with expired nextBillingDate + grace
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.nextBillingDate IS NOT NULL AND s.graceUntil IS NOT NULL AND s.graceUntil < :now")
    List<Subscription> findExpiredActiveSubscriptions(@Param("now") LocalDateTime now);

    // Manual subscriptions expiring in the next 7 days
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND (s.billingSource = 'MANUAL_CASH' OR s.billingSource = 'MANUAL_TRANSFER') AND s.currentPeriodEnd IS NOT NULL AND s.currentPeriodEnd >= :now AND s.currentPeriodEnd <= :sevenDaysFromNow")
    List<Subscription> findExpiringManualSubscriptions(@Param("now") LocalDateTime now, @Param("sevenDaysFromNow") LocalDateTime sevenDaysFromNow);

    // Count active subscriptions by plan code
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.status = 'ACTIVE' AND s.plan.code = :planCode")
    long countActiveByPlanCode(@Param("planCode") String planCode);

    // Active subscriptions due for renewal in the next 3 days that haven't received notice yet
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' " +
           "AND s.cancelAtPeriodEnd = false " +
           "AND (s.renewalReminderSent IS NULL OR s.renewalReminderSent = false) " +
           "AND COALESCE(s.nextBillingDate, s.currentPeriodEnd) IS NOT NULL " +
           "AND COALESCE(s.nextBillingDate, s.currentPeriodEnd) >= :now " +
           "AND COALESCE(s.nextBillingDate, s.currentPeriodEnd) <= :threeDaysFromNow")
    List<Subscription> findUpcomingRenewalsNeedingNotice(
            @Param("now") LocalDateTime now,
            @Param("threeDaysFromNow") LocalDateTime threeDaysFromNow);

    // Subscriptions with cancelAtPeriodEnd=true whose period has expired
    @Query("SELECT s FROM Subscription s WHERE s.cancelAtPeriodEnd = true " +
           "AND s.status != 'CANCELLED' " +
           "AND s.currentPeriodEnd IS NOT NULL " +
           "AND s.currentPeriodEnd < :now")
    List<Subscription> findExpiredCancelledSubscriptions(@Param("now") LocalDateTime now);
}
