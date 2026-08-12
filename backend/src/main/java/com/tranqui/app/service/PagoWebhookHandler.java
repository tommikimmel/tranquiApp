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

        // Idempotent: this can now be reached twice for the same payment — once from the real
        // Mercado Pago webhook and once from WebhookController#verificarPago's fallback, which
        // exists precisely because the webhook alone isn't reliable enough (delayed/dropped
        // deliveries used to leave turnos stuck "pending" forever even though the patient paid).
        if (solicitud.getEstado() == EstadoPago.APROBADO) {
            log.info("Aprobación de concepto para solicitud ID {} ya estaba procesada; ignorando.", solicitudId);
            return;
        }

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

        // Idempotent for the same reason as procesarAprobacionConcepto above — this now has two
        // callers (the async webhook and the synchronous fallback WebhookController#verificarPago
        // runs right after the patient returns from Checkout Pro) that can legitimately race to
        // process the very same approved payment.
        if (turno.getEstado() == EstadoTurno.CONFIRMADO) {
            log.info("Aprobación de pago para turno ID {} ya estaba procesada; ignorando.", turnoId);
            return;
        }

        turno.setEstado(EstadoTurno.CONFIRMADO);

        // Crear evento de Google Meet solo para turnos online; los presenciales no llevan videollamada
        if (turno.getModalidad() == com.tranqui.app.model.Modalidad.ONLINE) {
            String meetUrl = calendarService.crearEventoReunion(turno);
            turno.setTelemedicinaUrl(meetUrl);
        }
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

        log.info("Pago aprobado para turno ID: {}.", turnoId);

        // Crear notificación para el médico y el paciente
        try {
            String tituloMed = "Nuevo Turno Reservado";
            String mensajeMed = "El paciente " + turno.getPaciente().getNombre() + 
                             " ha reservado un turno para el día " + turno.getFecha() + 
                             " a las " + turno.getHoraInicio() + "hs.";
            notificacionService.crearNotificacion(turno.getMedico(), tituloMed, mensajeMed, "TURNO_RESERVADO");

            String tituloPac = "Turno Confirmado ✓";
            String mensajePac = "Tu turno con el profesional " + turno.getMedico().getNombre() + 
                                " para el día " + turno.getFecha() + " a las " + turno.getHoraInicio() + "hs ha sido confirmado.";
            notificacionService.crearNotificacion(turno.getPaciente(), tituloPac, mensajePac, "TURNO_CONFIRMADO");
        } catch (Exception e) {
            log.error("Error al crear notificaciones de confirmación para el turno ID: {}", turnoId, e);
        }

        // Intentar notificar por WhatsApp — salvo que el paciente haya desactivado estas
        // notificaciones desde "Mi Cuenta".
        try {
            if (turno.getPaciente().isNotificacionesWhatsappHabilitadas()) {
                whatsAppService.enviarMensajeRecordatorio(turno);
            }
        } catch (Exception e) {
            log.error("Error al enviar recordatorio de WhatsApp para el turno ID: {}", turnoId, e);
        }
    }
}
