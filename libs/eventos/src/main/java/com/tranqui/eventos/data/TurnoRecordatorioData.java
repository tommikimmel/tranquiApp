package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.recordatorio.programado.v1.
 * Consumido por notificaciones para enviar el mail de confirmación de asistencia (1-2 días antes).
 */
public record TurnoRecordatorioData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio
) {}
