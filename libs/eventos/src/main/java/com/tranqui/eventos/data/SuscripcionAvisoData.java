package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos del evento suscripciones.aviso.programado.v1 (agregado en la Fase 1).
 * Un aviso por mail al profesional sobre su suscripción. Consumido por notificaciones.
 *
 * <p>{@code tipo} y los campos que usa cada uno:
 * <ul>
 *   <li>{@code PREAVISO_RENOVACION}: monto, {@code fecha} (cobro) y {@code ciclo}.</li>
 *   <li>{@code PAGO_CONFIRMADO}: monto, {@code fecha} y {@code fechaHasta} (período) y {@code comprobante}.</li>
 *   <li>{@code PAGO_FALLIDO}: monto y {@code fecha} (fin del período de gracia).</li>
 *   <li>{@code CANCELACION_CONFIRMADA}: {@code fecha} (acceso hasta).</li>
 *   <li>{@code SUSPENSION}: solo el plan.</li>
 *   <li>{@code VENCIMIENTO_MANUAL}: {@code fecha} (vencimiento) y {@code diasRestantes}.</li>
 * </ul>
 */
public record SuscripcionAvisoData(
    @Positive Long suscripcionId,
    @NotNull @Positive Long profesionalId,
    String tipo,
    String nombrePlan,
    @PositiveOrZero BigDecimal monto,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDateTime fecha,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDateTime fechaHasta,
    String ciclo,
    String comprobante,
    Integer diasRestantes
) {
    public static final String PREAVISO_RENOVACION = "PREAVISO_RENOVACION";
    public static final String PAGO_CONFIRMADO = "PAGO_CONFIRMADO";
    public static final String PAGO_FALLIDO = "PAGO_FALLIDO";
    public static final String CANCELACION_CONFIRMADA = "CANCELACION_CONFIRMADA";
    public static final String SUSPENSION = "SUSPENSION";
    public static final String VENCIMIENTO_MANUAL = "VENCIMIENTO_MANUAL";
}
