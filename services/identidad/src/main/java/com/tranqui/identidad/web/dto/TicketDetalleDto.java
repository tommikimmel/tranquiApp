package com.tranqui.identidad.web.dto;

import java.util.List;

public record TicketDetalleDto(
        Long id,
        String asunto,
        String estado,
        String fechaCreacion,
        String fechaActualizacion,
        Long creadorId,
        String creadorNombre,
        String creadorEmail,
        String creadorRol,
        List<TicketMensajeDto> mensajes
) {}
