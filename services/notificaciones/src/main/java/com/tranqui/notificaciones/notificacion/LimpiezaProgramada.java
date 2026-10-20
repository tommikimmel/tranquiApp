package com.tranqui.notificaciones.notificacion;

import com.tranqui.eventos.consumo.InboxHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpiezas periódicas: las notificaciones duran 24 horas (como en el monolito) y el inbox de
 * eventos procesados se borra a los 30 días (sección 6.2).
 */
@Component
public class LimpiezaProgramada {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaProgramada.class);

    private final NotificacionService notificaciones;
    private final InboxHelper inbox;

    public LimpiezaProgramada(NotificacionService notificaciones, InboxHelper inbox) {
        this.notificaciones = notificaciones;
        this.inbox = inbox;
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void notificacionesViejas() {
        int borradas = notificaciones.borrarAnterioresA24Horas();
        log.info("Limpieza de notificaciones de más de 24 hs: {} borradas", borradas);
    }

    @Scheduled(cron = "0 30 4 * * ?", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void inboxViejo() {
        int borrados = inbox.limpiarAnteriores(30);
        log.info("Limpieza del inbox de eventos de más de 30 días: {} borrados", borrados);
    }
}
