package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento suscripciones.suscripcion.cambio-estado.v1.
 * Consumido por profesionales (visibilidad) y notificaciones (pago, vencimiento, cancelación).
 */
public record SuscripcionCambioEstadoData(
    Long suscripcionId,
    Long profesionalId,
    String estadoAnterior,
    String estadoNuevo,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaVencimiento
) {}
