package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.ticket.respondido.v1.
 * Consumido por notificaciones para enviar por mail la respuesta a un ticket de soporte.
 */
public record TicketRespondidoData(
    Long ticketId,
    Long usuarioId,
    String email,
    String asunto,
    String respuesta
) {}
