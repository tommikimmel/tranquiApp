package com.tranqui.app.model.dto;

import com.tranqui.app.model.Rol;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDto {
    private String email;
    private String password;
    private Rol rol; // PACIENTE or PSIQUIATRA

    // Common fields
    private String nombre;
    private String apellido;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String tipoDocumento;
    private Integer numeroDocumento;
    private String telefono;

    // Patient specific fields
    private String obraSocial;
    private String numAfiliado;

    // Professional specific fields
    private String matricula;
    private String titulo;
    private String specialty;
    private String cuit;
    private Long cuil;
    private String domicilioAtencion;
    private Long codigoReFeps;
    private String matriculaTipo;
    private String matriculaProvincia;
    private Integer matriculaNumero;
    private Boolean ofreceOnline;
    private Boolean ofrecePresencial;
    private String fotoUrl;
}
