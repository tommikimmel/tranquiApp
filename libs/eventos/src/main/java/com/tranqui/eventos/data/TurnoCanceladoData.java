package com.tranqui.eventos.data;

/**
 * Datos del evento turnos.turno.cancelado.v1.
 * Consumido por pagos (reembolso si aplica), notificaciones (avisos) y calendario (borrado de evento).
 */
public record TurnoCanceladoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    String canceladoPor,
    String motivo,
    boolean correspondeReembolso
) {}
