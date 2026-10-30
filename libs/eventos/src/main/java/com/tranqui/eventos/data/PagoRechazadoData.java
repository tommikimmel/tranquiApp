package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento pagos.pago.rechazado.v1.
 * Consumido por turnos para liberar el horario reservado.
 */
public record PagoRechazadoData(
    @NotNull @Positive Long pagoId,
    @Positive Long turnoId,
    String mpPaymentId,
    String motivo
) {}
