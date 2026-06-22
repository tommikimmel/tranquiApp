-- Esquema de Base de Datos PostgreSQL - Tranqui App (MVP - V1)
-- Todas las tablas y campos usan snake_case.

-- 1. Extensiones (opcional, para uuid se usa uuid-ossp si es necesario, pero usaremos BIGSERIAL para IDs incrementales)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Limpieza de tablas previas (en orden para respetar claves foráneas)
DROP TABLE IF EXISTS mensaje CASCADE;
DROP TABLE IF EXISTS pago CASCADE;
DROP TABLE IF EXISTS solicitud_documento CASCADE;
DROP TABLE IF EXISTS turno CASCADE;
DROP TABLE IF EXISTS disponibilidad CASCADE;
DROP TABLE IF EXISTS usuario CASCADE;

-- 3. Tabla de Usuarios
-- Almacena Pacientes, Médicos (Psiquiatras) y Administradores
CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    rol VARCHAR(20) NOT NULL,
    
    -- Campos exclusivos para Psiquiatras
    matricula VARCHAR(50),
    mp_access_token_encrypted TEXT, -- Guardado bajo cifrado AES-256
    mp_user_id VARCHAR(50),
    
    -- Campos de contacto generales
    telefono VARCHAR(30),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Restricciones de Dominio para Roles
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('ADMIN', 'PSIQUIATRA', 'PACIENTE'))
);

-- Índices en Usuario para optimizar búsquedas por email (logins frecuentes)
CREATE INDEX idx_usuario_email ON usuario(email);

-- 4. Tabla de Disponibilidad Horaria Semanal
-- Rango de trabajo establecido por el psiquiatra
CREATE TABLE disponibilidad (
    id BIGSERIAL PRIMARY KEY,
    medico_id BIGINT NOT NULL,
    dia_semana INT NOT NULL, -- 1 = Lunes, 7 = Domingo (ISO-8601)
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    
    -- Restricciones
    CONSTRAINT fk_disponibilidad_medico FOREIGN KEY (medico_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT chk_dia_semana CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT chk_horas CHECK (hora_inicio < hora_fin)
);

CREATE INDEX idx_disponibilidad_medico ON disponibilidad(medico_id);

-- 5. Tabla de Turnos
-- Representa las citas reservadas en los bloques de 45 minutos
CREATE TABLE turno (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL,
    medico_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    precio DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    
    -- Datos clínicos / administrativos extra
    metadata_afiliado VARCHAR(100), -- Número de credencial si es OSDE
    telemedicina_url VARCHAR(500), -- URL de Google Meet autogenerada
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Restricciones
    CONSTRAINT fk_turno_paciente FOREIGN KEY (paciente_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_turno_medico FOREIGN KEY (medico_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT chk_turno_tipo CHECK (tipo IN ('PARTICULAR', 'OSDE')),
    CONSTRAINT chk_turno_estado CHECK (estado IN ('PENDIENTE_PAGO', 'PENDIENTE_VALIDACION', 'CONFIRMADO', 'CANCELADO')),
    CONSTRAINT chk_turno_horas CHECK (hora_inicio < hora_fin)
);

-- Índices en Turno para búsquedas rápidas de agenda y prevención de choques horarios
CREATE INDEX idx_turno_medico_fecha ON turno(medico_id, fecha);
CREATE INDEX idx_turno_paciente ON turno(paciente_id);
CREATE INDEX idx_turno_expiracion ON turno(estado, fecha_creacion);

-- 6. Tabla de Solicitud de Documentos
-- Para la solicitud de recetas y certificados fuera de la consulta médica tradicional
CREATE TABLE solicitud_documento (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL,
    medico_id BIGINT NOT NULL,
    tipo_concepto VARCHAR(30) NOT NULL,
    estado_pago VARCHAR(25) NOT NULL DEFAULT 'PENDIENTE',
    precio DECIMAL(12, 2) NOT NULL,
    archivo_url VARCHAR(500), -- URL del PDF firmado cargado por el médico
    fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Restricciones
    CONSTRAINT fk_solicitud_paciente FOREIGN KEY (paciente_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitud_medico FOREIGN KEY (medico_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT chk_solicitud_concepto CHECK (tipo_concepto IN ('CERTIFICADO', 'RECETA_CONTROL')),
    CONSTRAINT chk_solicitud_estado CHECK (estado_pago IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO'))
);

CREATE INDEX idx_solicitud_medico ON solicitud_documento(medico_id);

-- 7. Tabla de Pagos
-- Registra las transacciones liquidadas mediante la pasarela de Mercado Pago
CREATE TABLE pago (
    id BIGSERIAL PRIMARY KEY,
    turno_id BIGINT,                -- Relacionado a un turno (opcional)
    solicitud_documento_id BIGINT,  -- Relacionado a un documento (opcional)
    transaction_id VARCHAR(100) UNIQUE NOT NULL, -- ID devuelto por Mercado Pago
    estado VARCHAR(20) NOT NULL,
    monto DECIMAL(12, 2) NOT NULL,
    fecha_pago TIMESTAMP NOT NULL,
    
    -- Restricciones
    CONSTRAINT fk_pago_turno FOREIGN KEY (turno_id) REFERENCES turno(id) ON DELETE SET NULL,
    CONSTRAINT fk_pago_solicitud FOREIGN KEY (solicitud_documento_id) REFERENCES solicitud_documento(id) ON DELETE SET NULL,
    CONSTRAINT chk_pago_estado CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO')),
    -- Asegura que el pago corresponda a un turno O a una solicitud de receta/certificado, pero no a ambos
    CONSTRAINT chk_origen_pago CHECK (
        (turno_id IS NOT NULL AND solicitud_documento_id IS NULL) OR
        (turno_id IS NULL AND solicitud_documento_id IS NOT NULL)
    )
);

CREATE INDEX idx_pago_transaction ON pago(transaction_id);

-- 8. Tabla de Mensajes
-- Canales de chat persistentes entre el médico y el paciente
CREATE TABLE mensaje (
    id BIGSERIAL PRIMARY KEY,
    remitente_id BIGINT NOT NULL,
    destinatario_id BIGINT NOT NULL,
    contenido TEXT NOT NULL,
    fecha_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    leido BOOLEAN DEFAULT FALSE,
    
    -- Restricciones
    CONSTRAINT fk_mensaje_remitente FOREIGN KEY (remitente_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_mensaje_destinatario FOREIGN KEY (destinatario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

-- Índices en Mensaje para acelerar la carga de chats ordenados por fecha
CREATE INDEX idx_mensaje_chat ON mensaje(remitente_id, destinatario_id, fecha_envio DESC);
