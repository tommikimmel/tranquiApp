-- Fase 9 (I3c): versión persistida y estado vinculada para resincronización de cuentas de Mercado Pago.
ALTER TABLE cuenta_mp ADD COLUMN IF NOT EXISTS vinculada BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE cuenta_mp ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE cuenta_mp ALTER COLUMN access_token_cifrado DROP NOT NULL;
