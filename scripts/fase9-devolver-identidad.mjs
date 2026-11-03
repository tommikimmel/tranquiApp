#!/usr/bin/env node
// Fase 9 (identidad), vuelta atrás: devuelve al monolito lo que se creó o cambió en el servicio de identidad
// (base identidad a tranqui_app / tranqui_local): cuentas, contraseñas, datos personales, verificación, tickets y
// envíos de novedades, para que volver la identidad al monolito no pierda registros ni cambios.
//
// - usuario: SOLO las 57 columnas de identidad (las demás columnas del monolito, como el perfil profesional, los
//   tokens de Mercado Pago o de Google, no se tocan). Inserta las cuentas nuevas con sus ids y actualiza las que
//   difieren. La versión nunca baja (GREATEST): el monolito pudo seguir subiéndola con las copias del perfil.
// - ticket: inserta los nuevos y actualiza estado, fecha_actualizacion y versión (nunca para abajo).
// - ticket_mensaje: inserta los nuevos (los mensajes no cambian).
// - novedades_envio: inserta los nuevos y actualiza estado, detalle, finalizado_en y los segmentos enviados.
// - Aborta sin tocar nada (código 1) si un mail de identidad pertenece a otro id en el monolito, o si un ticket o
//   un mensaje existente cambió de dueño o de ticket. Informa solo ids y campos: nunca mails, hashes ni datos
//   personales.
// - Todo en una transacción única en PostgreSQL; al final mueve las secuencias del monolito arriba del máximo id.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase9-devolver-identidad.mjs [--prueba]
// Producción (en el VPS, con aprobación, en mantenimiento, con identidad apagada e IDENTIDAD_CARGA=false):
//   node scripts/fase9-devolver-identidad.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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
const SERVICIO = 'identidad';

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

// Las 57 columnas de identidad de usuario (el contrato de la carga inicial de la Fase 9) con su tipo.
const TIPO_USUARIO = {
  id: 'BIGINT', version: 'INT', email: 'VARCHAR(150)', password: 'VARCHAR(100)', rol: 'VARCHAR(20)',
  fecha_registro: 'TIMESTAMP', email_verificado: 'BOOLEAN', codigo_verificacion: 'VARCHAR(10)',
  codigo_verificacion_expires_at: 'TIMESTAMP', reset_password_code: 'VARCHAR(10)', reset_password_expires_at: 'TIMESTAMP',
  must_change_password: 'BOOLEAN', terminos_aceptados_en: 'TIMESTAMP', perfil_completo: 'BOOLEAN',
  cuenta_eliminada: 'BOOLEAN', anonimizado_en: 'TIMESTAMP', notificaciones_email_habilitadas: 'BOOLEAN',
  recibir_novedades: 'BOOLEAN', nombre: 'VARCHAR(100)', apellido: 'VARCHAR(100)', telefono: 'VARCHAR(30)',
  sexo: 'VARCHAR(10)', fecha_nacimiento: 'DATE', tipo_documento: 'VARCHAR(20)', numero_documento: 'INT',
  dni: 'VARCHAR(20)', cuil: 'BIGINT', direccion: 'VARCHAR(255)', foto_url: 'TEXT', domicilio_calle: 'VARCHAR(50)',
  domicilio_numero: 'VARCHAR(6)', domicilio_piso: 'VARCHAR(2)', domicilio_dpto: 'VARCHAR(3)',
  domicilio_codigo_postal: 'VARCHAR(8)', domicilio_localidad: 'VARCHAR(200)', domicilio_provincia: 'VARCHAR(200)',
  domicilio_pais: 'VARCHAR(45)', obra_social: 'VARCHAR(100)', num_afiliado: 'VARCHAR(50)', datos_ofuscado: 'VARCHAR(10)',
  credencial_cod_entidad: 'INT', credencial_pan: 'VARCHAR(50)', credencial_plan: 'VARCHAR(50)',
  credencial_token: 'VARCHAR(50)', verificado_admin: 'BOOLEAN', profession: 'VARCHAR(30)', license_type: 'VARCHAR(20)',
  license_number: 'VARCHAR(50)', license_jurisdiction: 'VARCHAR(100)', license_document_url: 'TEXT',
  license_verified_at: 'TIMESTAMP', license_verified_by: 'VARCHAR(100)', tax_id_type: 'VARCHAR(20)',
  tax_id: 'VARCHAR(30)', legal_name: 'VARCHAR(200)', iva_condition_id: 'INT', fiscal_address: 'VARCHAR(255)',
};
const COL_USUARIO = Object.keys(TIPO_USUARIO);
if (COL_USUARIO.length !== 57) throw new Error(`Se esperaban 57 columnas de identidad, hay ${COL_USUARIO.length}`);
// Las que se comparan y se pisan en una cuenta que ya existe (todas menos el id y la versión).
const COL_USUARIO_CAMBIAN = COL_USUARIO.filter((c) => c !== 'id' && c !== 'version');

const TIPO_TICKET = { id: 'BIGINT', version: 'INT', creador_id: 'BIGINT', asunto: 'VARCHAR(200)', estado: 'VARCHAR(20)',
  fecha_creacion: 'TIMESTAMP', fecha_actualizacion: 'TIMESTAMP' };
const TIPO_MENSAJE = { id: 'BIGINT', ticket_id: 'BIGINT', autor_id: 'BIGINT', contenido: 'TEXT', fecha_envio: 'TIMESTAMP' };
const TIPO_NOVEDADES = { id: 'VARCHAR(100)', titulo: 'VARCHAR(200)', estado: 'VARCHAR(20)', detalle: 'TEXT',
  creado_en: 'TIMESTAMP', finalizado_en: 'TIMESTAMP', enviado_pacientes: 'BOOLEAN', enviado_profesionales: 'BOOLEAN' };

const tablas = [['usuario', 'Usuarios'], ['ticket', 'Tickets'], ['ticket_mensaje', 'Mensajes'], ['novedades_envio', 'Novedades']];
for (const [tabla, etiqueta] of tablas) {
  console.log(`${etiqueta.padEnd(10)} servicio ${numero(SERVICIO, `SELECT count(*) FROM ${tabla}`)}, `
    + `monolito ${numero(base, `SELECT count(*) FROM ${tabla}`)}`);
}

if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

// ------------------------------------------------------------------ conflictos (antes de escribir nada)

const conflictos = [];

const mailsMonolito = new Map(json(base, 'SELECT id, lower(email) AS email FROM usuario').map((u) => [u.email, String(u.id)]));
for (const u of json(SERVICIO, 'SELECT id, lower(email) AS email FROM usuario')) {
  const otro = mailsMonolito.get(u.email);
  if (otro !== undefined && otro !== String(u.id)) {
    conflictos.push({ tabla: 'usuario', id: u.id, campo: `email (en el monolito pertenece al usuario ${otro})` });
  }
}

const ticketsMonolito = new Map(json(base, 'SELECT id, creador_id FROM ticket').map((t) => [String(t.id), String(t.creador_id)]));
for (const t of json(SERVICIO, 'SELECT id, creador_id FROM ticket')) {
  const creador = ticketsMonolito.get(String(t.id));
  if (creador !== undefined && creador !== String(t.creador_id)) {
    conflictos.push({ tabla: 'ticket', id: t.id, campo: 'creador_id' });
  }
}

const mensajesMonolito = new Map(json(base, 'SELECT id, ticket_id, autor_id FROM ticket_mensaje')
  .map((m) => [String(m.id), `${m.ticket_id}/${m.autor_id}`]));
for (const m of json(SERVICIO, 'SELECT id, ticket_id, autor_id FROM ticket_mensaje')) {
  const actual = mensajesMonolito.get(String(m.id));
  if (actual !== undefined && actual !== `${m.ticket_id}/${m.autor_id}`) {
    conflictos.push({ tabla: 'ticket_mensaje', id: m.id, campo: 'ticket_id o autor_id' });
  }
}

if (conflictos.length > 0) {
  console.error(`Conflictos detectados (${conflictos.length}):`);
  for (const c of conflictos.slice(0, 50)) {
    console.error(`  - [${c.tabla}] id=${c.id}: difiere en ${c.campo}`);
  }
  if (conflictos.length > 50) console.error(`  ... y ${conflictos.length - 50} más`);
  console.error('Abortando vuelta atrás: ningún registro fue modificado.');
  process.exit(1);
}

// ------------------------------------------------------------------ copia en una transacción

const copiar = (tabla, columnas, orden) =>
  psql(SERVICIO, ['-c', `\\copy (SELECT ${columnas.join(', ')} FROM ${tabla} ORDER BY ${orden}) TO STDOUT WITH CSV`]);
const def = (tipos) => Object.entries(tipos).map(([c, t]) => `${c} ${t}`).join(', ');
const lista = (columnas, prefijo) => columnas.map((c) => (prefijo ? `${prefijo}.${c}` : c)).join(', ');
const distinto = (columnas) => `(${lista(columnas, 'm')}) IS DISTINCT FROM (${lista(columnas, 'd')})`;

const COL_TICKET = Object.keys(TIPO_TICKET);
const COL_MENSAJE = Object.keys(TIPO_MENSAJE);
const COL_NOVEDADES = Object.keys(TIPO_NOVEDADES);
const NOVEDADES_CAMBIAN = ['estado', 'detalle', 'finalizado_en', 'enviado_pacientes', 'enviado_profesionales'];

const carga = [
  'BEGIN;',
  `CREATE TEMP TABLE dev_usuario (${def(TIPO_USUARIO)}) ON COMMIT DROP;`,
  '\\copy dev_usuario FROM STDIN WITH CSV',
  copiar('usuario', COL_USUARIO, 'id').trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_ticket (${def(TIPO_TICKET)}) ON COMMIT DROP;`,
  '\\copy dev_ticket FROM STDIN WITH CSV',
  copiar('ticket', COL_TICKET, 'id').trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_ticket_mensaje (${def(TIPO_MENSAJE)}) ON COMMIT DROP;`,
  '\\copy dev_ticket_mensaje FROM STDIN WITH CSV',
  copiar('ticket_mensaje', COL_MENSAJE, 'id').trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_novedades_envio (${def(TIPO_NOVEDADES)}) ON COMMIT DROP;`,
  '\\copy dev_novedades_envio FROM STDIN WITH CSV',
  copiar('novedades_envio', COL_NOVEDADES, 'id').trimEnd(),
  '\\.',

  `INSERT INTO usuario (${lista(COL_USUARIO)}) SELECT ${lista(COL_USUARIO, 'd')} FROM dev_usuario d `
    + 'WHERE NOT EXISTS (SELECT 1 FROM usuario m WHERE m.id = d.id);',
  `UPDATE usuario m SET ${COL_USUARIO_CAMBIAN.map((c) => `${c} = d.${c}`).join(', ')}, `
    + 'version = GREATEST(COALESCE(m.version, 0), d.version) '
    + `FROM dev_usuario d WHERE m.id = d.id AND ${distinto(COL_USUARIO_CAMBIAN)};`,

  `INSERT INTO ticket (${lista(COL_TICKET)}) SELECT ${lista(COL_TICKET, 'd')} FROM dev_ticket d `
    + 'WHERE NOT EXISTS (SELECT 1 FROM ticket m WHERE m.id = d.id);',
  'UPDATE ticket m SET estado = d.estado, fecha_actualizacion = d.fecha_actualizacion, '
    + 'version = GREATEST(COALESCE(m.version, 0), d.version) '
    + 'FROM dev_ticket d WHERE m.id = d.id AND (m.estado, m.fecha_actualizacion) IS DISTINCT FROM (d.estado, d.fecha_actualizacion);',

  `INSERT INTO ticket_mensaje (${lista(COL_MENSAJE)}) SELECT ${lista(COL_MENSAJE, 'd')} FROM dev_ticket_mensaje d `
    + 'WHERE NOT EXISTS (SELECT 1 FROM ticket_mensaje m WHERE m.id = d.id);',

  `INSERT INTO novedades_envio (${lista(COL_NOVEDADES)}) SELECT ${lista(COL_NOVEDADES, 'd')} FROM dev_novedades_envio d `
    + 'WHERE NOT EXISTS (SELECT 1 FROM novedades_envio m WHERE m.id = d.id);',
  `UPDATE novedades_envio m SET ${NOVEDADES_CAMBIAN.map((c) => `${c} = d.${c}`).join(', ')} `
    + `FROM dev_novedades_envio d WHERE m.id = d.id AND ${distinto(NOVEDADES_CAMBIAN)};`,

  "SELECT setval(COALESCE(pg_get_serial_sequence('usuario', 'id'), 'usuario_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM usuario), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('ticket', 'id'), 'ticket_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM ticket), 1));",
  "SELECT setval(COALESCE(pg_get_serial_sequence('ticket_mensaje', 'id'), 'ticket_mensaje_id_seq'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM ticket_mensaje), 1));",

  'COMMIT;',
  '',
].join('\n');

psql(base, ['-q', '-f', '-'], carga);

console.log(`Listo: el monolito tiene ${numero(base, 'SELECT count(*) FROM usuario')} usuarios, `
  + `${numero(base, 'SELECT count(*) FROM ticket')} tickets, `
  + `${numero(base, 'SELECT count(*) FROM ticket_mensaje')} mensajes y `
  + `${numero(base, 'SELECT count(*) FROM novedades_envio')} envíos de novedades.`);
console.log('Conciliá con: node scripts/fase9-conciliar-identidad.mjs');
