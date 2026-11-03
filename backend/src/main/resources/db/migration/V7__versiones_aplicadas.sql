-- Fase 10, T3: la tabla del descarte por versión de libs/eventos (db/eventos/consumo.sql). V3 creó el inbox pero no
-- esta, y los manejadores versionados del monolito (copias de turnos, profesionales y suscripciones) fallaban todos.
CREATE TABLE IF NOT EXISTS versiones_aplicadas (
  consumidor      VARCHAR(80) NOT NULL,
  aggregate_type  VARCHAR(50) NOT NULL,
  aggregate_id    VARCHAR(64) NOT NULL,
  version         INT         NOT NULL,
  PRIMARY KEY (consumidor, aggregate_type, aggregate_id)
);
