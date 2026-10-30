package com.tranqui.identidad.web.dto;

public record TicketMensajeDto(
        Long id,
        Long autorId,
        String autorNombre,
        boolean deAdmin,
        String contenido,
        String fechaEnvio
) {}
