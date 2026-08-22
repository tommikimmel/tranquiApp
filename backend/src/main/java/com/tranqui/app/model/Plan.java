package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // consultorio | clinico | equipo
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_ars", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceArs;

    // Internal dollar benchmark reference (e.g. USD 99 / USD 149)
    @Column(name = "price_usd_ref", precision = 10, scale = 2)
    private BigDecimal priceUsdRef;

    @Builder.Default
    @Column(name = "billing_period", nullable = false, length = 20)
    private String billingPeriod = "monthly";

    @Builder.Default
    @Column(name = "min_seats", nullable = false)
    private Integer minSeats = 1;

    @Column(name = "mp_preapproval_plan_id", length = 100)
    private String mpPreapprovalPlanId;

    @Builder.Default
    @Column(name = "requires_prescriber", nullable = false)
    private Boolean requiresPrescriber = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;
}
