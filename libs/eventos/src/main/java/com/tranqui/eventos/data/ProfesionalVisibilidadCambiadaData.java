package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento profesionales.profesional.visibilidad-cambiada.v1.
 * Consumido por turnos para aceptar o rechazar nuevas reservas.
 */
public record ProfesionalVisibilidadCambiadaData(
    @NotNull @Positive Long profesionalId,
    boolean visible,
    String motivo
) {}
