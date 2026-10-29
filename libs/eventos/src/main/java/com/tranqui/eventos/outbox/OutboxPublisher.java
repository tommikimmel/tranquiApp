package com.tranqui.eventos.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.ValidadorEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publica un evento de dominio en la outbox del servicio, dentro de la misma transacción que el
 * cambio de negocio (sección 6.1): si la transacción se deshace, el evento no existe; si se
 * confirma, existe sí o sí. El relay lo manda a RabbitMQ después.
 *
 * <p>Mismo contrato que el {@code OutboxPublisher} del monolito, que sigue usando el suyo hasta la
 * Fase 9. Requiere la tabla de {@code db/eventos/outbox.sql}.
 */
public class OutboxPublisher {

    private static final String INSERTAR = "INSERT INTO outbox (event_id, topic, aggregate_type, aggregate_id, payload, "
            + "created_at, attempts) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, 0)";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = EventObjectMapperFactory.create();
    private final ValidadorEventos.Modo validacion;

    public OutboxPublisher(JdbcTemplate jdbc) {
        this(jdbc, ValidadorEventos.Modo.RECHAZAR);
    }

    /** @param validacion con {@code RECHAZAR}, un data inválido no se publica (falla en origen); con {@code LOGUEAR}, avisa */
    public OutboxPublisher(JdbcTemplate jdbc, ValidadorEventos.Modo validacion) {
        this.jdbc = jdbc;
        this.validacion = validacion;
    }

    /** Como {@link #publicar(String, String, String, int, Object, String)}, con el tipo de agregado del tópico. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publicar(String topico, String aggregateId, int aggregateVersion, T data) {
        String[] partes = topico != null ? topico.split("\\.") : new String[0];
        return publicar(topico, partes.length >= 2 ? partes[1] : "desconocido", aggregateId, aggregateVersion, data, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publicar(String topico, String aggregateType, String aggregateId, int aggregateVersion,
                                         T data) {
        return publicar(topico, aggregateType, aggregateId, aggregateVersion, data, null);
    }

    /**
     * @param causationId el {@code eventId} del evento que causó este, si lo hay (sagas)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publicar(String topico, String aggregateType, String aggregateId, int aggregateVersion,
                                         T data, String causationId) {
        if (!Topicos.esValido(topico)) {
            throw new IllegalArgumentException("Tópico inválido: " + topico);
        }
        if (aggregateType == null || aggregateType.isBlank() || aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateType y aggregateId son obligatorios");
        }
        ValidadorEventos.problemas(data).ifPresent(problemas -> {
            if (validacion == ValidadorEventos.Modo.RECHAZAR) {
                throw new IllegalArgumentException("El data de " + topico + " no cumple sus reglas: " + problemas);
            }
            log.warn("Se publica {} con data inválido (modo loguear): {}", topico, problemas);
        });
        String correlationId = MDC.get("correlationId");
        EventEnvelope<T> sobre = EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID())
                .eventType(Topicos.extraerTipoEvento(topico))
                .version(Topicos.extraerVersion(topico))
                .occurredAt(OffsetDateTime.now())
                .producer(Topicos.extraerDominio(topico))
                .correlationId(correlationId != null && !correlationId.isBlank() ? correlationId : UUID.randomUUID().toString())
                .causationId(causationId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .data(data)
                .build();
        String json;
        try {
            json = mapper.writeValueAsString(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar el evento " + topico, e);
        }
        jdbc.update(INSERTAR, sobre.eventId(), topico, aggregateType, aggregateId, json,
                Timestamp.from(sobre.occurredAt().toInstant()));
        return sobre;
    }
}
