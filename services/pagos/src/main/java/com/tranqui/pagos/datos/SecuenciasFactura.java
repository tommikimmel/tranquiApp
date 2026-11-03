package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Control de correlatividad y numeración de facturas (Fase 8, S2a).
 * Réplica de InvoiceSequenceRepository del monolito con bloqueo pesimista (FOR UPDATE).
 */
@Repository
public class SecuenciasFactura {

    public record Secuencia(
            Long id,
            Integer puntoVenta,
            Integer cbteTipo,
            Long lastNumber,
            LocalDateTime updatedAt
    ) {}

    private static final String COLUMNAS = "id, punto_venta, cbte_tipo, last_number, updated_at";

    private final JdbcTemplate jdbc;

    private final RowMapper<Secuencia> mapeo = (rs, i) -> new Secuencia(
            rs.getLong("id"),
            rs.getInt("punto_venta"),
            rs.getInt("cbte_tipo"),
            rs.getLong("last_number"),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    public SecuenciasFactura(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Secuencia> porPuntoVentaYTipo(Integer puntoVenta, Integer cbteTipo) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoice_sequences WHERE punto_venta = ? AND cbte_tipo = ?",
                mapeo, puntoVenta, cbteTipo).stream().findFirst();
    }

    /**
     * Consulta con bloqueo pesimista (SELECT ... FOR UPDATE) para evitar saltos o duplicados de numeración.
     */
    @Transactional
    public Optional<Secuencia> porPuntoVentaYTipoParaActualizar(Integer puntoVenta, Integer cbteTipo) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoice_sequences WHERE punto_venta = ? AND cbte_tipo = ? FOR UPDATE",
                mapeo, puntoVenta, cbteTipo).stream().findFirst();
    }

    /**
     * Obtiene el siguiente número correlativo de factura mediante lock pesimista FOR UPDATE.
     */
    @Transactional
    public long proximoNumero(Integer puntoVenta, Integer cbteTipo) {
        jdbc.update("""
                INSERT INTO invoice_sequences (id, punto_venta, cbte_tipo, last_number, updated_at)
                VALUES (nextval('invoice_sequences_id_seq'), ?, ?, 0, now())
                ON CONFLICT (punto_venta, cbte_tipo) DO NOTHING
                """, puntoVenta, cbteTipo);

        Secuencia seq = jdbc.query(
                "SELECT " + COLUMNAS + " FROM invoice_sequences WHERE punto_venta = ? AND cbte_tipo = ? FOR UPDATE",
                mapeo, puntoVenta, cbteTipo
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("Secuencia no encontrada para punto de venta " + puntoVenta + " y comprobante " + cbteTipo));

        long siguiente = seq.lastNumber() + 1;
        jdbc.update("UPDATE invoice_sequences SET last_number = ?, updated_at = now() WHERE id = ?", siguiente, seq.id());
        return siguiente;
    }

    @Transactional
    public void actualizarUltimoNumero(Integer puntoVenta, Integer cbteTipo, Long lastNumber) {
        jdbc.update("UPDATE invoice_sequences SET last_number = ?, updated_at = now() WHERE punto_venta = ? AND cbte_tipo = ?",
                lastNumber, puntoVenta, cbteTipo);
    }

    @Transactional
    public void insertar(Secuencia s) {
        jdbc.update("INSERT INTO invoice_sequences (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?)",
                s.id(), s.puntoVenta(), s.cbteTipo(), s.lastNumber(),
                s.updatedAt() != null ? Timestamp.valueOf(s.updatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    @Transactional
    public Long insertarNuevo(Secuencia s) {
        Long id = jdbc.queryForObject("SELECT nextval('invoice_sequences_id_seq')", Long.class);
        insertar(new Secuencia(id, s.puntoVenta(), s.cbteTipo(), s.lastNumber(), s.updatedAt()));
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM invoice_sequences", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('invoice_sequences_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM invoice_sequences), 1))",
                Long.class);
    }
}
