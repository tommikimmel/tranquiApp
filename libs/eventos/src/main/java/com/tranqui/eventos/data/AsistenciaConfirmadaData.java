package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.asistencia.confirmada.v1 (agregado en la Fase 1).
 * El paciente confirmó desde el mail que va a asistir. Consumido por notificaciones (aviso al profesional).
 */
public record AsistenciaConfirmadaData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio
) {}
