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
    private String name; // combined "nombre + apellido" for display, NOT for editing — see nombre
    private String nombre; // raw first name only; the settings form reads/writes this
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
    private Double domicilioLat;
    private Double domicilioLng;
    private Long codigoReFeps;
    private MatriculaInfoDto matriculaInfo;
    private boolean verificado;
    private Boolean verificadoAdmin;
    private String experiencia;
    private RedesSocialesDto redesSociales;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RedesSocialesDto {
        private String instagram;
        private String facebook;
        private String linkedin;
        private String sitioWeb;
    }

    private String descripcionPerfil;
    private List<String> pacientesAtiende;
    private String institucionFormacion;
    private Integer aniosExperiencia;

    // Agenda settings — see Usuario.duracionTurnoMinutos / intervaloEntreTurnosMinutos.
    private Integer duracionTurnoMinutos;
    private Integer intervaloEntreTurnosMinutos;

    // QBI2 Recipe integration — digital signature + PDF stamp. See Usuario for field docs.
    private String firmaUrl;
    private String selloLinea1;
    private String selloLinea2;
    private String selloLinea3;

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
