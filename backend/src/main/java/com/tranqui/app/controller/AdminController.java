package com.tranqui.app.controller;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CambiarEstadoTicketDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.ResendEmailService;
import com.tranqui.app.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ResponseEntity<List<Usuario>> getUsers() {
        return ResponseEntity.ok(usuarioRepository.findAll());
    }

    @PutMapping("/users/{id}/rol")
    public ResponseEntity<?> updateRol(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        String rolStr = body.get("rol");
        try {
            Rol rol = Rol.valueOf(rolStr.toUpperCase());
            usuario.setRol(rol);
            if (rol == Rol.PSIQUIATRA) {
                usuario.setVerificadoAdmin(true);
            }
            usuarioRepository.save(usuario);
            return ResponseEntity.ok(usuario);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Rol inválido");
        }
    }

    @Autowired
    private com.tranqui.app.service.SubscriptionService subscriptionService;

    @Autowired
    private com.tranqui.app.repository.SubscriptionRepository subscriptionRepository;

    @PostMapping("/users/{id}/verify")
    public ResponseEntity<?> verifyProfessional(@PathVariable Long id) {
        return verifyProfessional(id, null);
    }

    public ResponseEntity<?> verifyProfessional(Long id, org.springframework.security.core.Authentication auth) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        usuario.setRol(Rol.PSIQUIATRA);
        usuario.setVerificadoAdmin(true);
        usuario.setLicenseVerifiedAt(java.time.LocalDateTime.now());
        usuario.setLicenseVerifiedBy(auth != null ? auth.getName() : "admin");
        usuarioRepository.save(usuario);

        // Actualizar estado en la máquina de estados de suscripción (§6)
        if (subscriptionRepository != null) {
            subscriptionRepository.findByProfessionalId(usuario.getId()).ifPresent(sub -> {
                if (sub.getStatus() == com.tranqui.app.model.SubscriptionStatus.PENDING_VERIFICATION || sub.getStatus() == com.tranqui.app.model.SubscriptionStatus.REGISTERED) {
                    sub.setStatus(com.tranqui.app.model.SubscriptionStatus.VERIFIED);
                    subscriptionRepository.save(sub);
                    if (subscriptionService != null) {
                        subscriptionService.logEvent(sub.getId(), "PROFESSIONAL_VERIFIED", "ADMIN", auth != null ? auth.getName() : "admin", "Matrícula verificada por admin.");
                    }
                }
            });
        }

        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/users/{id}/reject")
    public ResponseEntity<?> rejectProfessional(@PathVariable Long id) {
        return rejectProfessional(id, null);
    }

    public ResponseEntity<?> rejectProfessional(Long id, org.springframework.security.core.Authentication auth) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        usuario.setVerificadoAdmin(false);
        usuarioRepository.save(usuario);

        if (subscriptionRepository != null) {
            subscriptionRepository.findByProfessionalId(usuario.getId()).ifPresent(sub -> {
                sub.setStatus(com.tranqui.app.model.SubscriptionStatus.REJECTED);
                subscriptionRepository.save(sub);
                if (subscriptionService != null) {
                    subscriptionService.logEvent(sub.getId(), "PROFESSIONAL_REJECTED", "ADMIN", auth != null ? auth.getName() : "admin", "Verificación rechazada.");
                }
            });
        }

        return ResponseEntity.ok(usuario);
    }

    // Genera una contraseña temporal segura, la guarda hasheada, marca mustChangePassword para
    // que el frontend fuerce el formulario de cambio en el próximo login (ver
    // AuthController#setNewPassword), y se la manda por mail al usuario. No queda persistida en
    // texto plano en ningún lado — se la devolvemos al admin acá para que la tenga como respaldo
    // (por si el mail tarda o se pierde), pero solo en esta única respuesta.
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));

        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Este usuario no tiene un email registrado.");
        }

        String passwordTemporal = generarPasswordTemporal();
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setMustChangePassword(true);
        usuarioRepository.save(usuario);

        resendEmailService.enviarPasswordTemporal(usuario.getEmail(), usuario.getNombre(), passwordTemporal);

        return ResponseEntity.ok(Map.of("password", passwordTemporal, "email", usuario.getEmail()));
    }

    private String generarPasswordTemporal() {
        // Sin caracteres ambiguos (0/O, 1/l/I) para que copiarla a mano desde el mail o la
        // pantalla no genere errores de tipeo.
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
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    // ── Tickets de soporte ──────────────────────────────────────────────────
    // El detalle de un ticket puntual y la respuesta del admin se manejan en TicketController
    // (mismo hilo que ve el paciente/profesional) — acá solo lo que es exclusivamente del admin:
    // ver todos los tickets de todos los usuarios, y cambiar su estado.
    @GetMapping("/tickets")
    public ResponseEntity<?> listarTickets() {
        return ResponseEntity.ok(ticketService.listarTodos());
    }

    @PutMapping("/tickets/{id}/estado")
    public ResponseEntity<?> cambiarEstadoTicket(@PathVariable Long id, @RequestBody CambiarEstadoTicketDto dto) {
        try {
            ticketService.cambiarEstado(id, dto.getEstado());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
