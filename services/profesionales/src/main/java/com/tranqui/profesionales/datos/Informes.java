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
 * Informes clínicos de la historia clínica.
 * Leyes 26.529 y 27.706: inalterables; las correcciones generan un nuevo anexo vinculando a informe_original_id.
 */
@Repository
public class Informes {

    public record Informe(
            Long id,
            Long pacienteId,
            Long medicoId,
            LocalDate fecha,
            String tipoInforme,
            String planTrabajo,
            String contenido,
            String nombreArchivo,
            LocalDateTime fechaCreacion,
            String estado,
            Long informeOriginalId,
            String motivo,
            String hashIntegridad
    ) {}

    private static final String COLUMNAS = "id, paciente_id, medico_id, fecha, tipo_informe, plan_trabajo, "
            + "contenido, nombre_archivo, fecha_creacion, estado, informe_original_id, motivo, hash_integridad";

    private final JdbcTemplate jdbc;
    private final RowMapper<Informe> mapeo = (rs, i) -> new Informe(
            rs.getLong("id"),
            rs.getLong("paciente_id"),
            rs.getLong("medico_id"),
            rs.getObject("fecha", LocalDate.class),
            rs.getString("tipo_informe"),
            rs.getString("plan_trabajo"),
            rs.getString("contenido"),
            rs.getString("nombre_archivo"),
            rs.getObject("fecha_creacion", LocalDateTime.class),
            rs.getString("estado"),
            (Long) rs.getObject("informe_original_id"),
            rs.getString("motivo"),
            rs.getString("hash_integridad")
    );

    public Informes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Informe> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM informe_clinico WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public List<Informe> porPaciente(Long pacienteId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM informe_clinico WHERE paciente_id = ? ORDER BY fecha DESC, id DESC",
                mapeo, pacienteId);
    }

    public List<Informe> porPacienteYMedico(Long pacienteId, Long medicoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM informe_clinico WHERE paciente_id = ? AND medico_id = ? ORDER BY fecha DESC, id DESC",
                mapeo, pacienteId, medicoId);
    }

    /** Carga inicial: inserta con el id del monolito. */
    @Transactional
    public void insertar(Informe inf) {
        jdbc.update("INSERT INTO informe_clinico (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                inf.id(), inf.pacienteId(), inf.medicoId(),
                inf.fecha() != null ? Date.valueOf(inf.fecha()) : null,
                inf.tipoInforme(), inf.planTrabajo(), inf.contenido(), inf.nombreArchivo(),
                inf.fechaCreacion() != null ? Timestamp.valueOf(inf.fechaCreacion()) : null,
                inf.estado(), inf.informeOriginalId(), inf.motivo(), inf.hashIntegridad());
    }

    /** Asiento nuevo: toma el id de la secuencia propia. */
    @Transactional
    public Long insertarNuevo(Informe inf) {
        Long id = jdbc.queryForObject("SELECT nextval('informe_clinico_id_seq')", Long.class);
        jdbc.update("INSERT INTO informe_clinico (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, inf.pacienteId(), inf.medicoId(),
                inf.fecha() != null ? Date.valueOf(inf.fecha()) : null,
                inf.tipoInforme(), inf.planTrabajo(), inf.contenido(), inf.nombreArchivo(),
                inf.fechaCreacion() != null ? Timestamp.valueOf(inf.fechaCreacion()) : null,
                inf.estado(), inf.informeOriginalId(), inf.motivo(), inf.hashIntegridad());
        return id;
    }

    @Transactional
    public void actualizarEstadoYMotivo(Long id, String estado, String motivo) {
        jdbc.update("UPDATE informe_clinico SET estado = ?, motivo = ? WHERE id = ?", estado, motivo, id);
    }

    @Transactional
    public void actualizarEstadoYMotivo(Long id, String estado, String motivo, String hashIntegridad) {
        jdbc.update("UPDATE informe_clinico SET estado = ?, motivo = ?, hash_integridad = ? WHERE id = ?",
                estado, motivo, hashIntegridad, id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM informe_clinico", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('informe_clinico_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM informe_clinico), 1))",
                Long.class);
    }
}
