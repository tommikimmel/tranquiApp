package com.tranqui.app.repository;

import com.tranqui.app.model.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {
    List<SubscriptionEvent> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<SubscriptionEvent> findAllByOrderByCreatedAtDesc();
}
