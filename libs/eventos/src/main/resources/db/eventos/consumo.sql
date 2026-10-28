-- Tablas que necesita el consumo de eventos de libs/eventos (secciones 6.2 y 6.4 del plan).
-- Cada servicio que consume eventos copia este SQL en una migración Flyway propia.

-- Inbox: un evento procesado por un consumidor (handler). Se limpia a los 30 días.
CREATE TABLE IF NOT EXISTS eventos_procesados (
  event_id      UUID        NOT NULL,
  consumidor    VARCHAR(80) NOT NULL,
  procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumidor)
);

-- Última versión aplicada por consumidor y entidad, para descartar eventos viejos que llegan tarde.
CREATE TABLE IF NOT EXISTS versiones_aplicadas (
  consumidor      VARCHAR(80) NOT NULL,
  aggregate_type  VARCHAR(50) NOT NULL,
  aggregate_id    VARCHAR(64) NOT NULL,
  version         INT         NOT NULL,
  PRIMARY KEY (consumidor, aggregate_type, aggregate_id)
);
