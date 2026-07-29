package com.tranqui.app.service;

import com.tranqui.app.model.Notificacion;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.NotificacionDocDto;
import com.tranqui.app.repository.NotificacionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Notificacion crearNotificacion(Usuario usuario, String titulo, String mensaje, String tipo) {
        Notificacion notif = Notificacion.builder()
                .usuario(usuario)
                .titulo(titulo)
                .mensaje(mensaje)
                .tipo(tipo)
                .leido(false)
                .build();

        notif = notificacionRepository.save(notif);
        log.info("Notificación creada para usuario ID {}: {}", usuario.getId(), titulo);

        // Broadcast live via WebSocket
        try {
            NotificacionDocDto payload = NotificacionDocDto.builder()
                    .id(notif.getId())
                    .pacienteNombre(usuario.getNombre())
                    .tipo(tipo)
                    .fecha(LocalDateTime.now().toString())
                    .titulo(titulo)
                    .mensaje(mensaje)
                    .build();
            
            // Format of target topic: /topic/notificaciones/{usuarioId}
            String destino = "/topic/notificaciones/" + usuario.getId();
            messagingTemplate.convertAndSend(destino, payload);
            log.info("Notificación enviada por WebSocket a {}", destino);
        } catch (Exception e) {
            log.error("Error al enviar notificación por WebSocket", e);
        }

        return notif;
    }

    @Transactional
    public List<Notificacion> obtenerNotificaciones(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        LocalDateTime hace24Horas = LocalDateTime.now().minusHours(24);
        notificacionRepository.deleteByFechaCreacionBefore(hace24Horas);
        return notificacionRepository.findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(usuario.getId(), hace24Horas);
    }

    @Transactional
    public void marcarTodasComoLeidas(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        notificacionRepository.markAllAsRead(usuario.getId());
    }

    @Transactional
    public void limpiarNotificacionesAntiguas() {
        LocalDateTime hace24Horas = LocalDateTime.now().minusHours(24);
        notificacionRepository.deleteByFechaCreacionBefore(hace24Horas);
        log.info("Limpieza de notificaciones de más de 24hs ejecutada.");
    }
}
