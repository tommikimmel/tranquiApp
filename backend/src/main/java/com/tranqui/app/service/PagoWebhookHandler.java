package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.NotificacionDocDto;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class PagoWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(PagoWebhookHandler.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SolicitudDocumentoRepository solicitudRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private GoogleCalendarService calendarService;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private NotificacionService notificacionService;

    @Transactional
    public void procesarAprobacionConcepto(Long solicitudId, String transactionId) {
        SolicitudDocumento solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada"));

        solicitud.setEstado(EstadoPago.APROBADO);
        solicitud.setTransactionId(transactionId);
        solicitudRepository.save(solicitud);

        log.info("Aprobación de concepto procesada para solicitud ID: {}. Enviando notificación WebSocket...", solicitudId);

        // Payload JSON for WebSocket notification
        NotificacionDocDto payload = NotificacionDocDto.builder()
                .id(solicitud.getId())
                .pacienteNombre(solicitud.getPaciente().getNombre())
                .tipo(solicitud.getTipoConcepto().toString())
                .fecha(LocalDateTime.now().toString())
                .build();

        // Publish to physician's private channel
        String destino = "/topic/notificaciones/" + solicitud.getMedico().getId();
        messagingTemplate.convertAndSend(destino, payload);
    }

    @Transactional
    public void procesarAprobacionTurno(Long turnoId, String transactionId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado con ID: " + turnoId));

        turno.setEstado(EstadoTurno.CONFIRMADO);
        
        // Crear evento de Google Meet
        String meetUrl = calendarService.crearEventoReunion(turno);
        turno.setTelemedicinaUrl(meetUrl);
        turnoRepository.save(turno);

        // Registrar el Pago
        Pago pago = Pago.builder()
                .turno(turno)
                .transactionId(transactionId)
                .estado(EstadoPago.APROBADO)
                .monto(turno.getPrecio())
                .fechaPago(LocalDateTime.now())
                .build();
        pagoRepository.save(pago);

        log.info("Pago aprobado para turno ID: {}. Generado evento de Google Meet.", turnoId);

        // Crear notificación para el médico
        try {
            String titulo = "Nuevo Turno Reservado";
            String mensaje = "El paciente " + turno.getPaciente().getNombre() + 
                             " ha reservado un turno para el día " + turno.getFecha() + 
                             " a las " + turno.getHoraInicio() + "hs.";
            notificacionService.crearNotificacion(turno.getMedico(), titulo, mensaje, "TURNO_RESERVADO");
        } catch (Exception e) {
            log.error("Error al crear notificación para el turno ID: {}", turnoId, e);
        }

        // Intentar notificar por WhatsApp
        try {
            whatsAppService.enviarMensajeRecordatorio(turno);
        } catch (Exception e) {
            log.error("Error al enviar recordatorio de WhatsApp para el turno ID: {}", turnoId, e);
        }
    }
}
