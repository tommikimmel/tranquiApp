package com.tranqui.calendario.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Caché de los eventos propios del Google Calendar de cada profesional (lo que antes era
 * google_calendar_evento_externo en el monolito). La lee el panel "Próximos eventos" y la
 * disponibilidad de turnos, sin llamar a Google en cada pedido.
 */
@Repository
public class EventosExternos {

    public record Evento(Long id, Long profesionalId, String googleEventId, String titulo, LocalDateTime inicio,
                         LocalDateTime fin, boolean todoElDia, boolean transparente, String estado, int version) {}

    /** Resultado de un alta o actualización: el id y la versión con la que se publica el evento. */
    public record Guardado(Long id, int version) {}

    private static final RowMapper<Evento> FILA = (rs, i) -> new Evento(rs.getLong("id"), rs.getLong("profesional_id"),
            rs.getString("google_event_id"), rs.getString("titulo"), rs.getTimestamp("inicio").toLocalDateTime(),
            rs.getTimestamp("fin").toLocalDateTime(), rs.getBoolean("todo_el_dia"), rs.getBoolean("transparente"),
            rs.getString("estado"), rs.getInt("version"));

    private final JdbcTemplate jdbc;

    public EventosExternos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Guardado guardar(Long profesionalId, String googleEventId, String titulo, LocalDateTime inicio, LocalDateTime fin,
                            boolean todoElDia, boolean transparente, String estado) {
        return jdbc.queryForObject("INSERT INTO evento_externo (profesional_id, google_event_id, titulo, inicio, fin, "
                        + "todo_el_dia, transparente, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (google_event_id) DO UPDATE SET titulo = EXCLUDED.titulo, inicio = EXCLUDED.inicio, "
                        + "fin = EXCLUDED.fin, todo_el_dia = EXCLUDED.todo_el_dia, transparente = EXCLUDED.transparente, "
                        + "estado = EXCLUDED.estado, version = evento_externo.version + 1, actualizado_en = now() "
                        + "RETURNING id, version",
                (rs, i) -> new Guardado(rs.getLong("id"), rs.getInt("version")),
                profesionalId, googleEventId, titulo, Timestamp.valueOf(inicio), Timestamp.valueOf(fin), todoElDia,
                transparente, estado);
    }

    /** Copia de la carga inicial, conservando el id del monolito. */
    @Transactional
    public void copiar(Long id, Long profesionalId, String googleEventId, String titulo, LocalDateTime inicio,
                       LocalDateTime fin, boolean todoElDia, String estado) {
        jdbc.update("INSERT INTO evento_externo (id, profesional_id, google_event_id, titulo, inicio, fin, todo_el_dia, estado) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                id, profesionalId, googleEventId, titulo, Timestamp.valueOf(inicio), Timestamp.valueOf(fin), todoElDia, estado);
    }

    /** Los nuevos siguen después de los copiados. */
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('evento_externo', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) "
                + "FROM evento_externo), 1), (SELECT COUNT(*) > 0 FROM evento_externo))", Long.class);
    }

    public List<Evento> deProfesional(Long profesionalId) {
        return jdbc.query("SELECT * FROM evento_externo WHERE profesional_id = ? ORDER BY inicio", FILA, profesionalId);
    }

    /** Los que tocan el día (incluye los que empiezan antes o terminan después). */
    public List<Evento> delDia(Long profesionalId, LocalDate fecha) {
        return jdbc.query("SELECT * FROM evento_externo WHERE profesional_id = ? AND inicio < ? AND fin > ? ORDER BY inicio",
                FILA, profesionalId, Timestamp.valueOf(fecha.plusDays(1).atStartOfDay()), Timestamp.valueOf(fecha.atStartOfDay()));
    }

    @Transactional
    public void borrarPorGoogleId(String googleEventId) {
        jdbc.update("DELETE FROM evento_externo WHERE google_event_id = ?", googleEventId);
    }

    @Transactional
    public void borrarDeProfesional(Long profesionalId) {
        jdbc.update("DELETE FROM evento_externo WHERE profesional_id = ?", profesionalId);
    }
}
