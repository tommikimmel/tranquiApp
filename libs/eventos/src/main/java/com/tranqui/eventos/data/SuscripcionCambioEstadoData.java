package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento suscripciones.suscripcion.cambio-estado.v1.
 * Consumido por profesionales (visibilidad) y notificaciones (pago, vencimiento, cancelación).
 */
public record SuscripcionCambioEstadoData(
    @Positive Long suscripcionId,
    @NotNull @Positive Long profesionalId,
    @Pattern(regexp = Valores.ESTADOS_SUSCRIPCION) String estadoAnterior,
    @Pattern(regexp = Valores.ESTADOS_SUSCRIPCION) String estadoNuevo,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaVencimiento
) {}
