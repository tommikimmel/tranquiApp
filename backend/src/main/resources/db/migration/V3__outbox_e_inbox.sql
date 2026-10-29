-- Outbox (sección 6.1 del plan de microservicios)
CREATE TABLE IF NOT EXISTS outbox (
  id              BIGSERIAL PRIMARY KEY,
  event_id        UUID        NOT NULL UNIQUE,
  topic           VARCHAR(150) NOT NULL,
  aggregate_type  VARCHAR(50)  NOT NULL,
  aggregate_id    VARCHAR(64)  NOT NULL,
  payload         JSONB        NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at    TIMESTAMPTZ,
  attempts        INT          NOT NULL DEFAULT 0,
  last_error      TEXT
);

CREATE INDEX IF NOT EXISTS outbox_pendientes ON outbox (id) WHERE published_at IS NULL;

-- Trigger AFTER INSERT que notifica a LISTEN 'outbox'
CREATE OR REPLACE FUNCTION notify_outbox_insert()
RETURNS TRIGGER AS $$
BEGIN
  PERFORM pg_notify('outbox', NEW.id::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_outbox_insert ON outbox;
CREATE TRIGGER trg_outbox_insert
AFTER INSERT ON outbox
FOR EACH ROW
EXECUTE FUNCTION notify_outbox_insert();

-- Inbox / Eventos procesados (sección 6.2 del plan de microservicios)
CREATE TABLE IF NOT EXISTS eventos_procesados (
  event_id      UUID        NOT NULL,
  consumidor    VARCHAR(80) NOT NULL,
  procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumidor)
);
