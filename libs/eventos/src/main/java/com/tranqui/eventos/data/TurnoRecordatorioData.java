package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.recordatorio.programado.v1.
 * Consumido por notificaciones para enviar el mail de confirmación de asistencia (1-2 días antes).
 *
 * <p>Campos opcionales agregados en la Fase 1 para el mail: modalidad, tipo de turno y dirección.
 * Los enlaces de confirmar y "no podré asistir" llevan un token de un solo uso, que no viaja en el
 * evento (sección 5.3): notificaciones los pide por la ruta interna del dueño del turno.
 */
public record TurnoRecordatorioData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    String modalidad,
    String tipoLabel,
    String direccion
) {
    public TurnoRecordatorioData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha, String horaInicio) {
        this(turnoId, pacienteId, profesionalId, fecha, horaInicio, null, null, null);
    }
}
