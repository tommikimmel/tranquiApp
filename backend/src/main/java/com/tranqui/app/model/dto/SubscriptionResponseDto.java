package com.tranqui.app.model.dto;

import com.tranqui.app.model.BillingSource;
import com.tranqui.app.model.SubscriptionStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponseDto {
    private Long id;
    private Long professionalId;
    private String professionalName;
    private String professionalEmail;
    private PlanResponseDto plan;
    private SubscriptionStatus status;
    private Integer seats;
    private BillingSource billingSource;
    private BigDecimal amountArs;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime nextBillingDate;
    private LocalDateTime graceUntil;
    private Boolean cancelAtPeriodEnd;
    private List<String> activeFeatures;
}
