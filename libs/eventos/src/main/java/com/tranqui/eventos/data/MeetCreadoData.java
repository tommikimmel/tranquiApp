package com.tranqui.eventos.data;

/**
 * Datos del evento calendario.meet.creado.v1.
 * Consumido por turnos (guardar enlace) y notificaciones (envío al paciente).
 */
public record MeetCreadoData(
    Long turnoId,
    String meetUrl,
    String googleEventId
) {}
