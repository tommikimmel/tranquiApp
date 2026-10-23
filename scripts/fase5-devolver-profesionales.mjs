#!/usr/bin/env node
// Fase 5 (profesionales), vuelta atrás: devuelve al monolito el perfil, la disponibilidad y las tarifas que
// administró el servicio de profesionales, para que volver el perfil al monolito no pierda ningún cambio.
// La copia del monolito ya se actualiza con los eventos y la resincronización; esto la deja exacta en el
// momento del corte, por si quedó algo en camino.
//
// - Perfil: las columnas del perfil de usuario (no la identidad: esa ya es del monolito).
// - Versión: la de usuario queda por encima de la del servicio, así los profesional.actualizado que vuelva a
//   publicar el monolito no los descartan calendario, chat, notificaciones ni pagos.
// - Disponibilidad: se reemplaza entera por la del servicio.
// - Tarifas: por servicio_id (se conservan los ids del monolito) y se borran las que el servicio no tiene.
//
// Las dos bases viven en la misma instancia de PostgreSQL. Es idempotente; con --prueba solo cuenta.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase5-devolver-profesionales.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el gateway ya apuntando al monolito y PROFESIONALES_EXTERNO=false):
//   node scripts/fase5-devolver-profesionales.mjs --proyecto app --contenedor tranqui-db \
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

// Campo de PerfilProfesionalData (libs/eventos) -> [columna de usuario, tipo SQL].
const CAMPOS = [
  ['emailContacto', 'email_contacto', 'text'], ['domicilioAtencion', 'domicilio_atencion', 'text'],
  ['domicilioLat', 'domicilio_lat', 'double precision'], ['domicilioLng', 'domicilio_lng', 'double precision'],
  ['domicilioAtencionTorre', 'domicilio_atencion_torre', 'text'], ['domicilioAtencionPiso', 'domicilio_atencion_piso', 'text'],
  ['domicilioAtencionDepto', 'domicilio_atencion_depto', 'text'], ['domicilioAtencionBarrio', 'domicilio_atencion_barrio', 'text'],
  ['matricula', 'matricula', 'text'], ['matriculaTipo', 'matricula_tipo', 'text'],
  ['matriculaProvincia', 'matricula_provincia', 'text'], ['matriculaNumero', 'matricula_numero', 'integer'],
  ['matriculaEspecialidad', 'matricula_especialidad', 'text'], ['matriculaAsocTipo', 'matricula_asoc_tipo', 'text'],
  ['matriculaAsocProvincia', 'matricula_asoc_provincia', 'text'], ['matriculaAsocNumero', 'matricula_asoc_numero', 'integer'],
  ['titulo', 'titulo', 'text'], ['especialidad', 'especialidad', 'text'], ['cuit', 'cuit', 'text'],
  ['precio', 'precio', 'numeric'], ['color', 'color', 'text'],
  ['ofreceOnline', 'ofrece_online', 'boolean'], ['ofrecePresencial', 'ofrece_presencial', 'boolean'],
  ['experiencia', 'experiencia', 'text'], ['publicaciones', 'publicaciones', 'text'],
  ['instagramUrl', 'instagram_url', 'text'], ['facebookUrl', 'facebook_url', 'text'],
  ['linkedinUrl', 'linkedin_url', 'text'], ['sitioWebUrl', 'sitio_web_url', 'text'],
  ['codigoRefeps', 'codigo_refeps', 'text'], ['selloLinea1', 'sello_linea1', 'text'],
  ['selloLinea2', 'sello_linea2', 'text'], ['selloLinea3', 'sello_linea3', 'text'], ['tags', 'tags', 'text'],
  ['descripcionPerfil', 'descripcion_perfil', 'text'], ['institucionFormacion', 'institucion_formacion', 'text'],
  ['aniosExperiencia', 'anios_experiencia', 'integer'], ['pacientesAtiende', 'pacientes_atiende', 'text'],
  ['duracionTurnoMinutos', 'duracion_turno_minutos', 'integer'],
  ['intervaloEntreTurnosMinutos', 'intervalo_entre_turnos_minutos', 'integer'],
];

const profesionales = numero('profesionales', 'SELECT count(*) FROM profesional');
const franjasServicio = numero('profesionales', 'SELECT count(*) FROM disponibilidad');
const tarifasServicio = numero('profesionales', 'SELECT count(*) FROM tarifa');
console.log(`En el servicio: ${profesionales} profesionales, ${franjasServicio} franjas y ${tarifasServicio} tarifas`);
if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

const perfiles = psql('profesionales', ['-c',
  '\\copy (SELECT id, version, perfil::text FROM profesional ORDER BY id) TO STDOUT WITH CSV']);
const franjas = psql('profesionales', ['-c',
  '\\copy (SELECT profesional_id, dia_semana, hora_inicio, hora_fin, modalidad FROM disponibilidad ORDER BY id) '
  + 'TO STDOUT WITH CSV']);
const tarifas = psql('profesionales', ['-c',
  '\\copy (SELECT profesional_id, servicio_id, label, precio, habilitado, requiere_obra_social, obra_social, '
  + 'precio_online, precio_presencial, requiere_agenda, es_receta FROM tarifa ORDER BY id) TO STDOUT WITH CSV']);

const asignaciones = CAMPOS.map(([campo, columna, tipo]) => `${columna} = (p.perfil->>'${campo}')::${tipo}`).join(', ');
const carga = [
  'BEGIN;',
  'CREATE TEMP TABLE perfil_devuelto (id BIGINT, version INT, perfil JSONB) ON COMMIT DROP;',
  '\\copy perfil_devuelto FROM STDIN WITH CSV',
  perfiles.trimEnd(),
  '\\.',
  'CREATE TEMP TABLE franja_devuelta (profesional_id BIGINT, dia_semana INT, hora_inicio TIME, hora_fin TIME, '
    + 'modalidad VARCHAR(20)) ON COMMIT DROP;',
  '\\copy franja_devuelta FROM STDIN WITH CSV',
  franjas.trimEnd(),
  '\\.',
  'CREATE TEMP TABLE tarifa_devuelta (profesional_id BIGINT, servicio_id VARCHAR(50), label VARCHAR(100), '
    + 'precio NUMERIC(12,2), habilitado BOOLEAN, requiere_obra_social BOOLEAN, obra_social VARCHAR(100), '
    + 'precio_online NUMERIC(12,2), precio_presencial NUMERIC(12,2), requiere_agenda BOOLEAN, es_receta BOOLEAN) ON COMMIT DROP;',
  '\\copy tarifa_devuelta FROM STDIN WITH CSV',
  tarifas.trimEnd(),
  '\\.',
  // Solo los profesionales que existen en los dos lados.
  'DELETE FROM perfil_devuelto p WHERE NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = p.id);',
  `UPDATE usuario u SET ${asignaciones}, version = GREATEST(COALESCE(u.version, 0), p.version) `
    + 'FROM perfil_devuelto p WHERE u.id = p.id;',
  'DELETE FROM disponibilidad d USING perfil_devuelto p WHERE d.medico_id = p.id;',
  'INSERT INTO disponibilidad (medico_id, dia_semana, hora_inicio, hora_fin, modalidad, version) '
    + 'SELECT f.profesional_id, f.dia_semana, f.hora_inicio, f.hora_fin, f.modalidad, 0 FROM franja_devuelta f '
    + 'JOIN perfil_devuelto p ON p.id = f.profesional_id;',
  'UPDATE tarifa_medico t SET label = d.label, precio = d.precio, habilitado = d.habilitado, '
    + 'requiere_obra_social = d.requiere_obra_social, obra_social = d.obra_social, precio_online = d.precio_online, '
    + 'precio_presencial = d.precio_presencial, requiere_agenda = d.requiere_agenda, es_receta = d.es_receta '
    + 'FROM tarifa_devuelta d WHERE t.medico_id = d.profesional_id AND t.servicio_id = d.servicio_id;',
  'INSERT INTO tarifa_medico (medico_id, servicio_id, label, precio, habilitado, requiere_obra_social, obra_social, '
    + 'precio_online, precio_presencial, requiere_agenda, es_receta, version) '
    + 'SELECT d.profesional_id, d.servicio_id, d.label, d.precio, d.habilitado, d.requiere_obra_social, d.obra_social, '
    + 'd.precio_online, d.precio_presencial, d.requiere_agenda, d.es_receta, 0 FROM tarifa_devuelta d '
    + 'JOIN perfil_devuelto p ON p.id = d.profesional_id '
    + 'WHERE NOT EXISTS (SELECT 1 FROM tarifa_medico t WHERE t.medico_id = d.profesional_id AND t.servicio_id = d.servicio_id);',
  'DELETE FROM tarifa_medico t USING perfil_devuelto p WHERE t.medico_id = p.id '
    + 'AND NOT EXISTS (SELECT 1 FROM tarifa_devuelta d WHERE d.profesional_id = t.medico_id AND d.servicio_id = t.servicio_id);',
  'COMMIT;',
  '',
].join('\n');
psql(base, ['-q', '-f', '-'], carga);

const franjasMonolito = numero(base, "SELECT count(*) FROM disponibilidad d JOIN usuario u ON u.id = d.medico_id WHERE u.rol = 'PSIQUIATRA'");
const tarifasMonolito = numero(base, "SELECT count(*) FROM tarifa_medico t JOIN usuario u ON u.id = t.medico_id WHERE u.rol = 'PSIQUIATRA'");
console.log(`Listo: el monolito tiene ${franjasMonolito} franjas y ${tarifasMonolito} tarifas de profesionales.`);
console.log('Conciliá con: node scripts/fase5-conciliar-profesionales.mjs');
