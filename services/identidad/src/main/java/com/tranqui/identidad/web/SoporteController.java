package com.tranqui.identidad.web;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.SolicitudDatosData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.seguridad.FiltroAutenticacionUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Solicitud ARCO de copia de datos (Ley 25.326) en identidad (Fase 9, I2c).
 * Publica el evento identidad.soporte.solicitud-datos.v1 por outbox.
 * El mail lo envía el servicio de notificaciones.
 */
@RestController
@RequestMapping("/api/soporte")
public class SoporteController {

    private static final Set<String> ROLES_PERMITIDOS = Set.of("PACIENTE", "PSIQUIATRA");

    private final OutboxPublisher outboxPublisher;

    public SoporteController(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    private Usuario usuarioAutenticado(HttpServletRequest request) {
        Object attr = request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (attr instanceof Usuario u) {
            return u;
        }
        return null;
    }

    @PostMapping("/solicitud-datos")
    @Transactional
    public ResponseEntity<Void> solicitarCopiaDatos(HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!ROLES_PERMITIDOS.contains(usuario.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String nombreCompleto = (usuario.nombre() != null ? usuario.nombre() : "") +
                (usuario.apellido() != null ? " " + usuario.apellido() : "");
        String rol = usuario.rol() != null ? usuario.rol() : "USUARIO";

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_SOPORTE_SOLICITUD_DATOS_V1,
                "soporte",
                String.valueOf(usuario.id()),
                usuario.aggregateVersion(),
                new SolicitudDatosData(
                        usuario.id(),
                        nombreCompleto.trim(),
                        usuario.email(),
                        rol
                )
        );

        return ResponseEntity.ok().build();
    }
}
