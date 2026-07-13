package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "matricula", length = 50)
    private String matricula;

    @Column(name = "mp_access_token_encrypted", columnDefinition = "TEXT")
    private String mpAccessTokenEncrypted;

    @Column(name = "mp_refresh_token_encrypted", columnDefinition = "TEXT")
    private String mpRefreshTokenEncrypted;

    @Column(name = "mp_token_expires_at")
    private LocalDateTime mpTokenExpiresAt;

    @Column(name = "mp_user_id", length = 50)
    private String mpUserId;

    @Column(name = "google_access_token_encrypted", columnDefinition = "TEXT")
    private String googleAccessTokenEncrypted;

    @Column(name = "google_refresh_token_encrypted", columnDefinition = "TEXT")
    private String googleRefreshTokenEncrypted;

    @Column(name = "google_token_expires_at")
    private LocalDateTime googleTokenExpiresAt;

    @Builder.Default
    @Column(name = "google_calendar_connected")
    private Boolean googleCalendarConnected = false;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "titulo", length = 50)
    private String titulo;

    @Column(name = "especialidad", length = 100)
    private String specialty; // maps to specialty in frontend

    @Column(name = "cuit", length = 20)
    private String cuit;

    @Column(name = "dni", length = 20)
    private String dni;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "obra_social", length = 100)
    private String obraSocial;

    @Column(name = "num_afiliado", length = 50)
    private String numAfiliado;

    @Column(name = "precio", precision = 12, scale = 2)
    private java.math.BigDecimal precio;

    @Column(name = "tags", length = 255)
    private String tags; // comma separated tags e.g. "Ansiedad,Estrés"

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "foto_url", columnDefinition = "TEXT")
    private String fotoUrl;

    @Column(name = "apellido", length = 100)
    private String apellido;

    @Column(name = "sexo", length = 10)
    private String sexo;

    @Column(name = "fecha_nacimiento")
    private java.time.LocalDate fechaNacimiento;

    @Column(name = "cuil")
    private Long cuil;

    @Column(name = "tipo_documento", length = 20)
    private String tipoDocumento;

    @Column(name = "numero_documento")
    private Integer numeroDocumento;

    @Column(name = "domicilio_atencion", length = 255)
    private String domicilioAtencion;

    @Column(name = "domicilio_lat")
    private Double domicilioLat;

    @Column(name = "domicilio_lng")
    private Double domicilioLng;

    @Column(name = "codigo_refeps")
    private Long codigoReFeps;

    @Column(name = "matricula_tipo", length = 20)
    private String matriculaTipo;

    @Column(name = "matricula_provincia", length = 20)
    private String matriculaProvincia;

    @Column(name = "matricula_numero")
    private Integer matriculaNumero;

    @Column(name = "matricula_especialidad", length = 100)
    private String matriculaEspecialidad;

    @Column(name = "matricula_asoc_tipo", length = 20)
    private String matriculaAsocTipo;

    @Column(name = "matricula_asoc_provincia", length = 20)
    private String matriculaAsocProvincia;

    @Column(name = "matricula_asoc_numero")
    private Integer matriculaAsocNumero;

    @Column(name = "datos_ofuscado", length = 10)
    private String datosOfuscado;

    @Column(name = "credencial_cod_entidad")
    private Integer credencialCodEntidad;

    @Column(name = "credencial_pan", length = 50)
    private String credencialPan;

    @Column(name = "credencial_plan", length = 50)
    private String credencialPlan;

    @Column(name = "credencial_token", length = 50)
    private String credencialToken;

    @Column(name = "ofrece_online")
    private Boolean ofreceOnline;

    @Column(name = "ofrece_presencial")
    private Boolean ofrecePresencial;

    public boolean isOfreceOnline() {
        return ofreceOnline == null || ofreceOnline;
    }

    public boolean isOfrecePresencial() {
        return ofrecePresencial != null && ofrecePresencial;
    }

    @Builder.Default
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(name = "password", length = 100)
    private String password;

    @Column(name = "verificado_admin")
    private Boolean verificadoAdmin;

    @PrePersist
    protected void onCreate() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
