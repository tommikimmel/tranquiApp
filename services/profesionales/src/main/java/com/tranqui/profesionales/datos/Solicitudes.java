package com.tranqui.profesionales.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Solicitudes de documentos pagos (conceptos).
 */
@Repository
public class Solicitudes {

    public record Solicitud(
            Long id,
            Long pacienteId,
            Long medicoId,
            String tipoConcepto,
            BigDecimal precio,
            String estado,
            String transactionId,
            boolean emitido,
            String urlDescarga,
            LocalDateTime fechaEmision,
            LocalDateTime fechaCreacion
    ) {}

    private static final String COLUMNAS = "id, paciente_id, medico_id, tipo_concepto, precio, estado, "
            + "transaction_id, emitido, url_descarga, fecha_emision, fecha_creacion";

    private final JdbcTemplate jdbc;
    private final RowMapper<Solicitud> mapeo = (rs, i) -> new Solicitud(
            rs.getLong("id"),
            rs.getLong("paciente_id"),
            rs.getLong("medico_id"),
            rs.getString("tipo_concepto"),
            rs.getBigDecimal("precio"),
            rs.getString("estado"),
            rs.getString("transaction_id"),
            rs.getBoolean("emitido"),
            rs.getString("url_descarga"),
            rs.getObject("fecha_emision", LocalDateTime.class),
            rs.getObject("fecha_creacion", LocalDateTime.class)
    );

    public Solicitudes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Solicitud> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM solicitud_documento WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public List<Solicitud> porMedicoYEstado(Long medicoId, String estado) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM solicitud_documento WHERE medico_id = ? AND estado = ? ORDER BY fecha_creacion DESC, id DESC",
                mapeo, medicoId, estado);
    }

    public List<Solicitud> porMedicoYEstadoYEmitido(Long medicoId, String estado, boolean emitido) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM solicitud_documento WHERE medico_id = ? AND estado = ? AND emitido = ? ORDER BY fecha_creacion DESC, id DESC",
                mapeo, medicoId, estado, emitido);
    }

    /** Carga inicial: inserta con el id del monolito. */
    @Transactional
    public void insertar(Solicitud s) {
        jdbc.update("INSERT INTO solicitud_documento (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.id(), s.pacienteId(), s.medicoId(), s.tipoConcepto(), s.precio(), s.estado(),
                s.transactionId(), s.emitido(), s.urlDescarga(),
                s.fechaEmision() != null ? Timestamp.valueOf(s.fechaEmision()) : null,
                s.fechaCreacion() != null ? Timestamp.valueOf(s.fechaCreacion()) : null);
    }

    /** Solicitud nueva: toma el id de la secuencia propia. */
    @Transactional
    public Long insertarNuevo(Solicitud s) {
        Long id = jdbc.queryForObject("SELECT nextval('solicitud_documento_id_seq')", Long.class);
        jdbc.update("INSERT INTO solicitud_documento (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, s.pacienteId(), s.medicoId(), s.tipoConcepto(), s.precio(), s.estado(),
                s.transactionId(), s.emitido(), s.urlDescarga(),
                s.fechaEmision() != null ? Timestamp.valueOf(s.fechaEmision()) : null,
                s.fechaCreacion() != null ? Timestamp.valueOf(s.fechaCreacion()) : null);
        return id;
    }

    @Transactional
    public void actualizarEstado(Long id, String estado) {
        jdbc.update("UPDATE solicitud_documento SET estado = ? WHERE id = ?", estado, id);
    }

    @Transactional
    public void actualizarEmitido(Long id, boolean emitido, String urlDescarga, LocalDateTime fechaEmision) {
        jdbc.update("UPDATE solicitud_documento SET emitido = ?, url_descarga = ?, fecha_emision = ? WHERE id = ?",
                emitido, urlDescarga, fechaEmision != null ? Timestamp.valueOf(fechaEmision) : null, id);
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM solicitud_documento", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('solicitud_documento_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM solicitud_documento), 1))",
                Long.class);
    }
}
