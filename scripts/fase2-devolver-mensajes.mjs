#!/usr/bin/env node
// Fase 2 (chat), vuelta atrás: devuelve al monolito los mensajes del servicio de chat, para que
// volver el chat al monolito no pierda las conversaciones escritas mientras estuvo en el servicio.
//
// Las dos bases viven en la misma instancia de PostgreSQL. El script copia todos los mensajes de la
// base `chat` a la tabla `mensaje` del monolito por id (los que ya estaban actualizan su estado de
// leído) y deja la secuencia del monolito después del último. Es idempotente: se puede correr varias
// veces. Con --prueba solo cuenta lo que haría, sin escribir.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase2-devolver-mensajes.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el chat ya apuntando al monolito en el gateway):
//   node scripts/fase2-devolver-mensajes.mjs --proyecto app --contenedor tranqui-db \
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

const enServicio = numero('chat', 'SELECT count(*) FROM mensaje');
const enMonolito = numero(base, 'SELECT count(*) FROM mensaje');
const maxMonolito = numero(base, 'SELECT COALESCE(max(id), 0) FROM mensaje');
const nuevos = numero('chat', `SELECT count(*) FROM mensaje WHERE id > ${maxMonolito}`);
console.log(`Mensajes en el servicio de chat: ${enServicio}`);
console.log(`Mensajes en el monolito: ${enMonolito} (último id ${maxMonolito})`);
console.log(`Escritos en el servicio que el monolito no tiene: ${nuevos}`);
if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

// Exporta del chat en CSV y lo carga en una tabla temporal del monolito, en una sola transacción.
const csv = psql('chat', ['-c',
  '\\copy (SELECT id, remitente_id, destinatario_id, contenido, leido, fecha_envio FROM mensaje ORDER BY id) TO STDOUT WITH CSV']);
const carga = [
  'BEGIN;',
  'CREATE TEMP TABLE mensaje_devuelto (id BIGINT, remitente_id BIGINT, destinatario_id BIGINT, contenido TEXT, leido BOOLEAN, fecha_envio TIMESTAMP) ON COMMIT DROP;',
  '\\copy mensaje_devuelto FROM STDIN WITH CSV',
  csv.trimEnd(),
  '\\.',
  // Los usuarios borrados del monolito no tienen fila en usuario: esos mensajes no se pueden devolver.
  'INSERT INTO mensaje (id, version, remitente_id, destinatario_id, contenido, leido, fecha_envio)',
  '  SELECT d.id, 0, d.remitente_id, d.destinatario_id, d.contenido, d.leido, d.fecha_envio FROM mensaje_devuelto d',
  '  WHERE EXISTS (SELECT 1 FROM usuario u WHERE u.id = d.remitente_id)',
  '    AND EXISTS (SELECT 1 FROM usuario u WHERE u.id = d.destinatario_id)',
  '  ON CONFLICT (id) DO UPDATE SET leido = EXCLUDED.leido;',
  "SELECT setval(pg_get_serial_sequence('mensaje', 'id'), GREATEST((SELECT COALESCE(max(id), 0) FROM mensaje), 1));",
  'COMMIT;',
  '',
].join('\n');
psql(base, ['-q', '-f', '-'], carga);

const despues = numero(base, 'SELECT count(*) FROM mensaje');
console.log(`Listo: el monolito tiene ${despues} mensajes (${despues - enMonolito} devueltos).`);
if (despues < enServicio) {
  console.log(`Aviso: ${enServicio - despues} mensajes no se devolvieron (usuarios que ya no existen en el monolito).`);
}
