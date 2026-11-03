package com.tranqui.profesionales.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seguimientos diarios de la historia clínica.
 * Leyes 26.529 y 27.706: nunca se borra una fila, solo se anula o corrige dejando motivo y hash.
 */
@Repository
public class Seguimientos {

    public record Seguimiento(
            Long id,
            Long pacienteId,
            Long medicoId,
            LocalDate fecha,
            String estadoAnimo,
            String sintomas,
            String notas,
            LocalDateTime fechaCreacion,
            String estado,
            String motivo,
            String hashIntegridad
    ) {}

    private static final String COLUMNAS = "id, paciente_id, medico_id, fecha, estado_animo, sintomas, notas, "
            + "fecha_creacion, estado, motivo, hash_integridad";

    private final JdbcTemplate jdbc;
    private final RowMapper<Seguimiento> mapeo = (rs, i) -> new Seguimiento(
            rs.getLong("id"),
            rs.getLong("paciente_id"),
            rs.getLong("medico_id"),
            rs.getObject("fecha", LocalDate.class),
            rs.getString("estado_animo"),
            rs.getString("sintomas"),
            rs.getString("notas"),
            rs.getObject("fecha_creacion", LocalDateTime.class),
            rs.getString("estado"),
            rs.getString("motivo"),
            rs.getString("hash_integridad")
    );

    public Seguimientos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Seguimiento> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM seguimiento_diario WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public List<Seguimiento> porPaciente(Long pacienteId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM seguimiento_diario WHERE paciente_id = ? ORDER BY fecha DESC, id DESC",
                mapeo, pacienteId);
    }

    public List<Seguimiento> porPacienteYMedico(Long pacienteId, Long medicoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM seguimiento_diario WHERE paciente_id = ? AND medico_id = ? ORDER BY fecha DESC, id DESC",
                mapeo, pacienteId, medicoId);
    }

    /** Carga inicial: inserta con el id del monolito. */
    @Transactional
    public void insertar(Seguimiento s) {
        jdbc.update("INSERT INTO seguimiento_diario (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.id(), s.pacienteId(), s.medicoId(),
                s.fecha() != null ? Date.valueOf(s.fecha()) : null,
                s.estadoAnimo(), s.sintomas(), s.notas(),
                s.fechaCreacion() != null ? Timestamp.valueOf(s.fechaCreacion()) : null,
                s.estado(), s.motivo(), s.hashIntegridad());
    }

    /** Asiento nuevo: toma el id de la secuencia propia. */
    @Transactional
    public Long insertarNuevo(Seguimiento s) {
        Long id = jdbc.queryForObject("SELECT nextval('seguimiento_diario_id_seq')", Long.class);
        jdbc.update("INSERT INTO seguimiento_diario (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, s.pacienteId(), s.medicoId(),
                s.fecha() != null ? Date.valueOf(s.fecha()) : null,
                s.estadoAnimo(), s.sintomas(), s.notas(),
                s.fechaCreacion() != null ? Timestamp.valueOf(s.fechaCreacion()) : null,
                s.estado(), s.motivo(), s.hashIntegridad());
        return id;
    }

    @Transactional
    public void actualizarEstadoYMotivo(Long id, String estado, String motivo) {
        jdbc.update("UPDATE seguimiento_diario SET estado = ?, motivo = ? WHERE id = ?", estado, motivo, id);
    }

    @Transactional
    public void actualizarEstadoYMotivo(Long id, String estado, String motivo, String hashIntegridad) {
        jdbc.update("UPDATE seguimiento_diario SET estado = ?, motivo = ?, hash_integridad = ? WHERE id = ?",
                estado, motivo, hashIntegridad, id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM seguimiento_diario", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('seguimiento_diario_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM seguimiento_diario), 1))",
                Long.class);
    }
}
