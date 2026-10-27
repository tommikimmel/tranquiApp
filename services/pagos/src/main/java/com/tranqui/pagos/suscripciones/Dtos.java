package com.tranqui.pagos.suscripciones;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class Dtos {

    public record PlanResponseDto(
            Long id,
            String code,
            String name,
            String description,
            BigDecimal priceArs,
            BigDecimal priceUsdRef,
            BigDecimal priceArsAnual,
            String billingPeriod,
            Integer minSeats,
            Boolean requiresPrescriber,
            List<String> features,
            Boolean isActive
    ) {}

    public record SubscriptionResponseDto(
            Long id,
            Long professionalId,
            String professionalName,
            String professionalEmail,
            PlanResponseDto plan,
            String status,
            Integer seats,
            String billingSource,
            String billingCycle,
            BigDecimal amountArs,
            LocalDateTime currentPeriodStart,
            LocalDateTime currentPeriodEnd,
            LocalDateTime nextBillingDate,
            LocalDateTime graceUntil,
            Boolean cancelAtPeriodEnd,
            List<String> activeFeatures
    ) {}

    public record CheckoutRequestDto(
            Long planId,
            String billingCycle
    ) {}

    public record ManualPaymentRequestDto(
            Long professionalId,
            Long planId,
            BigDecimal amountArs,
            String method,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            String receiptReference,
            String notes,
            Boolean emitInvoice,
            String idempotencyKey
    ) {}

    public record SubscriptionPaymentDto(
            Long id,
            Long subscriptionId,
            Long professionalId,
            String mpPaymentId,
            String idempotencyKey,
            String method,
            String status,
            BigDecimal amountArs,
            LocalDateTime paidAt,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            Long invoiceId,
            Long registeredByAdminId,
            String receiptReference,
            String notes,
            LocalDateTime createdAt
    ) {}

    public record SubscriptionEventDto(
            Long id,
            Long subscriptionId,
            String eventType,
            String previousStatus,
            String newStatus,
            String actorType,
            String actorId,
            @JsonProperty("details") String details,
            LocalDateTime createdAt
    ) {}

    public record EstadoSuscripcionDto(
            Long professionalId,
            String estado,
            String planCode,
            boolean acceso,
            boolean puedeRecetar,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime periodoHasta,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime graciaHasta,
            Long version
    ) {}

    public record InvoiceResponseDto(
            Long id,
            Long paymentId,
            Integer cbteTipo,
            String cbteTipoNombre,
            Integer puntoVenta,
            Long cbteNumero,
            String cae,
            LocalDate caeVencimiento,
            String receptorNombre,
            Long receptorDocNro,
            Integer receptorCondicionIva,
            BigDecimal importeTotal,
            LocalDate fechaEmision,
            String pdfUrl,
            String status,
            String lastError,
            Long comprobanteAsociadoId,
            LocalDateTime issuedAt
    ) {}

    public record CreditNoteRequestDto(
            String reason
    ) {}
}
