package com.tranqui.eventos.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Métricas y limpieza de la outbox de un servicio, con los mismos nombres que en el monolito para
 * que el panel de Observabilidad los muestre igual (sección 11.1):
 * {@code tranqui.outbox.pendientes} y {@code tranqui.outbox.antiguedad.segundos}. La limpieza borra
 * los eventos publicados de más de 7 días (sección 6.1); corre si el servicio tiene {@code @EnableScheduling}.
 */
public class OutboxMantenimiento {

    private static final Logger log = LoggerFactory.getLogger(OutboxMantenimiento.class);

    private final JdbcTemplate jdbc;

    public OutboxMantenimiento(JdbcTemplate jdbc, MeterRegistry metricas) {
        this.jdbc = jdbc;
        if (metricas != null) {
            Gauge.builder("tranqui.outbox.pendientes", this, OutboxMantenimiento::pendientes)
                    .description("Eventos pendientes de publicar en la outbox").register(metricas);
            Gauge.builder("tranqui.outbox.antiguedad.segundos", this, OutboxMantenimiento::antiguedadSegundos)
                    .description("Antigüedad del evento pendiente más viejo de la outbox").baseUnit("seconds")
                    .register(metricas);
        }
    }

    double pendientes() {
        try {
            Long n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class);
            return n != null ? n : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    double antiguedadSegundos() {
        try {
            Timestamp masViejo = jdbc.queryForObject(
                    "SELECT min(created_at) FROM outbox WHERE published_at IS NULL", Timestamp.class);
            return masViejo == null ? 0 : Math.max(0, Duration.between(masViejo.toInstant(), Instant.now()).getSeconds());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Scheduled(cron = "${eventos.cleaner.cron:0 0 3 * * ?}", zone = "America/Argentina/Buenos_Aires")
    public void limpiar() {
        int borrados = jdbc.update(
                "DELETE FROM outbox WHERE published_at IS NOT NULL AND published_at < now() - interval '7 days'");
        if (borrados > 0) {
            log.info("Limpieza de la outbox: {} eventos publicados de más de 7 días borrados", borrados);
        }
    }
}
