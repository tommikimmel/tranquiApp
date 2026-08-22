package com.tranqui.app.model.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDetalleDto {
    private Long id;
    private String asunto;
    private String estado;
    private String fechaCreacion;
    private String fechaActualizacion;
    private Long creadorId;
    private String creadorNombre;
    private String creadorEmail;
    private String creadorRol;
    private List<TicketMensajeDto> mensajes;
}
