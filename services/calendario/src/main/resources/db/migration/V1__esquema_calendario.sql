-- Base propia del servicio de calendario (Fase 3).

-- Copia mínima de los usuarios (sección 6.6): el mail identifica al profesional autenticado y el
-- nombre del paciente va en el título del evento de Google.
CREATE TABLE participante (
  id              BIGINT       PRIMARY KEY,
  email           VARCHAR(255) NOT NULL,
  nombre          VARCHAR(255),
  apellido        VARCHAR(255),
  rol             VARCHAR(30),
  actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_participante_email ON participante (lower(email));

-- Cuenta de Google de un profesional (antes, 11 columnas de usuario en el monolito). Los tokens van
-- cifrados con la misma clave que el monolito; la carga inicial los copia sin descifrarlos.
CREATE TABLE cuenta_google (
  profesional_id         BIGINT       PRIMARY KEY,
  access_token_cifrado   TEXT,
  refresh_token_cifrado  TEXT,
  token_vence_en         TIMESTAMP,
  calendar_id            VARCHAR(255),
  sync_token             TEXT,
  watch_channel_id       VARCHAR(100),
  watch_resource_id      VARCHAR(100),
  watch_vence_en         TIMESTAMP,
  watch_activo           BOOLEAN      NOT NULL DEFAULT FALSE,
  actualizado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Eventos propios del Google Calendar del profesional (no los turnos de la app), sincronizados por
-- syncToken. "transparente" = marcado como disponible en Google: no bloquea la agenda.
CREATE TABLE evento_externo (
  id               BIGSERIAL    PRIMARY KEY,
  profesional_id   BIGINT       NOT NULL,
  google_event_id  VARCHAR(255) NOT NULL UNIQUE,
  titulo           VARCHAR(500),
  inicio           TIMESTAMP    NOT NULL,
  fin              TIMESTAMP    NOT NULL,
  todo_el_dia      BOOLEAN      NOT NULL DEFAULT FALSE,
  transparente     BOOLEAN      NOT NULL DEFAULT FALSE,
  estado           VARCHAR(20),
  version          INT          NOT NULL DEFAULT 0,
  actualizado_en   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_evento_externo_profesional ON evento_externo (profesional_id, inicio);

-- Turnos confirmados que ocupan agenda, de hoy en adelante, con su evento de Google y su Meet.
CREATE TABLE turno_agendado (
  turno_id         BIGINT       PRIMARY KEY,
  profesional_id   BIGINT       NOT NULL,
  paciente_id      BIGINT,
  fecha            DATE         NOT NULL,
  hora_inicio      TIME         NOT NULL,
  hora_fin         TIME,
  modalidad        VARCHAR(20),
  google_event_id  VARCHAR(255),
  meet_url         VARCHAR(500),
  version          INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_turno_agendado_profesional ON turno_agendado (profesional_id, fecha);
CREATE INDEX idx_turno_agendado_evento ON turno_agendado (google_event_id);

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

-- Outbox (libs/eventos, db/eventos/outbox.sql): calendario.meet.creado.v1 y calendario.horario.bloqueado.v1.
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
