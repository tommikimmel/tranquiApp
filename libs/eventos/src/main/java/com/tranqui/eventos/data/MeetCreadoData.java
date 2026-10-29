package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento calendario.meet.creado.v1.
 * Consumido por turnos (guardar enlace) y notificaciones (envío al paciente).
 *
 * <p>Campos opcionales agregados en la Fase 1 para el mail del link: paciente, profesional, fecha y
 * hora, y {@code linkNuevo}: solo es {@code true} cuando el paciente recibió la confirmación sin
 * este link y hay que mandárselo (si no, el link ya iba en el mail de confirmación).
 */
public record MeetCreadoData(
    @NotNull @Positive Long turnoId,
    String meetUrl,
    String googleEventId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    Boolean linkNuevo
) {
    public MeetCreadoData(Long turnoId, String meetUrl, String googleEventId) {
        this(turnoId, meetUrl, googleEventId, null, null, null, null, null);
    }
}
