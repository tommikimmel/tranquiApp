package com.tranqui.app.eventos;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxMetricsTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private OutboxMetrics outboxMetrics;

    @BeforeEach
    void limpiar() {
        outboxRepository.deleteAll();
    }

    @Test
    void metricas_reflejanPendientesYAntiguedad() {
        assertThat(outboxMetrics.obtenerCantidadPendientes()).isZero();
        assertThat(outboxMetrics.obtenerAntiguedadMasViejoSegundos()).isZero();

        OffsetDateTime hace10Segundos = OffsetDateTime.now().minusSeconds(10);
        OutboxRecord r1 = OutboxRecord.builder()
                .eventId(UUID.randomUUID())
                .topic("turnos.turno.confirmado.v1")
                .aggregateType("turno")
                .aggregateId("1")
                .payload("{}")
                .createdAt(hace10Segundos)
                .build();
        outboxRepository.save(r1);

        OffsetDateTime hace5Segundos = OffsetDateTime.now().minusSeconds(5);
        OutboxRecord r2 = OutboxRecord.builder()
                .eventId(UUID.randomUUID())
                .topic("turnos.turno.confirmado.v1")
                .aggregateType("turno")
                .aggregateId("2")
                .payload("{}")
                .createdAt(hace5Segundos)
                .build();
        outboxRepository.save(r2);

        // Record ya publicado
        OutboxRecord rPublicado = OutboxRecord.builder()
                .eventId(UUID.randomUUID())
                .topic("turnos.turno.confirmado.v1")
                .aggregateType("turno")
                .aggregateId("3")
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusHours(1))
                .publishedAt(OffsetDateTime.now().minusMinutes(50))
                .build();
        outboxRepository.save(rPublicado);

        assertThat(outboxMetrics.obtenerCantidadPendientes()).isEqualTo(2.0);
        assertThat(outboxMetrics.obtenerAntiguedadMasViejoSegundos()).isGreaterThanOrEqualTo(9.0);

        Double gaugePendientes = meterRegistry.get("tranqui.outbox.pendientes").gauge().value();
        assertThat(gaugePendientes).isEqualTo(2.0);

        Double gaugeAntiguedad = meterRegistry.get("tranqui.outbox.antiguedad.segundos").gauge().value();
        assertThat(gaugeAntiguedad).isGreaterThanOrEqualTo(9.0);
    }
}
