package com.tranqui.profesionales.datos;

import com.tranqui.eventos.data.DisponibilidadProfesionalData.Franja;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** La disponibilidad semanal de los profesionales, franja por franja (como {@code disponibilidad} en el monolito). */
@Repository
public class Agendas {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final RowMapper<Franja> mapeo = (rs, i) -> new Franja(rs.getInt("dia_semana"),
            rs.getObject("hora_inicio", LocalTime.class).format(HORA), rs.getObject("hora_fin", LocalTime.class).format(HORA),
            rs.getString("modalidad"));

    public Agendas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Franja> franjas(Long profesionalId) {
        return jdbc.query("SELECT dia_semana, hora_inicio, hora_fin, modalidad FROM disponibilidad WHERE profesional_id = ? "
                + "ORDER BY id", mapeo, profesionalId);
    }

    /** Las de una modalidad más las viejas sin modalidad (valen para las dos), como {@code findByMedicoIdAndModalidadOLegacy}. */
    public List<Franja> franjas(Long profesionalId, String modalidad) {
        return jdbc.query("SELECT dia_semana, hora_inicio, hora_fin, modalidad FROM disponibilidad WHERE profesional_id = ? "
                + "AND (modalidad = ? OR modalidad IS NULL) ORDER BY id", mapeo, profesionalId, modalidad);
    }

    /** Guardar una grilla también "se queda" con las franjas viejas sin modalidad, como en el monolito. */
    @Transactional
    public void reemplazar(Long profesionalId, String modalidad, List<Franja> franjas) {
        jdbc.update("DELETE FROM disponibilidad WHERE profesional_id = ? AND (modalidad = ? OR modalidad IS NULL)",
                profesionalId, modalidad);
        insertar(profesionalId, franjas);
    }

    @Transactional
    public void reemplazarTodas(Long profesionalId, List<Franja> franjas) {
        jdbc.update("DELETE FROM disponibilidad WHERE profesional_id = ?", profesionalId);
        insertar(profesionalId, franjas);
    }

    private void insertar(Long profesionalId, List<Franja> franjas) {
        for (Franja f : franjas) {
            jdbc.update("INSERT INTO disponibilidad (profesional_id, dia_semana, hora_inicio, hora_fin, modalidad) "
                            + "VALUES (?, ?, ?, ?, ?)", profesionalId, f.diaSemana(), Time.valueOf(LocalTime.parse(f.horaInicio())),
                    Time.valueOf(LocalTime.parse(f.horaFin())), f.modalidad());
        }
    }
}
