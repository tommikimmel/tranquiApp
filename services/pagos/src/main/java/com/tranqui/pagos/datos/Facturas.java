package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Facturas y comprobantes fiscales ARCA (Fase 8, S2a).
 * Réplica de InvoiceRepository del monolito.
 */
@Repository
public class Facturas {

    public record Factura(
            Long id,
            Long paymentId,
            Integer cbteTipo,
            Integer puntoVenta,
            Long cbteNumero,
            String cae,
            LocalDate caeVencimiento,
            Integer receptorDocTipo,
            Long receptorDocNro,
            String receptorNombre,
            Integer receptorCondicionIva,
            BigDecimal importeTotal,
            String moneda,
            BigDecimal cotizacion,
            LocalDate fechaEmision,
            String pdfUrl,
            String pdfBase64,
            String status,
            Integer attempts,
            String lastError,
            String arcaRequestJson,
            String arcaResponseJson,
            Long comprobanteAsociadoId,
            LocalDateTime createdAt,
            LocalDateTime issuedAt
    ) {}

    private static final String COLUMNAS = "id, payment_id, cbte_tipo, punto_venta, cbte_numero, cae, "
            + "cae_vencimiento, receptor_doc_tipo, receptor_doc_nro, receptor_nombre, receptor_condicion_iva, "
            + "importe_total, moneda, cotizacion, fecha_emision, pdf_url, pdf_base64, status, attempts, "
            + "last_error, arca_request_json, arca_response_json, comprobante_asociado_id, created_at, issued_at";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    private final RowMapper<Factura> mapeo = (rs, i) -> new Factura(
            rs.getLong("id"),
            rs.getObject("payment_id") != null ? rs.getLong("payment_id") : null,
            rs.getInt("cbte_tipo"),
            rs.getInt("punto_venta"),
            rs.getObject("cbte_numero") != null ? rs.getLong("cbte_numero") : null,
            rs.getString("cae"),
            rs.getObject("cae_vencimiento", LocalDate.class),
            rs.getInt("receptor_doc_tipo"),
            rs.getLong("receptor_doc_nro"),
            rs.getString("receptor_nombre"),
            rs.getInt("receptor_condicion_iva"),
            rs.getBigDecimal("importe_total"),
            rs.getString("moneda"),
            rs.getBigDecimal("cotizacion"),
            rs.getObject("fecha_emision", LocalDate.class),
            rs.getString("pdf_url"),
            rs.getString("pdf_base64"),
            rs.getString("status"),
            rs.getInt("attempts"),
            rs.getString("last_error"),
            rs.getString("arca_request_json"),
            rs.getString("arca_response_json"),
            rs.getObject("comprobante_asociado_id") != null ? rs.getLong("comprobante_asociado_id") : null,
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("issued_at", LocalDateTime.class)
    );

    public Facturas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<Factura> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoices WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public Optional<Factura> porPagoId(Long paymentId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoices WHERE payment_id = ?", mapeo, paymentId)
                .stream().findFirst();
    }

    public Optional<Factura> porPuntoVentaTipoYNumero(Integer puntoVenta, Integer cbteTipo, Long cbteNumero) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoices WHERE punto_venta = ? AND cbte_tipo = ? AND cbte_numero = ?",
                mapeo, puntoVenta, cbteTipo, cbteNumero).stream().findFirst();
    }

    public List<Factura> porEstado(String status) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoices WHERE status = ? ORDER BY id", mapeo, status);
    }

    public List<Factura> fallidas() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoices WHERE status = 'FAILED' ORDER BY created_at DESC", mapeo);
    }

    public List<Factura> todasOrdenadas() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM invoices ORDER BY created_at DESC", mapeo);
    }

    public List<Factura> porPagoIds(List<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", paymentIds);
        return namedJdbc.query("SELECT " + COLUMNAS + " FROM invoices WHERE payment_id IN (:ids) ORDER BY created_at DESC",
                params, mapeo);
    }

    @Transactional
    public void actualizarResultadoCae(Long id, String cae, LocalDate caeVencimiento, Long cbteNumero,
                                      String pdfUrl, String pdfBase64, String status, String lastError,
                                      String arcaRequestJson, String arcaResponseJson, LocalDateTime issuedAt,
                                      int attempts) {
        jdbc.update("""
                UPDATE invoices SET
                    cae = ?, cae_vencimiento = ?, cbte_numero = ?, pdf_url = ?, pdf_base64 = ?,
                    status = ?, last_error = ?, arca_request_json = ?, arca_response_json = ?,
                    issued_at = ?, attempts = ?
                WHERE id = ?
                """,
                cae,
                caeVencimiento != null ? Date.valueOf(caeVencimiento) : null,
                cbteNumero,
                pdfUrl,
                pdfBase64,
                status,
                lastError,
                arcaRequestJson,
                arcaResponseJson,
                issuedAt != null ? Timestamp.valueOf(issuedAt) : null,
                attempts,
                id);
    }

    @Transactional
    public void insertar(Factura f) {
        jdbc.update("INSERT INTO invoices (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                f.id(), f.paymentId(),
                f.cbteTipo() != null ? f.cbteTipo() : 11,
                f.puntoVenta(), f.cbteNumero(), f.cae(),
                f.caeVencimiento() != null ? Date.valueOf(f.caeVencimiento()) : null,
                f.receptorDocTipo(), f.receptorDocNro(), f.receptorNombre(), f.receptorCondicionIva(),
                f.importeTotal(),
                f.moneda() != null ? f.moneda() : "PES",
                f.cotizacion() != null ? f.cotizacion() : BigDecimal.ONE,
                f.fechaEmision() != null ? Date.valueOf(f.fechaEmision()) : Date.valueOf(LocalDate.now()),
                f.pdfUrl(), f.pdfBase64(),
                f.status(),
                f.attempts() != null ? f.attempts() : 0,
                f.lastError(), f.arcaRequestJson(), f.arcaResponseJson(), f.comprobanteAsociadoId(),
                f.createdAt() != null ? Timestamp.valueOf(f.createdAt()) : Timestamp.valueOf(LocalDateTime.now()),
                f.issuedAt() != null ? Timestamp.valueOf(f.issuedAt()) : null);
    }

    @Transactional
    public Long insertarNuevo(Factura f) {
        Long id = jdbc.queryForObject("SELECT nextval('invoices_id_seq')", Long.class);
        insertar(new Factura(id, f.paymentId(), f.cbteTipo(), f.puntoVenta(), f.cbteNumero(), f.cae(),
                f.caeVencimiento(), f.receptorDocTipo(), f.receptorDocNro(), f.receptorNombre(),
                f.receptorCondicionIva(), f.importeTotal(), f.moneda(), f.cotizacion(), f.fechaEmision(),
                f.pdfUrl(), f.pdfBase64(), f.status(), f.attempts(), f.lastError(), f.arcaRequestJson(),
                f.arcaResponseJson(), f.comprobanteAsociadoId(), f.createdAt(), f.issuedAt()));
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM invoices", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('invoices_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM invoices), 1))",
                Long.class);
    }
}
