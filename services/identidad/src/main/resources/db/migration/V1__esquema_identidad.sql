-- Servicio de identidad (Fase 9, I2a): cuentas, sesiones, usuarios, soporte y novedades.

-- Secuencia para los nuevos usuarios. La carga inicial copia los ids del monolito y mueve
-- la secuencia arriba del máximo para que las nuevas altas no choquen.
CREATE SEQUENCE usuario_id_seq;

CREATE TABLE usuario (
  id                                BIGINT       PRIMARY KEY,
  version                           INT          NOT NULL DEFAULT 0,
  email                             VARCHAR(150) NOT NULL UNIQUE,
  password                          VARCHAR(100),
  rol                               VARCHAR(20)  NOT NULL,
  fecha_registro                    TIMESTAMP    NOT NULL,
  email_verificado                  BOOLEAN      DEFAULT false,
  codigo_verificacion               VARCHAR(10),
  codigo_verificacion_expires_at    TIMESTAMP,
  reset_password_code               VARCHAR(10),
  reset_password_expires_at         TIMESTAMP,
  must_change_password              BOOLEAN      NOT NULL DEFAULT false,
  terminos_aceptados_en             TIMESTAMP,
  perfil_completo                   BOOLEAN,
  cuenta_eliminada                  BOOLEAN      DEFAULT false,
  anonimizado_en                    TIMESTAMP,
  notificaciones_email_habilitadas  BOOLEAN      DEFAULT true,
  recibir_novedades                 BOOLEAN      DEFAULT true,
  nombre                            VARCHAR(100) NOT NULL,
  apellido                          VARCHAR(100),
  telefono                          VARCHAR(30),
  sexo                              VARCHAR(10),
  fecha_nacimiento                  DATE,
  tipo_documento                    VARCHAR(20),
  numero_documento                  INT,
  dni                               VARCHAR(20),
  cuil                              BIGINT,
  direccion                         VARCHAR(255),
  foto_url                          TEXT,
  domicilio_calle                   VARCHAR(50),
  domicilio_numero                  VARCHAR(6),
  domicilio_piso                    VARCHAR(2),
  domicilio_dpto                    VARCHAR(3),
  domicilio_codigo_postal           VARCHAR(8),
  domicilio_localidad               VARCHAR(200),
  domicilio_provincia               VARCHAR(200),
  domicilio_pais                    VARCHAR(45),
  obra_social                       VARCHAR(100),
  num_afiliado                      VARCHAR(50),
  datos_ofuscado                    VARCHAR(10),
  credencial_cod_entidad            INT,
  credencial_pan                    VARCHAR(50),
  credencial_plan                   VARCHAR(50),
  credencial_token                  VARCHAR(50),
  verificado_admin                  BOOLEAN,
  profession                        VARCHAR(30),
  license_type                      VARCHAR(20),
  license_number                    VARCHAR(50),
  license_jurisdiction              VARCHAR(100),
  license_document_url              TEXT,
  license_verified_at               TIMESTAMP,
  license_verified_by               VARCHAR(100),
  tax_id_type                       VARCHAR(20),
  tax_id                            VARCHAR(30),
  legal_name                        VARCHAR(200),
  iva_condition_id                  INT,
  fiscal_address                    VARCHAR(255)
);
CREATE INDEX idx_usuario_rol ON usuario (rol);
CREATE INDEX idx_usuario_email ON usuario (email);

-- Tickets de soporte.
CREATE SEQUENCE ticket_id_seq;

CREATE TABLE ticket (
  id                   BIGINT       PRIMARY KEY,
  version              INT          NOT NULL DEFAULT 0,
  creador_id           BIGINT       NOT NULL REFERENCES usuario (id),
  asunto               VARCHAR(200) NOT NULL,
  estado               VARCHAR(20)  NOT NULL,
  fecha_creacion       TIMESTAMP    NOT NULL,
  fecha_actualizacion  TIMESTAMP    NOT NULL
);
CREATE INDEX idx_ticket_creador_id ON ticket (creador_id);
CREATE INDEX idx_ticket_estado ON ticket (estado);

-- Mensajes de cada ticket de soporte (conversación con el admin).
CREATE SEQUENCE ticket_mensaje_id_seq;

CREATE TABLE ticket_mensaje (
  id           BIGINT    PRIMARY KEY,
  ticket_id    BIGINT    NOT NULL REFERENCES ticket (id),
  autor_id     BIGINT    NOT NULL REFERENCES usuario (id),
  contenido    TEXT      NOT NULL,
  fecha_envio  TIMESTAMP NOT NULL
);
CREATE INDEX idx_ticket_mensaje_ticket_id ON ticket_mensaje (ticket_id);
CREATE INDEX idx_ticket_mensaje_autor_id ON ticket_mensaje (autor_id);

-- Envíos masivos de novedades (mail a usuarios tras una ventana de mantenimiento). Su id es texto alfanumérico.
CREATE TABLE novedades_envio (
  id                     VARCHAR(100) PRIMARY KEY,
  titulo                 VARCHAR(200) NOT NULL,
  estado                 VARCHAR(20)  NOT NULL,
  detalle                TEXT,
  creado_en              TIMESTAMP    NOT NULL,
  finalizado_en          TIMESTAMP,
  enviado_pacientes      BOOLEAN      NOT NULL DEFAULT false,
  enviado_profesionales  BOOLEAN      NOT NULL DEFAULT false
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

-- Outbox (libs/eventos, db/eventos/outbox.sql).
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
