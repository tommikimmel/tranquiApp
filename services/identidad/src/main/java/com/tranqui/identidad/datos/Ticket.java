package com.tranqui.identidad.datos;

import java.time.LocalDateTime;

/**
 * Entidad Ticket de soporte (Fase 9, I2a).
 */
public record Ticket(
        Long id,
        Integer version,
        Long creadorId,
        String asunto,
        String estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {
    public int aggregateVersion() {
        return (version != null ? version : 0) + 1;
    }
}
