package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento pagos.cuenta-mp.desvinculada.v1.
 * Consumido por profesionales al desvincular la cuenta de cobro.
 */
public record CuentaMpDesvinculadaData(
    @NotNull @Positive Long profesionalId
) {}
