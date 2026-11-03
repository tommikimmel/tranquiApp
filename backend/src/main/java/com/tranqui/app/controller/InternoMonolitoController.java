package com.tranqui.app.controller;

import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
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

    private static final int TAMANIO_MAXIMO = 1000;

    private final UsuarioRepository usuarioRepository;
    private final TurnoRepository turnoRepository;
    private final String publicUrl;

    public InternoMonolitoController(UsuarioRepository usuarioRepository, TurnoRepository turnoRepository,
                                     @Value("${app.public-url:http://localhost:8081}") String publicUrl) {
        this.usuarioRepository = usuarioRepository;
        this.turnoRepository = turnoRepository;
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

    private static ContactoDto contacto(Usuario u) {
        return new ContactoDto(u.getId(), u.getEmail(), u.getNombre(), u.getApellido(),
                u.getRol() != null ? u.getRol().name() : null, u.isNotificacionesEmailHabilitadas());
    }
}
