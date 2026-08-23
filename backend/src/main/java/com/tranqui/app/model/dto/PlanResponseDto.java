package com.tranqui.app.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanResponseDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal priceArs;
    private BigDecimal priceUsdRef;
    private BigDecimal priceArsAnual;
    private String billingPeriod;
    private Integer minSeats;
    private Boolean requiresPrescriber;
    private List<String> features;
    private Boolean isActive;
}
