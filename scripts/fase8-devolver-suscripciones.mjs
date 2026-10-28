#!/usr/bin/env node
// Fase 8 (suscripciones, planes y facturación), vuelta atrás: devuelve al monolito las ocho tablas
// de suscripciones desde el servicio de pagos (base pagos a tranqui_app / tranqui_local), para que
// volver las suscripciones al monolito no pierda planes, features, suscripciones, eventos, pagos, facturas
// ni la correlatividad de comprobantes fiscales.
//
// - Inserta lo que el monolito no tiene con sus mismos ids.
// - Actualiza solo los campos mutables:
//   * subscriptions: status, amount_ars, current_period_start, current_period_end, grace_until,
//     cancel_at_period_end, cancelled_at, mp_preapproval_id, version, renewal_reminder_sent, seats,
//     billing_cycle, next_billing_date, updated_at
//   * subscription_payments: status, invoice_id
//   * invoices: status, cae, cae_vencimiento, cbte_numero, pdf_url, pdf_base64, attempts, last_error,
//     arca_request_json, arca_response_json, issued_at
//   * plans: price_ars, price_usd_ref, price_ars_anual, name, description, is_active, effective_from, effective_to
//   * invoice_sequences: last_number SOLO si el del servicio es mayor (nunca para abajo), y updated_at
// - NUNCA pisa campos inmutables:
//   * professional_id o plan_id de una suscripción
//   * amount_ars o mp_payment_id de un pago
//   * cbte_numero o cae de una factura ya emitida/autorizada
//   Si difieren, informa el conflicto (tabla, id, campo; sin datos fiscales) y aborta con código 1.
// - Aborta si un professional_id no existe en la tabla usuario del monolito.
// - Aborta si el monolito tiene otra suscripción para el mismo profesional con un id distinto.
// - Todo en una transacción única en PostgreSQL.
// - Ajusta las secuencias (identity/serial) del monolito por encima del máximo id de cada tabla.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase8-devolver-suscripciones.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el gateway ya apuntando al monolito y SUSCRIPCIONES_EXTERNO=false):
//   node scripts/fase8-devolver-suscripciones.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
import { execFileSync } from 'node:child_process';

const argv = process.argv.slice(2);
const opcion = (nombre, porDefecto) => {
  const i = argv.indexOf(`--${nombre}`);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : porDefecto;
};
const prueba = argv.includes('--prueba');
const proyecto = opcion('proyecto', 'tranqui-local');
const contenedor = opcion('contenedor', 'db');
const usuario = opcion('usuario', 'tranqui');
const base = opcion('base', 'tranqui_local');

function psql(baseDatos, args, entrada) {
  return execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-v', 'ON_ERROR_STOP=1', '-U', usuario, '-d', baseDatos, ...args],
  { encoding: 'utf8', input: entrada, maxBuffer: 512 * 1024 * 1024 });
}

function json(baseDatos, sql) {
  const salida = execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-v', 'ON_ERROR_STOP=1', '-U', usuario, '-d', baseDatos, '-tA', '-c',
    `SELECT COALESCE(json_agg(x), '[]') FROM (${sql}) x`],
  { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
  return JSON.parse(salida.trim());
}

const numero = (baseDatos, sql) => Number(psql(baseDatos, ['-tAc', sql]).trim());

const plansServicio = numero('pagos', 'SELECT count(*) FROM plans');
const plansMonolito = numero(base, 'SELECT count(*) FROM plans');
const featServicio = numero('pagos', 'SELECT count(*) FROM features');
const featMonolito = numero(base, 'SELECT count(*) FROM features');
const planFeatServicio = numero('pagos', 'SELECT count(*) FROM plan_features');
const planFeatMonolito = numero(base, 'SELECT count(*) FROM plan_features');
const subsServicio = numero('pagos', 'SELECT count(*) FROM subscriptions');
const subsMonolito = numero(base, 'SELECT count(*) FROM subscriptions');
const eventsServicio = numero('pagos', 'SELECT count(*) FROM subscription_events');
const eventsMonolito = numero(base, 'SELECT count(*) FROM subscription_events');
const paymentsServicio = numero('pagos', 'SELECT count(*) FROM subscription_payments');
const paymentsMonolito = numero(base, 'SELECT count(*) FROM subscription_payments');
const invServicio = numero('pagos', 'SELECT count(*) FROM invoices');
const invMonolito = numero(base, 'SELECT count(*) FROM invoices');
const seqServicio = numero('pagos', 'SELECT count(*) FROM invoice_sequences');
const seqMonolito = numero(base, 'SELECT count(*) FROM invoice_sequences');

console.log(`Planes:         servicio ${plansServicio}, monolito ${plansMonolito}`);
console.log(`Features:       servicio ${featServicio}, monolito ${featMonolito}`);
console.log(`Plan Features:  servicio ${planFeatServicio}, monolito ${planFeatMonolito}`);
console.log(`Suscripciones:  servicio ${subsServicio}, monolito ${subsMonolito}`);
console.log(`Eventos:        servicio ${eventsServicio}, monolito ${eventsMonolito}`);
console.log(`Pagos:          servicio ${paymentsServicio}, monolito ${paymentsMonolito}`);
console.log(`Facturas:       servicio ${invServicio}, monolito ${invMonolito}`);
console.log(`Secuencias:     servicio ${seqServicio}, monolito ${seqMonolito}`);

if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

// 1. Detección de conflictos, huérfanos y suscripciones duplicadas antes de modificar la base
const normal = (v) => (v === null || v === undefined || v === '' ? null : String(v));
const mismoNumero = (a, b) => a !== null && b !== null && !Number.isNaN(Number(a)) && Number(a) === Number(b);

const conflictos = [];
const huerfanos = [];

// Validación de existencia de usuarios en el monolito
const usuariosMonolito = new Set(json(base, 'SELECT id FROM usuario').map((u) => String(u.id)));

// Subscriptions
const subsS = json('pagos', 'SELECT id, professional_id, plan_id, status, amount_ars::text, current_period_start::text, current_period_end::text FROM subscriptions');
const subsM = json(base, 'SELECT id, professional_id, plan_id, status, amount_ars::text, current_period_start::text, current_period_end::text FROM subscriptions');
const mapSubsS = new Map(subsS.map((s) => [s.id, s]));
const mapSubsM = new Map(subsM.map((s) => [s.id, s]));
const profToSubIdM = new Map();
for (const m of subsM) {
  profToSubIdM.set(String(m.professional_id), m.id);
}

for (const s of subsS) {
  const profIdStr = String(s.professional_id);
  if (!usuariosMonolito.has(profIdStr)) {
    huerfanos.push({ tabla: 'subscriptions', id: s.id, campo: 'professional_id', usuarioId: s.professional_id });
  }

  // Verificar si el monolito ya tiene otra suscripción para el mismo profesional con un id distinto
  const existingSubIdInM = profToSubIdM.get(profIdStr);
  if (existingSubIdInM !== undefined && String(existingSubIdInM) !== String(s.id)) {
    conflictos.push({
      tabla: 'subscriptions',
      id: s.id,
      campo: `professional_id (el monolito ya tiene la suscripción id=${existingSubIdInM} para el profesional ${s.professional_id})`
    });
  }

  const m = mapSubsM.get(s.id);
  if (m) {
    if (normal(s.professional_id) !== normal(m.professional_id)) {
      conflictos.push({ tabla: 'subscriptions', id: s.id, campo: 'professional_id' });
    }
    if (normal(s.plan_id) !== normal(m.plan_id)) {
      conflictos.push({ tabla: 'subscriptions', id: s.id, campo: 'plan_id' });
    }
  }
}

// Subscription payments
const pagosS = json('pagos', 'SELECT id, subscription_id, professional_id, mp_payment_id, amount_ars::text FROM subscription_payments');
const pagosM = json(base, 'SELECT id, subscription_id, professional_id, mp_payment_id, amount_ars::text FROM subscription_payments');
const mapPagosS = new Map(pagosS.map((p) => [p.id, p]));
const mapPagosM = new Map(pagosM.map((p) => [p.id, p]));

for (const p of pagosS) {
  const profIdStr = String(p.professional_id);
  if (!usuariosMonolito.has(profIdStr)) {
    huerfanos.push({ tabla: 'subscription_payments', id: p.id, campo: 'professional_id', usuarioId: p.professional_id });
  }

  const m = mapPagosM.get(p.id);
  if (m) {
    if (!mismoNumero(p.amount_ars, m.amount_ars)) {
      conflictos.push({ tabla: 'subscription_payments', id: p.id, campo: 'amount_ars' });
    }
    if (normal(p.mp_payment_id) !== normal(m.mp_payment_id)) {
      conflictos.push({ tabla: 'subscription_payments', id: p.id, campo: 'mp_payment_id' });
    }
    if (normal(p.professional_id) !== normal(m.professional_id)) {
      conflictos.push({ tabla: 'subscription_payments', id: p.id, campo: 'professional_id' });
    }
    if (normal(p.subscription_id) !== normal(m.subscription_id)) {
      conflictos.push({ tabla: 'subscription_payments', id: p.id, campo: 'subscription_id' });
    }
  }
}

// Invoices
const invsS = json('pagos', 'SELECT id, payment_id, cbte_tipo, punto_venta, cbte_numero, cae, importe_total::text FROM invoices');
const invsM = json(base, 'SELECT id, payment_id, cbte_tipo, punto_venta, cbte_numero, cae, importe_total::text FROM invoices');
const mapInvsM = new Map(invsM.map((i) => [i.id, i]));

for (const s of invsS) {
  const m = mapInvsM.get(s.id);
  if (m) {
    if (m.cbte_numero !== null && m.cbte_numero !== undefined && normal(s.cbte_numero) !== normal(m.cbte_numero)) {
      conflictos.push({ tabla: 'invoices', id: s.id, campo: 'cbte_numero' });
    }
    if (m.cae !== null && m.cae !== undefined && normal(s.cae) !== normal(m.cae)) {
      conflictos.push({ tabla: 'invoices', id: s.id, campo: 'cae' });
    }
    if (!mismoNumero(s.importe_total, m.importe_total)) {
      conflictos.push({ tabla: 'invoices', id: s.id, campo: 'importe_total' });
    }
    if (normal(s.punto_venta) !== normal(m.punto_venta)) {
      conflictos.push({ tabla: 'invoices', id: s.id, campo: 'punto_venta' });
    }
    if (normal(s.cbte_tipo) !== normal(m.cbte_tipo)) {
      conflictos.push({ tabla: 'invoices', id: s.id, campo: 'cbte_tipo' });
    }
  }
}

// Plans
const plansS = json('pagos', 'SELECT id, code FROM plans');
const plansM = json(base, 'SELECT id, code FROM plans');
const mapPlansM = new Map(plansM.map((p) => [p.id, p]));
for (const s of plansS) {
  const m = mapPlansM.get(s.id);
  if (m && normal(s.code) !== normal(m.code)) {
    conflictos.push({ tabla: 'plans', id: s.id, campo: 'code' });
  }
}

if (conflictos.length > 0) {
  console.error(`Conflictos detectados (${conflictos.length}): un registro existente difiere en datos inmutables:`);
  for (const c of conflictos.slice(0, 50)) {
    console.error(`  - [${c.tabla}] id=${c.id}: difiere en ${c.campo}`);
  }
  if (conflictos.length > 50) console.error(`  ... y ${conflictos.length - 50} más`);
  console.error('Abortando vuelta atrás: ningún registro fue modificado.');
  process.exit(1);
}

if (huerfanos.length > 0) {
  console.error(`Registros con usuarios inexistentes en el monolito (${huerfanos.length}):`);
  for (const h of huerfanos.slice(0, 50)) {
    console.error(`  - [${h.tabla}] id=${h.id}: falta ${h.campo}=${h.usuarioId} en usuario`);
  }
  if (huerfanos.length > 50) console.error(`  ... y ${huerfanos.length - 50} más`);
  console.error('Abortando vuelta atrás: ningún registro fue modificado.');
  process.exit(1);
}

// 2. Extracción de datos del servicio de pagos vía CSV
const COL_PLANS = ['id', 'code', 'name', 'description', 'price_ars', 'price_usd_ref', 'price_ars_anual', 'billing_period', 'min_seats', 'mp_preapproval_plan_id', 'requires_prescriber', 'is_active', 'effective_from', 'effective_to'];
const TIPO_PLANS = { id: 'BIGINT', code: 'VARCHAR(50)', name: 'VARCHAR(100)', description: 'TEXT', price_ars: 'NUMERIC(12,2)', price_usd_ref: 'NUMERIC(10,2)', price_ars_anual: 'NUMERIC(12,2)', billing_period: 'VARCHAR(20)', min_seats: 'INT', mp_preapproval_plan_id: 'VARCHAR(100)', requires_prescriber: 'BOOLEAN', is_active: 'BOOLEAN', effective_from: 'TIMESTAMP', effective_to: 'TIMESTAMP' };

const COL_FEAT = ['feature_key', 'name', 'description'];
const TIPO_FEAT = { feature_key: 'VARCHAR(100)', name: 'VARCHAR(150)', description: 'TEXT' };

const COL_PLAN_FEAT = ['id', 'plan_id', 'feature_key', 'limit_value'];
const TIPO_PLAN_FEAT = { id: 'BIGINT', plan_id: 'BIGINT', feature_key: 'VARCHAR(100)', limit_value: 'INT' };

const COL_SUBS = ['id', 'version', 'professional_id', 'plan_id', 'status', 'seats', 'billing_source', 'mp_preapproval_id', 'amount_ars', 'billing_cycle', 'next_billing_date', 'current_period_start', 'current_period_end', 'grace_until', 'cancel_at_period_end', 'cancelled_at', 'renewal_reminder_sent', 'created_at', 'updated_at'];
const TIPO_SUBS = { id: 'BIGINT', version: 'INT', professional_id: 'BIGINT', plan_id: 'BIGINT', status: 'VARCHAR(30)', seats: 'INT', billing_source: 'VARCHAR(30)', mp_preapproval_id: 'VARCHAR(100)', amount_ars: 'NUMERIC(12,2)', billing_cycle: 'VARCHAR(20)', next_billing_date: 'TIMESTAMP', current_period_start: 'TIMESTAMP', current_period_end: 'TIMESTAMP', grace_until: 'TIMESTAMP', cancel_at_period_end: 'BOOLEAN', cancelled_at: 'TIMESTAMP', renewal_reminder_sent: 'BOOLEAN', created_at: 'TIMESTAMP', updated_at: 'TIMESTAMP' };

const COL_EVENTS = ['id', 'subscription_id', 'event_type', 'previous_status', 'new_status', 'actor_type', 'actor_id', 'payload_json', 'created_at'];
const TIPO_EVENTS = { id: 'BIGINT', subscription_id: 'BIGINT', event_type: 'VARCHAR(100)', previous_status: 'VARCHAR(50)', new_status: 'VARCHAR(50)', actor_type: 'VARCHAR(50)', actor_id: 'VARCHAR(100)', payload_json: 'TEXT', created_at: 'TIMESTAMP' };

const COL_PAYMENTS = ['id', 'subscription_id', 'professional_id', 'mp_payment_id', 'idempotency_key', 'method', 'status', 'amount_ars', 'paid_at', 'period_start', 'period_end', 'invoice_id', 'registered_by_admin_id', 'receipt_reference', 'notes', 'created_at'];
const TIPO_PAYMENTS = { id: 'BIGINT', subscription_id: 'BIGINT', professional_id: 'BIGINT', mp_payment_id: 'VARCHAR(100)', idempotency_key: 'VARCHAR(100)', method: 'VARCHAR(30)', status: 'VARCHAR(30)', amount_ars: 'NUMERIC(12,2)', paid_at: 'TIMESTAMP', period_start: 'TIMESTAMP', period_end: 'TIMESTAMP', invoice_id: 'BIGINT', registered_by_admin_id: 'BIGINT', receipt_reference: 'VARCHAR(150)', notes: 'TEXT', created_at: 'TIMESTAMP' };

const COL_INVOICES = ['id', 'payment_id', 'cbte_tipo', 'punto_venta', 'cbte_numero', 'cae', 'cae_vencimiento', 'receptor_doc_tipo', 'receptor_doc_nro', 'receptor_nombre', 'receptor_condicion_iva', 'importe_total', 'moneda', 'cotizacion', 'fecha_emision', 'pdf_url', 'pdf_base64', 'status', 'attempts', 'last_error', 'arca_request_json', 'arca_response_json', 'comprobante_asociado_id', 'created_at', 'issued_at'];
const TIPO_INVOICES = { id: 'BIGINT', payment_id: 'BIGINT', cbte_tipo: 'INT', punto_venta: 'INT', cbte_numero: 'BIGINT', cae: 'VARCHAR(30)', cae_vencimiento: 'DATE', receptor_doc_tipo: 'INT', receptor_doc_nro: 'BIGINT', receptor_nombre: 'VARCHAR(200)', receptor_condicion_iva: 'INT', importe_total: 'NUMERIC(12,2)', moneda: 'VARCHAR(10)', cotizacion: 'NUMERIC(8,4)', fecha_emision: 'DATE', pdf_url: 'TEXT', pdf_base64: 'TEXT', status: 'VARCHAR(30)', attempts: 'INT', last_error: 'TEXT', arca_request_json: 'TEXT', arca_response_json: 'TEXT', comprobante_asociado_id: 'BIGINT', created_at: 'TIMESTAMP', issued_at: 'TIMESTAMP' };

const COL_SEQS = ['id', 'punto_venta', 'cbte_tipo', 'last_number', 'updated_at'];
const TIPO_SEQS = { id: 'BIGINT', punto_venta: 'INT', cbte_tipo: 'INT', last_number: 'BIGINT', updated_at: 'TIMESTAMP' };

const csvPlans = psql('pagos', ['-c', `\\copy (SELECT ${COL_PLANS.join(', ')} FROM plans ORDER BY id) TO STDOUT WITH CSV`]);
const csvFeat = psql('pagos', ['-c', `\\copy (SELECT ${COL_FEAT.join(', ')} FROM features ORDER BY feature_key) TO STDOUT WITH CSV`]);
const csvPlanFeat = psql('pagos', ['-c', `\\copy (SELECT ${COL_PLAN_FEAT.join(', ')} FROM plan_features ORDER BY id) TO STDOUT WITH CSV`]);
const csvSubs = psql('pagos', ['-c', `\\copy (SELECT ${COL_SUBS.join(', ')} FROM subscriptions ORDER BY id) TO STDOUT WITH CSV`]);
const csvEvents = psql('pagos', ['-c', `\\copy (SELECT ${COL_EVENTS.join(', ')} FROM subscription_events ORDER BY id) TO STDOUT WITH CSV`]);
const csvPayments = psql('pagos', ['-c', `\\copy (SELECT ${COL_PAYMENTS.join(', ')} FROM subscription_payments ORDER BY id) TO STDOUT WITH CSV`]);
const csvInvoices = psql('pagos', ['-c', `\\copy (SELECT ${COL_INVOICES.join(', ')} FROM invoices ORDER BY id) TO STDOUT WITH CSV`]);
const csvSeqs = psql('pagos', ['-c', `\\copy (SELECT ${COL_SEQS.join(', ')} FROM invoice_sequences ORDER BY id) TO STDOUT WITH CSV`]);

const def = (cols, tipos) => cols.map((c) => `${c} ${tipos[c]}`).join(', ');

const carga = [
  'BEGIN;',
  // Tablas temporales
  `CREATE TEMP TABLE dev_plans (${def(COL_PLANS, TIPO_PLANS)}) ON COMMIT DROP;`,
  '\\copy dev_plans FROM STDIN WITH CSV',
  csvPlans.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_features (${def(COL_FEAT, TIPO_FEAT)}) ON COMMIT DROP;`,
  '\\copy dev_features FROM STDIN WITH CSV',
  csvFeat.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_plan_features (${def(COL_PLAN_FEAT, TIPO_PLAN_FEAT)}) ON COMMIT DROP;`,
  '\\copy dev_plan_features FROM STDIN WITH CSV',
  csvPlanFeat.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_subscriptions (${def(COL_SUBS, TIPO_SUBS)}) ON COMMIT DROP;`,
  '\\copy dev_subscriptions FROM STDIN WITH CSV',
  csvSubs.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_subscription_events (${def(COL_EVENTS, TIPO_EVENTS)}) ON COMMIT DROP;`,
  '\\copy dev_subscription_events FROM STDIN WITH CSV',
  csvEvents.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_subscription_payments (${def(COL_PAYMENTS, TIPO_PAYMENTS)}) ON COMMIT DROP;`,
  '\\copy dev_subscription_payments FROM STDIN WITH CSV',
  csvPayments.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_invoices (${def(COL_INVOICES, TIPO_INVOICES)}) ON COMMIT DROP;`,
  '\\copy dev_invoices FROM STDIN WITH CSV',
  csvInvoices.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_invoice_sequences (${def(COL_SEQS, TIPO_SEQS)}) ON COMMIT DROP;`,
  '\\copy dev_invoice_sequences FROM STDIN WITH CSV',
  csvSeqs.trimEnd(),
  '\\.',

  // 1. Features
  `INSERT INTO features (${COL_FEAT.join(', ')}) SELECT ${COL_FEAT.map((c) => `d.${c}`).join(', ')} FROM dev_features d WHERE NOT EXISTS (SELECT 1 FROM features m WHERE m.feature_key = d.feature_key);`,
  "UPDATE features m SET name = d.name, description = d.description FROM dev_features d WHERE m.feature_key = d.feature_key AND (m.name, COALESCE(m.description, '')) IS DISTINCT FROM (d.name, COALESCE(d.description, ''));",

  // 2. Plans
  `INSERT INTO plans (${COL_PLANS.join(', ')}) SELECT ${COL_PLANS.map((c) => `d.${c}`).join(', ')} FROM dev_plans d WHERE NOT EXISTS (SELECT 1 FROM plans m WHERE m.id = d.id);`,
  "UPDATE plans m SET price_ars = d.price_ars, price_usd_ref = d.price_usd_ref, price_ars_anual = d.price_ars_anual, name = d.name, description = d.description, is_active = d.is_active, effective_from = d.effective_from, effective_to = d.effective_to FROM dev_plans d WHERE m.id = d.id AND (m.price_ars, m.price_usd_ref, m.price_ars_anual, m.name, COALESCE(m.description, ''), m.is_active, m.effective_from, m.effective_to) IS DISTINCT FROM (d.price_ars, d.price_usd_ref, d.price_ars_anual, d.name, COALESCE(d.description, ''), d.is_active, d.effective_from, d.effective_to);",

  // 3. Plan features
  `INSERT INTO plan_features (${COL_PLAN_FEAT.join(', ')}) SELECT ${COL_PLAN_FEAT.map((c) => `d.${c}`).join(', ')} FROM dev_plan_features d WHERE NOT EXISTS (SELECT 1 FROM plan_features m WHERE m.id = d.id);`,
  'UPDATE plan_features m SET limit_value = d.limit_value FROM dev_plan_features d WHERE m.id = d.id AND m.limit_value IS DISTINCT FROM d.limit_value;',

  // 4. Subscriptions
  `INSERT INTO subscriptions (${COL_SUBS.join(', ')}) SELECT ${COL_SUBS.map((c) => `d.${c}`).join(', ')} FROM dev_subscriptions d WHERE NOT EXISTS (SELECT 1 FROM subscriptions m WHERE m.id = d.id);`,
  'UPDATE subscriptions m SET status = d.status, amount_ars = d.amount_ars, current_period_start = d.current_period_start, current_period_end = d.current_period_end, grace_until = d.grace_until, cancel_at_period_end = d.cancel_at_period_end, cancelled_at = d.cancelled_at, mp_preapproval_id = d.mp_preapproval_id, version = GREATEST(COALESCE(m.version, 0), d.version), renewal_reminder_sent = d.renewal_reminder_sent, seats = d.seats, billing_cycle = d.billing_cycle, next_billing_date = d.next_billing_date, updated_at = d.updated_at FROM dev_subscriptions d WHERE m.id = d.id AND (m.status, m.amount_ars, m.current_period_start, m.current_period_end, m.grace_until, m.cancel_at_period_end, m.cancelled_at, m.mp_preapproval_id, m.renewal_reminder_sent, m.seats, m.billing_cycle, m.next_billing_date) IS DISTINCT FROM (d.status, d.amount_ars, d.current_period_start, d.current_period_end, d.grace_until, d.cancel_at_period_end, d.cancelled_at, d.mp_preapproval_id, d.renewal_reminder_sent, d.seats, d.billing_cycle, d.next_billing_date);',

  // 5. Subscription events
  `INSERT INTO subscription_events (${COL_EVENTS.join(', ')}) SELECT ${COL_EVENTS.map((c) => `d.${c}`).join(', ')} FROM dev_subscription_events d WHERE NOT EXISTS (SELECT 1 FROM subscription_events m WHERE m.id = d.id);`,

  // 6. Subscription payments
  `INSERT INTO subscription_payments (${COL_PAYMENTS.join(', ')}) SELECT ${COL_PAYMENTS.map((c) => `d.${c}`).join(', ')} FROM dev_subscription_payments d WHERE NOT EXISTS (SELECT 1 FROM subscription_payments m WHERE m.id = d.id);`,
  'UPDATE subscription_payments m SET status = d.status, invoice_id = d.invoice_id FROM dev_subscription_payments d WHERE m.id = d.id AND (m.status, m.invoice_id) IS DISTINCT FROM (d.status, d.invoice_id);',

  // 7. Invoices
  `INSERT INTO invoices (${COL_INVOICES.join(', ')}) SELECT ${COL_INVOICES.map((c) => `d.${c}`).join(', ')} FROM dev_invoices d WHERE NOT EXISTS (SELECT 1 FROM invoices m WHERE m.id = d.id);`,
  "UPDATE invoices m SET status = d.status, cae = d.cae, cae_vencimiento = d.cae_vencimiento, cbte_numero = d.cbte_numero, pdf_url = d.pdf_url, pdf_base64 = d.pdf_base64, attempts = d.attempts, last_error = d.last_error, arca_request_json = d.arca_request_json, arca_response_json = d.arca_response_json, issued_at = d.issued_at FROM dev_invoices d WHERE m.id = d.id AND (m.status, m.cae, m.cae_vencimiento, m.cbte_numero, m.pdf_url, m.pdf_base64, m.attempts, COALESCE(m.last_error, ''), COALESCE(m.arca_request_json, ''), COALESCE(m.arca_response_json, ''), m.issued_at) IS DISTINCT FROM (d.status, d.cae, d.cae_vencimiento, d.cbte_numero, d.pdf_url, d.pdf_base64, d.attempts, COALESCE(d.last_error, ''), COALESCE(d.arca_request_json, ''), COALESCE(d.arca_response_json, ''), d.issued_at);",

  // 8. Invoice sequences: last_number SOLO si el del servicio es mayor
  `INSERT INTO invoice_sequences (${COL_SEQS.join(', ')}) SELECT ${COL_SEQS.map((c) => `d.${c}`).join(', ')} FROM dev_invoice_sequences d WHERE NOT EXISTS (SELECT 1 FROM invoice_sequences m WHERE m.punto_venta = d.punto_venta AND m.cbte_tipo = d.cbte_tipo);`,
  'UPDATE invoice_sequences m SET last_number = d.last_number, updated_at = d.updated_at FROM dev_invoice_sequences d WHERE m.punto_venta = d.punto_venta AND m.cbte_tipo = d.cbte_tipo AND d.last_number > m.last_number;',

  // Ajuste de secuencias
  "SELECT setval(COALESCE(pg_get_serial_sequence('plans', 'id'), 'plans_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM plans), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('plan_features', 'id'), 'plan_features_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM plan_features), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('subscriptions', 'id'), 'subscriptions_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM subscriptions), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('subscription_events', 'id'), 'subscription_events_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM subscription_events), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('subscription_payments', 'id'), 'subscription_payments_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM subscription_payments), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('invoices', 'id'), 'invoices_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM invoices), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('invoice_sequences', 'id'), 'invoice_sequences_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM invoice_sequences), 1));",

  'COMMIT;',
  '',
].join('\n');

psql(base, ['-q', '-f', '-'], carga);

console.log(`Listo: el monolito tiene ${numero(base, 'SELECT count(*) FROM plans')} planes, `
  + `${numero(base, 'SELECT count(*) FROM features')} features, `
  + `${numero(base, 'SELECT count(*) FROM plan_features')} plan-features, `
  + `${numero(base, 'SELECT count(*) FROM subscriptions')} suscripciones, `
  + `${numero(base, 'SELECT count(*) FROM subscription_events')} eventos, `
  + `${numero(base, 'SELECT count(*) FROM subscription_payments')} pagos, `
  + `${numero(base, 'SELECT count(*) FROM invoices')} facturas y `
  + `${numero(base, 'SELECT count(*) FROM invoice_sequences')} secuencias.`);
console.log('Conciliá con: node scripts/fase8-conciliar-suscripciones.mjs');
