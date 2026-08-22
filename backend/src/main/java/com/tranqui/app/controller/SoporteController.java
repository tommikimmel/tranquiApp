package com.tranqui.app.controller;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.ResendEmailService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "Quejas y Soporte" (el mail fijo a soporte@tranquisalud.com) fue reemplazado por el sistema
// de tickets (TicketController/TicketService) — este controller ahora solo cubre la solicitud
// ARCO de copia de datos (Ley 25.326), que es un trámite legal distinto, no soporte al cliente.
@RestController
@RequestMapping("/api/soporte")
public class SoporteController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ResendEmailService resendEmailService;

    @PostMapping("/solicitud-datos")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<Void> solicitarCopiaDatos(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        String nombreCompleto = (usuario.getNombre() != null ? usuario.getNombre() : "") +
                (usuario.getApellido() != null ? " " + usuario.getApellido() : "");
        String rol = usuario.getRol() != null ? usuario.getRol().toString() : "USUARIO";

        resendEmailService.enviarSolicitudCopiaDatos(nombreCompleto.trim(), usuario.getEmail(), rol);

        return ResponseEntity.ok().build();
    }
}
