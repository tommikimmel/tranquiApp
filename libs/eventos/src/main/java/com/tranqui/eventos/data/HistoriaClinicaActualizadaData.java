package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento profesionales.historia-clinica.actualizada.v1 (agregado en la Fase 1).
 * Se cargó un registro en la historia clínica de un paciente. Consumido por notificaciones (avisos
 * en la app).
 *
 * <p>Sección 5.3: no lleva contenido clínico. Solo dice qué se cargó ({@code tipo}:
 * {@code SEGUIMIENTO} o {@code INFORME}), quién lo cargó ({@code cargadoPor}: {@code PROFESIONAL} o
 * {@code PACIENTE}) y, para un informe, su categoría ({@code tipoInforme}).
 */
public record HistoriaClinicaActualizadaData(
    @NotNull @Positive Long profesionalId,
    @NotNull @Positive Long pacienteId,
    String tipo,
    String cargadoPor,
    String tipoInforme
) {}
