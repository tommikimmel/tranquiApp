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

    @Column(name = "instagram_url", length = 255)
    private String instagramUrl;

    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "sitio_web_url", length = 255)
    private String sitioWebUrl;

    // ── QBI2 Recipe integration fields (médico) ────────────────────────────
    // Digital signature image, required on every electronic prescription. Same
    // shape as fotoUrl (a URL or a base64 data-uri) and validated the same way
    // (see MedicoService.MAX_FOTO_BYTES) so médicos reuse the upload flow they
    // already know from their profile photo.
    @Column(name = "firma_url", columnDefinition = "TEXT")
    private String firmaUrl;

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

    @PrePersist
    protected void onCreate() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
