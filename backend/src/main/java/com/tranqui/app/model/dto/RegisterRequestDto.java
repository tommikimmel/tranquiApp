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
    private Double domicilioLat;
    private Double domicilioLng;
    private String domicilioAtencionTorre;
    private String domicilioAtencionPiso;
    private String domicilioAtencionDepto;
    private String domicilioAtencionBarrio;
    private String matriculaTipo;
    private String matriculaProvincia;
    private Integer matriculaNumero;
    private Boolean ofreceOnline;
    private Boolean ofrecePresencial;
    private String fotoUrl;

    // Plan Suscripciones (§6): Profesión, Matrícula extendida y Datos Fiscales
    private String profession; // psiquiatra | psicologo | otro
    private String licenseType; // MN | MP | MP_psico
    private String licenseNumber;
    private String licenseJurisdiction;
    private String licenseDocumentUrl;
    private String taxIdType; // CUIT | CUIL | DNI
    private String taxId;
    private String legalName; // Razón social
    private Integer ivaConditionId; // 1=RI, 4=Exento, 5=CF, 6=Monotributo
    private String fiscalAddress;

    // Debe venir en true para poder registrarse — ver AuthController.register().
    private Boolean aceptaTerminos;
}
