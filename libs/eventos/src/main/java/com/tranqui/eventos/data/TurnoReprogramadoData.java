package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento turnos.turno.reprogramado.v1.
 * Consumido por notificaciones (avisos) y calendario (mover el evento).
 *
 * <p>Campos opcionales agregados en la Fase 1 para los mails: modalidad, dirección del consultorio
 * (presencial) y link de Meet (online).
 */
public record TurnoReprogramadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaAnterior,
    String horaInicioAnterior,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fechaNueva,
    String horaInicioNueva,
    String modalidad,
    String direccion,
    String meetUrl
) {
    public TurnoReprogramadoData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fechaAnterior,
                                 String horaInicioAnterior, LocalDate fechaNueva, String horaInicioNueva) {
        this(turnoId, pacienteId, profesionalId, fechaAnterior, horaInicioAnterior, fechaNueva, horaInicioNueva,
                null, null, null);
    }
}
