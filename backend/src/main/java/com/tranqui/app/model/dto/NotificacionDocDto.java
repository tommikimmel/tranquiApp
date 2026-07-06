package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacionDocDto {
    private Long id;
    private String pacienteNombre;
    private String tipo;
    private String fecha;
    private String titulo;
    private String mensaje;
}
