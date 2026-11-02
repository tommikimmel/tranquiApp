package com.tranqui.eventos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sobre común de eventos del sistema (sección 5.3 de docs/arquitectura/microservicios.md).
 *
 * @param <T> Tipo de datos de negocio contenidos en el evento.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int version,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime occurredAt,
    String producer,
    String correlationId,
    String causationId,
    String aggregateType,
    String aggregateId,
    int aggregateVersion,
    T data
) {
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private UUID eventId = UUID.randomUUID();
        private String eventType;
        private int version = 1;
        private OffsetDateTime occurredAt = OffsetDateTime.now();
        private String producer;
        private String correlationId;
        private String causationId;
        private String aggregateType;
        private String aggregateId;
        private int aggregateVersion = 1;
        private T data;

        public Builder<T> eventId(UUID eventId) { this.eventId = eventId; return this; }
        public Builder<T> eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder<T> version(int version) { this.version = version; return this; }
        public Builder<T> occurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder<T> producer(String producer) { this.producer = producer; return this; }
        public Builder<T> correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder<T> causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder<T> aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public Builder<T> aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder<T> aggregateVersion(int aggregateVersion) { this.aggregateVersion = aggregateVersion; return this; }
        public Builder<T> data(T data) { this.data = data; return this; }

        public EventEnvelope<T> build() {
            return new EventEnvelope<>(
                eventId,
                eventType,
                version,
                occurredAt,
                producer,
                correlationId,
                causationId,
                aggregateType,
                aggregateId,
                aggregateVersion,
                data
            );
        }
    }
}
