package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Datos del evento turnos.turno.reservado.v1.
 * Consumido por pagos (creación de preferencia MP) y notificaciones (aviso al profesional).
 */
public record TurnoReservadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    BigDecimal precio,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime fechaExpiracion
) {}
