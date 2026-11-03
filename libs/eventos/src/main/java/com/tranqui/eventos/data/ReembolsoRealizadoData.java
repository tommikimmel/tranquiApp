package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos del evento pagos.reembolso.realizado.v1.
 * Consumido por turnos (marcar reembolso) y notificaciones (envío de comprobante).
 *
 * <p>Campos opcionales agregados en la Fase 4: paciente, profesional y fecha del turno para el mail, y
 * {@code avisarPaciente}. Con el servicio de pagos el reembolso llega después de la cancelación, así que
 * notificaciones manda un segundo mail confirmándolo; el monolito (vuelta atrás) lo hace dentro de la
 * cancelación y su mail ya lo dice, por eso no lo marca.
 */
public record ReembolsoRealizadoData(
    @Positive Long reembolsoId,
    @Positive Long pagoId,
    @NotNull @Positive Long turnoId,
    @PositiveOrZero BigDecimal monto,
    String motivo,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    Boolean avisarPaciente
) {
    public ReembolsoRealizadoData(Long reembolsoId, Long pagoId, Long turnoId, BigDecimal monto, String motivo) {
        this(reembolsoId, pagoId, turnoId, monto, motivo, null, null, null, null, null);
    }
}
