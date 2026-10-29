-- Servicio de profesionales (Fase 5): perfil, buscador, tarifas y disponibilidad. La historia clínica, las
-- recetas y las estadísticas del panel siguen en el monolito.

-- Un profesional. Dos grupos de datos con dueños distintos:
--  * del monolito (identidad, verificación, suscripción, Mercado Pago): se copian en la carga inicial, cada
--    minuto y cuando llega un evento de identidad. La foto solo se vuelve a pedir si cambia su hash.
--  * del servicio: el perfil (PerfilProfesionalData de libs/eventos, en JSON), la disponibilidad y las
--    tarifas, más sus versiones para los eventos.
CREATE TABLE profesional (
  id                         BIGINT       PRIMARY KEY,
  email                      VARCHAR(255) NOT NULL,
  rol                        VARCHAR(30),
  profession                 VARCHAR(50),
  nombre                     VARCHAR(255),
  apellido                   VARCHAR(255),
  telefono                   VARCHAR(50),
  sexo                       VARCHAR(30),
  fecha_nacimiento           DATE,
  cuil                       BIGINT,
  tipo_documento             VARCHAR(30),
  numero_documento           INT,
  foto_url                   TEXT,
  foto_hash                  VARCHAR(64),
  verificado_admin           BOOLEAN,
  mp_vinculada               BOOLEAN      NOT NULL DEFAULT false,
  suscripcion_estado         VARCHAR(30),
  suscripcion_periodo_hasta  TIMESTAMP,
  puede_recetar              BOOLEAN      NOT NULL DEFAULT false,
  perfil                     JSONB        NOT NULL,
  -- Versión de profesional.actualizado: sigue la numeración del usuario en el monolito.
  version                    INT          NOT NULL,
  disponibilidad_version     INT          NOT NULL DEFAULT 0,
  tarifas_version            INT          NOT NULL DEFAULT 0,
  -- Último cambio del perfil, la disponibilidad o las tarifas (la resincronización del monolito pide desde acá).
  actualizado_en             TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_profesional_email ON profesional (lower(email));
CREATE INDEX idx_profesional_actualizado ON profesional (actualizado_en);

-- Franjas semanales. modalidad null: franja vieja, válida para las dos modalidades (como en el monolito).
CREATE TABLE disponibilidad (
  id               BIGSERIAL   PRIMARY KEY,
  profesional_id   BIGINT      NOT NULL REFERENCES profesional (id) ON DELETE CASCADE,
  dia_semana       INT         NOT NULL,
  hora_inicio      TIME        NOT NULL,
  hora_fin         TIME        NOT NULL,
  modalidad        VARCHAR(20)
);
CREATE INDEX idx_disponibilidad_profesional ON disponibilidad (profesional_id);

-- Servicios del profesional ("Honorarios y servicios"), uno por servicio_id. Se listan por id, como en el
-- monolito (la carga inicial los copia en el orden de allá).
CREATE TABLE tarifa (
  id                    BIGSERIAL     PRIMARY KEY,
  profesional_id        BIGINT        NOT NULL REFERENCES profesional (id) ON DELETE CASCADE,
  servicio_id           VARCHAR(50)   NOT NULL,
  label                 VARCHAR(100)  NOT NULL,
  precio                NUMERIC(12,2) NOT NULL,
  habilitado            BOOLEAN       NOT NULL,
  requiere_obra_social  BOOLEAN       NOT NULL DEFAULT false,
  obra_social           VARCHAR(100),
  precio_online         NUMERIC(12,2),
  precio_presencial     NUMERIC(12,2),
  requiere_agenda       BOOLEAN       NOT NULL DEFAULT true,
  es_receta             BOOLEAN       NOT NULL DEFAULT false,
  UNIQUE (profesional_id, servicio_id)
);

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

-- Outbox (libs/eventos, db/eventos/outbox.sql): profesional.actualizado, disponibilidad.actualizada.v2 y
-- tarifa.actualizada.v2.
CREATE TABLE outbox (
  id              BIGSERIAL    PRIMARY KEY,
  event_id        UUID         NOT NULL UNIQUE,
  topic           VARCHAR(150) NOT NULL,
  aggregate_type  VARCHAR(50)  NOT NULL,
  aggregate_id    VARCHAR(64)  NOT NULL,
  payload         JSONB        NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at    TIMESTAMPTZ,
  attempts        INT          NOT NULL DEFAULT 0,
  last_error      TEXT
);
CREATE INDEX outbox_pendientes ON outbox (id) WHERE published_at IS NULL;

CREATE OR REPLACE FUNCTION notify_outbox_insert()
RETURNS TRIGGER AS $$
BEGIN
  PERFORM pg_notify('outbox', NEW.id::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_outbox_insert
AFTER INSERT ON outbox
FOR EACH ROW
EXECUTE FUNCTION notify_outbox_insert();
