-- Perfil profesional recibido durante el registro (Fase 9, I2b).
-- No pertenece al servicio de identidad sino al de profesionales; se preserva acá
-- como jsonb en el formato de PerfilProfesionalData (libs/eventos) para que profesionales
-- lo tome cuando sincronice o consuma el alta.
CREATE TABLE perfil_registro (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    perfil JSONB NOT NULL
);
