package com.tranqui.identidad.web.dto;

public record TicketResumenDto(
        Long id,
        String asunto,
        String estado,
        String fechaCreacion,
        String fechaActualizacion,
        Long creadorId,
        String creadorNombre,
        String creadorEmail,
        String creadorRol,
        String ultimoMensaje
) {}
