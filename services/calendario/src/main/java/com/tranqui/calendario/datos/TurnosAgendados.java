package com.tranqui.calendario.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Copia de los turnos confirmados que ocupan agenda, de hoy en adelante, con el evento de Google y el
 * Meet que les creó el servicio (antes, Turno.googleEventId y Turno.telemedicinaUrl del monolito).
 */
@Repository
public class TurnosAgendados {

    public record Turno(Long turnoId, Long profesionalId, Long pacienteId, LocalDate fecha, LocalTime horaInicio,
                        LocalTime horaFin, String modalidad, String googleEventId, String meetUrl, int version) {

        public boolean esOnline() {
            return "ONLINE".equalsIgnoreCase(modalidad);
        }

        /** El monolito siempre guarda la hora de fin; si un productor viejo no la manda, 45 minutos. */
        public LocalTime fin() {
            return horaFin != null ? horaFin : horaInicio.plusMinutes(45);
        }
    }

    private static final RowMapper<Turno> FILA = (rs, i) -> new Turno(rs.getLong("turno_id"), rs.getLong("profesional_id"),
            (Long) rs.getObject("paciente_id"), rs.getDate("fecha").toLocalDate(), rs.getTime("hora_inicio").toLocalTime(),
            rs.getTime("hora_fin") != null ? rs.getTime("hora_fin").toLocalTime() : null, rs.getString("modalidad"),
            rs.getString("google_event_id"), rs.getString("meet_url"), rs.getInt("version"));

    private final JdbcTemplate jdbc;

    public TurnosAgendados(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Alta o actualización de los datos del turno; el evento y el Meet que ya tenga no se pisan con nulos. */
    @Transactional
    public void guardar(Long turnoId, Long profesionalId, Long pacienteId, LocalDate fecha, LocalTime horaInicio,
                        LocalTime horaFin, String modalidad, String googleEventId, String meetUrl) {
        jdbc.update("INSERT INTO turno_agendado (turno_id, profesional_id, paciente_id, fecha, hora_inicio, hora_fin, modalidad, "
                        + "google_event_id, meet_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (turno_id) DO UPDATE SET "
                        + "fecha = EXCLUDED.fecha, hora_inicio = EXCLUDED.hora_inicio, "
                        + "hora_fin = COALESCE(EXCLUDED.hora_fin, turno_agendado.hora_fin), "
                        + "modalidad = COALESCE(EXCLUDED.modalidad, turno_agendado.modalidad), "
                        + "google_event_id = COALESCE(EXCLUDED.google_event_id, turno_agendado.google_event_id), "
                        + "meet_url = COALESCE(EXCLUDED.meet_url, turno_agendado.meet_url)",
                turnoId, profesionalId, pacienteId, fecha, Time.valueOf(horaInicio), horaFin != null ? Time.valueOf(horaFin) : null,
                modalidad, vacioANulo(googleEventId), vacioANulo(meetUrl));
    }

    public Optional<Turno> buscar(Long turnoId) {
        return jdbc.query("SELECT * FROM turno_agendado WHERE turno_id = ?", FILA, turnoId).stream().findFirst();
    }

    @Transactional
    public void mover(Long turnoId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        jdbc.update("UPDATE turno_agendado SET fecha = ?, hora_inicio = ?, hora_fin = ? WHERE turno_id = ?",
                fecha, Time.valueOf(horaInicio), horaFin != null ? Time.valueOf(horaFin) : null, turnoId);
    }

    /** Evento creado en Google (o Meet simulado en local). Devuelve la versión nueva, para el evento. */
    @Transactional
    public int guardarEvento(Long turnoId, String googleEventId, String meetUrl) {
        Integer version = jdbc.queryForObject("UPDATE turno_agendado SET google_event_id = ?, meet_url = COALESCE(?, meet_url), "
                        + "version = version + 1 WHERE turno_id = ? RETURNING version", Integer.class,
                vacioANulo(googleEventId), vacioANulo(meetUrl), turnoId);
        return version != null ? version : 1;
    }

    @Transactional
    public void quitarEvento(Long turnoId) {
        jdbc.update("UPDATE turno_agendado SET google_event_id = NULL WHERE turno_id = ?", turnoId);
    }

    @Transactional
    public void borrar(Long turnoId) {
        jdbc.update("DELETE FROM turno_agendado WHERE turno_id = ?", turnoId);
    }

    public boolean esEventoDeLaApp(Long profesionalId, String googleEventId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM turno_agendado WHERE profesional_id = ? AND google_event_id = ?",
                Integer.class, profesionalId, googleEventId);
        return n != null && n > 0;
    }

    /** Turnos de hoy en adelante que todavía no tienen evento en Google (se reintentan cada 5 minutos). */
    public List<Turno> sinEvento(Long profesionalId) {
        return jdbc.query("SELECT * FROM turno_agendado WHERE profesional_id = ? AND google_event_id IS NULL AND fecha >= ? "
                + "ORDER BY fecha, hora_inicio", FILA, profesionalId, LocalDate.now());
    }

    public List<Turno> conEvento(Long profesionalId) {
        return jdbc.query("SELECT * FROM turno_agendado WHERE profesional_id = ? AND google_event_id IS NOT NULL", FILA, profesionalId);
    }

    /** Los turnos que ya pasaron no cambian nada: se borran una vez por día. */
    @Transactional
    public int borrarPasados() {
        return jdbc.update("DELETE FROM turno_agendado WHERE fecha < ?", LocalDate.now());
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
