package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

/**
 * Datos del evento calendario.horario.bloqueado.v1.
 * Consumido por turnos para reflejar bloqueos originados en eventos personales de Google Calendar.
 */
public record HorarioBloqueadoData(
    Long profesionalId,
    String googleEventId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime inicio,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime fin,
    String motivo
) {}
