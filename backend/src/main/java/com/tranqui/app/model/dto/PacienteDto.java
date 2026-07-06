package com.tranqui.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteDto {
    private Long id;
    private String nombre;
    private String email;
    private String telefono;
    private String dni;
    private String direccion;
    private String obraSocial;
    private String numAfiliado;
    private String ultimaVisita;
    private String prioridadClinica;
    private boolean sinTurno;
}
