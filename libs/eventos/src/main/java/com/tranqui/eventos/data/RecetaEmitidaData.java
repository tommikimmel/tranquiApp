package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento profesionales.receta.emitida.v1 (Fase 6): el profesional le emitió recetas a un paciente.
 * Turnos marca como enviados los turnos de receta pendientes entre los dos. Sin datos de salud: ni la
 * receta, ni los medicamentos, ni el diagnóstico.
 */
public record RecetaEmitidaData(
    @NotNull @Positive Long profesionalId,
    @NotNull @Positive Long pacienteId
) {}
