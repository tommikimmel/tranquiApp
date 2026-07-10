package com.tranqui.app.controller;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UsuarioRepository usuarioRepository;

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

    @PostMapping("/users/{id}/verify")
    public ResponseEntity<?> verifyProfessional(@PathVariable Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        
        usuario.setRol(Rol.PSIQUIATRA);
        usuario.setVerificadoAdmin(true);
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/users/{id}/reject")
    public ResponseEntity<?> rejectProfessional(@PathVariable Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        
        usuario.setVerificadoAdmin(false);
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(usuario);
    }
}
