package com.tranqui.app.controller;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import com.tranqui.app.model.dto.MensajeDto;
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

        // Dispatch to recipient's private WebSocket queue (/user/{destinatarioId}/queue/mensajes)
        String destinatarioId = mensaje.getDestinatario().getId().toString();
        messagingTemplate.convertAndSendToUser(destinatarioId, "/queue/mensajes", mensajeDto);

        // Also dispatch to sender's own queue for delivery confirmation
        String remitenteId = mensaje.getRemitente().getId().toString();
        messagingTemplate.convertAndSendToUser(remitenteId, "/queue/mensajes", mensajeDto);
    }

    @GetMapping("/api/chat/historial/{destinatarioId}")
    @ResponseBody
    public ResponseEntity<Page<Mensaje>> obtenerHistorial(
            @PathVariable Long destinatarioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        Usuario usuarioActual = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Page<Mensaje> historial = mensajeService.obtenerHistorial(usuarioActual.getId(), destinatarioId, PageRequest.of(page, size));
        return ResponseEntity.ok(historial);
    }

    @GetMapping("/api/chat/canales")
    @ResponseBody
    public ResponseEntity<List<CanalPrioritarioDto>> obtenerCanales(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<CanalPrioritarioDto> canales = mensajeService.obtenerCanalesPrioritarios(userDetails.getUsername());
        return ResponseEntity.ok(canales);
    }

    @GetMapping("/api/chat/canales/visitadores")
    @ResponseBody
    public ResponseEntity<List<CanalPrioritarioDto>> obtenerCanalesVisitadores(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<CanalPrioritarioDto> canales = mensajeService.obtenerCanalesVisitadores(userDetails.getUsername());
        return ResponseEntity.ok(canales);
    }
}
