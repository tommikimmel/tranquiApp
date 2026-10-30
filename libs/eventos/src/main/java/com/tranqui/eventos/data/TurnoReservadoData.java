package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Datos del evento turnos.turno.reservado.v1.
 * Consumido por pagos (creación de preferencia MP) y notificaciones (aviso al profesional).
 *
 * <p>Campo opcional agregado en la Fase 4: el título del ítem de Mercado Pago (por ejemplo
 * "Consulta Psiquiátrica - Lucía"), para que pagos arme la preferencia igual que el monolito sin
 * guardar una copia de los nombres.
 */
public record TurnoReservadoData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    @PositiveOrZero BigDecimal precio,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime fechaExpiracion,
    String tituloCobro
) {
    public TurnoReservadoData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha, String horaInicio,
                              BigDecimal precio, OffsetDateTime fechaExpiracion) {
        this(turnoId, pacienteId, profesionalId, fecha, horaInicio, precio, fechaExpiracion, null);
    }
}
