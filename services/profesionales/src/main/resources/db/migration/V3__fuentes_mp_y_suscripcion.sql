-- Fase 9: con la fuente de identidad, Mercado Pago y la suscripción de cada profesional llegan de pagos (su dueño),
-- por eventos y por una resincronización, en cualquier orden. Cada dato guarda la versión con la que se escribió
-- para no pisar uno nuevo con uno viejo.
ALTER TABLE profesional ADD COLUMN mp_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE profesional ADD COLUMN suscripcion_version BIGINT NOT NULL DEFAULT 0;
