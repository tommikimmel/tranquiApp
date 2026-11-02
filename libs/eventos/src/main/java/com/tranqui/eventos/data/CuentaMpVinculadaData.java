package com.tranqui.eventos.data;

/**
 * Datos del evento pagos.cuenta-mp.vinculada.v1.
 * Consumido por profesionales para habilitar la aparición en el buscador público.
 * Nota: Nunca almacena ni propaga tokens privados ni contraseñas.
 */
public record CuentaMpVinculadaData(
    Long profesionalId,
    String mpUserId
) {}
