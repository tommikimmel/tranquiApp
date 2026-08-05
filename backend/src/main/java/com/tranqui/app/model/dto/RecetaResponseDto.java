package com.tranqui.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecetaResponseDto {
    private Long id;
    private MedicoSimpleDto medico;
    private PacienteSimpleDto paciente;
    private String medicamentos;
    private String diagnostico;
    private String indicaciones;
    private String pdfUrl;
    private LocalDateTime fechaEmision;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicoSimpleDto {
        private Long id;
        private String nombre;
        private String apellido;
        private String matricula;
        private String especialidad;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PacienteSimpleDto {
        private Long id;
        private String nombre;
        private String apellido;
        private String dni;
        private String email;
        private String telefono;
    }
}
