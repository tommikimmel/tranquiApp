package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.turno.cancelado.v1.
 * Consumido por pagos (reembolso), notificaciones (avisos) y calendario (borrar el evento).
 *
 * <p>Campos opcionales agregados en la Fase 1 para los mails: fecha y hora del turno cancelado, si
 * ocupa agenda y el resultado del reembolso ({@code REEMBOLSADO}, {@code SIN_REEMBOLSO},
 * {@code SIN_PAGO} o {@code PENDIENTE}).
 */
public record TurnoCanceladoData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    String canceladoPor,
    String motivo,
    boolean correspondeReembolso,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    Boolean ocupaAgenda,
    String resultadoReembolso
) {
    public TurnoCanceladoData(Long turnoId, Long pacienteId, Long profesionalId, String canceladoPor,
                              String motivo, boolean correspondeReembolso) {
        this(turnoId, pacienteId, profesionalId, canceladoPor, motivo, correspondeReembolso, null, null, null, null);
    }
}
