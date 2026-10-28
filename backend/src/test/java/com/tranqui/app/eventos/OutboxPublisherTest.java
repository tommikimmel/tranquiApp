package com.tranqui.app.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.comun.correlation.CorrelationIdFilter;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OutboxPublisherTest {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TestTxHelper txHelper;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @TestConfiguration
    static class Config {
        @Bean
        public TestTxHelper testTxHelper(OutboxPublisher publisher) {
            return new TestTxHelper(publisher);
        }
    }

    @Service
    static class TestTxHelper {
        private final OutboxPublisher publisher;

        public TestTxHelper(OutboxPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public <T> EventEnvelope<T> ejecutarEnTransaccion(String topico, String aggregateType, String aggregateId, int version, T data) {
            return publisher.publicar(topico, aggregateType, aggregateId, version, data);
        }

        @Transactional
        public void ejecutarYFallar(String topico, String aggregateType, String aggregateId, int version, Object data) {
            publisher.publicar(topico, aggregateType, aggregateId, version, data);
            throw new RuntimeException("Rollback forzado");
        }
    }

    @BeforeEach
    @AfterEach
    void limpiar() {
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        outboxRepository.deleteAll();
    }

    @Test
    void publicar_sinTransaccionActiva_lanzaIllegalTransactionStateException() {
        assertThatThrownBy(() -> outboxPublisher.publicar(
                "turnos.turno.confirmado.v1", "turno", "101", 1, "test-data"
        )).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void publicar_dentroDeTransaccionConfirmada_persisteFilaEnOutbox() throws Exception {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "test-corr-id-123");

        EventEnvelope<String> envelope = txHelper.ejecutarEnTransaccion(
                "turnos.turno.confirmado.v1", "turno", "4521", 3, "datos-turno"
        );

        assertThat(envelope).isNotNull();
        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("turnos.turno.confirmado");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.producer()).isEqualTo("turnos");
        assertThat(envelope.correlationId()).isEqualTo("test-corr-id-123");
        assertThat(envelope.aggregateType()).isEqualTo("turno");
        assertThat(envelope.aggregateId()).isEqualTo("4521");
        assertThat(envelope.aggregateVersion()).isEqualTo(3);
        assertThat(envelope.data()).isEqualTo("datos-turno");

        Optional<OutboxRecord> guardado = outboxRepository.findByEventId(envelope.eventId());
        assertThat(guardado).isPresent();

        OutboxRecord record = guardado.get();
        assertThat(record.getTopic()).isEqualTo("turnos.turno.confirmado.v1");
        assertThat(record.getAggregateType()).isEqualTo("turno");
        assertThat(record.getAggregateId()).isEqualTo("4521");
        assertThat(record.getPublishedAt()).isNull();
        assertThat(record.getAttempts()).isZero();

        JsonNode jsonNode = mapper.readTree(record.getPayload());
        assertThat(jsonNode.get("eventId").asText()).isEqualTo(envelope.eventId().toString());
        assertThat(jsonNode.get("correlationId").asText()).isEqualTo("test-corr-id-123");
        assertThat(jsonNode.get("data").asText()).isEqualTo("datos-turno");
    }

    @Test
    void publicar_conRollbackDeTransaccion_noGuardaFilaEnOutbox() {
        assertThatThrownBy(() -> txHelper.ejecutarYFallar(
                "pagos.pago.aprobado.v1", "pago", "999", 1, "pago-fallido"
        )).isInstanceOf(RuntimeException.class).hasMessage("Rollback forzado");

        assertThat(outboxRepository.count()).isZero();
    }
}
