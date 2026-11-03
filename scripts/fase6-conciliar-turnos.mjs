#!/usr/bin/env node
// Fase 6 (turnos): conciliación entre el servicio de turnos y la copia del monolito (la leen las estadísticas,
// la historia clínica y las recetas). Compara:
// - cuántos turnos hay por estado en cada lado;
// - turno por turno: profesional, paciente, fecha, horas, estado, asistencia, precio, documento enviado, el
//   Meet y el estado del pago.
//
// Solo lee. Sale con código 1 si hay diferencias, para usarlo a diario mientras dura la transición. Una
// diferencia de hasta 15 minutos puede ser normal (la resincronización del monolito no pasó todavía).
//
// Uso (por defecto, el entorno local):
//   node scripts/fase6-conciliar-turnos.mjs
// Producción (en el VPS):
//   node scripts/fase6-conciliar-turnos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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

const CAMPOS = ['medico_id', 'paciente_id', 'fecha', 'hora_inicio', 'hora_fin', 'estado', 'asistencia', 'precio',
  'documento_enviado', 'telemedicina_url', 'pago_estado'];
// Las mismas columnas en las dos tablas; t es el alias de la tabla ('' en el servicio, 't.' en el monolito).
const comunes = (t) => `${t}id AS id, ${t}medico_id AS medico_id, ${t}paciente_id AS paciente_id, ${t}fecha::text AS fecha, `
  + `${t}hora_inicio::text AS hora_inicio, ${t}hora_fin::text AS hora_fin, ${t}estado AS estado, ${t}asistencia AS asistencia, `
  + `${t}precio AS precio, ${t}documento_enviado AS documento_enviado, ${t}telemedicina_url AS telemedicina_url`;
const servicio = new Map(json('turnos', `SELECT ${comunes('')}, pago_estado FROM turno`).map((t) => [t.id, t]));
const monolito = new Map(json(base, `SELECT ${comunes('t.')}, p.estado AS pago_estado FROM turno t `
  + 'LEFT JOIN pago p ON p.turno_id = t.id').map((t) => [t.id, t]));

const porEstado = (mapa) => {
  const c = {};
  for (const t of mapa.values()) c[t.estado] = (c[t.estado] ?? 0) + 1;
  return c;
};
const normal = (v) => (v === null || v === undefined || v === '' ? null : String(v));
const mismoNumero = (a, b) => a !== null && b !== null && !Number.isNaN(Number(a)) && Number(a) === Number(b);

const diferencias = [];
for (const [id, s] of servicio) {
  const m = monolito.get(id);
  if (!m) {
    diferencias.push(`${id}: falta en el monolito (${s.estado})`);
    continue;
  }
  for (const campo of CAMPOS) {
    const a = normal(s[campo]);
    const b = normal(m[campo]);
    if (a !== b && !mismoNumero(a, b)) diferencias.push(`${id}: ${campo} servicio=${a} monolito=${b}`);
  }
}
for (const id of monolito.keys()) {
  if (!servicio.has(id)) diferencias.push(`${id}: está en el monolito y no en el servicio`);
}

console.log(`Turnos: servicio ${servicio.size} ${JSON.stringify(porEstado(servicio))}, monolito ${monolito.size} ${JSON.stringify(porEstado(monolito))}`);
if (diferencias.length === 0) {
  console.log('Sin diferencias.');
  process.exit(0);
}
console.log(`Diferencias (${diferencias.length}):`);
for (const d of diferencias.slice(0, 200)) console.log(`  - ${d}`);
if (diferencias.length > 200) console.log(`  ... y ${diferencias.length - 200} más`);
process.exit(1);
