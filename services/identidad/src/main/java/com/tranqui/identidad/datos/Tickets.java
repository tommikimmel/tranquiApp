package com.tranqui.identidad.datos;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso JDBC a la tabla {@code ticket} (Fase 9, I2a), replicando las consultas
 * de {@code TicketRepository} con control de concurrencia optimista sobre {@code version}.
 */
@Repository
public class Tickets {

    public static final String COLUMNAS = "id, version, creador_id, asunto, estado, fecha_creacion, fecha_actualizacion";

    private final JdbcTemplate jdbc;

    private final RowMapper<Ticket> mapeo = (rs, rowNum) -> new Ticket(
            rs.getLong("id"),
            rs.getInt("version"),
            rs.getLong("creador_id"),
            rs.getString("asunto"),
            rs.getString("estado"),
            rs.getTimestamp("fecha_creacion").toLocalDateTime(),
            rs.getTimestamp("fecha_actualizacion").toLocalDateTime()
    );

    public Tickets(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Ticket> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<Ticket> r = jdbc.query("SELECT " + COLUMNAS + " FROM ticket WHERE id = ?", mapeo, id);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public List<Ticket> buscarPorCreador(Long creadorId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM ticket WHERE creador_id = ? ORDER BY fecha_actualizacion DESC",
                mapeo, creadorId);
    }

    public List<Ticket> todosOrdenadosPorFechaActualizacionDesc() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM ticket ORDER BY fecha_actualizacion DESC", mapeo);
    }

    public List<Ticket> todos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM ticket ORDER BY id ASC", mapeo);
    }

    @Transactional
    public void insertar(Ticket t) {
        jdbc.update("""
                INSERT INTO ticket (id, version, creador_id, asunto, estado, fecha_creacion, fecha_actualizacion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                t.id(),
                t.version() != null ? t.version() : 0,
                t.creadorId(),
                t.asunto(),
                t.estado(),
                t.fechaCreacion() != null ? Timestamp.valueOf(t.fechaCreacion()) : Timestamp.valueOf(LocalDateTime.now()),
                t.fechaActualizacion() != null ? Timestamp.valueOf(t.fechaActualizacion()) : Timestamp.valueOf(LocalDateTime.now())
        );
    }

    @Transactional
    public Long insertarNuevo(Ticket t) {
        Long id = jdbc.queryForObject("SELECT nextval('ticket_id_seq')", Long.class);
        LocalDateTime ahora = LocalDateTime.now();
        Ticket nuevo = new Ticket(
                id,
                t.version() != null ? t.version() : 0,
                t.creadorId(),
                t.asunto(),
                t.estado(),
                t.fechaCreacion() != null ? t.fechaCreacion() : ahora,
                t.fechaActualizacion() != null ? t.fechaActualizacion() : ahora
        );
        insertar(nuevo);
        return id;
    }

    @Transactional
    public Ticket actualizar(Ticket t) {
        int filas = jdbc.update("""
                UPDATE ticket SET
                    version = version + 1,
                    creador_id = ?,
                    asunto = ?,
                    estado = ?,
                    fecha_creacion = ?,
                    fecha_actualizacion = ?
                WHERE id = ? AND version = ?
                """,
                t.creadorId(),
                t.asunto(),
                t.estado(),
                t.fechaCreacion() != null ? Timestamp.valueOf(t.fechaCreacion()) : Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                t.id(),
                t.version()
        );

        if (filas == 0) {
            throw new OptimisticLockingFailureException(
                    "Conflicto de versión optimista al actualizar ticket id=" + t.id() + " con versión " + t.version());
        }
        // La fila como quedó (versión subida): los eventos usan esa versión + 1, como el monolito.
        return new Ticket(t.id(), (t.version() != null ? t.version() : 0) + 1, t.creadorId(), t.asunto(), t.estado(),
                t.fechaCreacion(), t.fechaActualizacion());
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM ticket", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('ticket_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM ticket), 1))",
                Long.class);
    }
}
