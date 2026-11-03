package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento profesionales.disponibilidad.actualizada.v1.
 * Consumido por turnos para conocer los horarios reservables.
 */
public record DisponibilidadActualizadaData(
    @NotNull @Positive Long profesionalId,
    int diaSemana,
    String horaInicio,
    String horaFin,
    boolean activo
) {}
