package com.tranqui.pagos.datos;

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
 * Pagos de suscripciones (Fase 8, S2a).
 * Réplica de SubscriptionPaymentRepository del monolito.
 */
@Repository
public class PagosSuscripcion {

    public record PagoSuscripcion(
            Long id,
            Long subscriptionId,
            Long professionalId,
            String mpPaymentId,
            String idempotencyKey,
            String method,
            String status,
            BigDecimal amountArs,
            LocalDateTime paidAt,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            Long invoiceId,
            Long registeredByAdminId,
            String receiptReference,
            String notes,
            LocalDateTime createdAt
    ) {}

    private static final String COLUMNAS = "id, subscription_id, professional_id, mp_payment_id, idempotency_key, "
            + "method, status, amount_ars, paid_at, period_start, period_end, invoice_id, registered_by_admin_id, "
            + "receipt_reference, notes, created_at";

    private final JdbcTemplate jdbc;

    private final RowMapper<PagoSuscripcion> mapeo = (rs, i) -> new PagoSuscripcion(
            rs.getLong("id"),
            rs.getLong("subscription_id"),
            rs.getLong("professional_id"),
            rs.getString("mp_payment_id"),
            rs.getString("idempotency_key"),
            rs.getString("method"),
            rs.getString("status"),
            rs.getBigDecimal("amount_ars"),
            rs.getObject("paid_at", LocalDateTime.class),
            rs.getObject("period_start", LocalDateTime.class),
            rs.getObject("period_end", LocalDateTime.class),
            rs.getObject("invoice_id") != null ? rs.getLong("invoice_id") : null,
            rs.getObject("registered_by_admin_id") != null ? rs.getLong("registered_by_admin_id") : null,
            rs.getString("receipt_reference"),
            rs.getString("notes"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public PagosSuscripcion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PagoSuscripcion> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_payments WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public Optional<PagoSuscripcion> porClaveIdempotencia(String idempotencyKey) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_payments WHERE idempotency_key = ?",
                mapeo, idempotencyKey).stream().findFirst();
    }

    public Optional<PagoSuscripcion> porMpPaymentId(String mpPaymentId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_payments WHERE mp_payment_id = ?",
                mapeo, mpPaymentId).stream().findFirst();
    }

    public List<PagoSuscripcion> porSuscripcionId(Long subscriptionId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_payments WHERE subscription_id = ? ORDER BY paid_at DESC, id DESC",
                mapeo, subscriptionId);
    }

    public List<PagoSuscripcion> porProfesionalId(Long professionalId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_payments WHERE professional_id = ? ORDER BY paid_at DESC, id DESC",
                mapeo, professionalId);
    }

    public List<PagoSuscripcion> aprobadosSinFacturaAnterioresA(LocalDateTime limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscription_payments WHERE status = 'APPROVED' "
                + "AND invoice_id IS NULL AND method != 'COURTESY' AND paid_at < ? ORDER BY paid_at",
                mapeo, Timestamp.valueOf(limite));
    }

    public BigDecimal sumaAprobadosDesde(LocalDateTime desde) {
        BigDecimal sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_ars), 0) FROM subscription_payments WHERE status = 'APPROVED' AND paid_at >= ?",
                BigDecimal.class, Timestamp.valueOf(desde));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Transactional
    public void actualizarFacturaId(Long pagoId, Long invoiceId) {
        jdbc.update("UPDATE subscription_payments SET invoice_id = ? WHERE id = ?", invoiceId, pagoId);
    }

    @Transactional
    public void actualizarEstado(Long pagoId, String status) {
        jdbc.update("UPDATE subscription_payments SET status = ? WHERE id = ?", status, pagoId);
    }

    @Transactional
    public void insertar(PagoSuscripcion p) {
        jdbc.update("INSERT INTO subscription_payments (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.id(), p.subscriptionId(), p.professionalId(), p.mpPaymentId(), p.idempotencyKey(),
                p.method(), p.status(), p.amountArs(),
                p.paidAt() != null ? Timestamp.valueOf(p.paidAt()) : Timestamp.valueOf(LocalDateTime.now()),
                p.periodStart() != null ? Timestamp.valueOf(p.periodStart()) : null,
                p.periodEnd() != null ? Timestamp.valueOf(p.periodEnd()) : null,
                p.invoiceId(), p.registeredByAdminId(), p.receiptReference(), p.notes(),
                p.createdAt() != null ? Timestamp.valueOf(p.createdAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    @Transactional
    public Long insertarNuevo(PagoSuscripcion p) {
        Long id = jdbc.queryForObject("SELECT nextval('subscription_payments_id_seq')", Long.class);
        insertar(new PagoSuscripcion(id, p.subscriptionId(), p.professionalId(), p.mpPaymentId(), p.idempotencyKey(),
                p.method(), p.status(), p.amountArs(), p.paidAt(), p.periodStart(), p.periodEnd(),
                p.invoiceId(), p.registeredByAdminId(), p.receiptReference(), p.notes(), p.createdAt()));
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM subscription_payments", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('subscription_payments_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM subscription_payments), 1))",
                Long.class);
    }
}
