-- Outbox de un servicio que publica eventos (sección 6.1 del plan). Cada servicio copia este SQL en
-- una migración Flyway propia. Mismo formato que la outbox del monolito (V3__outbox_e_inbox.sql).
CREATE TABLE IF NOT EXISTS outbox (
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

CREATE INDEX IF NOT EXISTS outbox_pendientes ON outbox (id) WHERE published_at IS NULL;

-- Avisa al relay en el momento (LISTEN outbox) en vez de esperar al barrido de respaldo.
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
