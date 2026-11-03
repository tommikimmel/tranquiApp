package com.tranqui.eventos.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.ticket.respondido.v1.
 * Consumido por notificaciones para enviar por mail la respuesta a un ticket de soporte.
 */
public record TicketRespondidoData(
    @NotNull @Positive Long ticketId,
    @Positive Long usuarioId,
    @Email String email,
    String asunto,
    String respuesta
) {}
