package com.tranqui.app.repository;

import com.tranqui.app.model.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {
    List<PlanFeature> findByPlanId(Long planId);
    boolean existsByPlanIdAndFeatureKey(Long planId, String featureKey);
    List<PlanFeature> findByFeatureKey(String featureKey);
}
