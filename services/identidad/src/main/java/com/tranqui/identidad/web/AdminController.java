package com.tranqui.identidad.web;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.*;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.PerfilesRegistro;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.identidad.tickets.TicketService;
import com.tranqui.identidad.web.dto.CambiarEstadoTicketDto;
import com.tranqui.identidad.web.dto.TicketResumenDto;
import com.tranqui.identidad.web.dto.UsuarioAdminDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Rutas de administración en identidad (Fase 9, I2c).
 * Todo restringido a usuarios con rol ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<String> ROLES_VALIDOS = Set.of("ADMIN", "PSIQUIATRA", "PACIENTE", "VISITADOR");

    private final Usuarios usuarios;
    private final PerfilesRegistro perfilesRegistro;
    private final TicketService ticketService;
    private final OutboxPublisher outboxPublisher;
    private final PasswordEncoder passwordEncoder;

    public AdminController(Usuarios usuarios,
                           PerfilesRegistro perfilesRegistro,
                           TicketService ticketService,
                           OutboxPublisher outboxPublisher,
                           PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.perfilesRegistro = perfilesRegistro;
        this.ticketService = ticketService;
        this.outboxPublisher = outboxPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    private Usuario requireAdmin(HttpServletRequest request) {
        Object attr = request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (!(attr instanceof Usuario u)) {
            return null;
        }
        return "ADMIN".equals(u.rol()) ? u : null;
    }

    private boolean isAutenticado(HttpServletRequest request) {
        return request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO) instanceof Usuario;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UsuarioAdminDto>> getUsers(HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<UsuarioAdminDto> lista = usuarios.todos().stream()
                .map(u -> {
                    PerfilProfesionalData perfil = perfilesRegistro.buscarPorUsuarioId(u.id()).orElse(null);
                    return UsuarioAdminDto.desde(u, perfil);
                })
                .toList();

        return ResponseEntity.ok(lista);
    }

    @PutMapping("/users/{id}/rol")
    @Transactional
    public ResponseEntity<?> updateRol(@PathVariable("id") Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Usuario usuario = usuarios.buscarPorId(id)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        String rolStr = body.get("rol");
        if (rolStr == null || !ROLES_VALIDOS.contains(rolStr.trim().toUpperCase())) {
            return ResponseEntity.badRequest().body("Rol inválido");
        }
        String nuevoRol = rolStr.trim().toUpperCase();

        Boolean verificadoAdmin = usuario.verificadoAdmin();
        if ("PSIQUIATRA".equals(nuevoRol)) {
            verificadoAdmin = true;
        }

        Usuario actualizado = usuario.toBuilder()
                .rol(nuevoRol)
                .verificadoAdmin(verificadoAdmin)
                .build();
        actualizado = usuarios.actualizar(actualizado);

        publicarUsuarioActualizado(usuario, actualizado);

        PerfilProfesionalData perfil = perfilesRegistro.buscarPorUsuarioId(id).orElse(null);
        return ResponseEntity.ok(UsuarioAdminDto.desde(actualizado, perfil));
    }

    @PostMapping("/users/{id}/verify")
    @Transactional
    public ResponseEntity<?> verifyProfessional(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Usuario usuario = usuarios.buscarPorId(id)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        Usuario actualizado = usuario.toBuilder()
                .rol("PSIQUIATRA")
                .verificadoAdmin(true)
                .licenseVerifiedAt(LocalDateTime.now())
                .licenseVerifiedBy("admin")
                .build();
        actualizado = usuarios.actualizar(actualizado);

        int aggVersion = actualizado.aggregateVersion();
        publicarUsuarioActualizado(usuario, actualizado);

        outboxPublisher.publicar(
                Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
                "usuario",
                String.valueOf(usuario.id()),
                aggVersion,
                new ProfesionalVisibilidadCambiadaData(usuario.id(), true, "Verificado por admin")
        );
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1,
                "usuario",
                String.valueOf(usuario.id()),
                aggVersion,
                new VerificacionResueltaData(usuario.id(), "VERIFICADO")
        );

        PerfilProfesionalData perfil = perfilesRegistro.buscarPorUsuarioId(id).orElse(null);
        return ResponseEntity.ok(UsuarioAdminDto.desde(actualizado, perfil));
    }

    @PostMapping("/users/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectProfessional(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Usuario usuario = usuarios.buscarPorId(id)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        Usuario actualizado = usuario.toBuilder()
                .verificadoAdmin(false)
                .build();
        actualizado = usuarios.actualizar(actualizado);

        int aggVersion = actualizado.aggregateVersion();
        // Se publica además usuario.actualizado para que las copias no queden viejas
        publicarUsuarioActualizado(usuario, actualizado);

        outboxPublisher.publicar(
                Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
                "usuario",
                String.valueOf(usuario.id()),
                aggVersion,
                new ProfesionalVisibilidadCambiadaData(usuario.id(), false, "Verificación rechazada por admin")
        );
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1,
                "usuario",
                String.valueOf(usuario.id()),
                aggVersion,
                new VerificacionResueltaData(usuario.id(), "RECHAZADO")
        );

        PerfilProfesionalData perfil = perfilesRegistro.buscarPorUsuarioId(id).orElse(null);
        return ResponseEntity.ok(UsuarioAdminDto.desde(actualizado, perfil));
    }

    @PostMapping("/users/{id}/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Usuario usuario = usuarios.buscarPorId(id)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        if (usuario.email() == null || usuario.email().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Este usuario no tiene un email registrado.");
        }

        String passwordTemporal = generarPasswordTemporal();
        Usuario actualizado = usuario.toBuilder()
                .password(passwordEncoder.encode(passwordTemporal))
                .mustChangePassword(true)
                .build();
        actualizado = usuarios.actualizar(actualizado);

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_PASSWORD_TEMPORAL_GENERADA_V1,
                "usuario",
                String.valueOf(usuario.id()),
                actualizado.aggregateVersion(),
                new PasswordTemporalGeneradaData(
                        usuario.id(),
                        usuario.email(),
                        usuario.nombre(),
                        passwordTemporal
                )
        );

        return ResponseEntity.ok(Map.of("password", passwordTemporal, "email", usuario.email()));
    }

    @GetMapping("/tickets")
    public ResponseEntity<?> listarTickets(HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<TicketResumenDto> tickets = ticketService.listarTodos();
        return ResponseEntity.ok(tickets);
    }

    @PutMapping("/tickets/{id}/estado")
    @Transactional
    public ResponseEntity<?> cambiarEstadoTicket(@PathVariable("id") Long id, @RequestBody CambiarEstadoTicketDto dto, HttpServletRequest request) {
        if (!isAutenticado(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario admin = requireAdmin(request);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            ticketService.cambiarEstado(id, dto.getEstado());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private void publicarUsuarioActualizado(Usuario antes, Usuario despues) {
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                "usuario",
                String.valueOf(despues.id()),
                despues.aggregateVersion(),
                new UsuarioActualizadoData(
                        despues.id(),
                        despues.email(),
                        despues.nombre(),
                        despues.apellido(),
                        despues.telefono(),
                        despues.rol(),
                        despues.isNotificacionesEmailHabilitadas()
                )
        );
    }

    private String generarPasswordTemporal() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghjkmnpqrstuvwxyz";
        String digits = "23456789";
        String todos = upper + lower + digits;
        SecureRandom random = new SecureRandom();

        List<Character> chars = new ArrayList<>();
        chars.add(upper.charAt(random.nextInt(upper.length())));
        chars.add(lower.charAt(random.nextInt(lower.length())));
        chars.add(digits.charAt(random.nextInt(digits.length())));
        for (int i = 0; i < 7; i++) {
            chars.add(todos.charAt(random.nextInt(todos.length())));
        }
        Collections.shuffle(chars, random);

        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
