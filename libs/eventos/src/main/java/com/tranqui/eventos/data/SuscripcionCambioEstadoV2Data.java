package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos del evento suscripciones.suscripcion.cambio-estado.v2.
 * Reemplaza al v1 incorporando planCode, flags calculados de acceso y fechas de período y gracia.
 */
public record SuscripcionCambioEstadoV2Data(
    @Positive Long suscripcionId,
    @NotNull @Positive Long profesionalId,
    @Pattern(regexp = Valores.ESTADOS_SUSCRIPCION) String estadoAnterior,
    @NotBlank @Pattern(regexp = Valores.ESTADOS_SUSCRIPCION) String estadoNuevo,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaVencimiento,
    String planCode,
    boolean acceso,
    boolean puedeRecetar,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDateTime periodoHasta,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDateTime graciaHasta
) {}
