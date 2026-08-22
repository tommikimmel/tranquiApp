package com.tranqui.app.repository;

import com.tranqui.app.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(String code);
    List<Plan> findByIsActiveTrue();
    Optional<Plan> findByMpPreapprovalPlanId(String mpPreapprovalPlanId);
}
