package com.tranqui.app.eventos;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Métricas Micrometer para la outbox (sección 11.1 de docs/arquitectura/microservicios.md):
 * - tranqui.outbox.pendientes: cantidad de eventos pendientes de publicación en la outbox.
 * - tranqui.outbox.antiguedad.segundos: antigüedad en segundos del evento pendiente más antiguo.
 */
@Component
public class OutboxMetrics {

    private final OutboxRepository outboxRepository;

    public OutboxMetrics(OutboxRepository outboxRepository, MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;

        Gauge.builder("tranqui.outbox.pendientes", this, OutboxMetrics::obtenerCantidadPendientes)
                .description("Cantidad de eventos pendientes de publicación en la outbox")
                .register(meterRegistry);

        Gauge.builder("tranqui.outbox.antiguedad.segundos", this, OutboxMetrics::obtenerAntiguedadMasViejoSegundos)
                .description("Antigüedad en segundos del evento pendiente más viejo en la outbox")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    public double obtenerCantidadPendientes() {
        try {
            return outboxRepository.countPendientes();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double obtenerAntiguedadMasViejoSegundos() {
        try {
            OffsetDateTime minCreatedAt = outboxRepository.findMinCreatedAtPendiente();
            if (minCreatedAt == null) {
                return 0.0;
            }
            long segundos = Duration.between(minCreatedAt, OffsetDateTime.now()).getSeconds();
            return Math.max(0, segundos);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
