package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento suscripciones.suscripcion.cambio-estado.v2.
 * Reemplaza al v1 incorporando planCode, flags calculados de acceso y fechas de período y gracia.
 */
public record SuscripcionCambioEstadoV2Data(
    Long suscripcionId,
    Long profesionalId,
    String estadoAnterior,
    String estadoNuevo,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaVencimiento,
    String planCode,
    boolean acceso,
    boolean puedeRecetar,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate periodoHasta,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate graciaHasta
) {}
