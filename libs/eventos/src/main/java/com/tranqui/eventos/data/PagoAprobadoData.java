package com.tranqui.eventos.data;

import java.math.BigDecimal;

/**
 * Datos del evento pagos.pago.aprobado.v1.
 * Consumido por turnos (confirmación de turno) y notificaciones (comprobante/aviso).
 */
public record PagoAprobadoData(
    Long pagoId,
    Long turnoId,
    String mpPaymentId,
    BigDecimal monto,
    String metodoPago
) {}
