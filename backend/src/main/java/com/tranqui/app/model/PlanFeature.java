package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plan_features", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"plan_id", "feature_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(name = "limit_value")
    private Integer limitValue;
}
