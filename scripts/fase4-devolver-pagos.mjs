#!/usr/bin/env node
// Fase 4 (pagos), vuelta atrás: devuelve al monolito lo que cambió en el servicio de pagos, para que volver
// Mercado Pago al monolito no desvincule a nadie ni pierda pagos.
//
// - Cuentas de Mercado Pago: las del servicio pisan las columnas mp_* de usuario (tokens renovados,
//   vinculaciones nuevas); las que el servicio ya no tiene (desvinculadas mientras tanto) quedan
//   desvinculadas en el monolito.
// - Pagos: los que registró el servicio (aprobados, rechazados y reembolsados) quedan en la tabla pago del
//   monolito, uno por turno como siempre: el aprobado o reembolsado le gana a un rechazo. Los documentos de
//   la API de conceptos que pagó el servicio quedan aprobados con su transaction_id.
//
// Las dos bases viven en la misma instancia de PostgreSQL. Los tokens se copian cifrados, sin descifrarlos
// (las dos aplicaciones usan la misma ENCRYPTION_KEY). Es idempotente; con --prueba solo cuenta.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase4-devolver-pagos.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el gateway ya apuntando al monolito y PAGOS_EXTERNO=false):
//   node scripts/fase4-devolver-pagos.mjs --proyecto app --contenedor tranqui-db \
//     --usuario <DB_USER> --base tranqui_app
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

const cuentasServicio = numero('pagos', 'SELECT count(*) FROM cuenta_mp');
const vinculadosMonolito = numero(base, 'SELECT count(*) FROM usuario WHERE mp_access_token_encrypted IS NOT NULL OR mp_vinculada');
const pagosServicio = numero('pagos', 'SELECT count(*) FROM pago WHERE turno_id IS NOT NULL');
const documentosServicio = numero('pagos', "SELECT count(*) FROM pago WHERE solicitud_id IS NOT NULL AND estado = 'APROBADO'");
console.log(`Cuentas de Mercado Pago en el servicio: ${cuentasServicio}`);
console.log(`Profesionales vinculados en el monolito: ${vinculadosMonolito}`);
console.log(`Pagos de turnos en el servicio: ${pagosServicio}; documentos pagados: ${documentosServicio}`);
if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

const cuentas = psql('pagos', ['-c',
  '\\copy (SELECT profesional_id, access_token_cifrado, refresh_token_cifrado, token_vence_en, mp_user_id '
  + 'FROM cuenta_mp ORDER BY profesional_id) TO STDOUT WITH CSV']);
// Un pago por turno, como en el monolito: el aprobado o reembolsado le gana a un rechazo, y entre iguales
// el más reciente.
const pagos = psql('pagos', ['-c',
  "\\copy (SELECT DISTINCT ON (turno_id) turno_id, transaction_id, estado, monto, fecha_pago FROM pago "
  + "WHERE turno_id IS NOT NULL ORDER BY turno_id, (estado = 'RECHAZADO'), id DESC) TO STDOUT WITH CSV"]);
const documentos = psql('pagos', ['-c',
  "\\copy (SELECT solicitud_id, transaction_id FROM pago WHERE solicitud_id IS NOT NULL AND estado = 'APROBADO' "
  + 'ORDER BY solicitud_id) TO STDOUT WITH CSV']);

const carga = [
  'BEGIN;',
  'CREATE TEMP TABLE cuenta_devuelta (profesional_id BIGINT, access_token_cifrado TEXT, refresh_token_cifrado TEXT, '
    + 'token_vence_en TIMESTAMP, mp_user_id VARCHAR(50)) ON COMMIT DROP;',
  '\\copy cuenta_devuelta FROM STDIN WITH CSV',
  cuentas.trimEnd(),
  '\\.',
  'CREATE TEMP TABLE pago_devuelto (turno_id BIGINT, transaction_id VARCHAR(100), estado VARCHAR(20), '
    + 'monto NUMERIC(12,2), fecha_pago TIMESTAMP) ON COMMIT DROP;',
  '\\copy pago_devuelto FROM STDIN WITH CSV',
  pagos.trimEnd(),
  '\\.',
  'CREATE TEMP TABLE documento_devuelto (solicitud_id BIGINT, transaction_id VARCHAR(100)) ON COMMIT DROP;',
  '\\copy documento_devuelto FROM STDIN WITH CSV',
  documentos.trimEnd(),
  '\\.',
  // Vinculadas en el servicio: sus tokens pisan los del monolito.
  'UPDATE usuario u SET mp_access_token_encrypted = c.access_token_cifrado, '
    + 'mp_refresh_token_encrypted = COALESCE(c.refresh_token_cifrado, u.mp_refresh_token_encrypted), '
    + 'mp_token_expires_at = c.token_vence_en, mp_user_id = COALESCE(c.mp_user_id, u.mp_user_id), mp_vinculada = TRUE '
    + 'FROM cuenta_devuelta c WHERE u.id = c.profesional_id;',
  // Desvinculadas mientras estuvo el servicio.
  'UPDATE usuario u SET mp_access_token_encrypted = NULL, mp_refresh_token_encrypted = NULL, mp_token_expires_at = NULL, '
    + 'mp_user_id = NULL, mp_vinculada = FALSE '
    + 'WHERE (u.mp_access_token_encrypted IS NOT NULL OR u.mp_vinculada) '
    + 'AND NOT EXISTS (SELECT 1 FROM cuenta_devuelta c WHERE c.profesional_id = u.id);',
  // Pagos: el registro del turno queda como en el servicio; un rechazo no pisa un aprobado del monolito.
  'UPDATE pago p SET transaction_id = d.transaction_id, estado = d.estado, monto = d.monto, fecha_pago = d.fecha_pago '
    + "FROM pago_devuelto d WHERE p.turno_id = d.turno_id AND NOT (d.estado = 'RECHAZADO' AND p.estado <> 'RECHAZADO') "
    + 'AND (p.transaction_id, p.estado) IS DISTINCT FROM (d.transaction_id, d.estado);',
  'INSERT INTO pago (turno_id, transaction_id, estado, monto, fecha_pago, version) '
    + 'SELECT d.turno_id, d.transaction_id, d.estado, d.monto, d.fecha_pago, 0 FROM pago_devuelto d '
    + 'JOIN turno t ON t.id = d.turno_id '
    + 'WHERE NOT EXISTS (SELECT 1 FROM pago p WHERE p.turno_id = d.turno_id) '
    + 'AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.transaction_id = d.transaction_id);',
  "UPDATE solicitud_documento s SET estado = 'APROBADO', transaction_id = d.transaction_id "
    + "FROM documento_devuelto d WHERE s.id = d.solicitud_id AND s.estado <> 'APROBADO';",
  'COMMIT;',
  '',
].join('\n');
psql(base, ['-q', '-f', '-'], carga);

const despues = numero(base, 'SELECT count(*) FROM usuario WHERE mp_access_token_encrypted IS NOT NULL');
const pagosDespues = numero(base, 'SELECT count(*) FROM pago');
console.log(`Listo: el monolito tiene ${despues} profesionales vinculados a Mercado Pago y ${pagosDespues} pagos.`);
console.log('Conciliá con: node scripts/fase4-conciliar-pagos.mjs');
