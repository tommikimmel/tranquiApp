package com.tranqui.eventos.data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos del evento profesionales.tarifa.actualizada.v2 (Fase 5): todas las tarifas (servicios) del
 * profesional. El monolito actualiza su copia por {@code servicioId} y borra las que ya no están.
 */
public record TarifasProfesionalData(
    Long profesionalId,
    List<Tarifa> tarifas
) {
    public record Tarifa(String servicioId, String label, BigDecimal precio, boolean habilitado,
                         boolean requiereObraSocial, String obraSocial, BigDecimal precioOnline,
                         BigDecimal precioPresencial, boolean requiereAgenda, boolean esReceta) {}
}
