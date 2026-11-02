package com.tranqui.app.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxCleanerSchedulerTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxCleanerScheduler cleanerScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiar() {
        outboxRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM eventos_procesados");
    }

    @Test
    void limpiarEventos_eliminaPublicadasMasDe7DiasEInboxMasDe30Dias() {
        OffsetDateTime ahora = OffsetDateTime.now();

        // 1. Outbox publicada hace 8 días (debe borrarse)
        OutboxRecord viejaPublicada = OutboxRecord.builder()
                .eventId(UUID.randomUUID())
                .topic("turnos.turno.confirmado.v1")
                .aggregateType("turno")
                .aggregateId("1")
                .payload("{}")
                .createdAt(ahora.minusDays(9))
                .publishedAt(ahora.minusDays(8))
                .build();
        outboxRepository.save(viejaPublicada);

        // 2. Outbox publicada hace 3 días (debe conservarse)
        OutboxRecord recientePublicada = OutboxRecord.builder()
                .eventId(UUID.randomUUID())
                .topic("turnos.turno.confirmado.v1")
                .aggregateType("turno")
                .aggregateId("2")
                .payload("{}")
                .createdAt(ahora.minusDays(4))
                .publishedAt(ahora.minusDays(3))
                .build();
        outboxRepository.save(recientePublicada);

        // 3. Outbox NO publicada creada hace 10 días (debe conservarse siempre)
        OutboxRecord viejaSinPublicar = OutboxRecord.builder()
                .eventId(UUID.randomUUID())
                .topic("turnos.turno.confirmado.v1")
                .aggregateType("turno")
                .aggregateId("3")
                .payload("{}")
                .createdAt(ahora.minusDays(10))
                .publishedAt(null)
                .build();
        outboxRepository.save(viejaSinPublicar);

        // 4. Inbox procesado hace 35 días (debe borrarse)
        UUID eventViejo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?)",
                eventViejo, "consumidor-a", Timestamp.from(ahora.minusDays(35).toInstant())
        );

        // 5. Inbox procesado hace 10 días (debe conservarse)
        UUID eventReciente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?)",
                eventReciente, "consumidor-b", Timestamp.from(ahora.minusDays(10).toInstant())
        );

        // Ejecutar limpieza
        cleanerScheduler.limpiarEventos();

        assertThat(outboxRepository.findByEventId(viejaPublicada.getEventId())).isEmpty();
        assertThat(outboxRepository.findByEventId(recientePublicada.getEventId())).isPresent();
        assertThat(outboxRepository.findByEventId(viejaSinPublicar.getEventId())).isPresent();

        Integer countViejo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ?",
                Integer.class, eventViejo
        );
        assertThat(countViejo).isZero();

        Integer countReciente = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ?",
                Integer.class, eventReciente
        );
        assertThat(countReciente).isEqualTo(1);
    }
}
