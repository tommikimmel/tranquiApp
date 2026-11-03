package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento turnos.turno.expirado.v1.
 * Consumido por pagos para invalidar la preferencia o checkout pendiente tras 5 min.
 */
public record TurnoExpiradoData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId
) {}
