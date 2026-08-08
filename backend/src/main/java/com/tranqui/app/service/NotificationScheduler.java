package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TurnoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class NotificationScheduler {

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private WhatsAppService whatsappService;

    @Autowired
    private NotificacionService notificacionService;

    // Cron se ejecuta cada 1 hora para eliminar notificaciones enviadas hace más de 24 horas
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void autoEliminarNotificacionesVencidas() {
        try {
            notificacionService.limpiarNotificacionesAntiguas();
        } catch (Exception e) {
            log.error("Fallo al ejecutar la limpieza de notificaciones antiguas", e);
        }
    }

    // Cron se ejecuta todos los días a las 20:00:00 (Zona Horaria Argentina)
    @Scheduled(cron = "0 0 20 * * ?", zone = "America/Argentina/Cordoba")
    @Transactional
    public void enviarRecordatoriosTurnosSiguienteDia() {
        LocalDate mañana = LocalDate.now().plusDays(1);
        log.info("Iniciando tarea programada de recordatorios de turnos para el dia: {}", mañana);
        
        List<Turno> turnosMañana = turnoRepository.findByEstadoAndFechaAndRecordatorioEnviado(
                EstadoTurno.CONFIRMADO, mañana, false
        );

        log.info("Se encontraron {} turnos confirmados para el dia: {}", turnosMañana.size(), mañana);

        for (Turno turno : turnosMañana) {
            try {
                if (turno.getPaciente().isNotificacionesWhatsappHabilitadas()) {
                    whatsappService.enviarMensajeRecordatorio(turno);
                }
                turno.setRecordatorioEnviado(true);
                turnoRepository.save(turno);
                log.info("Recordatorio enviado con exito para el turno ID: {}", turno.getId());
            } catch (Exception e) {
                // Capturar excepción para que un fallo en un mensaje no bloquee el resto de los envíos
                log.error("Fallo al enviar recordatorio de WhatsApp para el turno ID: " + turno.getId(), e);
            }
        }
    }
}
