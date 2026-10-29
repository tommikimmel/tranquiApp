package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento pagos.cuenta-mp.vinculada.v1.
 * Consumido por profesionales para habilitar la aparición en el buscador público.
 * Nota: Nunca almacena ni propaga tokens privados ni contraseñas.
 */
public record CuentaMpVinculadaData(
    @NotNull @Positive Long profesionalId,
    String mpUserId
) {}
