package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.ticket.creado.v1 (agregado en la Fase 1).
 * Un usuario abrió un ticket de soporte. Consumido por notificaciones (aviso a los administradores).
 */
public record TicketCreadoData(
    Long ticketId,
    Long creadorId,
    String creadorNombre,
    String asunto
) {}
