package com.tranqui.app.controller;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import com.tranqui.app.model.dto.MensajeDto;
import com.tranqui.app.model.dto.NotificacionDocDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.MensajeService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MessageMapping("/chat.enviar")
    public void procesarMensaje(@Payload MensajeDto mensajeDto, Principal principal) {
        Mensaje mensaje = mensajeService.guardarMensaje(mensajeDto, principal.getName());

        mensajeDto.setId(mensaje.getId());
        mensajeDto.setRemitenteId(mensaje.getRemitente().getId());
        mensajeDto.setFechaEnvio(mensaje.getFechaEnvio().toString());

        // Dispatch to recipient's private WebSocket queue (/user/{destinatarioEmail}/queue/mensajes)
        String destinatarioEmail = mensaje.getDestinatario().getEmail();
        messagingTemplate.convertAndSendToUser(destinatarioEmail, "/queue/mensajes", mensajeDto);

        // Also dispatch to sender's own queue for delivery confirmation
        String remitenteEmail = mensaje.getRemitente().getEmail();
        messagingTemplate.convertAndSendToUser(remitenteEmail, "/queue/mensajes", mensajeDto);

        // Notify recipient's general notifications channel to update unread status in real-time
        try {
            NotificacionDocDto notifPayload = NotificacionDocDto.builder()
                    .tipo("NUEVO_MENSAJE")
                    .fecha(java.time.LocalDateTime.now().toString())
                    .titulo("Nuevo mensaje de " + mensaje.getRemitente().getNombre())
                    .mensaje(mensaje.getContenido())
                    .build();
            messagingTemplate.convertAndSend("/topic/notificaciones/" + mensaje.getDestinatario().getId(), notifPayload);
        } catch (Exception e) {
            // ignore
        }
    }

    @GetMapping("/api/chat/historial/{destinatarioId}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA', 'VISITADOR')")
    public ResponseEntity<Page<Mensaje>> obtenerHistorial(
            @PathVariable Long destinatarioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        Usuario usuarioActual = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        mensajeService.marcarMensajesComoLeidos(destinatarioId, userDetails.getUsername());

        Page<Mensaje> historial = mensajeService.obtenerHistorial(usuarioActual.getId(), destinatarioId, PageRequest.of(page, size));
        return ResponseEntity.ok(historial);
    }

    @GetMapping("/api/chat/tiene-no-leidos")
    @ResponseBody
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<Boolean> tieneNoLeidos(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        boolean hasUnread = mensajeService.tieneMensajesSinLeer(userDetails.getUsername());
        return ResponseEntity.ok(hasUnread);
    }

    @GetMapping("/api/chat/canales")
    @ResponseBody
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA', 'VISITADOR')")
    public ResponseEntity<List<CanalPrioritarioDto>> obtenerCanales(
            @AuthenticationPrincipal UserDetails userDetails) {

        Usuario usuarioActual = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        // findPrioritizedChannels is written from the doctor's point of view (it looks for
        // PACIENTE messaging partners and computes clinical priority against their turnos), so
        // it only makes sense when the caller is a PSIQUIATRA. A PACIENTE (or VISITADOR) calling
        // this same endpoint needs the mirror-image query — their PSIQUIATRA partners — otherwise
        // it silently returns zero channels regardless of real message history.
        List<CanalPrioritarioDto> canales = usuarioActual.getRol() == Rol.PSIQUIATRA
                ? mensajeService.obtenerCanalesPrioritarios(userDetails.getUsername())
                : mensajeService.obtenerCanalesPaciente(userDetails.getUsername());
        return ResponseEntity.ok(canales);
    }

    @GetMapping("/api/chat/canales/visitadores")
    @ResponseBody
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<List<CanalPrioritarioDto>> obtenerCanalesVisitadores(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<CanalPrioritarioDto> canales = mensajeService.obtenerCanalesVisitadores(userDetails.getUsername());
        return ResponseEntity.ok(canales);
    }
}
