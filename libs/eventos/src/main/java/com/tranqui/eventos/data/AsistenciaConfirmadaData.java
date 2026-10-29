package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.asistencia.confirmada.v1 (agregado en la Fase 1).
 * El paciente confirmó desde el mail que va a asistir. Consumido por notificaciones (aviso al profesional).
 */
public record AsistenciaConfirmadaData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio
) {}
