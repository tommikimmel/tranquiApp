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
 * Planes de suscripción y sus features asociadas (Fase 8, S2a).
 * Réplica de PlanRepository, FeatureRepository y PlanFeatureRepository del monolito.
 */
@Repository
public class Planes {

    public record Plan(
            Long id,
            String code,
            String name,
            String description,
            BigDecimal priceArs,
            BigDecimal priceUsdRef,
            BigDecimal priceArsAnual,
            String billingPeriod,
            Integer minSeats,
            String mpPreapprovalPlanId,
            Boolean requiresPrescriber,
            Boolean isActive,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo
    ) {}

    public record Feature(
            String key,
            String name,
            String description
    ) {}

    public record PlanFeature(
            Long id,
            Long planId,
            String featureKey,
            Integer limitValue
    ) {}

    private static final String COLUMNAS_PLAN = "id, code, name, description, price_ars, price_usd_ref, "
            + "price_ars_anual, billing_period, min_seats, mp_preapproval_plan_id, requires_prescriber, "
            + "is_active, effective_from, effective_to";

    private static final String COLUMNAS_FEATURE = "feature_key, name, description";

    private static final String COLUMNAS_PLAN_FEATURE = "id, plan_id, feature_key, limit_value";

    private final JdbcTemplate jdbc;

    private final RowMapper<Plan> mapeoPlan = (rs, i) -> new Plan(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price_ars"),
            rs.getBigDecimal("price_usd_ref"),
            rs.getBigDecimal("price_ars_anual"),
            rs.getString("billing_period"),
            rs.getInt("min_seats"),
            rs.getString("mp_preapproval_plan_id"),
            rs.getBoolean("requires_prescriber"),
            rs.getBoolean("is_active"),
            rs.getObject("effective_from", LocalDateTime.class),
            rs.getObject("effective_to", LocalDateTime.class)
    );

    private final RowMapper<Feature> mapeoFeature = (rs, i) -> new Feature(
            rs.getString("feature_key"),
            rs.getString("name"),
            rs.getString("description")
    );

    private final RowMapper<PlanFeature> mapeoPlanFeature = (rs, i) -> new PlanFeature(
            rs.getLong("id"),
            rs.getLong("plan_id"),
            rs.getString("feature_key"),
            rs.getObject("limit_value") != null ? rs.getInt("limit_value") : null
    );

    public Planes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ Planes

    public Optional<Plan> porId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS_PLAN + " FROM plans WHERE id = ?", mapeoPlan, id)
                .stream().findFirst();
    }

    public Optional<Plan> porCodigo(String code) {
        return jdbc.query("SELECT " + COLUMNAS_PLAN + " FROM plans WHERE code = ?", mapeoPlan, code)
                .stream().findFirst();
    }

    public List<Plan> activos() {
        return jdbc.query("SELECT " + COLUMNAS_PLAN + " FROM plans WHERE is_active = true ORDER BY id", mapeoPlan);
    }

    public Optional<Plan> porMpPreapprovalPlanId(String mpPreapprovalPlanId) {
        return jdbc.query("SELECT " + COLUMNAS_PLAN + " FROM plans WHERE mp_preapproval_plan_id = ?",
                mapeoPlan, mpPreapprovalPlanId).stream().findFirst();
    }

    public List<Plan> todos() {
        return jdbc.query("SELECT " + COLUMNAS_PLAN + " FROM plans ORDER BY id", mapeoPlan);
    }

    @Transactional
    public void insertar(Plan p) {
        jdbc.update("INSERT INTO plans (" + COLUMNAS_PLAN + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.id(), p.code(), p.name(), p.description(), p.priceArs(), p.priceUsdRef(), p.priceArsAnual(),
                p.billingPeriod() != null ? p.billingPeriod() : "monthly",
                p.minSeats() != null ? p.minSeats() : 1,
                p.mpPreapprovalPlanId(),
                p.requiresPrescriber() != null ? p.requiresPrescriber() : false,
                p.isActive() != null ? p.isActive() : true,
                p.effectiveFrom() != null ? Timestamp.valueOf(p.effectiveFrom()) : null,
                p.effectiveTo() != null ? Timestamp.valueOf(p.effectiveTo()) : null);
    }

    @Transactional
    public Long insertarNuevo(Plan p) {
        Long id = jdbc.queryForObject("SELECT nextval('plans_id_seq')", Long.class);
        insertar(new Plan(id, p.code(), p.name(), p.description(), p.priceArs(), p.priceUsdRef(), p.priceArsAnual(),
                p.billingPeriod(), p.minSeats(), p.mpPreapprovalPlanId(), p.requiresPrescriber(), p.isActive(),
                p.effectiveFrom(), p.effectiveTo()));
        return id;
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM plans", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuencia() {
        jdbc.queryForObject("SELECT setval('plans_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM plans), 1))",
                Long.class);
    }

    // ------------------------------------------------------------------ Features

    public Optional<Feature> featurePorKey(String key) {
        return jdbc.query("SELECT " + COLUMNAS_FEATURE + " FROM features WHERE feature_key = ?", mapeoFeature, key)
                .stream().findFirst();
    }

    public List<Feature> todasLasFeatures() {
        return jdbc.query("SELECT " + COLUMNAS_FEATURE + " FROM features ORDER BY feature_key", mapeoFeature);
    }

    @Transactional
    public void insertarFeature(Feature f) {
        jdbc.update("INSERT INTO features (" + COLUMNAS_FEATURE + ") VALUES (?, ?, ?) "
                + "ON CONFLICT (feature_key) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description",
                f.key(), f.name(), f.description());
    }

    public long cantidadFeatures() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM features", Long.class);
        return n != null ? n : 0;
    }

    // ------------------------------------------------------------------ PlanFeatures

    public List<PlanFeature> featuresPorPlanId(Long planId) {
        return jdbc.query("SELECT " + COLUMNAS_PLAN_FEATURE + " FROM plan_features WHERE plan_id = ? ORDER BY id",
                mapeoPlanFeature, planId);
    }

    public boolean existePorPlanIdYFeatureKey(Long planId, String featureKey) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM plan_features WHERE plan_id = ? AND feature_key = ?",
                Integer.class, planId, featureKey);
        return count != null && count > 0;
    }

    public List<PlanFeature> planFeaturesPorFeatureKey(String featureKey) {
        return jdbc.query("SELECT " + COLUMNAS_PLAN_FEATURE + " FROM plan_features WHERE feature_key = ? ORDER BY id",
                mapeoPlanFeature, featureKey);
    }

    @Transactional
    public void insertarPlanFeature(PlanFeature pf) {
        jdbc.update("INSERT INTO plan_features (" + COLUMNAS_PLAN_FEATURE + ") VALUES (?, ?, ?, ?)",
                pf.id(), pf.planId(), pf.featureKey(), pf.limitValue());
    }

    @Transactional
    public Long insertarNuevoPlanFeature(PlanFeature pf) {
        Long id = jdbc.queryForObject("SELECT nextval('plan_features_id_seq')", Long.class);
        insertarPlanFeature(new PlanFeature(id, pf.planId(), pf.featureKey(), pf.limitValue()));
        return id;
    }

    public long cantidadPlanFeatures() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM plan_features", Long.class);
        return n != null ? n : 0;
    }

    @Transactional
    public void ajustarSecuenciaPlanFeatures() {
        jdbc.queryForObject("SELECT setval('plan_features_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM plan_features), 1))",
                Long.class);
    }

    @Transactional
    public void ajustarSecuencias() {
        ajustarSecuencia();
        ajustarSecuenciaPlanFeatures();
    }
}
