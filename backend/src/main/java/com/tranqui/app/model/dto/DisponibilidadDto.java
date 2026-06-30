package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadDto {
    private Integer diaSemana; // 1 = Lunes, 7 = Domingo
    private String horaInicio; // "HH:mm"
    private String horaFin;    // "HH:mm"
}
