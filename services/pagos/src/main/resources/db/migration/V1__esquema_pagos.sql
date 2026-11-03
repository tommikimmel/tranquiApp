-- Servicio de pagos (Fase 4): Mercado Pago de los turnos y documentos pagos. Las suscripciones siguen en
-- el monolito hasta la Fase 7.

-- Copia mínima de los usuarios (sección 6.6): el JWT trae el mail y las rutas de Mercado Pago necesitan
-- el id del profesional.
CREATE TABLE participante (
  id              BIGINT       PRIMARY KEY,
  email           VARCHAR(255) NOT NULL,
  nombre          VARCHAR(255),
  apellido        VARCHAR(255),
  rol             VARCHAR(30),
  actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_participante_email ON participante (lower(email));

-- Cuenta de Mercado Pago de un profesional (antes, 4 columnas mp_* de usuario en el monolito). Que exista
-- la fila es estar vinculado. Los tokens van cifrados con la ENCRYPTION_KEY del monolito.
CREATE TABLE cuenta_mp (
  profesional_id         BIGINT       PRIMARY KEY,
  access_token_cifrado   TEXT         NOT NULL,
  refresh_token_cifrado  TEXT,
  token_vence_en         TIMESTAMP,
  mp_user_id             VARCHAR(50),
  actualizado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_cuenta_mp_usuario ON cuenta_mp (mp_user_id);

-- Un cobro por external_reference de Mercado Pago: el id del turno, o doc-<id> para un documento de la API
-- de conceptos. Estados: PENDIENTE (sin link todavía), LISTO, ERROR (no se pudo crear la preferencia),
-- PAGADO y VENCIDO (venció el bloqueo de 5 minutos).
CREATE TABLE cobro (
  referencia      VARCHAR(40)   PRIMARY KEY,
  turno_id        BIGINT,
  solicitud_id    BIGINT,
  profesional_id  BIGINT        NOT NULL,
  paciente_id     BIGINT,
  monto           NUMERIC(12,2) NOT NULL,
  titulo          VARCHAR(200),
  tipo_concepto   VARCHAR(30),
  checkout_url    VARCHAR(500),
  estado          VARCHAR(20)   NOT NULL,
  error           TEXT,
  creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Pagos de Mercado Pago: uno por transaction_id (el id del pago en Mercado Pago), así un webhook repetido o
-- el webhook y "verificar" a la vez no registran dos veces. Estados: APROBADO, RECHAZADO y REEMBOLSADO.
CREATE TABLE pago (
  id                BIGSERIAL     PRIMARY KEY,
  referencia        VARCHAR(40)   NOT NULL,
  turno_id          BIGINT,
  solicitud_id      BIGINT,
  profesional_id    BIGINT,
  paciente_id       BIGINT,
  transaction_id    VARCHAR(100)  NOT NULL UNIQUE,
  estado            VARCHAR(20)   NOT NULL,
  monto             NUMERIC(12,2) NOT NULL,
  fecha_pago        TIMESTAMP     NOT NULL,
  -- Los copiados del monolito en la carga inicial, para la conciliación y la vuelta atrás.
  pago_monolito_id  BIGINT
);
CREATE INDEX idx_pago_turno ON pago (turno_id);

-- Un reembolso por turno como máximo. Estados: REALIZADO y FALLIDO.
CREATE TABLE reembolso (
  id         BIGSERIAL     PRIMARY KEY,
  pago_id    BIGINT        NOT NULL REFERENCES pago (id),
  turno_id   BIGINT        NOT NULL UNIQUE,
  estado     VARCHAR(20)   NOT NULL,
  monto      NUMERIC(12,2) NOT NULL,
  motivo     VARCHAR(200),
  error      TEXT,
  creado_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
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

-- Outbox (libs/eventos, db/eventos/outbox.sql): pagos.checkout.creado, pago.aprobado/rechazado,
-- concepto.aprobado, reembolso.realizado/fallido y cuenta-mp.vinculada/desvinculada.
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
