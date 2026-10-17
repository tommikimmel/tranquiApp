package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.turno.reprogramado.v1.
 * Consumido por notificaciones (avisos) y calendario (actualización del evento).
 */
public record TurnoReprogramadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaAnterior,
    String horaInicioAnterior,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaNueva,
    String horaInicioNueva
) {}
