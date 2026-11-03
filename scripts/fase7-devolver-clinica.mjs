#!/usr/bin/env node
// Fase 7 (historia clínica y recetas), vuelta atrás: devuelve al monolito los seguimientos diarios,
// informes clínicos, recetas y solicitudes de documentos del servicio de profesionales, para que
// volver la clínica al monolito no pierda ningún asiento ni modificación (Ley 26.529 y 27.706).
//
// - Inserta los registros que el monolito no tiene con sus mismos ids, versiones, estados y hashes.
// - Actualiza solo los campos mutables de los que cambiaron (estado, motivo, emitido, url_descarga,
//   fecha_emision, transaction_id).
// - NUNCA pisa el contenido clínico ni el hash de un asiento existente: si difieren, informa el
//   conflicto y aborta con código de error.
// - NUNCA descarta asientos de historia clínica: si algún paciente o médico no existe en el
//   monolito, informa los asientos afectados y aborta con código de error para que el operador decida.
// - Ajusta las secuencias (identity) del monolito por encima del máximo id.
// - Todo en una transacción única en PostgreSQL.
//
// Uso (por defecto, el entorno local):
//   node scripts/fase7-devolver-clinica.mjs [--prueba]
// Producción (en el VPS, con aprobación, con el gateway ya apuntando al monolito y CLINICA_EXTERNO=false):
//   node scripts/fase7-devolver-clinica.mjs --proyecto app --contenedor tranqui-db --usuario <DB_USER> --base tranqui_app
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

function json(baseDatos, sql) {
  const salida = execFileSync('docker', ['compose', '-p', proyecto, 'exec', '-T', contenedor,
    'psql', '-v', 'ON_ERROR_STOP=1', '-U', usuario, '-d', baseDatos, '-tA', '-c',
    `SELECT COALESCE(json_agg(x), '[]') FROM (${sql}) x`],
  { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
  return JSON.parse(salida.trim());
}

const numero = (baseDatos, sql) => Number(psql(baseDatos, ['-tAc', sql]).trim());

const segServicio = numero('profesionales', 'SELECT count(*) FROM seguimiento_diario');
const segMonolito = numero(base, 'SELECT count(*) FROM seguimiento_diario');
const infServicio = numero('profesionales', 'SELECT count(*) FROM informe_clinico');
const infMonolito = numero(base, 'SELECT count(*) FROM informe_clinico');
const recServicio = numero('profesionales', 'SELECT count(*) FROM receta');
const recMonolito = numero(base, 'SELECT count(*) FROM receta');
const solServicio = numero('profesionales', 'SELECT count(*) FROM solicitud_documento');
const solMonolito = numero(base, 'SELECT count(*) FROM solicitud_documento');

console.log(`Seguimientos: servicio ${segServicio}, monolito ${segMonolito}`);
console.log(`Informes:     servicio ${infServicio}, monolito ${infMonolito}`);
console.log(`Recetas:      servicio ${recServicio}, monolito ${recMonolito}`);
console.log(`Solicitudes:  servicio ${solServicio}, monolito ${solMonolito}`);

if (prueba) {
  console.log('--prueba: no se escribió nada.');
  process.exit(0);
}

// 1. Detección de conflictos y de usuarios inexistentes antes de modificar la base:
// Si un asiento existente difiere en contenido clínico o hash, no se debe pisar.
// Si un asiento referencia un paciente o médico inexistente en el monolito, no se descarta en silencio:
// se aborta para que el operador decida (Ley 26.529 y 27.706).
const normal = (v) => (v === null || v === undefined || v === '' ? null : String(v));
const mismoNumero = (a, b) => a !== null && b !== null && !Number.isNaN(Number(a)) && Number(a) === Number(b);

const conflictos = [];

// Seguimiento: inmutables: paciente_id, medico_id, fecha, estado_animo, sintomas, notas, hash_integridad
const segsS = new Map(json('profesionales', 'SELECT id, paciente_id, medico_id, fecha::text, estado_animo, sintomas, notas, hash_integridad FROM seguimiento_diario').map((s) => [s.id, s]));
const segsM = new Map(json(base, 'SELECT id, paciente_id, medico_id, fecha::text, estado_animo, sintomas, notas, hash_integridad FROM seguimiento_diario').map((s) => [s.id, s]));
for (const [id, s] of segsS) {
  const m = segsM.get(id);
  if (m) {
    for (const campo of ['paciente_id', 'medico_id', 'fecha', 'estado_animo', 'sintomas', 'notas', 'hash_integridad']) {
      if (normal(s[campo]) !== normal(m[campo])) {
        conflictos.push({ tabla: 'seguimiento_diario', id, campo });
        break;
      }
    }
  }
}

// Informe: inmutables: paciente_id, medico_id, fecha, tipo_informe, plan_trabajo, contenido, nombre_archivo, informe_original_id, hash_integridad
const infsS = new Map(json('profesionales', 'SELECT id, paciente_id, medico_id, fecha::text, tipo_informe, plan_trabajo, contenido, nombre_archivo, informe_original_id, hash_integridad FROM informe_clinico').map((i) => [i.id, i]));
const infsM = new Map(json(base, 'SELECT id, paciente_id, medico_id, fecha::text, tipo_informe, plan_trabajo, contenido, nombre_archivo, informe_original_id, hash_integridad FROM informe_clinico').map((i) => [i.id, i]));
for (const [id, s] of infsS) {
  const m = infsM.get(id);
  if (m) {
    for (const campo of ['paciente_id', 'medico_id', 'fecha', 'tipo_informe', 'plan_trabajo', 'contenido', 'nombre_archivo', 'informe_original_id', 'hash_integridad']) {
      if (normal(s[campo]) !== normal(m[campo])) {
        conflictos.push({ tabla: 'informe_clinico', id, campo });
        break;
      }
    }
  }
}

// Receta: inmutables: medico_id, paciente_id, medicamentos, laboratorios, diagnostico, indicaciones
const recsS = new Map(json('profesionales', 'SELECT id, medico_id, paciente_id, medicamentos, laboratorios, diagnostico, indicaciones FROM receta').map((r) => [r.id, r]));
const recsM = new Map(json(base, 'SELECT id, medico_id, paciente_id, medicamentos, laboratorios, diagnostico, indicaciones FROM receta').map((r) => [r.id, r]));
for (const [id, s] of recsS) {
  const m = recsM.get(id);
  if (m) {
    for (const campo of ['medico_id', 'paciente_id', 'medicamentos', 'laboratorios', 'diagnostico', 'indicaciones']) {
      if (normal(s[campo]) !== normal(m[campo])) {
        conflictos.push({ tabla: 'receta', id, campo });
        break;
      }
    }
  }
}

// Solicitud: inmutables: paciente_id, medico_id, tipo_concepto, precio
const solsS = new Map(json('profesionales', 'SELECT id, paciente_id, medico_id, tipo_concepto, precio FROM solicitud_documento').map((d) => [d.id, d]));
const solsM = new Map(json(base, 'SELECT id, paciente_id, medico_id, tipo_concepto, precio FROM solicitud_documento').map((d) => [d.id, d]));
for (const [id, s] of solsS) {
  const m = solsM.get(id);
  if (m) {
    for (const campo of ['paciente_id', 'medico_id', 'tipo_concepto']) {
      if (normal(s[campo]) !== normal(m[campo])) {
        conflictos.push({ tabla: 'solicitud_documento', id, campo });
        break;
      }
    }
    if (!mismoNumero(s.precio, m.precio)) {
      conflictos.push({ tabla: 'solicitud_documento', id, campo: 'precio' });
    }
  }
}

// Usuarios inexistentes en el monolito:
const usuariosMonolito = new Set(json(base, 'SELECT id FROM usuario').map((u) => String(u.id)));
const huerfanos = [];
const verificarUsuario = (tabla, id, campo, usuarioId) => {
  if (usuarioId !== null && usuarioId !== undefined && !usuariosMonolito.has(String(usuarioId))) {
    huerfanos.push({ tabla, id, campo, usuarioId });
  }
};

for (const [id, s] of segsS) {
  verificarUsuario('seguimiento_diario', id, 'paciente_id', s.paciente_id);
  verificarUsuario('seguimiento_diario', id, 'medico_id', s.medico_id);
}
for (const [id, i] of infsS) {
  verificarUsuario('informe_clinico', id, 'paciente_id', i.paciente_id);
  verificarUsuario('informe_clinico', id, 'medico_id', i.medico_id);
}
for (const [id, r] of recsS) {
  verificarUsuario('receta', id, 'paciente_id', r.paciente_id);
  verificarUsuario('receta', id, 'medico_id', r.medico_id);
}
for (const [id, d] of solsS) {
  verificarUsuario('solicitud_documento', id, 'paciente_id', d.paciente_id);
  verificarUsuario('solicitud_documento', id, 'medico_id', d.medico_id);
}

if (conflictos.length > 0) {
  console.error(`Conflictos detectados (${conflictos.length}): un asiento existente difiere en contenido o hash:`);
  for (const c of conflictos.slice(0, 50)) {
    console.error(`  - [${c.tabla}] id=${c.id}: difiere en ${c.campo}`);
  }
  if (conflictos.length > 50) console.error(`  ... y ${conflictos.length - 50} más`);
  console.error('Abortando vuelta atrás: ningún asiento fue modificado.');
  process.exit(1);
}

if (huerfanos.length > 0) {
  console.error(`Asientos con usuarios inexistentes en el monolito (${huerfanos.length}):`);
  for (const h of huerfanos.slice(0, 50)) {
    console.error(`  - [${h.tabla}] id=${h.id}: falta ${h.campo}=${h.usuarioId} en usuario`);
  }
  if (huerfanos.length > 50) console.error(`  ... y ${huerfanos.length - 50} más`);
  console.error('Abortando vuelta atrás: ningún asiento fue modificado.');
  process.exit(1);
}

// 2. Extracción de datos del servicio vía CSV
const COL_SEG = ['id', 'paciente_id', 'medico_id', 'fecha', 'estado_animo', 'sintomas', 'notas', 'fecha_creacion', 'estado', 'motivo', 'hash_integridad'];
const TIPO_SEG = { id: 'BIGINT', paciente_id: 'BIGINT', medico_id: 'BIGINT', fecha: 'DATE', estado_animo: 'VARCHAR(50)', sintomas: 'TEXT', notas: 'TEXT', fecha_creacion: 'TIMESTAMP', estado: 'VARCHAR(30)', motivo: 'TEXT', hash_integridad: 'VARCHAR(64)' };

const COL_INF = ['id', 'paciente_id', 'medico_id', 'fecha', 'tipo_informe', 'plan_trabajo', 'contenido', 'nombre_archivo', 'fecha_creacion', 'estado', 'informe_original_id', 'motivo', 'hash_integridad'];
const TIPO_INF = { id: 'BIGINT', paciente_id: 'BIGINT', medico_id: 'BIGINT', fecha: 'DATE', tipo_informe: 'VARCHAR(50)', plan_trabajo: 'TEXT', contenido: 'TEXT', nombre_archivo: 'VARCHAR(255)', fecha_creacion: 'TIMESTAMP', estado: 'VARCHAR(30)', informe_original_id: 'BIGINT', motivo: 'TEXT', hash_integridad: 'VARCHAR(64)' };

const COL_REC = ['id', 'medico_id', 'paciente_id', 'medicamentos', 'laboratorios', 'diagnostico', 'indicaciones', 'pdf_url', 'qbi2_id_receta', 'qbi2_verificador', 'qbi2_nro_cuir', 'qbi2_fecha_vencimiento', 'qbi2_id_transaccion', 'fecha_emision'];
const TIPO_REC = { id: 'BIGINT', medico_id: 'BIGINT', paciente_id: 'BIGINT', medicamentos: 'TEXT', laboratorios: 'TEXT', diagnostico: 'VARCHAR(150)', indicaciones: 'TEXT', pdf_url: 'VARCHAR(500)', qbi2_id_receta: 'VARCHAR(100)', qbi2_verificador: 'VARCHAR(255)', qbi2_nro_cuir: 'VARCHAR(500)', qbi2_fecha_vencimiento: 'VARCHAR(50)', qbi2_id_transaccion: 'VARCHAR(100)', fecha_emision: 'TIMESTAMP' };

const COL_SOL = ['id', 'paciente_id', 'medico_id', 'tipo_concepto', 'precio', 'estado', 'transaction_id', 'emitido', 'url_descarga', 'fecha_emision', 'fecha_creacion'];
const TIPO_SOL = { id: 'BIGINT', paciente_id: 'BIGINT', medico_id: 'BIGINT', tipo_concepto: 'VARCHAR(30)', precio: 'NUMERIC(12,2)', estado: 'VARCHAR(30)', transaction_id: 'VARCHAR(100)', emitido: 'BOOLEAN', url_descarga: 'VARCHAR(500)', fecha_emision: 'TIMESTAMP', fecha_creacion: 'TIMESTAMP' };

const csvSeg = psql('profesionales', ['-c', `\\copy (SELECT ${COL_SEG.join(', ')} FROM seguimiento_diario ORDER BY id) TO STDOUT WITH CSV`]);
const csvInf = psql('profesionales', ['-c', `\\copy (SELECT ${COL_INF.join(', ')} FROM informe_clinico ORDER BY id) TO STDOUT WITH CSV`]);
const csvRec = psql('profesionales', ['-c', `\\copy (SELECT ${COL_REC.join(', ')} FROM receta ORDER BY id) TO STDOUT WITH CSV`]);
const csvSol = psql('profesionales', ['-c', `\\copy (SELECT ${COL_SOL.join(', ')} FROM solicitud_documento ORDER BY id) TO STDOUT WITH CSV`]);

const def = (cols, tipos) => cols.map((c) => `${c} ${tipos[c]}`).join(', ');

const carga = [
  'BEGIN;',
  // Tablas temporales
  `CREATE TEMP TABLE dev_seguimiento (${def(COL_SEG, TIPO_SEG)}) ON COMMIT DROP;`,
  '\\copy dev_seguimiento FROM STDIN WITH CSV',
  csvSeg.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_informe (${def(COL_INF, TIPO_INF)}) ON COMMIT DROP;`,
  '\\copy dev_informe FROM STDIN WITH CSV',
  csvInf.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_receta (${def(COL_REC, TIPO_REC)}) ON COMMIT DROP;`,
  '\\copy dev_receta FROM STDIN WITH CSV',
  csvRec.trimEnd(),
  '\\.',
  `CREATE TEMP TABLE dev_solicitud (${def(COL_SOL, TIPO_SOL)}) ON COMMIT DROP;`,
  '\\copy dev_solicitud FROM STDIN WITH CSV',
  csvSol.trimEnd(),
  '\\.',
  // Inserciones de nuevos registros
  `INSERT INTO seguimiento_diario (${COL_SEG.join(', ')}) SELECT ${COL_SEG.map((c) => `d.${c}`).join(', ')} FROM dev_seguimiento d WHERE NOT EXISTS (SELECT 1 FROM seguimiento_diario m WHERE m.id = d.id);`,
  `INSERT INTO informe_clinico (${COL_INF.join(', ')}) SELECT ${COL_INF.map((c) => `d.${c}`).join(', ')} FROM dev_informe d WHERE NOT EXISTS (SELECT 1 FROM informe_clinico m WHERE m.id = d.id);`,
  `INSERT INTO receta (${COL_REC.join(', ')}) SELECT ${COL_REC.map((c) => `d.${c}`).join(', ')} FROM dev_receta d WHERE NOT EXISTS (SELECT 1 FROM receta m WHERE m.id = d.id);`,
  `INSERT INTO solicitud_documento (${COL_SOL.join(', ')}) SELECT ${COL_SOL.map((c) => `d.${c}`).join(', ')} FROM dev_solicitud d WHERE NOT EXISTS (SELECT 1 FROM solicitud_documento m WHERE m.id = d.id);`,
  // Actualizaciones de registros mutables existentes
  "UPDATE seguimiento_diario m SET estado = d.estado, motivo = d.motivo FROM dev_seguimiento d WHERE m.id = d.id AND (m.estado, COALESCE(m.motivo, '')) IS DISTINCT FROM (d.estado, COALESCE(d.motivo, ''));",
  "UPDATE informe_clinico m SET estado = d.estado, motivo = d.motivo FROM dev_informe d WHERE m.id = d.id AND (m.estado, COALESCE(m.motivo, '')) IS DISTINCT FROM (d.estado, COALESCE(d.motivo, ''));",
  'UPDATE receta m SET pdf_url = d.pdf_url, qbi2_id_receta = d.qbi2_id_receta, qbi2_verificador = d.qbi2_verificador, qbi2_nro_cuir = d.qbi2_nro_cuir, qbi2_fecha_vencimiento = d.qbi2_fecha_vencimiento, qbi2_id_transaccion = d.qbi2_id_transaccion, fecha_emision = d.fecha_emision FROM dev_receta d WHERE m.id = d.id AND (m.pdf_url, m.qbi2_id_receta, m.qbi2_verificador, m.qbi2_nro_cuir, m.qbi2_fecha_vencimiento, m.qbi2_id_transaccion, m.fecha_emision) IS DISTINCT FROM (d.pdf_url, d.qbi2_id_receta, d.qbi2_verificador, d.qbi2_nro_cuir, d.qbi2_fecha_vencimiento, d.qbi2_id_transaccion, d.fecha_emision);',
  'UPDATE solicitud_documento m SET estado = d.estado, transaction_id = d.transaction_id, emitido = d.emitido, url_descarga = d.url_descarga, fecha_emision = d.fecha_emision FROM dev_solicitud d WHERE m.id = d.id AND (m.estado, m.transaction_id, m.emitido, m.url_descarga, m.fecha_emision) IS DISTINCT FROM (d.estado, d.transaction_id, d.emitido, d.url_descarga, d.fecha_emision);',
  // Ajuste de secuencias
  "SELECT setval(pg_get_serial_sequence('seguimiento_diario', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM seguimiento_diario), 1));",
  "SELECT setval(pg_get_serial_sequence('informe_clinico', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM informe_clinico), 1));",
  "SELECT setval(pg_get_serial_sequence('receta', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM receta), 1));",
  "SELECT setval(pg_get_serial_sequence('solicitud_documento', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM solicitud_documento), 1));",
  'COMMIT;',
  '',
].join('\n');

psql(base, ['-q', '-f', '-'], carga);

console.log(`Listo: el monolito tiene ${numero(base, 'SELECT count(*) FROM seguimiento_diario')} seguimientos, ${numero(base, 'SELECT count(*) FROM informe_clinico')} informes, ${numero(base, 'SELECT count(*) FROM receta')} recetas y ${numero(base, 'SELECT count(*) FROM solicitud_documento')} solicitudes.`);
console.log('Conciliá con: node scripts/fase7-conciliar-clinica.mjs');
