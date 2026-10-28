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
 * Acceso JDBC a la tabla {@code novedades_envio} (Fase 9, I2a), replicando
 * {@code NovedadesEnvioRepository}.
 */
@Repository
public class NovedadesEnvios {

    public static final String COLUMNAS = "id, titulo, estado, detalle, creado_en, finalizado_en, enviado_pacientes, enviado_profesionales";

    private final JdbcTemplate jdbc;

    private final RowMapper<NovedadesEnvio> mapeo = (rs, rowNum) -> new NovedadesEnvio(
            rs.getString("id"),
            rs.getString("titulo"),
            rs.getString("estado"),
            rs.getString("detalle"),
            rs.getTimestamp("creado_en").toLocalDateTime(),
            rs.getTimestamp("finalizado_en") != null ? rs.getTimestamp("finalizado_en").toLocalDateTime() : null,
            rs.getBoolean("enviado_pacientes"),
            rs.getBoolean("enviado_profesionales")
    );

    public NovedadesEnvios(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<NovedadesEnvio> buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        List<NovedadesEnvio> r = jdbc.query("SELECT " + COLUMNAS + " FROM novedades_envio WHERE id = ?", mapeo, id);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public List<NovedadesEnvio> todos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM novedades_envio ORDER BY creado_en DESC", mapeo);
    }

    @Transactional
    public void insertar(NovedadesEnvio n) {
        jdbc.update("""
                INSERT INTO novedades_envio (
                    id, titulo, estado, detalle, creado_en, finalizado_en, enviado_pacientes, enviado_profesionales
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                n.id(),
                n.titulo(),
                n.estado(),
                n.detalle(),
                n.creadoEn() != null ? Timestamp.valueOf(n.creadoEn()) : Timestamp.valueOf(LocalDateTime.now()),
                n.finalizadoEn() != null ? Timestamp.valueOf(n.finalizadoEn()) : null,
                n.enviadoPacientes(),
                n.enviadoProfesionales()
        );
    }

    @Transactional
    public void actualizar(NovedadesEnvio n) {
        jdbc.update("""
                UPDATE novedades_envio SET
                    titulo = ?,
                    estado = ?,
                    detalle = ?,
                    creado_en = ?,
                    finalizado_en = ?,
                    enviado_pacientes = ?,
                    enviado_profesionales = ?
                WHERE id = ?
                """,
                n.titulo(),
                n.estado(),
                n.detalle(),
                n.creadoEn() != null ? Timestamp.valueOf(n.creadoEn()) : Timestamp.valueOf(LocalDateTime.now()),
                n.finalizadoEn() != null ? Timestamp.valueOf(n.finalizadoEn()) : null,
                n.enviadoPacientes(),
                n.enviadoProfesionales(),
                n.id()
        );
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM novedades_envio", Long.class);
        return n != null ? n : 0;
    }
}
