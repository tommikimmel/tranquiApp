-- Servicio de profesionales (Fase 7): historia clínica, recetas, solicitudes y copias de turnos y personas.

-- Las tablas de la historia clínica conservan los ids del monolito (están en las recetas, solicitudes,
-- links de descarga y auditoría). Los nuevos salen de las secuencias, que la carga inicial pone después del máximo.
CREATE SEQUENCE seguimiento_diario_id_seq;

CREATE TABLE seguimiento_diario (
  id               BIGINT        PRIMARY KEY,
  paciente_id      BIGINT        NOT NULL,
  medico_id        BIGINT        NOT NULL,
  fecha            DATE          NOT NULL,
  estado_animo     VARCHAR(50)   NOT NULL,
  sintomas         TEXT,
  notas            TEXT,
  fecha_creacion   TIMESTAMP     NOT NULL,
  estado           VARCHAR(30)   DEFAULT 'VIGENTE',
  motivo           TEXT,
  hash_integridad  VARCHAR(64)
);
CREATE INDEX idx_seguimiento_diario_paciente_id ON seguimiento_diario (paciente_id);
CREATE INDEX idx_seguimiento_diario_medico_id ON seguimiento_diario (medico_id);

CREATE SEQUENCE informe_clinico_id_seq;

CREATE TABLE informe_clinico (
  id                   BIGINT        PRIMARY KEY,
  paciente_id          BIGINT        NOT NULL,
  medico_id            BIGINT        NOT NULL,
  fecha                DATE          NOT NULL,
  tipo_informe         VARCHAR(50)   NOT NULL,
  plan_trabajo         TEXT,
  contenido            TEXT,
  nombre_archivo       VARCHAR(255),
  fecha_creacion       TIMESTAMP     NOT NULL,
  estado               VARCHAR(30)   DEFAULT 'VIGENTE',
  informe_original_id  BIGINT,
  motivo               TEXT,
  hash_integridad      VARCHAR(64)
);
CREATE INDEX idx_informe_clinico_paciente_id ON informe_clinico (paciente_id);
CREATE INDEX idx_informe_clinico_medico_id ON informe_clinico (medico_id);

CREATE SEQUENCE receta_id_seq;

CREATE TABLE receta (
  id                     BIGINT        PRIMARY KEY,
  medico_id              BIGINT        NOT NULL,
  paciente_id            BIGINT        NOT NULL,
  medicamentos           TEXT          NOT NULL,
  laboratorios           TEXT,
  diagnostico            VARCHAR(150),
  indicaciones           TEXT,
  pdf_url                VARCHAR(500),
  qbi2_id_receta         VARCHAR(100),
  qbi2_verificador       VARCHAR(255),
  qbi2_nro_cuir          VARCHAR(500),
  qbi2_fecha_vencimiento VARCHAR(50),
  qbi2_id_transaccion    VARCHAR(100),
  fecha_emision          TIMESTAMP     NOT NULL
);
CREATE INDEX idx_receta_medico_id ON receta (medico_id);
CREATE INDEX idx_receta_paciente_id ON receta (paciente_id);

CREATE SEQUENCE solicitud_documento_id_seq;

CREATE TABLE solicitud_documento (
  id              BIGINT         PRIMARY KEY,
  paciente_id     BIGINT         NOT NULL,
  medico_id       BIGINT         NOT NULL,
  tipo_concepto   VARCHAR(30)    NOT NULL,
  precio          NUMERIC(12,2)  NOT NULL DEFAULT 0,
  estado          VARCHAR(30)    NOT NULL,
  transaction_id  VARCHAR(100),
  emitido         BOOLEAN        NOT NULL DEFAULT false,
  url_descarga    VARCHAR(500),
  fecha_emision   TIMESTAMP,
  fecha_creacion  TIMESTAMP      NOT NULL
);
CREATE INDEX idx_solicitud_documento_paciente_id ON solicitud_documento (paciente_id);
CREATE INDEX idx_solicitud_documento_medico_id ON solicitud_documento (medico_id);

-- Copia de los usuarios (pacientes y profesionales) que necesitan la ficha, la lista de pacientes y
-- la receta. Es del monolito (identidad): se copia en la carga inicial y con los eventos de identidad.
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
  domicilio_calle         VARCHAR(255),
  domicilio_numero        VARCHAR(50),
  domicilio_piso          VARCHAR(50),
  domicilio_dpto          VARCHAR(50),
  domicilio_codigo_postal VARCHAR(50),
  domicilio_localidad     VARCHAR(255),
  domicilio_provincia     VARCHAR(255),
  domicilio_pais          VARCHAR(100)
);
CREATE INDEX idx_persona_email ON persona (lower(email));

-- Copia mínima de los turnos para saber a quién atiende cada profesional y armar la lista de Pacientes.
-- Se actualiza con los eventos de turnos y con la resincronización periódica desde el monolito.
CREATE TABLE turno_atendido (
  id              BIGINT       PRIMARY KEY,
  profesional_id  BIGINT       NOT NULL,
  paciente_id     BIGINT       NOT NULL,
  fecha           DATE         NOT NULL,
  estado          VARCHAR(30)  NOT NULL,
  version         INT          NOT NULL
);
CREATE INDEX idx_turno_atendido_profesional_paciente ON turno_atendido (profesional_id, paciente_id);
