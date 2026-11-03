#!/usr/bin/env node
// Fase 7 (historia clínica y recetas): conciliación entre el servicio de profesionales y el monolito.
// Compara las cuatro tablas clínicas:
// - Cantidades y desglose por estado en cada lado;
// - Ids faltantes en cada lado;
// - Asiento por asiento: estado y hash_integridad en seguimientos e informes;
// - Para recetas y solicitudes: campos principales (sin contenido clínico ni diagnósticos).
//
// Solo lee. Termina con código 0 si coinciden y 1 si hay diferencias.
// Por privacidad y Ley 26.529, nunca imprime síntomas, notas, planes ni contenido clínico.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase7-conciliar-clinica.mjs
// Producción (en el VPS):
//   node scripts/fase7-conciliar-clinica.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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
const mismoNumero = (a, b) => a !== null && b !== null && !Number.isNaN(Number(a)) && Number(a) === Number(b);

const porEstado = (items) => {
  const c = {};
  for (const it of items) c[it.estado ?? 'SIN_ESTADO'] = (c[it.estado ?? 'SIN_ESTADO'] ?? 0) + 1;
  return c;
};

const diferencias = [];

// 1. Seguimiento diario (campos a comparar: estado, hash_integridad, paciente_id, medico_id, fecha)
const segServ = json('profesionales', 'SELECT id, paciente_id, medico_id, fecha::text, estado, hash_integridad FROM seguimiento_diario');
const segMono = json(base, 'SELECT id, paciente_id, medico_id, fecha::text, estado, hash_integridad FROM seguimiento_diario');
const mapSegServ = new Map(segServ.map((s) => [s.id, s]));
const mapSegMono = new Map(segMono.map((s) => [s.id, s]));

for (const [id, s] of mapSegServ) {
  const m = mapSegMono.get(id);
  if (!m) {
    diferencias.push(`[seguimiento_diario] id=${id}: falta en el monolito (estado=${s.estado})`);
    continue;
  }
  for (const c of ['estado', 'hash_integridad', 'paciente_id', 'medico_id', 'fecha']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[seguimiento_diario] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapSegMono.keys()) {
  if (!mapSegServ.has(id)) diferencias.push(`[seguimiento_diario] id=${id}: está en el monolito y no en el servicio`);
}

// 2. Informe clínico (campos a comparar: estado, hash_integridad, informe_original_id, tipo_informe, paciente_id, medico_id, fecha)
const infServ = json('profesionales', 'SELECT id, paciente_id, medico_id, fecha::text, tipo_informe, estado, informe_original_id, hash_integridad FROM informe_clinico');
const infMono = json(base, 'SELECT id, paciente_id, medico_id, fecha::text, tipo_informe, estado, informe_original_id, hash_integridad FROM informe_clinico');
const mapInfServ = new Map(infServ.map((i) => [i.id, i]));
const mapInfMono = new Map(infMono.map((i) => [i.id, i]));

for (const [id, s] of mapInfServ) {
  const m = mapInfMono.get(id);
  if (!m) {
    diferencias.push(`[informe_clinico] id=${id}: falta en el monolito (estado=${s.estado})`);
    continue;
  }
  for (const c of ['estado', 'hash_integridad', 'informe_original_id', 'tipo_informe', 'paciente_id', 'medico_id', 'fecha']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[informe_clinico] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapInfMono.keys()) {
  if (!mapInfServ.has(id)) diferencias.push(`[informe_clinico] id=${id}: está en el monolito y no en el servicio`);
}

// 3. Receta (campos principales: medico_id, paciente_id, qbi2_id_receta, fecha_emision; NO se imprime ni compara texto de medicamentos)
const recServ = json('profesionales', 'SELECT id, medico_id, paciente_id, qbi2_id_receta, fecha_emision::text FROM receta');
const recMono = json(base, 'SELECT id, medico_id, paciente_id, qbi2_id_receta, fecha_emision::text FROM receta');
const mapRecServ = new Map(recServ.map((r) => [r.id, r]));
const mapRecMono = new Map(recMono.map((r) => [r.id, r]));

for (const [id, s] of mapRecServ) {
  const m = mapRecMono.get(id);
  if (!m) {
    diferencias.push(`[receta] id=${id}: falta en el monolito`);
    continue;
  }
  for (const c of ['medico_id', 'paciente_id', 'qbi2_id_receta', 'fecha_emision']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[receta] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
}
for (const id of mapRecMono.keys()) {
  if (!mapRecServ.has(id)) diferencias.push(`[receta] id=${id}: está en el monolito y no en el servicio`);
}

// 4. Solicitud de documento (campos principales: paciente_id, medico_id, tipo_concepto, precio, estado, transaction_id, emitido)
const solServ = json('profesionales', 'SELECT id, paciente_id, medico_id, tipo_concepto, precio, estado, transaction_id, emitido FROM solicitud_documento');
const solMono = json(base, 'SELECT id, paciente_id, medico_id, tipo_concepto, precio, estado, transaction_id, emitido FROM solicitud_documento');
const mapSolServ = new Map(solServ.map((d) => [d.id, d]));
const mapSolMono = new Map(solMono.map((d) => [d.id, d]));

for (const [id, s] of mapSolServ) {
  const m = mapSolMono.get(id);
  if (!m) {
    diferencias.push(`[solicitud_documento] id=${id}: falta en el monolito (estado=${s.estado})`);
    continue;
  }
  for (const c of ['paciente_id', 'medico_id', 'tipo_concepto', 'estado', 'transaction_id', 'emitido']) {
    if (normal(s[c]) !== normal(m[c])) {
      diferencias.push(`[solicitud_documento] id=${id}: ${c} servicio=${s[c]} monolito=${m[c]}`);
    }
  }
  if (!mismoNumero(s.precio, m.precio)) {
    diferencias.push(`[solicitud_documento] id=${id}: precio servicio=${s.precio} monolito=${m.precio}`);
  }
}
for (const id of mapSolMono.keys()) {
  if (!mapSolServ.has(id)) diferencias.push(`[solicitud_documento] id=${id}: está en el monolito y no en el servicio`);
}

console.log(`Seguimientos: servicio ${segServ.length} ${JSON.stringify(porEstado(segServ))}, monolito ${segMono.length} ${JSON.stringify(porEstado(segMono))}`);
console.log(`Informes:     servicio ${infServ.length} ${JSON.stringify(porEstado(infServ))}, monolito ${infMono.length} ${JSON.stringify(porEstado(infMono))}`);
console.log(`Recetas:      servicio ${recServ.length}, monolito ${recMono.length}`);
console.log(`Solicitudes:  servicio ${solServ.length} ${JSON.stringify(porEstado(solServ))}, monolito ${solMono.length} ${JSON.stringify(porEstado(solMono))}`);

if (diferencias.length === 0) {
  console.log('Sin diferencias en la historia clínica, recetas ni solicitudes.');
  process.exit(0);
}

console.log(`Diferencias encontradas (${diferencias.length}):`);
for (const d of diferencias.slice(0, 200)) console.log(`  - ${d}`);
if (diferencias.length > 200) console.log(`  ... y ${diferencias.length - 200} más`);
process.exit(1);
