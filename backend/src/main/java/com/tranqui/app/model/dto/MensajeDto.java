package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeDto {
    private Long id;
    private Long remitenteId;
    private Long destinatarioId;
    private String contenido;
    private String fechaEnvio;
}
