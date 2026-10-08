package com.tranqui.app.model.dto;

import lombok.*;
import java.time.LocalDate;

// "Mi Cuenta" / configuración de perfil — usado tanto para pacientes como para médicos (aunque
// hoy solo el frontend de paciente tiene una página que lo consume). Deliberadamente separado
// de MedicoDto: ese DTO trae campos exclusivos de médico (tarifas, matrícula, Google Calendar,
// etc.) que no tienen sentido para un paciente.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MiCuentaDto {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String tipoDocumento;
    private Integer numeroDocumento;
    private String dni;
    private Long cuil;
    private String obraSocial;
    private String numAfiliado;
    private String fotoUrl;

    private String domicilioCalle;
    private String domicilioNumero;
    private String domicilioPiso;
    private String domicilioDpto;
    private String domicilioCodigoPostal;
    private String domicilioLocalidad;
    private String domicilioProvincia;
    private String domicilioPais;

    private boolean tienePassword; // false si la cuenta se creó/usa solo con Google
    private boolean notificacionesEmailHabilitadas;
    private boolean recibirNovedades;
}
