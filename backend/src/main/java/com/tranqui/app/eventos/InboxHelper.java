package com.tranqui.app.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Helper reutilizable para el patrón Inbox (sección 6.2 de docs/arquitectura/microservicios.md).
 * Garantiza idempotencia registrando (event_id, consumidor) y ejecutando la acción de negocio
 * en la misma transacción. Si el evento ya fue procesado, ignora el duplicado.
 */
@Service
public class InboxHelper {

    private static final Logger log = LoggerFactory.getLogger(InboxHelper.class);
    private final JdbcTemplate jdbcTemplate;

    public InboxHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserta (event_id, consumidor) y ejecuta la acción en la misma transacción.
     * Si el evento ya fue procesado por este consumidor, ignora el duplicado y retorna false sin ejecutar la acción.
     *
     * @param eventId    Identificador único del evento
     * @param consumidor Nombre del consumidor/handler (ej. 'mail-turno-confirmado')
     * @param accion     Acción de negocio a ejecutar si el evento es nuevo
     * @return true si el evento era nuevo y fue procesado, false si era un duplicado ignorado
     */
    @Transactional
    public boolean procesar(UUID eventId, String consumidor, Runnable accion) {
        if (eventId == null || consumidor == null || consumidor.isBlank()) {
            throw new IllegalArgumentException("eventId y consumidor no pueden ser nulos ni vacíos");
        }

        int insertados = registrarEventoProcesado(eventId, consumidor);
        if (insertados == 0) {
            log.info("Evento {} ya procesado por el consumidor '{}', ignorando duplicado", eventId, consumidor);
            return false;
        }

        if (accion != null) {
            accion.run();
        }
        return true;
    }

    /**
     * Sobrecarga de procesar que permite ejecutar un {@link Supplier} y retornar su resultado.
     */
    @Transactional
    public <T> Optional<T> procesar(UUID eventId, String consumidor, Supplier<T> accion) {
        if (eventId == null || consumidor == null || consumidor.isBlank()) {
            throw new IllegalArgumentException("eventId y consumidor no pueden ser nulos ni vacíos");
        }

        int insertados = registrarEventoProcesado(eventId, consumidor);
        if (insertados == 0) {
            log.info("Evento {} ya procesado por el consumidor '{}', ignorando duplicado", eventId, consumidor);
            return Optional.empty();
        }

        return Optional.ofNullable(accion != null ? accion.get() : null);
    }

    private int registrarEventoProcesado(UUID eventId, String consumidor) {
        try {
            return jdbcTemplate.update(
                "INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?) " +
                "ON CONFLICT (event_id, consumidor) DO NOTHING",
                eventId, consumidor, Timestamp.from(Instant.now())
            );
        } catch (Exception ex) {
            // Fallback ante motores SQL que no soporten ON CONFLICT DO NOTHING
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ? AND consumidor = ?",
                Integer.class, eventId, consumidor
            );
            if (count != null && count > 0) {
                return 0;
            }
            try {
                return jdbcTemplate.update(
                    "INSERT INTO eventos_procesados (event_id, consumidor, procesado_en) VALUES (?, ?, ?)",
                    eventId, consumidor, Timestamp.from(Instant.now())
                );
            } catch (DataIntegrityViolationException dive) {
                return 0;
            }
        }
    }
}
