package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.ticket.creado.v1 (agregado en la Fase 1).
 * Un usuario abrió un ticket de soporte. Consumido por notificaciones (aviso a los administradores).
 */
public record TicketCreadoData(
    @NotNull @Positive Long ticketId,
    @Positive Long creadorId,
    String creadorNombre,
    String asunto
) {}
