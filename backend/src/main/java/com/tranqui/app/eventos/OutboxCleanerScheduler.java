package com.tranqui.app.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Tarea periódica de limpieza de eventos publicados antiguos en la outbox (> 7 días)
 * y registros de idempotencia antiguos en eventos_procesados / inbox (> 30 días),
 * conforme a las secciones 6.1 y 6.2 de docs/arquitectura/microservicios.md.
 */
@Component
@ConditionalOnProperty(prefix = "eventos.cleaner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanerScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanerScheduler.class);
    private final OutboxRepository outboxRepository;

    public OutboxCleanerScheduler(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Limpieza programada (por defecto diaria a las 03:00 AM).
     */
    @Scheduled(cron = "${eventos.cleaner.cron:0 0 3 * * ?}")
    @Transactional
    public void limpiarEventos() {
        OffsetDateTime limiteOutbox = OffsetDateTime.now().minusDays(7);
        int outboxBorrados = outboxRepository.eliminarPublicadasAnterioresA(limiteOutbox);
        if (outboxBorrados > 0) {
            log.info("Limpieza periódica outbox: {} eventos publicados eliminados (> 7 días)", outboxBorrados);
        }

        OffsetDateTime limiteInbox = OffsetDateTime.now().minusDays(30);
        int inboxBorrados = outboxRepository.eliminarInboxAnterioresA(limiteInbox);
        if (inboxBorrados > 0) {
            log.info("Limpieza periódica inbox: {} eventos procesados eliminados (> 30 días)", inboxBorrados);
        }
    }
}
