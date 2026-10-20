-- Base propia del servicio de chat (Fase 2).

-- Mensajes entre dos usuarios. Mismos campos que la tabla del monolito; la carga inicial copia los
-- mensajes existentes conservando sus ids.
CREATE TABLE mensaje (
  id               BIGSERIAL   PRIMARY KEY,
  remitente_id     BIGINT      NOT NULL,
  destinatario_id  BIGINT      NOT NULL,
  contenido        TEXT        NOT NULL,
  leido            BOOLEAN     NOT NULL DEFAULT FALSE,
  fecha_envio      TIMESTAMP   NOT NULL DEFAULT now(),
  version          INT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_mensaje_remitente_id ON mensaje (remitente_id);
CREATE INDEX idx_mensaje_destinatario_id ON mensaje (destinatario_id);
CREATE INDEX idx_mensaje_no_leidos ON mensaje (destinatario_id) WHERE leido = FALSE;

-- Copia mínima de los usuarios (sección 6.6): nombre, mail y rol para los canales.
CREATE TABLE participante (
  id              BIGINT       PRIMARY KEY,
  email           VARCHAR(255) NOT NULL,
  nombre          VARCHAR(255),
  apellido        VARCHAR(255),
  rol             VARCHAR(30),
  actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_participante_email ON participante (lower(email));

-- Copia de los turnos confirmados de hoy en adelante: la prioridad clínica de los canales del
-- profesional depende de si el paciente tiene un turno confirmado en los próximos 3 días.
CREATE TABLE turno_confirmado (
  turno_id        BIGINT  PRIMARY KEY,
  paciente_id     BIGINT  NOT NULL,
  profesional_id  BIGINT  NOT NULL,
  fecha           DATE    NOT NULL,
  hora_inicio     TIME
);
CREATE INDEX idx_turno_confirmado_profesional ON turno_confirmado (profesional_id, paciente_id);

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

-- Outbox (libs/eventos, db/eventos/outbox.sql): el chat publica chat.mensaje.enviado.v1.
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
