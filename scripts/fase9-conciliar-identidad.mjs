#!/usr/bin/env node
// Fase 9 (identidad): conciliación entre el servicio de identidad y el monolito.
// Compara las cuatro tablas de identidad:
// - Cantidades (y usuarios por rol) en cada lado;
// - Ids faltantes en cada lado;
// - Fila por fila, los campos principales:
//   * usuario: mail, rol, verificación del mail, verificación del administrador, cuenta eliminada, si debe cambiar la
//     contraseña, y la contraseña comparada por un hash de su hash (nunca se imprime ni el mail ni el hash);
//   * ticket: creador, estado; ticket_mensaje: ticket y autor; novedades_envio: estado y segmentos enviados.
//   La versión del servicio no puede ser menor que la del monolito para la misma cuenta.
//
// Solo lee. Termina con código 0 si coinciden y 1 si hay diferencias.
// Por privacidad, nunca imprime mails, hashes, códigos ni datos personales: solo ids y nombres de campo.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase9-conciliar-identidad.mjs
// Producción (en el VPS):
//   node scripts/fase9-conciliar-identidad.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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
const SERVICIO = 'identidad';

function json(baseDatos, sql) {
  const salida = execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-v', 'ON_ERROR_STOP=1', '-U', usuario, '-d', baseDatos, '-tA', '-c',
    `SELECT COALESCE(json_agg(x), '[]') FROM (${sql}) x`],
  { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
  return JSON.parse(salida.trim());
}

const normal = (v) => (v === null || v === undefined || v === '' ? null : String(v));
const diferencias = [];

function comparar(tabla, servicio, monolito, campos, extra) {
  const mapServ = new Map(servicio.map((f) => [String(f.id), f]));
  const mapMono = new Map(monolito.map((f) => [String(f.id), f]));
  for (const [id, s] of mapServ) {
    const m = mapMono.get(id);
    if (!m) {
      diferencias.push(`[${tabla}] id=${id}: falta en el monolito`);
      continue;
    }
    for (const c of campos) {
      if (normal(s[c]) !== normal(m[c])) diferencias.push(`[${tabla}] id=${id}: difiere ${c}`);
    }
    if (extra) extra(id, s, m);
  }
  for (const id of mapMono.keys()) {
    if (!mapServ.has(id)) diferencias.push(`[${tabla}] id=${id}: está en el monolito y no en el servicio`);
  }
}

// El mail y la contraseña se comparan por hash (md5 del valor, y del hash bcrypt): así no salen de la base.
const SQL_USUARIOS = 'SELECT id, version, md5(lower(email)) AS email, rol, email_verificado, verificado_admin, '
  + 'cuenta_eliminada, must_change_password, md5(COALESCE(password, \'\')) AS password FROM usuario';
const usuariosServ = json(SERVICIO, SQL_USUARIOS);
const usuariosMono = json(base, SQL_USUARIOS);
comparar('usuario', usuariosServ, usuariosMono,
  ['email', 'rol', 'email_verificado', 'verificado_admin', 'cuenta_eliminada', 'must_change_password', 'password'],
  (id, s, m) => {
    if (Number(s.version) < Number(m.version ?? 0)) diferencias.push(`[usuario] id=${id}: la versión del servicio es menor`);
  });

const ticketsServ = json(SERVICIO, 'SELECT id, creador_id, estado FROM ticket');
const ticketsMono = json(base, 'SELECT id, creador_id, estado FROM ticket');
comparar('ticket', ticketsServ, ticketsMono, ['creador_id', 'estado']);

const mensajesServ = json(SERVICIO, 'SELECT id, ticket_id, autor_id FROM ticket_mensaje');
const mensajesMono = json(base, 'SELECT id, ticket_id, autor_id FROM ticket_mensaje');
comparar('ticket_mensaje', mensajesServ, mensajesMono, ['ticket_id', 'autor_id']);

const novedadesServ = json(SERVICIO, 'SELECT id, estado, enviado_pacientes, enviado_profesionales FROM novedades_envio');
const novedadesMono = json(base, 'SELECT id, estado, enviado_pacientes, enviado_profesionales FROM novedades_envio');
comparar('novedades_envio', novedadesServ, novedadesMono, ['estado', 'enviado_pacientes', 'enviado_profesionales']);

const porRol = (filas) => filas.reduce((a, f) => ({ ...a, [f.rol]: (a[f.rol] || 0) + 1 }), {});
console.log(`Usuarios:  servicio ${usuariosServ.length} ${JSON.stringify(porRol(usuariosServ))}, `
  + `monolito ${usuariosMono.length} ${JSON.stringify(porRol(usuariosMono))}`);
console.log(`Tickets:   servicio ${ticketsServ.length}, monolito ${ticketsMono.length}`);
console.log(`Mensajes:  servicio ${mensajesServ.length}, monolito ${mensajesMono.length}`);
console.log(`Novedades: servicio ${novedadesServ.length}, monolito ${novedadesMono.length}`);

if (diferencias.length === 0) {
  console.log('Sin diferencias en las cuatro tablas de identidad.');
  process.exit(0);
}

console.log(`Diferencias encontradas (${diferencias.length}):`);
for (const d of diferencias.slice(0, 200)) console.log(`  - ${d}`);
if (diferencias.length > 200) console.log(`  ... y ${diferencias.length - 200} más`);
process.exit(1);
