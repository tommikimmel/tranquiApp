package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Datos del evento pagos.pago.aprobado.v1.
 * Consumido por turnos (confirmación de turno) y notificaciones (comprobante/aviso).
 */
public record PagoAprobadoData(
    @NotNull @Positive Long pagoId,
    @Positive Long turnoId,
    String mpPaymentId,
    @PositiveOrZero BigDecimal monto,
    String metodoPago
) {}
