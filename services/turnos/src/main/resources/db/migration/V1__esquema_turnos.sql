-- Servicio de turnos (Fase 6): reservas, agenda, cancelación, reprogramación, asistencia y documentos.

-- Los turnos conservan los ids del monolito (están en pagos, chat, calendario, notificaciones y en los
-- links de los mails). Los nuevos salen de la secuencia, que la carga inicial pone después del máximo.
CREATE SEQUENCE turno_id_seq;

CREATE TABLE turno (
  id                                     BIGINT        PRIMARY KEY,
  -- Versión de los eventos del turno: sigue la numeración del monolito (los consumidores descartan las viejas).
  version                                INT           NOT NULL,
  paciente_id                            BIGINT        NOT NULL,
  medico_id                              BIGINT        NOT NULL,
  fecha                                  DATE          NOT NULL,
  hora_inicio                            TIME          NOT NULL,
  hora_fin                               TIME          NOT NULL,
  tipo                                   VARCHAR(20)   NOT NULL,
  modalidad                              VARCHAR(20),
  estado                                 VARCHAR(30)   NOT NULL,
  asistencia                             VARCHAR(30)   NOT NULL DEFAULT 'ESPERANDO',
  precio                                 NUMERIC(12,2) NOT NULL,
  metadata_afiliado                      VARCHAR(100),
  servicio_id                            VARCHAR(50),
  ocupa_agenda                           BOOLEAN       NOT NULL DEFAULT true,
  id_financiador                         VARCHAR(50),
  telemedicina_url                       VARCHAR(500),
  google_event_id                        VARCHAR(255),
  checkout_url                           VARCHAR(500),
  documento_enviado                      BOOLEAN       NOT NULL DEFAULT false,
  es_receta                              BOOLEAN       NOT NULL DEFAULT false,
  fecha_creacion                         TIMESTAMP     NOT NULL,
  confirmacion_asistencia_email_enviado  BOOLEAN       NOT NULL DEFAULT false,
  asistencia_confirmada_paciente         BOOLEAN       NOT NULL DEFAULT false,
  -- Los tokens de los links de los mails: nunca viajan en eventos.
  token_confirmacion_asistencia          VARCHAR(64),
  token_reserva                          VARCHAR(64),
  -- El pago del turno, como lo informa el servicio de pagos (uno por turno, como en el monolito).
  pago_transaction_id                    VARCHAR(100),
  pago_estado                            VARCHAR(20),
  pago_monto                             NUMERIC(12,2),
  pago_fecha                             TIMESTAMP,
  -- Último cambio (la resincronización del monolito pide desde acá).
  actualizado_en                         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_turno_medico_fecha ON turno (medico_id, fecha);
CREATE INDEX idx_turno_paciente ON turno (paciente_id);
CREATE INDEX idx_turno_estado ON turno (estado);
CREATE INDEX idx_turno_actualizado ON turno (actualizado_en);

-- Copia de los usuarios (pacientes y profesionales) con lo que muestran la agenda y "Mis turnos". Es del
-- monolito (identidad): se copia en la carga inicial y con los eventos de identidad.
CREATE TABLE persona (
  id                      BIGINT       PRIMARY KEY,
  version                 INT          NOT NULL DEFAULT 0,
  rol                     VARCHAR(30),
  email                   VARCHAR(255),
  nombre                  VARCHAR(255),
  apellido                VARCHAR(255),
  telefono                VARCHAR(50),
  dni                     VARCHAR(50),
  direccion               VARCHAR(255),
  obra_social             VARCHAR(100),
  num_afiliado            VARCHAR(100),
  sexo                    VARCHAR(30),
  fecha_nacimiento        DATE,
  cuil                    BIGINT,
  tipo_documento          VARCHAR(30),
  numero_documento        INT,
  datos_ofuscado          VARCHAR(5),
  credencial_cod_entidad  INT,
  credencial_pan          VARCHAR(100),
  credencial_plan         VARCHAR(100),
  credencial_token        VARCHAR(255),
  email_habilitado        BOOLEAN      NOT NULL DEFAULT true,
  -- Solo profesionales: la reserva exige Mercado Pago vinculado (con Mercado Pago real).
  mp_vinculada            BOOLEAN      NOT NULL DEFAULT false
);
CREATE INDEX idx_persona_email ON persona (lower(email));

-- Copia de la agenda de cada profesional (del servicio de profesionales, o del monolito en su vuelta atrás).
CREATE TABLE profesional (
  id                          BIGINT       PRIMARY KEY,
  duracion_turno_minutos      INT,
  intervalo_entre_turnos_minutos INT,
  ofrece_online               BOOLEAN      NOT NULL DEFAULT false,
  ofrece_presencial           BOOLEAN      NOT NULL DEFAULT false,
  precio                      NUMERIC(12,2),
  domicilio_atencion          VARCHAR(255),
  domicilio_lat               DOUBLE PRECISION,
  domicilio_lng               DOUBLE PRECISION,
  domicilio_atencion_torre    VARCHAR(50),
  domicilio_atencion_piso     VARCHAR(20),
  domicilio_atencion_depto    VARCHAR(20),
  domicilio_atencion_barrio   VARCHAR(100)
);

-- modalidad null: franja vieja, válida para las dos modalidades (como en el monolito).
CREATE TABLE disponibilidad (
  id               BIGSERIAL   PRIMARY KEY,
  profesional_id   BIGINT      NOT NULL,
  dia_semana       INT         NOT NULL,
  hora_inicio      TIME        NOT NULL,
  hora_fin         TIME        NOT NULL,
  modalidad        VARCHAR(20)
);
CREATE INDEX idx_disponibilidad_profesional ON disponibilidad (profesional_id);

CREATE TABLE tarifa (
  id                    BIGSERIAL     PRIMARY KEY,
  profesional_id        BIGINT        NOT NULL,
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

-- Outbox (libs/eventos, db/eventos/outbox.sql): los turnos.* de siempre y turnos.turno.actualizado.
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
