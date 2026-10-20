package com.tranqui.eventos.data;

import java.math.BigDecimal;

/**
 * Datos del evento profesionales.tarifa.actualizada.v1.
 * Consumido por turnos para validar los precios vigentes.
 */
public record TarifaActualizadaData(
    Long profesionalId,
    Long tarifaId,
    BigDecimal precio,
    String moneda
) {}
