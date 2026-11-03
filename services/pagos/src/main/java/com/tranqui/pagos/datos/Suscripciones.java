package com.tranqui.pagos.datos;

import org.springframework.dao.OptimisticLockingFailureException;
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
 * Suscripciones de los profesionales a los planes de la plataforma (Fase 8, S2a).
 * Réplica de SubscriptionRepository del monolito con control de versión optimista.
 */
@Repository
public class Suscripciones {

    public record Suscripcion(
            Long id,
            Integer version,
            Long professionalId,
            Long planId,
            String status,
            Integer seats,
            String billingSource,
            String mpPreapprovalId,
            BigDecimal amountArs,
            String billingCycle,
            LocalDateTime nextBillingDate,
            LocalDateTime currentPeriodStart,
            LocalDateTime currentPeriodEnd,
            LocalDateTime graceUntil,
            Boolean cancelAtPeriodEnd,
            LocalDateTime cancelledAt,
            Boolean renewalReminderSent,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public int aggregateVersion() {
            return (version != null ? version : 0) + 1;
        }
    }

    private static final String COLUMNAS = "id, version, professional_id, plan_id, status, seats, billing_source, "
            + "mp_preapproval_id, amount_ars, billing_cycle, next_billing_date, current_period_start, "
            + "current_period_end, grace_until, cancel_at_period_end, cancelled_at, renewal_reminder_sent, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbc;

    private final RowMapper<Suscripcion> mapeo = (rs, i) -> new Suscripcion(
            rs.getLong("id"),
            rs.getInt("version"),
            rs.getLong("professional_id"),
            rs.getLong("plan_id"),
            rs.getString("status"),
            rs.getInt("seats"),
            rs.getString("billing_source"),
            rs.getString("mp_preapproval_id"),
            rs.getBigDecimal("amount_ars"),
            rs.getString("billing_cycle"),
            rs.getObject("next_billing_date", LocalDateTime.class),
            rs.getObject("current_period_start", LocalDateTime.class),
            rs.getObject("current_period_end", LocalDateTime.class),
            rs.getObject("grace_until", LocalDateTime.class),
            rs.getBoolean("cancel_at_period_end"),
            rs.getObject("cancelled_at", LocalDateTime.class),
            rs.getBoolean("renewal_reminder_sent"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    public Suscripciones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Suscripcion> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE id = ?", mapeo, id)
                .stream().findFirst();
    }

    public Optional<Suscripcion> porProfesionalId(Long professionalId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE professional_id = ?", mapeo, professionalId)
                .stream().findFirst();
    }

    public Optional<Suscripcion> porMpPreapprovalId(String mpPreapprovalId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE mp_preapproval_id = ?", mapeo, mpPreapprovalId)
                .stream().findFirst();
    }

    public List<Suscripcion> porEstado(String status) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE status = ? ORDER BY id", mapeo, status);
    }

    public List<Suscripcion> todasPorProfesionalIdOrdenadas(Long professionalId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE professional_id = ? ORDER BY created_at DESC",
                mapeo, professionalId);
    }

    public List<Suscripcion> activasVencidasConGracia(LocalDateTime now) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE status = 'ACTIVE' "
                + "AND next_billing_date IS NOT NULL AND grace_until IS NOT NULL AND grace_until < ?",
                mapeo, Timestamp.valueOf(now));
    }

    public List<Suscripcion> manualesPorVencer(LocalDateTime now, LocalDateTime sieteDias) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE status = 'ACTIVE' "
                + "AND (billing_source = 'MANUAL_CASH' OR billing_source = 'MANUAL_TRANSFER') "
                + "AND current_period_end IS NOT NULL AND current_period_end >= ? AND current_period_end <= ?",
                mapeo, Timestamp.valueOf(now), Timestamp.valueOf(sieteDias));
    }

    public long contarActivasPorCodigoPlan(String planCode) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM subscriptions s JOIN plans p ON s.plan_id = p.id WHERE s.status = 'ACTIVE' AND p.code = ?",
                Long.class, planCode);
        return count != null ? count : 0;
    }

    public List<Suscripcion> proximasRenovacionesSinAviso(LocalDateTime now, LocalDateTime tresDias) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE status = 'ACTIVE' "
                + "AND cancel_at_period_end = false "
                + "AND (renewal_reminder_sent IS NULL OR renewal_reminder_sent = false) "
                + "AND COALESCE(next_billing_date, current_period_end) IS NOT NULL "
                + "AND COALESCE(next_billing_date, current_period_end) >= ? "
                + "AND COALESCE(next_billing_date, current_period_end) <= ?",
                mapeo, Timestamp.valueOf(now), Timestamp.valueOf(tresDias));
    }

    public List<Suscripcion> canceladasVencidas(LocalDateTime now) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions WHERE cancel_at_period_end = true "
                + "AND status != 'CANCELLED' "
                + "AND current_period_end IS NOT NULL AND current_period_end < ?",
                mapeo, Timestamp.valueOf(now));
    }

    public List<Suscripcion> todas() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM subscriptions ORDER BY id", mapeo);
    }

    /** Carga inicial: inserta con el id y versión explícitos del monolito. */
    @Transactional
    public void insertar(Suscripcion s) {
        jdbc.update("INSERT INTO subscriptions (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.id(),
                s.version() != null ? s.version() : 0,
                s.professionalId(),
                s.planId(),
                s.status(),
                s.seats() != null ? s.seats() : 1,
                s.billingSource(),
                s.mpPreapprovalId(),
                s.amountArs(),
                s.billingCycle() != null ? s.billingCycle() : "monthly",
                s.nextBillingDate() != null ? Timestamp.valueOf(s.nextBillingDate()) : null,
                s.currentPeriodStart() != null ? Timestamp.valueOf(s.currentPeriodStart()) : null,
                s.currentPeriodEnd() != null ? Timestamp.valueOf(s.currentPeriodEnd()) : null,
                s.graceUntil() != null ? Timestamp.valueOf(s.graceUntil()) : null,
                s.cancelAtPeriodEnd() != null ? s.cancelAtPeriodEnd() : false,
                s.cancelledAt() != null ? Timestamp.valueOf(s.cancelledAt()) : null,
                s.renewalReminderSent() != null ? s.renewalReminderSent() : false,
                s.createdAt() != null ? Timestamp.valueOf(s.createdAt()) : Timestamp.valueOf(LocalDateTime.now()),
                s.updatedAt() != null ? Timestamp.valueOf(s.updatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    @Transactional
    public Long insertarNuevo(Suscripcion s) {
        Long id = jdbc.queryForObject("SELECT nextval('subscriptions_id_seq')", Long.class);
        insertar(new Suscripcion(id, s.version() != null ? s.version() : 0, s.professionalId(), s.planId(),
                s.status(), s.seats(), s.billingSource(), s.mpPreapprovalId(), s.amountArs(), s.billingCycle(),
                s.nextBillingDate(), s.currentPeriodStart(), s.currentPeriodEnd(), s.graceUntil(),
                s.cancelAtPeriodEnd(), s.cancelledAt(), s.renewalReminderSent(), s.createdAt(), s.updatedAt()));
        return id;
    }

    /**
     * Actualiza la suscripción aplicando control de concurrencia optimista sobre la versión.
     * Incrementa la versión en 1 y actualiza updated_at.
     */
    @Transactional
    public void actualizar(Suscripcion s) {
        int filas = jdbc.update("""
                UPDATE subscriptions SET
                    version = version + 1,
                    professional_id = ?,
                    plan_id = ?,
                    status = ?,
                    seats = ?,
                    billing_source = ?,
                    mp_preapproval_id = ?,
                    amount_ars = ?,
                    billing_cycle = ?,
                    next_billing_date = ?,
                    current_period_start = ?,
                    current_period_end = ?,
                    grace_until = ?,
                    cancel_at_period_end = ?,
                    cancelled_at = ?,
                    renewal_reminder_sent = ?,
                    updated_at = ?
                WHERE id = ? AND version = ?
                """,
                s.professionalId(),
                s.planId(),
                s.status(),
                s.seats() != null ? s.seats() : 1,
                s.billingSource(),
                s.mpPreapprovalId(),
                s.amountArs(),
                s.billingCycle() != null ? s.billingCycle() : "monthly",
                s.nextBillingDate() != null ? Timestamp.valueOf(s.nextBillingDate()) : null,
                s.currentPeriodStart() != null ? Timestamp.valueOf(s.currentPeriodStart()) : null,
                s.currentPeriodEnd() != null ? Timestamp.valueOf(s.currentPeriodEnd()) : null,
                s.graceUntil() != null ? Timestamp.valueOf(s.graceUntil()) : null,
                s.cancelAtPeriodEnd() != null ? s.cancelAtPeriodEnd() : false,
                s.cancelledAt() != null ? Timestamp.valueOf(s.cancelledAt()) : null,
                s.renewalReminderSent() != null ? s.renewalReminderSent() : false,
                Timestamp.valueOf(LocalDateTime.now()),
                s.id(),
                s.version());

        if (filas == 0) {
            throw new OptimisticLockingFailureException(
                    "Conflicto de versión optimista al actualizar la suscripción " + s.id() + " con versión " + s.version());
        }
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM subscriptions", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('subscriptions_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM subscriptions), 1))",
                Long.class);
    }
}
