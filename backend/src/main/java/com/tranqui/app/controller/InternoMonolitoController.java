package com.tranqui.app.controller;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.NotificacionRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas internas que el monolito responde a otros servicios mientras dura la migración
 * (sección 4.2: solo consultas; los cambios viajan por eventos). Protegidas por
 * {@link com.tranqui.app.config.FiltroServicioInterno}.
 */
@RestController
@RequestMapping("/internal/monolito")
public class InternoMonolitoController {

    /** Lo mínimo para la copia de contactos de notificaciones (sección 6.6). */
    public record ContactoDto(Long id, String email, String nombre, String apellido, String rol, boolean emailHabilitado) {}

    public record EnlacesAsistenciaDto(String confirmarUrl, String noAsistiraUrl) {}

    public record NotificacionDto(Long usuarioId, String titulo, String mensaje, boolean leido, String tipo,
                                  LocalDateTime fechaCreacion) {}

    public record AccesoDto(boolean permitido) {}

    private static final int TAMANIO_MAXIMO = 1000;

    private final UsuarioRepository usuarioRepository;
    private final TurnoRepository turnoRepository;
    private final NotificacionRepository notificacionRepository;
    private final com.tranqui.app.repository.MensajeRepository mensajeRepository;
    private final com.tranqui.app.service.SubscriptionService subscriptionService;
    private final com.tranqui.app.repository.GoogleCalendarEventoExternoRepository eventoExternoRepository;
    private final String publicUrl;

    public InternoMonolitoController(UsuarioRepository usuarioRepository, TurnoRepository turnoRepository,
                                     NotificacionRepository notificacionRepository,
                                     com.tranqui.app.repository.MensajeRepository mensajeRepository,
                                     com.tranqui.app.service.SubscriptionService subscriptionService,
                                     com.tranqui.app.repository.GoogleCalendarEventoExternoRepository eventoExternoRepository,
                                     @Value("${app.public-url:http://localhost:8081}") String publicUrl) {
        this.usuarioRepository = usuarioRepository;
        this.turnoRepository = turnoRepository;
        this.notificacionRepository = notificacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.subscriptionService = subscriptionService;
        this.eventoExternoRepository = eventoExternoRepository;
        this.publicUrl = publicUrl;
    }

    /** Carga inicial de la copia de contactos de notificaciones, paginada por id. */
    @GetMapping("/contactos")
    @Transactional(readOnly = true)
    public List<ContactoDto> contactos(@RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return usuarioRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoMonolitoController::contacto)
                .toList();
    }

    /**
     * Enlaces del mail de confirmación de asistencia. El token de un solo uso no viaja en el evento
     * turnos.recordatorio.programado (sección 5.3); notificaciones lo pide acá al armar el mail.
     */
    @GetMapping("/turnos/{id}/enlaces-asistencia")
    @Transactional(readOnly = true)
    public ResponseEntity<EnlacesAsistenciaDto> enlacesAsistencia(@PathVariable Long id) {
        Turno turno = turnoRepository.findById(id).orElse(null);
        if (turno == null || turno.getTokenConfirmacionAsistencia() == null) {
            return ResponseEntity.notFound().build();
        }
        String token = turno.getTokenConfirmacionAsistencia();
        return ResponseEntity.ok(new EnlacesAsistenciaDto(
                publicUrl + "/api/turnos/" + turno.getId() + "/confirmar-asistencia?token=" + token,
                publicUrl + "/api/turnos/" + turno.getId() + "/no-asistira?token=" + token));
    }

    /**
     * Notificaciones de las últimas 24 horas (después se borran), para la carga inicial del
     * servicio de notificaciones al pasarle la campanita.
     */
    @GetMapping("/notificaciones-vigentes")
    @Transactional(readOnly = true)
    public List<NotificacionDto> notificacionesVigentes() {
        return notificacionRepository.findByFechaCreacionAfterOrderByIdAsc(LocalDateTime.now().minusHours(24)).stream()
                .filter(n -> n.getUsuario() != null)
                .map(n -> new NotificacionDto(n.getUsuario().getId(), n.getTitulo(), n.getMensaje(),
                        Boolean.TRUE.equals(n.getLeido()), n.getTipo(), n.getFechaCreacion()))
                .toList();
    }

    public record MensajeDto(Long id, Long remitenteId, Long destinatarioId, String contenido, boolean leido,
                             LocalDateTime fechaEnvio) {}

    // La fecha va como texto ISO (yyyy-MM-dd): el monolito serializa LocalDate como dd/MM/yyyy para la
    // interfaz (DateConfig), y las rutas internas son un contrato entre servicios.
    /** Fase 3: suma lo que necesita el calendario para sus eventos (el chat ignora lo que no usa). */
    public record TurnoConfirmadoDto(Long turnoId, Long pacienteId, Long profesionalId, String fecha,
                                     String horaInicio, String horaFin, String modalidad, boolean ocupaAgenda,
                                     String googleEventId, String meetUrl) {}

    /** Fase 3: cuenta de Google de un profesional, con los tokens tal cual están guardados (cifrados). */
    public record CuentaGoogleDto(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                                  LocalDateTime tokenVenceEn, String calendarId, String syncToken,
                                  String watchChannelId, String watchResourceId, LocalDateTime watchVenceEn,
                                  boolean watchActivo) {}

    public record EventoExternoDto(Long id, Long profesionalId, String googleEventId, String titulo,
                                   LocalDateTime inicio, LocalDateTime fin, boolean todoElDia, String estado) {}

    /** Fase 2: carga inicial de los mensajes del servicio de chat, por id y conservando los ids. */
    @GetMapping("/mensajes")
    @Transactional(readOnly = true)
    public List<MensajeDto> mensajes(@RequestParam(defaultValue = "0") long desdeId,
                                     @RequestParam(defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return mensajeRepository.findByIdGreaterThanOrderByIdAsc(desdeId, PageRequest.of(0, tamanioSeguro)).stream()
                .map(m -> new MensajeDto(m.getId(), m.getRemitente().getId(), m.getDestinatario().getId(),
                        m.getContenido(), Boolean.TRUE.equals(m.getLeido()), m.getFechaEnvio()))
                .toList();
    }

    /**
     * Fase 2: turnos confirmados de hoy en adelante, para la copia del servicio de chat (la prioridad
     * de los canales del profesional depende de los turnos de los próximos días).
     */
    @GetMapping("/turnos-confirmados")
    @Transactional(readOnly = true)
    public List<TurnoConfirmadoDto> turnosConfirmados() {
        return turnoRepository.findByEstadoAndFechaGreaterThanEqualOrderByIdAsc(EstadoTurno.CONFIRMADO,
                        java.time.LocalDate.now()).stream()
                .map(t -> new TurnoConfirmadoDto(t.getId(), t.getPaciente().getId(), t.getMedico().getId(), t.getFecha().toString(),
                        t.getHoraInicio() != null ? t.getHoraInicio().toString() : null,
                        t.getHoraFin() != null ? t.getHoraFin().toString() : null,
                        t.getModalidad() != null ? t.getModalidad().name() : null,
                        t.isOcupaAgenda(), t.getGoogleEventId(), t.getTelemedicinaUrl()))
                .toList();
    }

    /**
     * Fase 3: cuentas de Google conectadas, para la carga inicial del servicio de calendario. Los tokens
     * viajan cifrados con la ENCRYPTION_KEY, que el servicio comparte (decisión 3 de la spec): nunca se
     * descifran para moverlos. Solo con JWT de servicio (FiltroServicioInterno).
     */
    @GetMapping("/calendario/cuentas")
    @Transactional(readOnly = true)
    public List<CuentaGoogleDto> cuentasGoogle() {
        return usuarioRepository.findByRolAndGoogleCalendarConnectedTrue(com.tranqui.app.model.Rol.PSIQUIATRA).stream()
                .map(u -> new CuentaGoogleDto(u.getId(), u.getGoogleAccessTokenEncrypted(), u.getGoogleRefreshTokenEncrypted(),
                        u.getGoogleTokenExpiresAt(), u.getGoogleCalendarId(), u.getGoogleSyncToken(),
                        u.getGoogleWatchChannelId(), u.getGoogleWatchResourceId(), u.getGoogleWatchExpiresAt(),
                        Boolean.TRUE.equals(u.getGoogleWatchActive())))
                .toList();
    }

    /** Fase 3: caché de eventos externos de Google (los propios del profesional), paginada por id. */
    @GetMapping("/calendario/eventos-externos")
    @Transactional(readOnly = true)
    public List<EventoExternoDto> eventosExternos(@RequestParam(name = "desdeId", defaultValue = "0") long desdeId,
                                                  @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return eventoExternoRepository.findByIdGreaterThanOrderByIdAsc(desdeId, PageRequest.of(0, tamanioSeguro)).stream()
                .map(e -> new EventoExternoDto(e.getId(), e.getMedico().getId(), e.getGoogleEventId(), e.getTitulo(),
                        e.getFechaInicio(), e.getFechaFin(), Boolean.TRUE.equals(e.getTodoElDia()), e.getEstado()))
                .toList();
    }

    private static ContactoDto contacto(Usuario u) {
        return new ContactoDto(u.getId(), u.getEmail(), u.getNombre(), u.getApellido(),
                u.getRol() != null ? u.getRol().name() : null, u.isNotificacionesEmailHabilitadas());
    }

    /**
     * Paywall de los servicios (Fase 3, G1): el gateway pregunta si un profesional tiene acceso. El JWT
     * de usuario trae el mail como subject, no el id. Un usuario que no existe no se bloquea acá: lo
     * resuelve la autenticación del servicio.
     */
    @GetMapping("/suscripciones/acceso")
    public AccesoDto accesoSuscripcion(@RequestParam(name = "email") String email) {
        return usuarioRepository.findByEmail(email)
                .map(u -> new AccesoDto(subscriptionService.isAccessAllowed(u.getId())))
                .orElse(new AccesoDto(true));
    }
}
