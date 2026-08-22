package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMensajeDto {
    private Long id;
    private Long autorId;
    private String autorNombre;
    private boolean deAdmin; // true si lo escribió alguien con rol ADMIN — para pintarlo distinto en el chat
    private String contenido;
    private String fechaEnvio;
}
