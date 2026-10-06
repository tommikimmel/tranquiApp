package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario", indexes = {
        @Index(name = "idx_usuario_rol", columnList = "rol")
})
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

    // Which Google calendar we read/write for this médico. Always "primary" today, but
    // persisted explicitly so a future "pick a specific calendar" feature doesn't need
    // another schema change.
    @Column(name = "google_calendar_id", length = 255)
    private String googleCalendarId;

    // Sync token from the last successful events.list() call, used to fetch only what
    // changed since then instead of re-listing the médico's whole calendar every poll.
    // Null means "never synced yet" — the next sync does a full listing to seed it.
    @Column(name = "google_sync_token", columnDefinition = "TEXT")
    private String googleSyncToken;

    // Google Calendar push notification channel (events.watch) for this médico — lets Google
    // tell us about changes in near-real-time instead of waiting for the next poll. All null
    // until GoogleCalendarWatchService.registrarCanal() runs (only when
    // google.calendar.watch.enabled=true).
    @Column(name = "google_watch_channel_id", length = 100)
    private String googleWatchChannelId;

    @Column(name = "google_watch_resource_id", length = 100)
    private String googleWatchResourceId;

    @Column(name = "google_watch_expires_at")
    private LocalDateTime googleWatchExpiresAt;

    @Builder.Default
    @Column(name = "google_watch_active")
    private Boolean googleWatchActive = false;

    @Column(name = "telefono", length = 30)
    private String telefono;

    // Public-facing contact email shown on the médico's professional card — deliberately
    // separate from `email` above, which is the account/login identity and stays private.
    @Column(name = "email_contacto", length = 150)
    private String emailContacto;

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

    @Column(name = "domicilio_atencion_torre", length = 50)
    private String domicilioAtencionTorre;

    @Column(name = "domicilio_atencion_piso", length = 20)
    private String domicilioAtencionPiso;

    @Column(name = "domicilio_atencion_depto", length = 20)
    private String domicilioAtencionDepto;

    @Column(name = "domicilio_atencion_barrio", length = 100)
    private String domicilioAtencionBarrio;

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

    @Builder.Default
    @Column(name = "email_verificado")
    private Boolean emailVerificado = false;

    @Column(name = "codigo_verificacion", length = 10)
    private String codigoVerificacion;

    @Column(name = "codigo_verificacion_expires_at")
    private LocalDateTime codigoVerificacionExpiresAt;

    @Column(name = "reset_password_code", length = 10)
    private String resetPasswordCode;

    @Column(name = "reset_password_expires_at")
    private LocalDateTime resetPasswordExpiresAt;

    // Set to true when an admin resets this user's password (AdminController#resetPassword) —
    // the generated password is temporary, and the frontend blocks the rest of the app behind a
    // mandatory "set your new password" modal (same gating pattern as terminosAceptadosEn) until
    // AuthController#setNewPassword clears it.
    @Builder.Default
    @Column(name = "must_change_password", columnDefinition = "boolean not null default false")
    private boolean mustChangePassword = false;

    @Column(name = "verificado_admin")
    private Boolean verificadoAdmin;

    // Whether the user has filled in the Paso 2 profile data (nombre, apellido, sexo,
    // fechaNacimiento, tipoDocumento, numeroDocumento, telefono). Null/true means complete —
    // every pre-existing user and every account created through the normal multi-step register
    // flow already has this data, so they don't need a migration backfill. Only Google sign-in
    // creates a user with just email+nombre and explicitly sets this to false, which gates the
    // "complete your profile" prompt on the frontend until PATCHed true via /auth/complete-profile.
    @Column(name = "perfil_completo")
    private Boolean perfilCompleto;

    public boolean isPerfilCompleto() {
        return perfilCompleto == null || perfilCompleto;
    }

    @Column(name = "descripcion_perfil", columnDefinition = "TEXT")
    private String descripcionPerfil;

    @Column(name = "pacientes_atiende", length = 255)
    private String pacientesAtiende; // comma separated, e.g. "Adultos,Adultos mayores"

    @Column(name = "institucion_formacion", length = 255)
    private String institucionFormacion;

    @Column(name = "anios_experiencia")
    private Integer aniosExperiencia;

    // Per-médico agenda settings: how long each appointment slot lasts, and how much gap
    // to leave between the end of one bookable slot and the start of the next when
    // AgendaService walks through an availability window generating candidate start times.
    // Null means "not configured yet" — callers fall back to the historical defaults
    // (45 / 10) so existing doctors keep their current behavior until they opt in.
    @Column(name = "duracion_turno_minutos")
    private Integer duracionTurnoMinutos;

    @Column(name = "intervalo_entre_turnos_minutos")
    private Integer intervaloEntreTurnosMinutos;

    @Column(name = "experiencia", columnDefinition = "TEXT")
    private String experiencia;

    // JSON array of press/media mentions (título, descripción, link) the médico opts to publish
    // on their profile — same "stringly-typed JSON blob" storage as experiencia above, parsed
    // client-side. Optional, unlike experiencia which getMissingRequirements enforces.
    @Column(name = "publicaciones", columnDefinition = "TEXT")
    private String publicaciones;

    @Column(name = "instagram_url", length = 255)
    private String instagramUrl;

    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "sitio_web_url", length = 255)
    private String sitioWebUrl;

    // ── QBI2 Recipe integration fields (médico) ────────────────────────────
    // REFEPS registry code — QBI2/Innovamed requires it to generate the electronic
    // signature on every prescription automatically; we no longer collect a signature
    // image ourselves.
    @Column(name = "codigo_refeps", length = 50)
    private String codigoRefeps;

    // "Sello" — the 3-line stamp block QBI2 prints on the PDF (e.g. "Dr. Juan Pérez" /
    // "Psiquiatría" / "MN 12345"). Kept editable/separate from nombre+especialidad+matricula
    // so a médico can control the exact wording that appears on a legal document.
    @Column(name = "sello_linea1", length = 40)
    private String selloLinea1;

    @Column(name = "sello_linea2", length = 40)
    private String selloLinea2;

    @Column(name = "sello_linea3", length = 25)
    private String selloLinea3;

    // ── QBI2 Recipe integration fields (paciente) ──────────────────────────
    // Note: QBI2's tipoDoc/nroDoc need is already covered by the existing tipoDocumento/
    // numeroDocumento fields above, which are already wired for both médico and paciente rows
    // (see ClinicalService.actualizarPaciente). No new field needed there.

    // Structured domicilio (QBI2's paciente.domicilio object). Additive alongside the existing
    // free-text `direccion` — the frontend still edits `direccion` as a single field; these are
    // only populated where the structured breakdown is actually collected/needed for a receta.
    @Column(name = "domicilio_calle", length = 50)
    private String domicilioCalle;

    @Column(name = "domicilio_numero", length = 6)
    private String domicilioNumero;

    @Column(name = "domicilio_piso", length = 2)
    private String domicilioPiso;

    @Column(name = "domicilio_dpto", length = 3)
    private String domicilioDpto;

    @Column(name = "domicilio_codigo_postal", length = 8)
    private String domicilioCodigoPostal;

    @Column(name = "domicilio_localidad", length = 200)
    private String domicilioLocalidad;

    @Column(name = "domicilio_provincia", length = 200)
    private String domicilioProvincia;

    @Column(name = "domicilio_pais", length = 45)
    private String domicilioPais;

    // ── Cuenta (paciente/médico) ────────────────────────────────────────────
    // Eliminar cuenta never deletes this row (Turno/InformeClinico/SeguimientoDiario point at
    // it without cascade, and historia clínica has a legal minimum retention) — it anonymizes
    // the PII fields and sets these two instead. See AccountService.eliminarCuenta.
    @Builder.Default
    @Column(name = "cuenta_eliminada")
    private Boolean cuentaEliminada = false;

    @Column(name = "anonimizado_en")
    private LocalDateTime anonimizadoEn;

    public boolean isCuentaEliminada() {
        return cuentaEliminada != null && cuentaEliminada;
    }

    // Opt-out notification preferences — default true so nobody stops receiving notifications
    // just because this column got added. See AccountService.actualizarPreferenciasNotificacion
    // and the checks in ResendEmailService.
    @Builder.Default
    @Column(name = "notificaciones_email_habilitadas")
    private Boolean notificacionesEmailHabilitadas = true;

    public boolean isNotificacionesEmailHabilitadas() {
        return notificacionesEmailHabilitadas == null || notificacionesEmailHabilitadas;
    }

    // ── Professional Profile, Matrícula & Datos Fiscales (Plan Suscripciones) ──
    @Column(name = "profession", length = 30)
    private String profession; // psiquiatra | psicologo | otro

    @Column(name = "license_type", length = 20)
    private String licenseType; // MN | MP | MP_psico

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(name = "license_jurisdiction", length = 100)
    private String licenseJurisdiction;

    @Column(name = "license_document_url", columnDefinition = "TEXT")
    private String licenseDocumentUrl;

    @Column(name = "license_verified_at")
    private LocalDateTime licenseVerifiedAt;

    @Column(name = "license_verified_by", length = 100)
    private String licenseVerifiedBy;

    // tax_id_type: CUIT | CUIL | DNI
    @Column(name = "tax_id_type", length = 20)
    private String taxIdType;

    @Column(name = "tax_id", length = 30)
    private String taxId;

    @Column(name = "legal_name", length = 200)
    private String legalName; // Razón Social / Nombre fiscal

    // ARCA CondicionIVAReceptorId: 1=RI, 4=Exento, 5=Consumidor Final, 6=Monotributo
    @Column(name = "iva_condition_id")
    private Integer ivaConditionId;

    @Column(name = "fiscal_address", length = 255)
    private String fiscalAddress;

    @Column(name = "qbi2_client_id", length = 100)
    private String qbi2ClientId;

    public boolean isCanPrescribe() {
        boolean isMedical = "psiquiatra".equalsIgnoreCase(profession) || "medico".equalsIgnoreCase(profession)
                || rol == Rol.PSIQUIATRA;
        return isMedical && (licenseVerifiedAt != null || Boolean.TRUE.equals(verificadoAdmin));
    }

    public String getEffectiveTaxId() {
        if (taxId != null && !taxId.isBlank()) return taxId;
        if (cuit != null && !cuit.isBlank()) return cuit;
        if (cuil != null) return String.valueOf(cuil);
        if (dni != null && !dni.isBlank()) return dni;
        if (numeroDocumento != null) return String.valueOf(numeroDocumento);
        return null;
    }

    public String getEffectiveLegalName() {
        if (legalName != null && !legalName.isBlank()) return legalName;
        String full = ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
        return full.isEmpty() ? email : full;
    }

    public Integer getEffectiveIvaConditionId() {
        if (ivaConditionId != null) return ivaConditionId;
        return 6; // Default to Monotributo (6) for Argentina health professionals
    }

    // Null means "not accepted yet" — set once, at register() time, and never cleared. Existing
    // rows from before this column existed are backfilled once via a manual SQL UPDATE at
    // deploy time (see runbook), not by app code, so they never see the acceptance modal.
    @Column(name = "terminos_aceptados_en")
    private LocalDateTime terminosAceptadosEn;

    @PrePersist
    protected void onCreate() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        normalizeEmail();
    }

    // Belt-and-suspenders: every write path (AuthController.register/login already normalize
    // before calling in, but GoogleAuthService/TurnoService historically didn't — see the bug
    // where a mixed-case or padded email created via one of those paths would silently bypass
    // the "email already registered" check done via findByEmail elsewhere) ends up here before
    // hitting the DB, so `email` is guaranteed lowercase/trimmed no matter which code path wrote
    // it. Combined with the UNIQUE constraint on this column, this makes duplicate accounts for
    // the same address (just differing by case/whitespace) impossible going forward.
    @PreUpdate
    protected void onUpdate() {
        normalizeEmail();
    }

    private void normalizeEmail() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}
