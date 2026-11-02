package com.tranqui.eventos.data;

/**
 * Datos del evento profesionales.disponibilidad.actualizada.v1.
 * Consumido por turnos para conocer los horarios reservables.
 */
public record DisponibilidadActualizadaData(
    Long profesionalId,
    int diaSemana,
    String horaInicio,
    String horaFin,
    boolean activo
) {}
