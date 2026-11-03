package com.tranqui.eventos.consumo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Patrón Inbox (sección 6.2 de docs/arquitectura/microservicios.md). Registra (event_id, consumidor)
 * y ejecuta la acción de negocio en la misma transacción: si el evento ya fue procesado por ese
 * consumidor, lo ignora.
 *
 * <p>Requiere la tabla {@code eventos_procesados} (y {@code versiones_aplicadas} para el descarte por
 * versión) en la base del servicio; el SQL está en {@code db/eventos/consumo.sql} de esta librería.
 */
public class InboxHelper {

    private static final Logger log = LoggerFactory.getLogger(InboxHelper.class);
    private final JdbcTemplate jdbcTemplate;

    public InboxHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserta (event_id, consumidor) y ejecuta la acción en la misma transacción.
     *
     * @return true si el evento era nuevo y se procesó; false si era un duplicado
     */
    @Transactional
    public boolean procesar(UUID eventId, String consumidor, Runnable accion) {
        validar(eventId, consumidor);
        if (!registrar(eventId, consumidor)) {
            return false;
        }
        if (accion != null) {
            accion.run();
        }
        return true;
    }

    /** Variante de {@link #procesar(UUID, String, Runnable)} que devuelve el resultado de la acción. */
    @Transactional
    public <T> Optional<T> procesar(UUID eventId, String consumidor, Supplier<T> accion) {
        validar(eventId, consumidor);
        if (!registrar(eventId, consumidor)) {
            return Optional.empty();
        }
        return Optional.ofNullable(accion != null ? accion.get() : null);
    }

    /**
     * Registra el evento como procesado. Debe llamarse dentro de una transacción ya abierta.
     *
     * @return false si ya estaba registrado (duplicado)
     */
    public boolean registrar(UUID eventId, String consumidor) {
        Timestamp ahora = Timestamp.from(Instant.now());
        int insertados;
        try {
            insertados = jdbcTemplate.update(
                    "INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?) "
                            + "ON CONFLICT (event_id, consumidor) DO NOTHING",
                    eventId, consumidor, ahora);
        } catch (BadSqlGrammarException e) {
            // Motores sin ON CONFLICT (H2 de los tests del monolito). En PostgreSQL no pasa nunca.
            insertados = insertarSinOnConflict(eventId, consumidor, ahora);
        }
        if (insertados == 0) {
            log.info("Evento {} ya procesado por el consumidor '{}', se ignora el duplicado", eventId, consumidor);
            return false;
        }
        return true;
    }

    /**
     * Guarda la versión si supera a la última aplicada por el consumidor para esa entidad (sección 6.4).
     * Debe llamarse dentro de una transacción ya abierta.
     *
     * @return false si la versión es vieja o repetida y el evento debe descartarse
     */
    public boolean avanzarVersion(String consumidor, String aggregateType, String aggregateId, int version) {
        List<Integer> actual = jdbcTemplate.queryForList(
                "SELECT version FROM versiones_aplicadas WHERE consumidor = ? AND aggregate_type = ? "
                        + "AND aggregate_id = ? FOR UPDATE",
                Integer.class, consumidor, aggregateType, aggregateId);
        if (!actual.isEmpty() && actual.get(0) >= version) {
            log.info("Evento de {}#{} con versión {} descartado por '{}': ya se aplicó la {}",
                    aggregateType, aggregateId, version, consumidor, actual.get(0));
            return false;
        }
        jdbcTemplate.update(
                "INSERT INTO versiones_aplicadas (consumidor, aggregate_type, aggregate_id, version) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (consumidor, aggregate_type, aggregate_id) DO UPDATE SET version = EXCLUDED.version",
                consumidor, aggregateType, aggregateId, version);
        return true;
    }

    /** Borra los registros de más de {@code dias} días (sección 6.2: 30 días). */
    public int limpiarAnteriores(int dias) {
        return jdbcTemplate.update(
                "DELETE FROM eventos_procesados WHERE procesado_en < now() - make_interval(days => ?)", dias);
    }

    private int insertarSinOnConflict(UUID eventId, String consumidor, Timestamp ahora) {
        Integer existentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ? AND consumidor = ?",
                Integer.class, eventId, consumidor);
        if (existentes != null && existentes > 0) {
            return 0;
        }
        try {
            return jdbcTemplate.update(
                    "INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?)",
                    eventId, consumidor, ahora);
        } catch (DataIntegrityViolationException e) {
            return 0;
        }
    }

    private static void validar(UUID eventId, String consumidor) {
        if (eventId == null || consumidor == null || consumidor.isBlank()) {
            throw new IllegalArgumentException("eventId y consumidor no pueden ser nulos ni vacíos");
        }
    }
}
