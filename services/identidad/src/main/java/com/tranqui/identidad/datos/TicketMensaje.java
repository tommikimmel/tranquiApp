package com.tranqui.identidad.datos;

import java.time.LocalDateTime;

/**
 * Mensaje de un ticket de soporte (Fase 9, I2a).
 */
public record TicketMensaje(
        Long id,
        Long ticketId,
        Long autorId,
        String contenido,
        LocalDateTime fechaEnvio
) {}
