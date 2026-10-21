package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.ticket.mensaje-recibido.v1 (agregado en la Fase 1).
 * El usuario que abrió un ticket respondió en él. Consumido por notificaciones (aviso a los
 * administradores). La respuesta de soporte al usuario es identidad.ticket.respondido.v1.
 */
public record TicketMensajeRecibidoData(
    Long ticketId,
    Long autorId,
    String autorNombre,
    String asunto
) {}
