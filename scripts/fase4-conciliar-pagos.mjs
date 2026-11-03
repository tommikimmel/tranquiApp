#!/usr/bin/env node
// Fase 4 (pagos): conciliación entre el servicio de pagos y la copia del monolito. Criterio de salida del
// plan: ningún pago perdido ni duplicado. Compara por transaction_id (el id del pago en Mercado Pago) los
// pagos aprobados y reembolsados de turnos, sus estados y montos, y los documentos de la API de conceptos.
// Los rechazos no se comparan: el monolito guarda un solo pago por turno.
//
// Solo lee. Sale con código 1 si hay diferencias, para usarlo a diario mientras dura la transición.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase4-conciliar-pagos.mjs
// Producción (en el VPS):
//   node scripts/fase4-conciliar-pagos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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

function filas(baseDatos, sql) {
  const salida = execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-v', 'ON_ERROR_STOP=1', '-U', usuario, '-d', baseDatos, '-tA', '-F', '|', '-c', sql],
  { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
  return salida.split('\n').filter(Boolean).map((linea) => linea.split('|'));
}

const porTransaccion = (lista) => new Map(lista.map(([tx, ...resto]) => [tx, resto]));

// [transaction_id, turno_id, estado, monto]
const servicio = porTransaccion(filas('pagos',
  "SELECT transaction_id, turno_id, estado, monto FROM pago WHERE turno_id IS NOT NULL AND estado IN ('APROBADO', 'REEMBOLSADO')"));
const monolito = porTransaccion(filas(base,
  "SELECT transaction_id, turno_id, estado, monto FROM pago WHERE turno_id IS NOT NULL AND estado IN ('APROBADO', 'REEMBOLSADO')"));
// [transaction_id, solicitud_id]
const docsServicio = porTransaccion(filas('pagos',
  "SELECT transaction_id, solicitud_id FROM pago WHERE solicitud_id IS NOT NULL AND estado = 'APROBADO'"));
const docsMonolito = porTransaccion(filas(base,
  "SELECT transaction_id, id FROM solicitud_documento WHERE estado = 'APROBADO' AND transaction_id IS NOT NULL"));

const diferencias = [];
for (const [tx, [turno, estado, monto]] of servicio) {
  const m = monolito.get(tx);
  if (!m) {
    diferencias.push(`Pago ${tx} (turno ${turno}, ${estado}) está en el servicio y no en el monolito`);
  } else if (m[1] !== estado) {
    diferencias.push(`Pago ${tx} (turno ${turno}): ${estado} en el servicio y ${m[1]} en el monolito`);
  } else if (Number(m[2]) !== Number(monto)) {
    diferencias.push(`Pago ${tx} (turno ${turno}): $${monto} en el servicio y $${m[2]} en el monolito`);
  }
}
for (const [tx, [turno, estado]] of monolito) {
  if (!servicio.has(tx)) {
    diferencias.push(`Pago ${tx} (turno ${turno}, ${estado}) está en el monolito y no en el servicio`);
  }
}
for (const [tx, [solicitud]] of docsServicio) {
  if (!docsMonolito.has(tx)) {
    diferencias.push(`Documento ${solicitud} pagado (${tx}) en el servicio y no aprobado en el monolito`);
  }
}
for (const [tx, [solicitud]] of docsMonolito) {
  if (!docsServicio.has(tx)) {
    diferencias.push(`Documento ${solicitud} aprobado (${tx}) en el monolito y no en el servicio`);
  }
}

// Para revisar (no es una diferencia): turnos cancelados con el pago todavía aprobado. Puede ser correcto
// (cancelación con menos de 48 hs) o un reembolso que quedó sin hacer (por ejemplo, durante una vuelta atrás).
const paraRevisar = filas(base, "SELECT t.id, t.fecha, p.transaction_id FROM turno t JOIN pago p ON p.turno_id = t.id "
  + "WHERE t.estado = 'CANCELADO' AND p.estado = 'APROBADO' ORDER BY t.id");

console.log(`Pagos de turnos: ${servicio.size} en el servicio, ${monolito.size} en el monolito.`);
console.log(`Documentos pagados: ${docsServicio.size} en el servicio, ${docsMonolito.size} en el monolito.`);
if (paraRevisar.length > 0) {
  console.log(`Para revisar: ${paraRevisar.length} turno(s) cancelado(s) con el pago aprobado (¿correspondía reembolso?):`);
  for (const [turno, fecha, tx] of paraRevisar) console.log(`  - turno ${turno} del ${fecha}, pago ${tx}`);
}
if (diferencias.length === 0) {
  console.log('Sin diferencias.');
} else {
  console.log(`${diferencias.length} diferencia(s):`);
  for (const d of diferencias) console.log(`  - ${d}`);
  process.exit(1);
}
