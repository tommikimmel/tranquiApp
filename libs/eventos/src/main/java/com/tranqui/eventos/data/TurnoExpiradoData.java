package com.tranqui.eventos.data;

/**
 * Datos del evento turnos.turno.expirado.v1.
 * Consumido por pagos para invalidar la preferencia o checkout pendiente tras 5 min.
 */
public record TurnoExpiradoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId
) {}
