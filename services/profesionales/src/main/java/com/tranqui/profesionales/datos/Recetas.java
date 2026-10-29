package com.tranqui.profesionales.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Recetas emitidas (QBI2 / Innovamed).
 * No guarda datos de salud en logs ni eventos.
 */
@Repository
public class Recetas {

    public record Receta(
            Long id,
            Long medicoId,
            Long pacienteId,
            String medicamentos,
            String laboratorios,
            String diagnostico,
            String indicaciones,
            String pdfUrl,
            String qbi2IdReceta,
            String qbi2Verificador,
            String qbi2NroCuir,
            String qbi2FechaVencimiento,
            String qbi2IdTransaccion,
            LocalDateTime fechaEmision
    ) {}

    private static final String COLUMNAS = "id, medico_id, paciente_id, medicamentos, laboratorios, diagnostico, "
            + "indicaciones, pdf_url, qbi2_id_receta, qbi2_verificador, qbi2_nro_cuir, qbi2_fecha_vencimiento, "
            + "qbi2_id_transaccion, fecha_emision";

    private final JdbcTemplate jdbc;
    private final RowMapper<Receta> mapeo = (rs, i) -> new Receta(
            rs.getLong("id"),
            rs.getLong("medico_id"),
            rs.getLong("paciente_id"),
            rs.getString("medicamentos"),
            rs.getString("laboratorios"),
            rs.getString("diagnostico"),
            rs.getString("indicaciones"),
            rs.getString("pdf_url"),
            rs.getString("qbi2_id_receta"),
            rs.getString("qbi2_verificador"),
            rs.getString("qbi2_nro_cuir"),
            rs.getString("qbi2_fecha_vencimiento"),
            rs.getString("qbi2_id_transaccion"),
            rs.getObject("fecha_emision", LocalDateTime.class)
    );

    public Recetas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Receta> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM receta WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public List<Receta> porMedico(Long medicoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM receta WHERE medico_id = ? ORDER BY fecha_emision DESC, id DESC",
                mapeo, medicoId);
    }

    public List<Receta> porPaciente(Long pacienteId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM receta WHERE paciente_id = ? ORDER BY fecha_emision DESC, id DESC",
                mapeo, pacienteId);
    }

    /** Carga inicial: inserta con el id del monolito. */
    @Transactional
    public void insertar(Receta r) {
        jdbc.update("INSERT INTO receta (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.medicoId(), r.pacienteId(), r.medicamentos(), r.laboratorios(), r.diagnostico(),
                r.indicaciones(), r.pdfUrl(), r.qbi2IdReceta(), r.qbi2Verificador(), r.qbi2NroCuir(),
                r.qbi2FechaVencimiento(), r.qbi2IdTransaccion(),
                r.fechaEmision() != null ? Timestamp.valueOf(r.fechaEmision()) : null);
    }

    /** Receta nueva: toma el id de la secuencia propia. */
    @Transactional
    public Long insertarNuevo(Receta r) {
        Long id = jdbc.queryForObject("SELECT nextval('receta_id_seq')", Long.class);
        jdbc.update("INSERT INTO receta (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, r.medicoId(), r.pacienteId(), r.medicamentos(), r.laboratorios(), r.diagnostico(),
                r.indicaciones(), r.pdfUrl(), r.qbi2IdReceta(), r.qbi2Verificador(), r.qbi2NroCuir(),
                r.qbi2FechaVencimiento(), r.qbi2IdTransaccion(),
                r.fechaEmision() != null ? Timestamp.valueOf(r.fechaEmision()) : null);
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM receta", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('receta_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM receta), 1))",
                Long.class);
    }
}
