-- Servicio de pagos (Fase 8, S2a): suscripciones, planes, facturación y copia de suscriptores.
-- Las suscripciones y la facturación se mudan del monolito a este servicio (decisión 1).

-- Planes de suscripción disponibles en la plataforma.
CREATE SEQUENCE plans_id_seq;

CREATE TABLE plans (
  id                      BIGINT         PRIMARY KEY,
  code                    VARCHAR(50)    NOT NULL UNIQUE,
  name                    VARCHAR(100)   NOT NULL,
  description             TEXT,
  price_ars               NUMERIC(12,2)  NOT NULL,
  price_usd_ref           NUMERIC(10,2),
  price_ars_anual         NUMERIC(12,2),
  billing_period          VARCHAR(20)    NOT NULL DEFAULT 'monthly',
  min_seats               INT            NOT NULL DEFAULT 1,
  mp_preapproval_plan_id  VARCHAR(100),
  requires_prescriber     BOOLEAN        NOT NULL DEFAULT false,
  is_active               BOOLEAN        NOT NULL DEFAULT true,
  effective_from          TIMESTAMP,
  effective_to            TIMESTAMP
);
CREATE INDEX idx_plans_code ON plans (code);

-- Features o funcionalidades que habilitan los planes (ej. recetario, videollamadas).
CREATE TABLE features (
  feature_key  VARCHAR(100) PRIMARY KEY,
  name         VARCHAR(150) NOT NULL,
  description  TEXT
);

-- Asociación entre planes y features con sus límites opcionales.
CREATE SEQUENCE plan_features_id_seq;

CREATE TABLE plan_features (
  id           BIGINT       PRIMARY KEY,
  plan_id      BIGINT       NOT NULL,
  feature_key  VARCHAR(100) NOT NULL,
  limit_value  INT,
  CONSTRAINT uq_plan_features_plan_feature UNIQUE (plan_id, feature_key)
);
CREATE INDEX idx_plan_features_plan_id ON plan_features (plan_id);

-- Suscripciones activas o históricas de cada profesional.
-- Decisión 5: professional_id es ÚNICO (una sola suscripción por profesional).
CREATE SEQUENCE subscriptions_id_seq;

CREATE TABLE subscriptions (
  id                     BIGINT         PRIMARY KEY,
  version                INT            NOT NULL DEFAULT 0,
  professional_id        BIGINT         NOT NULL UNIQUE,
  plan_id                BIGINT         NOT NULL,
  status                 VARCHAR(30)    NOT NULL,
  seats                  INT            NOT NULL DEFAULT 1,
  billing_source         VARCHAR(30)    NOT NULL,
  mp_preapproval_id      VARCHAR(100),
  amount_ars             NUMERIC(12,2)  NOT NULL,
  billing_cycle          VARCHAR(20)    DEFAULT 'monthly',
  next_billing_date      TIMESTAMP,
  current_period_start   TIMESTAMP,
  current_period_end     TIMESTAMP,
  grace_until            TIMESTAMP,
  cancel_at_period_end   BOOLEAN        NOT NULL DEFAULT false,
  cancelled_at           TIMESTAMP,
  renewal_reminder_sent  BOOLEAN        NOT NULL DEFAULT false,
  created_at             TIMESTAMP      NOT NULL DEFAULT now(),
  updated_at             TIMESTAMP      NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_status ON subscriptions (status);
CREATE INDEX idx_subscriptions_mp_preapproval ON subscriptions (mp_preapproval_id);

-- Historial de eventos y auditoría de cambios de estado en las suscripciones.
CREATE SEQUENCE subscription_events_id_seq;

CREATE TABLE subscription_events (
  id               BIGINT       PRIMARY KEY,
  subscription_id  BIGINT       NOT NULL,
  event_type       VARCHAR(100) NOT NULL,
  previous_status  VARCHAR(50),
  new_status       VARCHAR(50),
  actor_type       VARCHAR(50)  NOT NULL,
  actor_id         VARCHAR(100),
  payload_json     TEXT,
  created_at       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscription_events_sub_id ON subscription_events (subscription_id);
CREATE INDEX idx_subscription_events_created ON subscription_events (created_at DESC);

-- Pagos individuales de suscripciones (Mercado Pago o manuales registrados por admin).
CREATE SEQUENCE subscription_payments_id_seq;

CREATE TABLE subscription_payments (
  id                      BIGINT         PRIMARY KEY,
  subscription_id         BIGINT         NOT NULL,
  professional_id         BIGINT         NOT NULL,
  mp_payment_id           VARCHAR(100)   UNIQUE,
  idempotency_key         VARCHAR(100)   NOT NULL UNIQUE,
  method                  VARCHAR(30)    NOT NULL,
  status                  VARCHAR(30)    NOT NULL,
  amount_ars              NUMERIC(12,2)  NOT NULL,
  paid_at                 TIMESTAMP      NOT NULL,
  period_start            TIMESTAMP,
  period_end              TIMESTAMP,
  invoice_id              BIGINT,
  registered_by_admin_id  BIGINT,
  receipt_reference       VARCHAR(150),
  notes                   TEXT,
  created_at              TIMESTAMP      NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscription_payments_sub_id ON subscription_payments (subscription_id);
CREATE INDEX idx_subscription_payments_prof_id ON subscription_payments (professional_id);
CREATE INDEX idx_subscription_payments_paid_at ON subscription_payments (paid_at DESC);

-- Facturas y comprobantes emitidos vía ARCA para cada cobro.
CREATE SEQUENCE invoices_id_seq;

CREATE TABLE invoices (
  id                       BIGINT         PRIMARY KEY,
  payment_id               BIGINT,
  cbte_tipo                INT            NOT NULL DEFAULT 11,
  punto_venta              INT            NOT NULL,
  cbte_numero              BIGINT,
  cae                      VARCHAR(30),
  cae_vencimiento          DATE,
  receptor_doc_tipo        INT            NOT NULL,
  receptor_doc_nro         BIGINT         NOT NULL,
  receptor_nombre          VARCHAR(200)   NOT NULL,
  receptor_condicion_iva   INT            NOT NULL,
  importe_total            NUMERIC(12,2)  NOT NULL,
  moneda                   VARCHAR(10)    NOT NULL DEFAULT 'PES',
  cotizacion               NUMERIC(8,4)   NOT NULL DEFAULT 1.0000,
  fecha_emision            DATE           NOT NULL,
  pdf_url                  TEXT,
  pdf_base64               TEXT,
  status                   VARCHAR(30)    NOT NULL,
  attempts                 INT            NOT NULL DEFAULT 0,
  last_error               TEXT,
  arca_request_json        TEXT,
  arca_response_json       TEXT,
  comprobante_asociado_id  BIGINT,
  created_at               TIMESTAMP      NOT NULL DEFAULT now(),
  issued_at                TIMESTAMP
);
CREATE INDEX idx_invoices_payment_id ON invoices (payment_id);
CREATE INDEX idx_invoices_pto_cbte ON invoices (punto_venta, cbte_tipo, cbte_numero);
CREATE INDEX idx_invoices_cae ON invoices (cae);
CREATE INDEX idx_invoices_status ON invoices (status);

-- Control estricto de la correlatividad numérica de comprobantes por punto de venta y tipo.
CREATE SEQUENCE invoice_sequences_id_seq;

CREATE TABLE invoice_sequences (
  id           BIGINT       PRIMARY KEY,
  punto_venta  INT          NOT NULL,
  cbte_tipo    INT          NOT NULL,
  last_number  BIGINT       NOT NULL,
  updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
  CONSTRAINT uq_invoice_sequences_pto_cbte UNIQUE (punto_venta, cbte_tipo)
);

-- Copia de suscriptores (datos fiscales, profesión, rol y versión del usuario en identidad).
-- Se copia en el arranque y se mantiene al día con los eventos de identidad del monolito.
CREATE TABLE suscriptor (
  id                BIGINT       PRIMARY KEY,
  email             VARCHAR(255),
  nombre            VARCHAR(255),
  apellido          VARCHAR(255),
  rol               VARCHAR(50),
  profession        VARCHAR(100),
  verificado_admin  BOOLEAN,
  version           INT          NOT NULL DEFAULT 0,
  tax_id_type       VARCHAR(20),
  tax_id            VARCHAR(50),
  legal_name        VARCHAR(255),
  iva_condition_id  INT,
  fiscal_address    TEXT,
  cuit              VARCHAR(50),
  cuil              BIGINT,
  dni               VARCHAR(50),
  numero_documento  INT,
  actualizado_en    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_suscriptor_email ON suscriptor (lower(email));
