package com.tranqui.notificaciones.notificacion;

import com.tranqui.notificaciones.contacto.Contacto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notificaciones dentro de la app: las guarda y las empuja en vivo a
 * {@code /topic/notificaciones/{usuarioId}} por {@code /ws-notificaciones}, igual que el
 * {@code NotificacionService} del monolito.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository repositorio;
    private final SimpMessagingTemplate mensajeria;

    public NotificacionService(NotificacionRepository repositorio, SimpMessagingTemplate mensajeria) {
        this.repositorio = repositorio;
        this.mensajeria = mensajeria;
    }

    /**
     * Guarda la notificación. El aviso en vivo sale recién cuando la transacción del evento se
     * confirma: si el handler falla y el evento se reintenta, el usuario no ve una notificación
     * que después no existe.
     */
    @Transactional
    public Notificacion crear(Contacto destinatario, String titulo, String mensaje, String tipo) {
        Notificacion notificacion = repositorio.save(new Notificacion(destinatario.getId(), titulo, mensaje, tipo));
        log.info("Notificación creada para usuario ID {}: {}", destinatario.getId(), titulo);
        Map<String, Object> payload = payload(notificacion, destinatario.getNombre());
        String destino = "/topic/notificaciones/" + destinatario.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    empujar(destino, payload);
                }
            });
        } else {
            empujar(destino, payload);
        }
        return notificacion;
    }

    /**
     * Aviso en vivo que no se guarda (no aparece en la campanita): el frontend solo refresca. Como
     * los demás, sale recién cuando se confirma la transacción del evento.
     */
    public void empujarSinGuardar(Long usuarioId, Map<String, Object> payload) {
        String destino = "/topic/notificaciones/" + usuarioId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    empujar(destino, payload);
                }
            });
        } else {
            empujar(destino, payload);
        }
    }

    @Transactional(readOnly = true)
    public List<Notificacion> ultimas24Horas(Long usuarioId) {
        return repositorio.findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(
                usuarioId, LocalDateTime.now().minusHours(24));
    }

    @Transactional
    public void marcarTodasComoLeidas(Long usuarioId) {
        repositorio.marcarTodasComoLeidas(usuarioId);
    }

    @Transactional
    public int borrarAnterioresA24Horas() {
        return repositorio.borrarAnterioresA(LocalDateTime.now().minusHours(24));
    }

    private void empujar(String destino, Map<String, Object> payload) {
        try {
            mensajeria.convertAndSend(destino, payload);
        } catch (RuntimeException e) {
            // En vivo es un extra: la notificación ya está guardada y aparece al recargar.
            log.error("No se pudo empujar la notificación a {}", destino, e);
        }
    }

    // Mismo formato que NotificacionDocDto del monolito, que es lo que lee el frontend.
    private static Map<String, Object> payload(Notificacion n, String nombreDestinatario) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", n.getId());
        payload.put("pacienteNombre", nombreDestinatario);
        payload.put("tipo", n.getTipo());
        payload.put("fecha", LocalDateTime.now().toString());
        payload.put("titulo", n.getTitulo());
        payload.put("mensaje", n.getMensaje());
        return payload;
    }
}
