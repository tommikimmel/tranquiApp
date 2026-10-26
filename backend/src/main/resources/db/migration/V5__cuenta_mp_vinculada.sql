-- V5 (Fase 4): con el servicio de pagos los tokens de Mercado Pago viven allá. El monolito se entera de
-- que un profesional vinculó o desvinculó su cuenta por eventos y lo guarda acá (buscador y reserva).
ALTER TABLE IF EXISTS usuario ADD COLUMN IF NOT EXISTS mp_vinculada BOOLEAN;
