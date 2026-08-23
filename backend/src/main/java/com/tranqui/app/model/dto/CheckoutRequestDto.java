package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequestDto {
    private Long planId;
    // "monthly" | "annual" — null/cualquier otro valor se trata como "monthly" en SubscriptionService.
    private String billingCycle;
}
