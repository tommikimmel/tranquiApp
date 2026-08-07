package com.tranqui.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformeClinicoDto {
    private String tipoInforme;
    private String planTrabajo;
    private String contenido;
    private String nombreArchivo;
    private String motivo; // solo usado por PUT: motivo de la corrección que genera el anexo
}
