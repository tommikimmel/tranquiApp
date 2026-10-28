-- V6 (Fase 8): copia local del estado y acceso de la suscripción de cada profesional,
-- para que SubscriptionAccessFilter, el portón del gateway y el servicio de profesionales
-- sigan consultando el monolito sin cambios mientras las suscripciones viven en el servicio de pagos.
CREATE TABLE IF NOT EXISTS suscripcion_acceso (
    professional_id BIGINT PRIMARY KEY,
    estado VARCHAR(50),
    plan_code VARCHAR(50),
    acceso BOOLEAN NOT NULL DEFAULT FALSE,
    puede_recetar BOOLEAN NOT NULL DEFAULT FALSE,
    periodo_hasta TIMESTAMP,
    gracia_hasta TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
