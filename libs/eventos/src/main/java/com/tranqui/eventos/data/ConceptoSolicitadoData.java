package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Datos del evento profesionales.concepto.solicitado.v1 (Fase 4).
 * Un paciente pidió un documento pago por la API de conceptos ({@code /api/conceptos/solicitar}).
 * Consumido por pagos, que crea la preferencia con la referencia {@code doc-<solicitudId>}. El título
 * es el del ítem de Mercado Pago, armado por quien conoce al profesional.
 */
public record ConceptoSolicitadoData(
    @NotNull @Positive Long solicitudId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    String tipoConcepto,
    @PositiveOrZero BigDecimal precio,
    String titulo
) {}
