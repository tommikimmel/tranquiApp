package com.tranqui.identidad.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso JDBC a la tabla {@code ticket_mensaje} (Fase 9, I2a), replicando las consultas
 * de {@code TicketMensajeRepository}.
 */
@Repository
public class TicketMensajes {

    public static final String COLUMNAS = "id, ticket_id, autor_id, contenido, fecha_envio";

    private final JdbcTemplate jdbc;

    private final RowMapper<TicketMensaje> mapeo = (rs, rowNum) -> new TicketMensaje(
            rs.getLong("id"),
            rs.getLong("ticket_id"),
            rs.getLong("autor_id"),
            rs.getString("contenido"),
            rs.getTimestamp("fecha_envio").toLocalDateTime()
    );

    public TicketMensajes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TicketMensaje> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<TicketMensaje> r = jdbc.query("SELECT " + COLUMNAS + " FROM ticket_mensaje WHERE id = ?", mapeo, id);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public List<TicketMensaje> buscarPorTicketId(Long ticketId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM ticket_mensaje WHERE ticket_id = ? ORDER BY fecha_envio ASC",
                mapeo, ticketId);
    }

    public List<TicketMensaje> todos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM ticket_mensaje ORDER BY id ASC", mapeo);
    }

    @Transactional
    public void insertar(TicketMensaje m) {
        jdbc.update("""
                INSERT INTO ticket_mensaje (id, ticket_id, autor_id, contenido, fecha_envio)
                VALUES (?, ?, ?, ?, ?)
                """,
                m.id(),
                m.ticketId(),
                m.autorId(),
                m.contenido(),
                m.fechaEnvio() != null ? Timestamp.valueOf(m.fechaEnvio()) : Timestamp.valueOf(LocalDateTime.now())
        );
    }

    @Transactional
    public Long insertarNuevo(TicketMensaje m) {
        Long id = jdbc.queryForObject("SELECT nextval('ticket_mensaje_id_seq')", Long.class);
        TicketMensaje nuevo = new TicketMensaje(
                id,
                m.ticketId(),
                m.autorId(),
                m.contenido(),
                m.fechaEnvio() != null ? m.fechaEnvio() : LocalDateTime.now()
        );
        insertar(nuevo);
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM ticket_mensaje", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('ticket_mensaje_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM ticket_mensaje), 1))",
                Long.class);
    }
}
