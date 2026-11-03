#!/usr/bin/env node
// Fase 1 (servicio de notificaciones): compara la base del monolito con la del servicio, paso 4 de
// "Migración de datos en cada fase" (docs/arquitectura/microservicios.md). Solo lee.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase1-comparar-notificaciones.mjs
// Producción (en el VPS, con aprobación):
//   node scripts/fase1-comparar-notificaciones.mjs --proyecto app --contenedor tranqui-db \
//     --usuario <DB_USER> --base tranqui_app
//
// Compara: usuarios del monolito contra la copia de contactos, y notificaciones de las últimas 24 h
// (las que copia la carga inicial). Sale con código 1 si hay diferencias.
import { execFileSync } from 'node:child_process';

const args = Object.fromEntries(process.argv.slice(2).reduce((pares, arg, i, todos) => {
  if (arg.startsWith('--')) pares.push([arg.slice(2), todos[i + 1]]);
  return pares;
}, []));
const proyecto = args.proyecto ?? 'tranqui-local';
const contenedor = args.contenedor ?? 'db';
const usuario = args.usuario ?? 'tranqui';
const base = args.base ?? 'tranqui_local';

function consulta(baseDatos, sql) {
  const salida = execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-U', usuario, '-d', baseDatos, '-tAc', sql], { encoding: 'utf8' });
  return Number(salida.trim());
}

const filas = [
  ['Usuarios / contactos',
    consulta(base, 'SELECT count(*) FROM usuario'),
    consulta('notificaciones', 'SELECT count(*) FROM contacto')],
  ['Contactos que no aceptan mails',
    consulta(base, 'SELECT count(*) FROM usuario WHERE notificaciones_email_habilitadas = false'),
    consulta('notificaciones', 'SELECT count(*) FROM contacto WHERE email_habilitado = false')],
  ['Notificaciones de las últimas 24 h',
    consulta(base, "SELECT count(*) FROM notificacion WHERE fecha_creacion > now() - interval '24 hours'"),
    consulta('notificaciones', "SELECT count(*) FROM notificacion WHERE fecha_creacion > now() - interval '24 hours'")],
];

let diferencias = 0;
console.log('Comparación monolito / servicio de notificaciones');
for (const [que, monolito, servicio] of filas) {
  const marca = monolito === servicio ? 'igual' : 'DIFERENTE';
  if (monolito !== servicio) diferencias++;
  console.log(`  ${que.padEnd(36)} monolito ${String(monolito).padStart(6)}  servicio ${String(servicio).padStart(6)}  ${marca}`);
}
console.log(diferencias === 0
  ? 'Sin diferencias.'
  : `${diferencias} diferencia(s). Las notificaciones pueden diferir si ya se crearon avisos nuevos desde el servicio.`);
process.exit(diferencias === 0 ? 0 : 1);
