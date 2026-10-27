#!/usr/bin/env node
// Fase 5 (profesionales): conciliación entre el servicio de profesionales y la copia del monolito, que es la
// que leen turnos, recetas, facturación y los mails. Por profesional compara:
// - el perfil (cada campo de PerfilProfesionalData contra su columna de usuario);
// - la disponibilidad (las franjas, como conjunto);
// - las tarifas (por servicio_id, todos sus campos);
// - la identidad que el servicio copia del monolito (nombre, apellido, teléfono, documento y la foto).
//
// Solo lee. Sale con código 1 si hay diferencias, para usarlo a diario mientras dura la transición. Una
// diferencia de hasta 15 minutos puede ser normal (la resincronización del monolito no pasó todavía).
//
// Uso (por defecto, el entorno local):
//   node scripts/fase5-conciliar-profesionales.mjs
// Producción (en el VPS):
//   node scripts/fase5-conciliar-profesionales.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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

// Campo de PerfilProfesionalData -> columna de usuario (ver scripts/fase5-devolver-profesionales.mjs).
const CAMPOS = {
  emailContacto: 'email_contacto', domicilioAtencion: 'domicilio_atencion', domicilioLat: 'domicilio_lat',
  domicilioLng: 'domicilio_lng', domicilioAtencionTorre: 'domicilio_atencion_torre',
  domicilioAtencionPiso: 'domicilio_atencion_piso', domicilioAtencionDepto: 'domicilio_atencion_depto',
  domicilioAtencionBarrio: 'domicilio_atencion_barrio', matricula: 'matricula', matriculaTipo: 'matricula_tipo',
  matriculaProvincia: 'matricula_provincia', matriculaNumero: 'matricula_numero',
  matriculaEspecialidad: 'matricula_especialidad', matriculaAsocTipo: 'matricula_asoc_tipo',
  matriculaAsocProvincia: 'matricula_asoc_provincia', matriculaAsocNumero: 'matricula_asoc_numero', titulo: 'titulo',
  especialidad: 'especialidad', cuit: 'cuit', precio: 'precio', color: 'color', ofreceOnline: 'ofrece_online',
  ofrecePresencial: 'ofrece_presencial', experiencia: 'experiencia', publicaciones: 'publicaciones',
  instagramUrl: 'instagram_url', facebookUrl: 'facebook_url', linkedinUrl: 'linkedin_url', sitioWebUrl: 'sitio_web_url',
  codigoRefeps: 'codigo_refeps', selloLinea1: 'sello_linea1', selloLinea2: 'sello_linea2', selloLinea3: 'sello_linea3',
  tags: 'tags', descripcionPerfil: 'descripcion_perfil', institucionFormacion: 'institucion_formacion',
  aniosExperiencia: 'anios_experiencia', pacientesAtiende: 'pacientes_atiende',
  duracionTurnoMinutos: 'duracion_turno_minutos', intervaloEntreTurnosMinutos: 'intervalo_entre_turnos_minutos',
};
const IDENTIDAD = { nombre: 'nombre', apellido: 'apellido', telefono: 'telefono', tipo_documento: 'tipo_documento',
  numero_documento: 'numero_documento', foto_hash: 'foto_hash' };

// null, '' y los números/booleanos como texto valen lo mismo en las dos bases.
const normal = (v) => (v === null || v === undefined || v === '' ? null : String(v === true ? 'true' : v === false ? 'false' : v));
const mismoNumero = (a, b) => a !== null && b !== null && !Number.isNaN(Number(a)) && Number(a) === Number(b);

const servicio = json('profesionales',
  'SELECT id, perfil, nombre, apellido, telefono, tipo_documento, numero_documento, foto_hash FROM profesional ORDER BY id');
const ids = servicio.map((p) => p.id);
if (ids.length === 0) {
  console.log('El servicio no tiene profesionales: nada para conciliar.');
  process.exit(0);
}
const lista = ids.join(',');
const columnas = [...new Set([...Object.values(CAMPOS), ...Object.keys(IDENTIDAD).filter((c) => c !== 'foto_hash')])];
const monolito = new Map(json(base, `SELECT id, ${columnas.join(', ')}, encode(sha256(convert_to(foto_url, 'UTF8')), 'hex') AS foto_hash `
  + `FROM usuario WHERE id IN (${lista})`).map((u) => [u.id, u]));
const franjas = (filas) => {
  const m = new Map();
  for (const f of filas) {
    const clave = `${f.dia}|${f.inicio.slice(0, 5)}|${f.fin.slice(0, 5)}|${f.modalidad ?? ''}`;
    m.set(f.id, [...(m.get(f.id) ?? []), clave]);
  }
  for (const [k, v] of m) m.set(k, v.sort().join(','));
  return m;
};
const franjasServicio = franjas(json('profesionales',
  'SELECT profesional_id AS id, dia_semana AS dia, hora_inicio::text AS inicio, hora_fin::text AS fin, modalidad FROM disponibilidad'));
const franjasMonolito = franjas(json(base,
  `SELECT medico_id AS id, dia_semana AS dia, hora_inicio::text AS inicio, hora_fin::text AS fin, modalidad FROM disponibilidad WHERE medico_id IN (${lista})`));
const tarifas = (filas) => {
  const m = new Map();
  for (const t of filas) {
    const clave = [t.servicio_id, t.label, Number(t.precio), t.habilitado, t.requiere_obra_social, t.obra_social ?? '',
      t.precio_online === null ? '' : Number(t.precio_online), t.precio_presencial === null ? '' : Number(t.precio_presencial),
      t.requiere_agenda, t.es_receta].join('|');
    m.set(t.id, [...(m.get(t.id) ?? []), clave]);
  }
  for (const [k, v] of m) m.set(k, v.sort().join('\n'));
  return m;
};
const columnasTarifa = 'servicio_id, label, precio, habilitado, requiere_obra_social, obra_social, precio_online, precio_presencial, requiere_agenda, es_receta';
const tarifasServicio = tarifas(json('profesionales', `SELECT profesional_id AS id, ${columnasTarifa} FROM tarifa`));
const tarifasMonolito = tarifas(json(base, `SELECT medico_id AS id, ${columnasTarifa} FROM tarifa_medico WHERE medico_id IN (${lista})`));

const diferencias = [];
for (const p of servicio) {
  const u = monolito.get(p.id);
  if (!u) {
    diferencias.push(`${p.id}: no existe en el monolito`);
    continue;
  }
  for (const [campo, columna] of Object.entries(CAMPOS)) {
    const a = normal(p.perfil[campo]);
    const b = normal(u[columna]);
    if (a !== b && !mismoNumero(a, b)) diferencias.push(`${p.id}: perfil.${campo} servicio=${a} monolito=${b}`);
  }
  for (const [campo, columna] of Object.entries(IDENTIDAD)) {
    const a = normal(p[campo]);
    const b = normal(u[columna]);
    if (a !== b && !mismoNumero(a, b)) diferencias.push(`${p.id}: identidad.${campo} servicio=${a} monolito=${b}`);
  }
  if ((franjasServicio.get(p.id) ?? '') !== (franjasMonolito.get(p.id) ?? '')) {
    diferencias.push(`${p.id}: disponibilidad distinta (servicio ${franjasServicio.get(p.id)?.split(',').length ?? 0} franjas, `
      + `monolito ${franjasMonolito.get(p.id)?.split(',').length ?? 0})`);
  }
  if ((tarifasServicio.get(p.id) ?? '') !== (tarifasMonolito.get(p.id) ?? '')) {
    diferencias.push(`${p.id}: tarifas distintas`);
  }
}

console.log(`Profesionales comparados: ${servicio.length}`);
if (diferencias.length === 0) {
  console.log('Sin diferencias.');
  process.exit(0);
}
console.log(`Diferencias (${diferencias.length}):`);
for (const d of diferencias) console.log(`  - ${d}`);
process.exit(1);
