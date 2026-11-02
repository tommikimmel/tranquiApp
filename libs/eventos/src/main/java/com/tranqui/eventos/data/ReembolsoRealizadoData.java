package com.tranqui.eventos.data;

import java.math.BigDecimal;

/**
 * Datos del evento pagos.reembolso.realizado.v1.
 * Consumido por turnos (marcar reembolso) y notificaciones (envío de comprobante).
 */
public record ReembolsoRealizadoData(
    Long reembolsoId,
    Long pagoId,
    Long turnoId,
    BigDecimal monto,
    String motivo
) {}
