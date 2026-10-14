package com.tranqui.app.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.Topicos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publicador de dominio responsable de construir el sobre común {@link EventEnvelope}
 * y persistir el evento en la tabla outbox dentro de la misma transacción de negocio.
 */
@Service
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = EventObjectMapperFactory.create();
    }

    /**
     * Publica un evento de dominio en la outbox dentro de una transacción obligatoria existente.
     *
     * @param topico           Nombre completo del tópico según convención (ej. turnos.turno.confirmado.v1)
     * @param aggregateType    Tipo de agregado (ej. turno, pago, usuario)
     * @param aggregateId      Identificador del agregado
     * @param aggregateVersion Versión incremental del agregado
     * @param data             Cuerpo de datos del evento (record sin datos clínicos ni secretos)
     * @return El {@link EventEnvelope} generado y almacenado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publicar(String topico, String aggregateType, String aggregateId, int aggregateVersion, T data) {
        return publicar(topico, aggregateType, aggregateId, aggregateVersion, data, null);
    }

    /**
     * Sobrecarga de conveniencia que infiere el aggregateType a partir del tópico (<dominio>.<entidad>.<evento>.v<n>).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publicar(String topico, String aggregateId, int aggregateVersion, T data) {
        return publicar(topico, extraerEntidad(topico), aggregateId, aggregateVersion, data, null);
    }

    private static String extraerEntidad(String topico) {
        if (topico != null) {
            String[] parts = topico.split("\\.");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return "desconocido";
    }

    /**
     * Sobrecarga de publicación permitiendo especificar causationId.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publicar(String topico, String aggregateType, String aggregateId, int aggregateVersion, T data, String causationId) {
        if (topico == null || topico.isBlank()) {
            throw new IllegalArgumentException("El tópico no puede ser nulo ni vacío");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType no puede ser nulo ni vacío");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId no puede ser nulo ni vacío");
        }

        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = CorrelationIdHolder.getOrGenerateCorrelationId();

        String producer = Topicos.extraerDominio(topico);
        String eventType = Topicos.extraerTipoEvento(topico);
        int version = Topicos.extraerVersion(topico);

        EventEnvelope<T> envelope = EventEnvelope.<T>builder()
                .eventId(eventId)
                .eventType(eventType)
                .version(version)
                .occurredAt(occurredAt)
                .producer(producer)
                .correlationId(correlationId)
                .causationId(causationId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .data(data)
                .build();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error al serializar EventEnvelope a JSON", e);
        }

        OutboxRecord record = OutboxRecord.builder()
                .eventId(eventId)
                .topic(topico)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(payloadJson)
                .createdAt(occurredAt)
                .attempts(0)
                .build();

        outboxRepository.save(record);
        return envelope;
    }
}
