#!/usr/bin/env node
// Fase 6 (turnos), vuelta atrás: devuelve al monolito los turnos del servicio de turnos, para que volver los
// turnos al monolito no pierda reservas, cancelaciones, pagos ni los links de los mails ya enviados.
// La copia del monolito ya se actualiza con turno.actualizado y la resincronización; esto la deja exacta en
// el momento del corte (con los tokens de los mails, que no viajan en eventos).
//
// - Turnos: los que creó el servicio se insertan con su id; los demás se pisan campo por campo.
// - Versión: la del turno en el monolito queda por encima de la del servicio, así los eventos que vuelva a
//   publicar el monolito no los descartan notificaciones, chat, calendario ni pagos.
// - Pago: el pago del turno (uno por turno, como siempre) con el estado que informó pagos.
// - Secuencia: la de turno sigue después del más alto (los nuevos del servicio tienen ids propios).
//
// Las dos bases viven en la misma instancia de PostgreSQL. Es idempotente; con --prueba solo cuenta.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase6-devolver-turnos.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el gateway ya apuntando al monolito y TURNOS_EXTERNO=false):
//   node scripts/fase6-devolver-turnos.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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
const numero = (baseDatos, sql) => Number(psql(baseDatos, ['-tAc', sql]).trim());

// Las columnas que son iguales en las dos tablas (turno del monolito y turno del servicio).
const COLUMNAS = ['id', 'version', 'paciente_id', 'medico_id', 'fecha', 'hora_inicio', 'hora_fin', 'tipo', 'modalidad', 'estado',
  'asistencia', 'precio', 'metadata_afiliado', 'servicio_id', 'ocupa_agenda', 'id_financiador', 'telemedicina_url',
  'google_event_id', 'checkout_url', 'documento_enviado', 'es_receta', 'fecha_creacion', 'confirmacion_asistencia_email_enviado',
  'asistencia_confirmada_paciente', 'token_confirmacion_asistencia', 'token_reserva'];
const TIPOS = {
  id: 'BIGINT', version: 'INT', paciente_id: 'BIGINT', medico_id: 'BIGINT', fecha: 'DATE', hora_inicio: 'TIME', hora_fin: 'TIME',
  tipo: 'VARCHAR(20)', modalidad: 'VARCHAR(20)', estado: 'VARCHAR(30)', asistencia: 'VARCHAR(30)', precio: 'NUMERIC(12,2)',
  metadata_afiliado: 'VARCHAR(100)', servicio_id: 'VARCHAR(50)', ocupa_agenda: 'BOOLEAN', id_financiador: 'VARCHAR(50)',
  telemedicina_url: 'VARCHAR(500)', google_event_id: 'VARCHAR(255)', checkout_url: 'VARCHAR(500)', documento_enviado: 'BOOLEAN',
  es_receta: 'BOOLEAN', fecha_creacion: 'TIMESTAMP', confirmacion_asistencia_email_enviado: 'BOOLEAN',
  asistencia_confirmada_paciente: 'BOOLEAN', token_confirmacion_asistencia: 'VARCHAR(64)', token_reserva: 'VARCHAR(64)',
};
const PAGO = ['pago_transaction_id', 'pago_estado', 'pago_monto', 'pago_fecha'];
const TIPOS_PAGO = { pago_transaction_id: 'VARCHAR(100)', pago_estado: 'VARCHAR(20)', pago_monto: 'NUMERIC(12,2)', pago_fecha: 'TIMESTAMP' };

const turnosServicio = numero('turnos', 'SELECT count(*) FROM turno');
const turnosMonolito = numero(base, 'SELECT count(*) FROM turno');
console.log(`Turnos en el servicio: ${turnosServicio}; en el monolito: ${turnosMonolito}`);
if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

const filas = psql('turnos', ['-c',
  `\\copy (SELECT ${[...COLUMNAS, ...PAGO].join(', ')} FROM turno ORDER BY id) TO STDOUT WITH CSV`]);

const definicion = [...COLUMNAS.map((c) => `${c} ${TIPOS[c]}`), ...PAGO.map((c) => `${c} ${TIPOS_PAGO[c]}`)].join(', ');
const sinId = COLUMNAS.filter((c) => c !== 'id' && c !== 'version');
const carga = [
  'BEGIN;',
  `CREATE TEMP TABLE turno_devuelto (${definicion}) ON COMMIT DROP;`,
  '\\copy turno_devuelto FROM STDIN WITH CSV',
  filas.trimEnd(),
  '\\.',
  // Solo los turnos cuyos usuarios existen en el monolito (identidad nunca se fue).
  'DELETE FROM turno_devuelto d WHERE NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = d.paciente_id) '
    + 'OR NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = d.medico_id);',
  `UPDATE turno t SET ${sinId.map((c) => `${c} = d.${c}`).join(', ')}, version = GREATEST(COALESCE(t.version, 0), d.version) `
    + 'FROM turno_devuelto d WHERE t.id = d.id;',
  `INSERT INTO turno (${COLUMNAS.join(', ')}) SELECT ${COLUMNAS.map((c) => `d.${c}`).join(', ')} FROM turno_devuelto d `
    + 'WHERE NOT EXISTS (SELECT 1 FROM turno t WHERE t.id = d.id);',
  // El pago de cada turno: se actualiza el que hay o se crea uno (sin repetir una transacción de Mercado Pago).
  'UPDATE pago p SET transaction_id = d.pago_transaction_id, estado = d.pago_estado, '
    + 'monto = COALESCE(d.pago_monto, p.monto), fecha_pago = COALESCE(d.pago_fecha, p.fecha_pago) '
    + 'FROM turno_devuelto d WHERE p.turno_id = d.id AND d.pago_estado IS NOT NULL '
    + 'AND (p.transaction_id, p.estado) IS DISTINCT FROM (d.pago_transaction_id, d.pago_estado);',
  'INSERT INTO pago (turno_id, transaction_id, estado, monto, fecha_pago, version) '
    + 'SELECT d.id, d.pago_transaction_id, d.pago_estado, COALESCE(d.pago_monto, d.precio), d.pago_fecha, 0 FROM turno_devuelto d '
    + 'WHERE d.pago_estado IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.turno_id = d.id) '
    + 'AND (d.pago_transaction_id IS NULL OR NOT EXISTS (SELECT 1 FROM pago p WHERE p.transaction_id = d.pago_transaction_id));',
  "SELECT setval(pg_get_serial_sequence('turno', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM turno), 1));",
  'COMMIT;',
  '',
].join('\n');
psql(base, ['-q', '-f', '-'], carga);

console.log(`Listo: el monolito tiene ${numero(base, 'SELECT count(*) FROM turno')} turnos y ${numero(base, 'SELECT count(*) FROM pago')} pagos.`);
console.log('Conciliá con: node scripts/fase6-conciliar-turnos.mjs');
