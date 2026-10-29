package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos del evento profesionales.tarifa.actualizada.v2 (Fase 5): todas las tarifas (servicios) del
 * profesional. El monolito actualiza su copia por {@code servicioId} y borra las que ya no están.
 */
public record TarifasProfesionalData(
    @NotNull @Positive Long profesionalId,
    @Valid List<Tarifa> tarifas
) {
    public record Tarifa(String servicioId, String label, @PositiveOrZero BigDecimal precio, boolean habilitado,
                         boolean requiereObraSocial, String obraSocial, @PositiveOrZero BigDecimal precioOnline,
                         @PositiveOrZero BigDecimal precioPresencial, boolean requiereAgenda, boolean esReceta) {}
}
