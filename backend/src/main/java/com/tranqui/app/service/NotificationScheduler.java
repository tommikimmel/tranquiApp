package com.tranqui.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@Slf4j
public class NotificationScheduler {

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private TurnoService turnoService;

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

    // Cron se ejecuta todos los días a las 09:00:00 (Zona Horaria Argentina) — mail de "confirmá
    // tu turno" para los turnos que ocurren en exactamente 2 días, para que el paciente todavía
    // tenga tiempo de avisar si no va a poder ir. Ver TurnoService#enviarRecordatoriosConfirmacionAsistencia.
    @Scheduled(cron = "0 0 9 * * ?", zone = "America/Argentina/Cordoba")
    public void enviarRecordatoriosConfirmacionAsistencia() {
        try {
            turnoService.enviarRecordatoriosConfirmacionAsistencia();
        } catch (Exception e) {
            log.error("Fallo al ejecutar el envío de recordatorios de confirmación de asistencia", e);
        }
    }
}
