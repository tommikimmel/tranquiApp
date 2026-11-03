package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Datos del evento profesionales.tarifa.actualizada.v1.
 * Consumido por turnos para validar los precios vigentes.
 */
public record TarifaActualizadaData(
    @NotNull @Positive Long profesionalId,
    @Positive Long tarifaId,
    @PositiveOrZero BigDecimal precio,
    String moneda
) {}
