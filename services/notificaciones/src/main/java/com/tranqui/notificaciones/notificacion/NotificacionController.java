package com.tranqui.notificaciones.notificacion;

import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.contacto.ContactoService;
import com.tranqui.notificaciones.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.notificaciones.seguridad.UsuarioJwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mismas rutas que el monolito ({@code ClinicalController}): el frontend no cambia. Solo pacientes y
 * profesionales, igual que su {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private static final Set<String> ROLES_PERMITIDOS = Set.of("PACIENTE", "PSIQUIATRA");

    /** Lo que lee el frontend de cada notificación. */
    public record NotificacionDto(Long id, String titulo, String mensaje, boolean leido, String tipo,
                                  LocalDateTime fechaCreacion) {
        static NotificacionDto de(Notificacion n) {
            return new NotificacionDto(n.getId(), n.getTitulo(), n.getMensaje(), n.isLeido(), n.getTipo(),
                    n.getFechaCreacion());
        }
    }

    private final NotificacionService notificaciones;
    private final ContactoService contactos;

    public NotificacionController(NotificacionService notificaciones, ContactoService contactos) {
        this.notificaciones = notificaciones;
        this.contactos = contactos;
    }

    @GetMapping
    public ResponseEntity<List<NotificacionDto>> listar(
            @RequestAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) UsuarioJwt.Usuario usuario) {
        if (!ROLES_PERMITIDOS.contains(usuario.rol())) {
            return ResponseEntity.status(403).build();
        }
        Optional<Contacto> contacto = contactos.buscarPorEmail(usuario.email());
        if (contacto.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(notificaciones.ultimas24Horas(contacto.get().getId()).stream()
                .map(NotificacionDto::de).toList());
    }

    @PostMapping("/marcar-leidas")
    public ResponseEntity<Void> marcarLeidas(
            @RequestAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) UsuarioJwt.Usuario usuario) {
        if (!ROLES_PERMITIDOS.contains(usuario.rol())) {
            return ResponseEntity.status(403).build();
        }
        contactos.buscarPorEmail(usuario.email())
                .ifPresent(contacto -> notificaciones.marcarTodasComoLeidas(contacto.getId()));
        return ResponseEntity.ok().build();
    }
}
