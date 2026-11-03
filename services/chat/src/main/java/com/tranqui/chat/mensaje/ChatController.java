package com.tranqui.chat.mensaje;

import com.tranqui.chat.copias.Copias;
import com.tranqui.chat.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.chat.seguridad.UsuarioJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mismas rutas, roles y formatos que el {@code ChatController} del monolito: el frontend no cambia.
 */
@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Set<String> ROLES_CHAT = Set.of("PACIENTE", "PSIQUIATRA", "VISITADOR");
    private static final Set<String> ROLES_NO_LEIDOS = Set.of("PACIENTE", "PSIQUIATRA");

    /** Lo que viaja por /app/chat.enviar y /user/queue/mensajes ({@code MensajeDto} del monolito). */
    public record MensajeDto(Long id, Long remitenteId, Long destinatarioId, String contenido, String fechaEnvio) {}

    /** Un usuario dentro del historial: el frontend solo lee el id. */
    public record Referencia(Long id) {}

    /** Un mensaje del historial, con la forma que leía el frontend ({@code remitente.id}, {@code destinatario.id}). */
    public record MensajeHistorial(Long id, Referencia remitente, Referencia destinatario, String contenido,
                                   LocalDateTime fechaEnvio, boolean leido) {
        static MensajeHistorial de(Mensaje m) {
            return new MensajeHistorial(m.getId(), new Referencia(m.getRemitenteId()), new Referencia(m.getDestinatarioId()),
                    m.getContenido(), m.getFechaEnvio(), m.isLeido());
        }
    }

    private final MensajeService servicio;
    private final Copias copias;
    private final SimpMessagingTemplate mensajeria;

    public ChatController(MensajeService servicio, Copias copias, SimpMessagingTemplate mensajeria) {
        this.servicio = servicio;
        this.copias = copias;
        this.mensajeria = mensajeria;
    }

    // ------------------------------------------------------------------ STOMP

    @MessageMapping("/chat.enviar")
    public void enviar(@Payload MensajeDto entrada, Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("Acceso no autorizado: no se encuentra autenticado");
        }
        Copias.Participante remitente = copias.participantePorEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("Remitente no encontrado"));
        Mensaje mensaje = servicio.guardar(remitente.id(), entrada.destinatarioId(), entrada.contenido());
        Copias.Participante destinatario = copias.participante(mensaje.getDestinatarioId()).orElseThrow();

        MensajeDto salida = new MensajeDto(mensaje.getId(), remitente.id(), destinatario.id(), mensaje.getContenido(),
                mensaje.getFechaEnvio().toString());
        mensajeria.convertAndSendToUser(destinatario.email(), "/queue/mensajes", salida);
        mensajeria.convertAndSendToUser(remitente.email(), "/queue/mensajes", salida);

        // Aviso al canal general del destinatario para que marque "no leídos" en vivo (como el monolito).
        try {
            Map<String, Object> aviso = new LinkedHashMap<>();
            aviso.put("tipo", "NUEVO_MENSAJE");
            aviso.put("fecha", LocalDateTime.now().toString());
            aviso.put("titulo", "Nuevo mensaje de " + remitente.nombre());
            aviso.put("mensaje", mensaje.getContenido());
            mensajeria.convertAndSend("/topic/notificaciones/" + destinatario.id(), aviso);
        } catch (RuntimeException e) {
            log.debug("No se pudo mandar el aviso de nuevo mensaje: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ REST

    private Optional<Copias.Participante> usuario(UsuarioJwt.Usuario u) {
        return copias.participantePorEmail(u.email());
    }

    @GetMapping("/api/chat/historial/{destinatarioId}")
    @ResponseBody
    public ResponseEntity<Page<MensajeHistorial>> historial(
            @RequestAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) UsuarioJwt.Usuario jwt,
            @PathVariable("destinatarioId") Long destinatarioId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (!ROLES_CHAT.contains(jwt.rol())) {
            return ResponseEntity.status(403).build();
        }
        return usuario(jwt)
                .map(u -> ResponseEntity.ok(servicio.historial(u.id(), destinatarioId, PageRequest.of(page, size))
                        .map(MensajeHistorial::de)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/chat/tiene-no-leidos")
    @ResponseBody
    public ResponseEntity<Boolean> tieneNoLeidos(
            @RequestAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) UsuarioJwt.Usuario jwt) {
        if (!ROLES_NO_LEIDOS.contains(jwt.rol())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(usuario(jwt).map(u -> servicio.tieneNoLeidos(u.id())).orElse(false));
    }

    @GetMapping("/api/chat/canales")
    @ResponseBody
    public ResponseEntity<List<MensajeRepository.Canal>> canales(
            @RequestAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) UsuarioJwt.Usuario jwt) {
        if (!ROLES_CHAT.contains(jwt.rol())) {
            return ResponseEntity.status(403).build();
        }
        // Igual que el monolito: el profesional ve sus pacientes por prioridad clínica; el paciente
        // (y el visitador) ven a sus profesionales.
        return usuario(jwt)
                .map(u -> ResponseEntity.ok("PSIQUIATRA".equals(jwt.rol())
                        ? servicio.canalesDelProfesional(u.id())
                        : servicio.canalesConRol(u.id(), "PSIQUIATRA")))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/chat/canales/visitadores")
    @ResponseBody
    public ResponseEntity<List<MensajeRepository.Canal>> canalesVisitadores(
            @RequestAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) UsuarioJwt.Usuario jwt) {
        if (!"PSIQUIATRA".equals(jwt.rol())) {
            return ResponseEntity.status(403).build();
        }
        return usuario(jwt)
                .map(u -> ResponseEntity.ok(servicio.canalesConRol(u.id(), "VISITADOR")))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
