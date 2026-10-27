package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Eventos y auditoría de suscripciones (Fase 8, S2a).
 * Réplica de SubscriptionEventRepository del monolito.
 */
@Repository
public class EventosSuscripcion {

    public record Evento(
            Long id,
            Long subscriptionId,
            String eventType,
            String previousStatus,
            String newStatus,
            String actorType,
            String actorId,
            String payloadJson,
            LocalDateTime createdAt
    ) {}

    private static final String COLUMNAS = "id, subscription_id, event_type, previous_status, new_status, "
            + "actor_type, actor_id, payload_json, created_at";

    private final JdbcTemplate jdbc;

    private final RowMapper<Evento> mapeo = (rs, i) -> new Evento(
            rs.getLong("id"),
            rs.getLong("subscription_id"),
            rs.getString("event_type"),
            rs.getString("previous_status"),
            rs.getString("new_status"),
            rs.getString("actor_type"),
            rs.getString("actor_id"),
            rs.getString("payload_json"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public EventosSuscripcion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Evento> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_events WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public List<Evento> porSuscripcionId(Long subscriptionId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_events WHERE subscription_id = ? ORDER BY created_at DESC, id DESC",
                mapeo, subscriptionId);
    }

    public List<Evento> todosOrdenados() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_events ORDER BY created_at DESC, id DESC", mapeo);
    }

    @Transactional
    public void insertar(Evento e) {
        jdbc.update("INSERT INTO subscription_events (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                e.id(), e.subscriptionId(), e.eventType(), e.previousStatus(), e.newStatus(),
                e.actorType(), e.actorId(), e.payloadJson(),
                e.createdAt() != null ? Timestamp.valueOf(e.createdAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    @Transactional
    public Long insertarNuevo(Evento e) {
        Long id = jdbc.queryForObject("SELECT nextval('subscription_events_id_seq')", Long.class);
        insertar(new Evento(id, e.subscriptionId(), e.eventType(), e.previousStatus(), e.newStatus(),
                e.actorType(), e.actorId(), e.payloadJson(), e.createdAt()));
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM subscription_events", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('subscription_events_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM subscription_events), 1))",
                Long.class);
    }
}
