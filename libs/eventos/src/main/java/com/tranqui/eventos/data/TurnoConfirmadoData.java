package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos del evento turnos.turno.confirmado.v1 (ejemplo exacto de la sección 5.3).
 * Consumido por notificaciones (mail al paciente), calendario (evento Meet) y chat (habilitación).
 */
public record TurnoConfirmadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    String modalidad,
    BigDecimal precio
) {}
