-- Base propia del servicio de notificaciones (Fase 1).

-- Copia mínima de los usuarios (sección 6.6): lo que hace falta para mandar un mail o una
-- notificación sin preguntarle a identidad. El id es el del usuario en identidad.
CREATE TABLE contacto (
  id                BIGINT       PRIMARY KEY,
  email             VARCHAR(255) NOT NULL,
  nombre            VARCHAR(255),
  apellido          VARCHAR(255),
  rol               VARCHAR(30),
  email_habilitado  BOOLEAN      NOT NULL DEFAULT TRUE,
  actualizado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_contacto_email ON contacto (lower(email));
CREATE INDEX idx_contacto_rol ON contacto (rol);

-- Notificaciones dentro de la app (la campanita). Se borran a las 24 horas, igual que en el monolito.
CREATE TABLE notificacion (
  id              BIGSERIAL    PRIMARY KEY,
  usuario_id      BIGINT       NOT NULL,
  titulo          VARCHAR(150) NOT NULL,
  mensaje         TEXT         NOT NULL,
  leido           BOOLEAN      NOT NULL DEFAULT FALSE,
  tipo            VARCHAR(50),
  fecha_creacion  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_notificacion_usuario_fecha ON notificacion (usuario_id, fecha_creacion DESC);

-- Consumo de eventos (libs/eventos, db/eventos/consumo.sql).
CREATE TABLE eventos_procesados (
  event_id      UUID        NOT NULL,
  consumidor    VARCHAR(80) NOT NULL,
  procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumidor)
);

CREATE TABLE versiones_aplicadas (
  consumidor      VARCHAR(80) NOT NULL,
  aggregate_type  VARCHAR(50) NOT NULL,
  aggregate_id    VARCHAR(64) NOT NULL,
  version         INT         NOT NULL,
  PRIMARY KEY (consumidor, aggregate_type, aggregate_id)
);
