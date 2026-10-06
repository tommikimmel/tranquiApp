-- Columnas que quedaron sin uso en el modelo y que `ddl-auto: update` nunca borra:
--  * subscriptions.aviso_vencimiento_enviado: flag de una versión anterior de los avisos de
--    vencimiento (reemplazado por renewal_reminder_sent).
--  * usuario.notificaciones_whatsapp_habilitadas y turno.recordatorio_enviado: notificaciones
--    por WhatsApp (Twilio), eliminadas el 06/10/2026. recordatorio_enviado es NOT NULL sin
--    default, así que mientras exista la columna cualquier INSERT de turno necesita el campo.
-- IF EXISTS en todo: en una base nueva estas tablas todavía no existen cuando corre Flyway
-- (las crea Hibernate después), y la migración no debe fallar.
ALTER TABLE IF EXISTS subscriptions DROP COLUMN IF EXISTS aviso_vencimiento_enviado;
ALTER TABLE IF EXISTS usuario DROP COLUMN IF EXISTS notificaciones_whatsapp_habilitadas;
ALTER TABLE IF EXISTS turno DROP COLUMN IF EXISTS recordatorio_enviado;
