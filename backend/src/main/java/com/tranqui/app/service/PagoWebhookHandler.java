package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.dto.NotificacionDocDto;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
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
}
