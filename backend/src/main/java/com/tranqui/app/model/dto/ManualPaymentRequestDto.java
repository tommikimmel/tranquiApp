package com.tranqui.app.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualPaymentRequestDto {
    private Long professionalId;
    private Long planId;
    private BigDecimal amountArs;
    private String method; // MANUAL_CASH | MANUAL_TRANSFER | COURTESY
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String receiptReference;
    private String notes;
    private Boolean emitInvoice; // default true
    private String idempotencyKey;
}
