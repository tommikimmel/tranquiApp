package com.tranqui.profesionales.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Copia mínima de los turnos para determinar qué pacientes atiende cada profesional.
 * Solo guarda id, profesional_id, paciente_id, fecha, estado y versión.
 */
@Repository
public class TurnosAtendidos {

    public record TurnoAtendido(
            Long id,
            Long profesionalId,
            Long pacienteId,
            LocalDate fecha,
            String estado,
            int version
    ) {}

    private static final String COLUMNAS = "id, profesional_id, paciente_id, fecha, estado, version";

    private final JdbcTemplate jdbc;
    private final RowMapper<TurnoAtendido> mapeo = (rs, i) -> new TurnoAtendido(
            rs.getLong("id"),
            rs.getLong("profesional_id"),
            rs.getLong("paciente_id"),
            rs.getObject("fecha", LocalDate.class),
            rs.getString("estado"),
            rs.getInt("version")
    );

    public TurnosAtendidos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TurnoAtendido> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM turno_atendido WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    /**
     * Guarda el turno respetando la versión: solo actualiza si la versión que llega es mayor o igual.
     */
    @Transactional
    public void guardar(Long id, Long profesionalId, Long pacienteId, LocalDate fecha, String estado, int version) {
        String sql = "INSERT INTO turno_atendido (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "profesional_id = EXCLUDED.profesional_id, "
                + "paciente_id = EXCLUDED.paciente_id, "
                + "fecha = EXCLUDED.fecha, "
                + "estado = EXCLUDED.estado, "
                + "version = EXCLUDED.version "
                + "WHERE EXCLUDED.version >= turno_atendido.version";

        jdbc.update(sql, id, profesionalId, pacienteId, fecha != null ? Date.valueOf(fecha) : null, estado, version);
    }

    @Transactional
    public void guardar(TurnoAtendido t) {
        guardar(t.id(), t.profesionalId(), t.pacienteId(), t.fecha(), t.estado(), t.version());
    }

    /**
     * Replica TurnoRepository.existsByMedicoIdAndPacienteIdAndEstadoIn con CONFIRMADO y PENDIENTE_VALIDACION.
     */
    public boolean existeConfirmadoOPendiente(Long profesionalId, Long pacienteId) {
        Boolean r = jdbc.queryForObject(
                "SELECT count(*) > 0 FROM turno_atendido WHERE profesional_id = ? AND paciente_id = ? AND estado IN ('CONFIRMADO', 'PENDIENTE_VALIDACION')",
                Boolean.class, profesionalId, pacienteId);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Replica TurnoRepository.findDistinctPacientesByMedicoId: ids distintos de pacientes con turno
     * CONFIRMADO o PENDIENTE_VALIDACION de ese profesional.
     */
    public List<Long> pacientesDistintos(Long profesionalId) {
        return jdbc.queryForList(
                "SELECT DISTINCT paciente_id FROM turno_atendido WHERE profesional_id = ? AND estado IN ('CONFIRMADO', 'PENDIENTE_VALIDACION')",
                Long.class, profesionalId);
    }

    /**
     * Replica TurnoRepository.findByMedicoIdAndEstadoNot con CANCELADO: todos los turnos no cancelados del profesional.
     */
    public List<TurnoAtendido> turnosNoCancelados(Long profesionalId) {
        return jdbc.query(
                "SELECT " + COLUMNAS + " FROM turno_atendido WHERE profesional_id = ? AND estado != 'CANCELADO' ORDER BY fecha ASC, id ASC",
                mapeo, profesionalId);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM turno_atendido", Long.class);
        return n != null ? n : 0;
    }
}
