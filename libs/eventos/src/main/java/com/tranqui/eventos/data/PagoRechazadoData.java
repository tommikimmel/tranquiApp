package com.tranqui.eventos.data;

/**
 * Datos del evento pagos.pago.rechazado.v1.
 * Consumido por turnos para liberar el horario reservado.
 */
public record PagoRechazadoData(
    Long pagoId,
    Long turnoId,
    String mpPaymentId,
    String motivo
) {}
