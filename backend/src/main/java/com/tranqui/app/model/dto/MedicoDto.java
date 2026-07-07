package com.tranqui.app.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicoDto {
    private Long id;
    private String name; // maps to name in frontend
    private String email;
    private String initials;
    private String degree; // maps to degree in frontend
    private String specialty; // maps to specialty in frontend
    private String matricula;
    private String cuit;
    private BigDecimal price; // maps to price in frontend
    private List<String> tags;
    private String color;
    private String fotoUrl;
    private boolean ofreceOnline;
    private boolean ofrecePresencial;
    private List<TarifaDto> tariffs;

    private String apellido;
    private String sexo;
    private java.time.LocalDate fechaNacimiento;
    private Long cuil;
    private String tipoDocumento;
    private Integer numeroDocumento;
    private String domicilioAtencion;
    private Long codigoReFeps;
    private MatriculaInfoDto matriculaInfo;
    private boolean verificado;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatriculaInfoDto {
        private String tipo;
        private String provincia;
        private Integer numero;
        private EspecialidadDto especialidad;
        private AsociadaDto asociada;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EspecialidadDto {
        private String textoLibre;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AsociadaDto {
        private String tipo;
        private String provincia;
        private Integer numero;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TarifaDto {
        private String id; // particular, primera, osde, etc.
        private String label;
        private BigDecimal price;
        private boolean enabled;
    }
}
