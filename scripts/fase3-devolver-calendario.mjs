#!/usr/bin/env node
// Fase 3 (calendario), vuelta atrás: devuelve al monolito lo que cambió en el servicio de calendario,
// para que volver Google Calendar al monolito no desconecte a nadie ni pierda eventos.
//
// - Cuentas de Google: las del servicio pisan las columnas google_* de usuario (tokens renovados,
//   conexiones nuevas); las que el servicio ya no tiene (desvinculadas mientras tanto) quedan
//   desconectadas en el monolito y sin su caché de eventos externos. El syncToken queda vacío: la
//   primera pasada del monolito hace una sincronización completa y reconstruye su caché.
// - Turnos: el id del evento de Google y el Meet que creó el servicio.
//
// Las dos bases viven en la misma instancia de PostgreSQL. Los tokens se copian cifrados, sin
// descifrarlos (las dos aplicaciones usan la misma clave). Es idempotente; con --prueba solo cuenta.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase3-devolver-calendario.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el gateway ya apuntando al monolito y CALENDARIO_EXTERNO=false):
//   node scripts/fase3-devolver-calendario.mjs --proyecto app --contenedor tranqui-db \
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

const cuentasServicio = numero('calendario', 'SELECT count(*) FROM cuenta_google');
const conectadosMonolito = numero(base, 'SELECT count(*) FROM usuario WHERE google_calendar_connected');
const turnosConEvento = numero('calendario', 'SELECT count(*) FROM turno_agendado WHERE google_event_id IS NOT NULL OR meet_url IS NOT NULL');
console.log(`Cuentas de Google en el servicio: ${cuentasServicio}`);
console.log(`Profesionales conectados en el monolito: ${conectadosMonolito}`);
console.log(`Turnos con evento o Meet en el servicio: ${turnosConEvento}`);
if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

const cuentas = psql('calendario', ['-c',
  '\\copy (SELECT profesional_id, access_token_cifrado, refresh_token_cifrado, token_vence_en, calendar_id, '
  + 'watch_channel_id, watch_resource_id, watch_vence_en, watch_activo FROM cuenta_google ORDER BY profesional_id) TO STDOUT WITH CSV']);
const turnos = psql('calendario', ['-c',
  '\\copy (SELECT turno_id, google_event_id, meet_url FROM turno_agendado '
  + 'WHERE google_event_id IS NOT NULL OR meet_url IS NOT NULL ORDER BY turno_id) TO STDOUT WITH CSV']);

const carga = [
  'BEGIN;',
  'CREATE TEMP TABLE cuenta_devuelta (profesional_id BIGINT, access_token_cifrado TEXT, refresh_token_cifrado TEXT, '
    + 'token_vence_en TIMESTAMP, calendar_id VARCHAR(255), watch_channel_id VARCHAR(100), watch_resource_id VARCHAR(100), '
    + 'watch_vence_en TIMESTAMP, watch_activo BOOLEAN) ON COMMIT DROP;',
  '\\copy cuenta_devuelta FROM STDIN WITH CSV',
  cuentas.trimEnd(),
  '\\.',
  'CREATE TEMP TABLE turno_devuelto (turno_id BIGINT, google_event_id VARCHAR(255), meet_url TEXT) ON COMMIT DROP;',
  '\\copy turno_devuelto FROM STDIN WITH CSV',
  turnos.trimEnd(),
  '\\.',
  // Conectados en el servicio: sus datos pisan los del monolito.
  'UPDATE usuario u SET google_access_token_encrypted = c.access_token_cifrado, '
    + 'google_refresh_token_encrypted = c.refresh_token_cifrado, google_token_expires_at = c.token_vence_en, '
    + 'google_calendar_id = c.calendar_id, google_sync_token = NULL, google_watch_channel_id = c.watch_channel_id, '
    + 'google_watch_resource_id = c.watch_resource_id, google_watch_expires_at = c.watch_vence_en, '
    + 'google_watch_active = c.watch_activo, google_calendar_connected = TRUE '
    + 'FROM cuenta_devuelta c WHERE u.id = c.profesional_id;',
  // Desvinculados mientras estuvo el servicio: desconectados y sin su caché.
  'DELETE FROM google_calendar_evento_externo e USING usuario u WHERE e.medico_id = u.id AND u.google_calendar_connected '
    + 'AND NOT EXISTS (SELECT 1 FROM cuenta_devuelta c WHERE c.profesional_id = u.id);',
  'UPDATE usuario u SET google_access_token_encrypted = NULL, google_refresh_token_encrypted = NULL, '
    + 'google_token_expires_at = NULL, google_sync_token = NULL, google_watch_channel_id = NULL, '
    + 'google_watch_resource_id = NULL, google_watch_expires_at = NULL, google_watch_active = FALSE, '
    + 'google_calendar_connected = FALSE '
    + 'WHERE u.google_calendar_connected AND NOT EXISTS (SELECT 1 FROM cuenta_devuelta c WHERE c.profesional_id = u.id);',
  'UPDATE turno t SET google_event_id = COALESCE(d.google_event_id, t.google_event_id), '
    + 'telemedicina_url = COALESCE(d.meet_url, t.telemedicina_url) FROM turno_devuelto d WHERE t.id = d.turno_id;',
  'COMMIT;',
  '',
].join('\n');
psql(base, ['-q', '-f', '-'], carga);

const despues = numero(base, 'SELECT count(*) FROM usuario WHERE google_calendar_connected');
console.log(`Listo: el monolito tiene ${despues} profesionales conectados a Google Calendar.`);
