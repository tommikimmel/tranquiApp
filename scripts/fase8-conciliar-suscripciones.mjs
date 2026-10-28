#!/usr/bin/env node
// Fase 8 (suscripciones, planes y facturación): conciliación entre el servicio de pagos y el monolito.
// Compara las ocho tablas de suscripciones y facturación:
// - Cantidades y desglose por estado en cada lado;
// - Ids faltantes en cada lado;
// - Fila por fila, los campos principales:
//   * suscripciones: estado, período, montos, preapproval;
//   * pagos: estado, monto y mp_payment_id;
//   * facturas: número, CAE y estado (sin datos fiscales ni PDF);
//   * secuencias: last_number;
//   * planes: precios, código y estado.
//
// Solo lee. Termina con código 0 si coinciden y 1 si hay diferencias.
// Por privacidad y secreto fiscal, nunca imprime datos de receptores fiscales ni PDFs.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase8-conciliar-suscripciones.mjs
// Producción (en el VPS):
//   node scripts/fase8-conciliar-suscripciones.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
import { execFileSync } from 'node:child_process';

const argv = process.argv.slice(2);
const opcion = (nombre, porDefecto) => {
  const i = argv.indexOf(`--${nombre}`);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : porDefecto;
};
const proyecto = opcion('proyecto', 'tranqui-local');
const contenedor = opcion('contenedor', 'db');
const usuario = opcion('usuario', 'tranqui');
const base = opcion('base', 'tranqui_local');

function json(baseDatos, sql) {
  const salida = execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-v', 'ON_ERROR_STOP=1', '-U', usuario, '-d', baseDatos, '-tA', '-c',
    `SELECT COALESCE(json_agg(x), '[]') FROM (${sql}) x`],
  { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
  return JSON.parse(salida.trim());
}

const normal = (v) => (v === null || v === undefined || v === '' ? null : String(v));
const normalFecha = (v) => (v === null || v === undefined || v === '' ? null : String(v).replace('T', ' ').replace(/\.\d+/, '').trim());
const mismoNumero = (a, b) => a !== null && b !== null && !Number.isNaN(Number(a)) && Number(a) === Number(b);

const porEstado = (items) => {
  const c = {};
  for (const it of items) c[it.status ?? 'SIN_ESTADO'] = (c[it.status ?? 'SIN_ESTADO'] ?? 0) + 1;
  return c;
};

const diferencias = [];

// 1. Planes (id, code, name, price_ars, price_ars_anual, is_active)
const plansServ = json('pagos', 'SELECT id, code, name, price_ars::text, price_ars_anual::text, is_active FROM plans');
const plansMono = json(base, 'SELECT id, code, name, price_ars::text, price_ars_anual::text, is_active FROM plans');
const mapPlansServ = new Map(plansServ.map((p) => [p.id, p]));
const mapPlansMono = new Map(plansMono.map((p) => [p.id, p]));

for (const [id, s] of mapPlansServ) {
  const m = mapPlansMono.get(id);
  if (!m) {
    diferencias.push(`[plans] id=${id}: falta en el monolito (code=${s.code})`);
    continue;
  }
  for (const c of ['code', 'name', 'is_active']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[plans] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
  for (const c of ['price_ars', 'price_ars_anual']) {
    if (!mismoNumero(s[c], m[c])) {
      diferencias.push(`[plans] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapPlansMono.keys()) {
  if (!mapPlansServ.has(id)) diferencias.push(`[plans] id=${id}: está en el monolito y no en el servicio`);
}

// 2. Features (feature_key, name)
const featServ = json('pagos', 'SELECT feature_key, name FROM features');
const featMono = json(base, 'SELECT feature_key, name FROM features');
const mapFeatServ = new Map(featServ.map((f) => [f.feature_key, f]));
const mapFeatMono = new Map(featMono.map((f) => [f.feature_key, f]));

for (const [key, s] of mapFeatServ) {
  const m = mapFeatMono.get(key);
  if (!m) {
    diferencias.push(`[features] key=${key}: falta en el monolito`);
    continue;
  }
  if (normal(s.name) !== normal(m.name)) {
    diferencias.push(`[features] key=${key}: name servicio=${s.name} monolito=${m.name}`);
  }
}
for (const key of mapFeatMono.keys()) {
  if (!mapFeatServ.has(key)) diferencias.push(`[features] key=${key}: está en el monolito y no en el servicio`);
}

// 3. Plan Features (id, plan_id, feature_key, limit_value)
const planFeatServ = json('pagos', 'SELECT id, plan_id, feature_key, limit_value FROM plan_features');
const planFeatMono = json(base, 'SELECT id, plan_id, feature_key, limit_value FROM plan_features');
const mapPlanFeatServ = new Map(planFeatServ.map((pf) => [pf.id, pf]));
const mapPlanFeatMono = new Map(planFeatMono.map((pf) => [pf.id, pf]));

for (const [id, s] of mapPlanFeatServ) {
  const m = mapPlanFeatMono.get(id);
  if (!m) {
    diferencias.push(`[plan_features] id=${id}: falta en el monolito`);
    continue;
  }
  for (const c of ['plan_id', 'feature_key', 'limit_value']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[plan_features] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapPlanFeatMono.keys()) {
  if (!mapPlanFeatServ.has(id)) diferencias.push(`[plan_features] id=${id}: está en el monolito y no en el servicio`);
}

// 4. Subscriptions (id, professional_id, plan_id, status, amount_ars, current_period_start, current_period_end, grace_until, cancel_at_period_end, mp_preapproval_id)
const subsServ = json('pagos', 'SELECT id, professional_id, plan_id, status, amount_ars::text, current_period_start::text, current_period_end::text, grace_until::text, cancel_at_period_end, mp_preapproval_id FROM subscriptions');
const subsMono = json(base, 'SELECT id, professional_id, plan_id, status, amount_ars::text, current_period_start::text, current_period_end::text, grace_until::text, cancel_at_period_end, mp_preapproval_id FROM subscriptions');
const mapSubsServ = new Map(subsServ.map((s) => [s.id, s]));
const mapSubsMono = new Map(subsMono.map((s) => [s.id, s]));

for (const [id, s] of mapSubsServ) {
  const m = mapSubsMono.get(id);
  if (!m) {
    diferencias.push(`[subscriptions] id=${id}: falta en el monolito (status=${s.status})`);
    continue;
  }
  for (const c of ['professional_id', 'plan_id', 'status', 'cancel_at_period_end', 'mp_preapproval_id']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[subscriptions] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
  if (!mismoNumero(s.amount_ars, m.amount_ars)) {
    diferencias.push(`[subscriptions] id=${id}: amount_ars servicio=${s.amount_ars} monolito=${m.amount_ars}`);
  }
  for (const c of ['current_period_start', 'current_period_end', 'grace_until']) {
    if (normalFecha(s[c]) !== normalFecha(m[c])) {
      diferencias.push(`[subscriptions] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapSubsMono.keys()) {
  if (!mapSubsServ.has(id)) diferencias.push(`[subscriptions] id=${id}: está en el monolito y no en el servicio`);
}

// 5. Subscription Events (id, subscription_id, event_type, previous_status, new_status)
const eventsServ = json('pagos', 'SELECT id, subscription_id, event_type, previous_status, new_status FROM subscription_events');
const eventsMono = json(base, 'SELECT id, subscription_id, event_type, previous_status, new_status FROM subscription_events');
const mapEventsServ = new Map(eventsServ.map((e) => [e.id, e]));
const mapEventsMono = new Map(eventsMono.map((e) => [e.id, e]));

for (const [id, s] of mapEventsServ) {
  const m = mapEventsMono.get(id);
  if (!m) {
    diferencias.push(`[subscription_events] id=${id}: falta en el monolito (event_type=${s.event_type})`);
    continue;
  }
  for (const c of ['subscription_id', 'event_type', 'previous_status', 'new_status']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[subscription_events] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapEventsMono.keys()) {
  if (!mapEventsServ.has(id)) diferencias.push(`[subscription_events] id=${id}: está en el monolito y no en el servicio`);
}

// 6. Subscription Payments (id, subscription_id, professional_id, mp_payment_id, status, amount_ars, invoice_id)
const pagosServ = json('pagos', 'SELECT id, subscription_id, professional_id, mp_payment_id, status, amount_ars::text, invoice_id FROM subscription_payments');
const pagosMono = json(base, 'SELECT id, subscription_id, professional_id, mp_payment_id, status, amount_ars::text, invoice_id FROM subscription_payments');
const mapPagosServ = new Map(pagosServ.map((p) => [p.id, p]));
const mapPagosMono = new Map(pagosMono.map((p) => [p.id, p]));

for (const [id, s] of mapPagosServ) {
  const m = mapPagosMono.get(id);
  if (!m) {
    diferencias.push(`[subscription_payments] id=${id}: falta en el monolito (status=${s.status})`);
    continue;
  }
  for (const c of ['subscription_id', 'professional_id', 'mp_payment_id', 'status', 'invoice_id']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[subscription_payments] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
  if (!mismoNumero(s.amount_ars, m.amount_ars)) {
    diferencias.push(`[subscription_payments] id=${id}: amount_ars servicio=${s.amount_ars} monolito=${m.amount_ars}`);
  }
}
for (const id of mapPagosMono.keys()) {
  if (!mapPagosServ.has(id)) diferencias.push(`[subscription_payments] id=${id}: está en el monolito y no en el servicio`);
}

// 7. Invoices (id, payment_id, cbte_tipo, punto_venta, cbte_numero, cae, status, importe_total)
// NOTA: No se consulta ni imprime receptor_doc_nro, receptor_nombre, domicilio ni pdf
const invsServ = json('pagos', 'SELECT id, payment_id, cbte_tipo, punto_venta, cbte_numero, cae, status, importe_total::text FROM invoices');
const invsMono = json(base, 'SELECT id, payment_id, cbte_tipo, punto_venta, cbte_numero, cae, status, importe_total::text FROM invoices');
const mapInvsServ = new Map(invsServ.map((i) => [i.id, i]));
const mapInvsMono = new Map(invsMono.map((i) => [i.id, i]));

for (const [id, s] of mapInvsServ) {
  const m = mapInvsMono.get(id);
  if (!m) {
    diferencias.push(`[invoices] id=${id}: falta en el monolito (status=${s.status})`);
    continue;
  }
  for (const c of ['payment_id', 'cbte_tipo', 'punto_venta', 'cbte_numero', 'cae', 'status']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[invoices] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
  if (!mismoNumero(s.importe_total, m.importe_total)) {
    diferencias.push(`[invoices] id=${id}: importe_total servicio=${s.importe_total} monolito=${m.importe_total}`);
  }
}
for (const id of mapInvsMono.keys()) {
  if (!mapInvsServ.has(id)) diferencias.push(`[invoices] id=${id}: está en el monolito y no en el servicio`);
}

// 8. Invoice Sequences (id, punto_venta, cbte_tipo, last_number)
const seqServ = json('pagos', 'SELECT id, punto_venta, cbte_tipo, last_number FROM invoice_sequences');
const seqMono = json(base, 'SELECT id, punto_venta, cbte_tipo, last_number FROM invoice_sequences');
const mapSeqServ = new Map(seqServ.map((sq) => [`${sq.punto_venta}-${sq.cbte_tipo}`, sq]));
const mapSeqMono = new Map(seqMono.map((sq) => [`${sq.punto_venta}-${sq.cbte_tipo}`, sq]));

for (const [key, s] of mapSeqServ) {
  const m = mapSeqMono.get(key);
  if (!m) {
    diferencias.push(`[invoice_sequences] ${key}: falta en el monolito`);
    continue;
  }
  if (!mismoNumero(s.last_number, m.last_number)) {
    diferencias.push(`[invoice_sequences] ${key}: last_number servicio=${s.last_number} monolito=${m.last_number}`);
  }
}
for (const key of mapSeqMono.keys()) {
  if (!mapSeqServ.has(key)) diferencias.push(`[invoice_sequences] ${key}: está en el monolito y no en el servicio`);
}

// Resumen legible
console.log(`Planes:         servicio ${plansServ.length}, monolito ${plansMono.length}`);
console.log(`Features:       servicio ${featServ.length}, monolito ${featMono.length}`);
console.log(`Plan Features:  servicio ${planFeatServ.length}, monolito ${planFeatMono.length}`);
console.log(`Suscripciones:  servicio ${subsServ.length} ${JSON.stringify(porEstado(subsServ))}, monolito ${subsMono.length} ${JSON.stringify(porEstado(subsMono))}`);
console.log(`Eventos:        servicio ${eventsServ.length}, monolito ${eventsMono.length}`);
console.log(`Pagos:          servicio ${pagosServ.length} ${JSON.stringify(porEstado(pagosServ))}, monolito ${pagosMono.length} ${JSON.stringify(porEstado(pagosMono))}`);
console.log(`Facturas:       servicio ${invsServ.length} ${JSON.stringify(porEstado(invsServ))}, monolito ${invsMono.length} ${JSON.stringify(porEstado(invsMono))}`);
console.log(`Secuencias:     servicio ${seqServ.length}, monolito ${seqMono.length}`);

if (diferencias.length === 0) {
  console.log('Sin diferencias en las ocho tablas de suscripciones y facturación.');
  process.exit(0);
}

console.log(`Diferencias encontradas (${diferencias.length}):`);
for (const d of diferencias.slice(0, 200)) console.log(`  - ${d}`);
if (diferencias.length > 200) console.log(`  ... y ${diferencias.length - 200} más`);
process.exit(1);
