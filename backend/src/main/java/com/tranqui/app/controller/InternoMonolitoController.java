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

    private static final int TAMANIO_MAXIMO = 1000;

    private final UsuarioRepository usuarioRepository;
    private final TurnoRepository turnoRepository;
    private final NotificacionRepository notificacionRepository;
    private final com.tranqui.app.repository.MensajeRepository mensajeRepository;
    private final String publicUrl;

    public InternoMonolitoController(UsuarioRepository usuarioRepository, TurnoRepository turnoRepository,
                                     NotificacionRepository notificacionRepository,
                                     com.tranqui.app.repository.MensajeRepository mensajeRepository,
                                     @Value("${app.public-url:http://localhost:8081}") String publicUrl) {
        this.usuarioRepository = usuarioRepository;
        this.turnoRepository = turnoRepository;
        this.notificacionRepository = notificacionRepository;
        this.mensajeRepository = mensajeRepository;
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

    public record TurnoConfirmadoDto(Long turnoId, Long pacienteId, Long profesionalId, java.time.LocalDate fecha,
                                     String horaInicio) {}

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
                .map(t -> new TurnoConfirmadoDto(t.getId(), t.getPaciente().getId(), t.getMedico().getId(), t.getFecha(),
                        t.getHoraInicio() != null ? t.getHoraInicio().toString() : null))
                .toList();
    }

    private static ContactoDto contacto(Usuario u) {
        return new ContactoDto(u.getId(), u.getEmail(), u.getNombre(), u.getApellido(),
                u.getRol() != null ? u.getRol().name() : null, u.isNotificacionesEmailHabilitadas());
    }
}
