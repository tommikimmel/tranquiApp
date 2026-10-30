package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.turno.reprogramado.v1.
 * Consumido por notificaciones (avisos) y calendario (mover el evento).
 *
 * <p>Campos opcionales agregados en la Fase 1 para los mails: modalidad, dirección del consultorio
 * (presencial) y link de Meet (online). Fase 3: {@code horaFinNueva} (opcional), para mover el evento
 * de Google con su duración.
 */
public record TurnoReprogramadoData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaAnterior,
    String horaInicioAnterior,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaNueva,
    String horaInicioNueva,
    String modalidad,
    String direccion,
    String meetUrl,
    String horaFinNueva
) {
    public TurnoReprogramadoData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fechaAnterior,
                                 String horaInicioAnterior, LocalDate fechaNueva, String horaInicioNueva) {
        this(turnoId, pacienteId, profesionalId, fechaAnterior, horaInicioAnterior, fechaNueva, horaInicioNueva,
                null, null, null, null);
    }

    /** Sin {@code horaFinNueva} (los productores anteriores a la Fase 3). */
    public TurnoReprogramadoData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fechaAnterior,
                                 String horaInicioAnterior, LocalDate fechaNueva, String horaInicioNueva,
                                 String modalidad, String direccion, String meetUrl) {
        this(turnoId, pacienteId, profesionalId, fechaAnterior, horaInicioAnterior, fechaNueva, horaInicioNueva,
                modalidad, direccion, meetUrl, null);
    }
}
