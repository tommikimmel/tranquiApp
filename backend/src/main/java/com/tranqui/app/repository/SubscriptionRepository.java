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
}
