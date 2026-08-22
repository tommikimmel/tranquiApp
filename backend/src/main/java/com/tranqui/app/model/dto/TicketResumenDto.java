package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResumenDto {
    private Long id;
    private String asunto;
    private String estado;
    private String fechaCreacion;
    private String fechaActualizacion;
    // Solo relevantes para el listado del admin (ve tickets de todos) — el listado propio del
    // paciente/profesional los ignora, ya sabe quién es.
    private Long creadorId;
    private String creadorNombre;
    private String creadorEmail;
    private String creadorRol;
    private String ultimoMensaje;
}
