#!/usr/bin/env node
// Fase 11, C4: "golden master" de la API. Manda las lecturas que hace el frontend de main, con cada rol de los datos de
// demo, a un stack y guarda el código y la forma de cada respuesta (campos y tipos de JSON, no los valores: los datos
// de demo tienen fechas relativas y la suite de punta a punta los cambia). Después compara dos grabaciones.
// Los stacks no entran juntos en la PC: se graba uno, se baja, se levanta el otro y se graba.
//
// Uso:
//   node scripts/comparacion/golden-master.mjs grabar <url-api> <salida.json>   (main: http://localhost:18081,
//                                                                                migración: http://localhost:8080)
//   node scripts/comparacion/golden-master.mjs comparar <main.json> <migracion.json>

import fs from 'node:fs'

const CLAVE = 'admin123'
const CUENTAS = {
  paciente: 'valentina.gomez@demo.tranqui',
  profesional: 'lucia.fernandez@demo.tranqui',
  psiquiatra: 'martin.rios@demo.tranqui',
  vencida: 'sofia.medina@demo.tranqui',
  admin: 'admin@tranqui.com',
}

const fecha = (dias) => new Date(Date.now() + dias * 86400000).toISOString().slice(0, 10)

// [rol o null, ruta]. {lucia} y {paciente} se resuelven con el buscador y los pacientes atendidos.
const PEDIDOS = [
  [null, '/api/health'],
  [null, '/api/site-access/estado'],
  [null, '/api/medicos'],
  [null, `/api/medicos/{lucia}/turnos-disponibles?fecha=${fecha(3)}&modalidad=ONLINE`],
  [null, `/api/medicos/turnos-disponibles-conteo?fecha=${fecha(3)}&modalidad=ONLINE&medicoIds={lucia}`],
  [null, '/api/subscriptions/plans'],
  [null, '/api/auth/me'],
  ['paciente', '/api/auth/me'],
  ['paciente', '/api/auth/mi-cuenta'],
  ['paciente', '/api/auth/mi-cuenta/novedades'],
  ['paciente', '/api/pacientes/me/turnos'],
  ['paciente', '/api/pacientes/me/seguimientos'],
  ['paciente', '/api/pacientes/me/informes'],
  ['paciente', '/api/recetas/me'],
  ['paciente', '/api/recetas/financiadores'],
  ['paciente', '/api/notificaciones'],
  ['paciente', '/api/chat/canales'],
  ['paciente', '/api/chat/tiene-no-leidos'],
  ['paciente', '/api/tickets/mios'],
  ['paciente', '/api/medicos/perfil'],
  ['paciente', '/api/admin/users'],
  ['profesional', '/api/auth/me'],
  ['profesional', '/api/medicos/perfil'],
  ['profesional', '/api/medicos/stats?periodo=MENSUAL'],
  ['profesional', '/api/medicos/turnos'],
  ['profesional', '/api/medicos/turnos/hoy'],
  ['profesional', '/api/medicos/disponibilidad?modalidad=ONLINE'],
  ['profesional', '/api/medicos/disponibilidad?modalidad=PRESENCIAL'],
  ['profesional', '/api/medicos/disponibilidad-config'],
  ['profesional', '/api/medicos/mercadopago/status'],
  ['profesional', '/api/medicos/google-calendar/status'],
  ['profesional', '/api/medicos/google-calendar/eventos'],
  ['profesional', '/api/pacientes/atendidos'],
  ['profesional', '/api/pacientes/{paciente}/seguimientos'],
  ['profesional', '/api/pacientes/{paciente}/informes'],
  ['profesional', '/api/chat/canales'],
  ['profesional', '/api/chat/canales/visitadores'],
  ['profesional', '/api/chat/historial/{paciente}?page=0&size=20'],
  ['profesional', '/api/notificaciones'],
  ['profesional', '/api/subscriptions/my-subscription'],
  ['profesional', '/api/tickets/mios'],
  ['psiquiatra', '/api/recetas/me'],
  ['vencida', '/api/medicos/perfil'],
  ['vencida', '/api/medicos/turnos'],
  ['vencida', '/api/subscriptions/my-subscription'],
  ['admin', '/api/admin/users'],
  ['admin', '/api/admin/tickets'],
  ['admin', '/api/admin/subscriptions/overview'],
  ['admin', '/api/admin/subscriptions/list'],
  ['admin', '/api/admin/subscriptions/plans'],
  ['admin', '/api/admin/subscriptions/events'],
  ['admin', '/api/medicos/perfil'],
]

/** La forma de un JSON: tipos en las hojas, la unión de los campos de los elementos de cada lista. */
export function forma(v) {
  if (v === null) return 'null'
  if (Array.isArray(v)) {
    if (!v.length) return []
    const unida = {}
    let hoja = null
    for (const e of v) {
      const f = forma(e)
      if (f && typeof f === 'object' && !Array.isArray(f)) Object.assign(unida, f)
      else hoja = f
    }
    return [Object.keys(unida).length ? ordenar(unida) : hoja]
  }
  if (typeof v === 'object') {
    // Un mapa por id (claves numéricas): solo importa la forma de los valores.
    const claves = Object.keys(v)
    if (claves.length && claves.every((k) => /^-?\d+$/.test(k))) return { '{id}': forma(Object.values(v)[0]) }
    return ordenar(Object.fromEntries(claves.map((k) => [k, forma(v[k])])))
  }
  return typeof v
}

function ordenar(o) {
  return Object.fromEntries(Object.keys(o).sort().map((k) => [k, o[k]]))
}

/** Diferencias de forma: campos que faltan o sobran y tipos distintos. null de un lado no cuenta como diferencia. */
export function diferencias(a, b, camino = '$', salida = []) {
  if (a === 'null' || b === 'null' || JSON.stringify(a) === JSON.stringify(b)) return salida
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length && b.length) diferencias(a[0], b[0], `${camino}[]`, salida)
    return salida
  }
  const objeto = (x) => x && typeof x === 'object' && !Array.isArray(x)
  if (objeto(a) && objeto(b)) {
    for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
      if (!(k in b)) salida.push(`${camino}.${k}: falta en la migración`)
      else if (!(k in a)) salida.push(`${camino}.${k}: solo en la migración`)
      else diferencias(a[k], b[k], `${camino}.${k}`, salida)
    }
    return salida
  }
  salida.push(`${camino}: ${JSON.stringify(a)} en main, ${JSON.stringify(b)} en la migración`)
  return salida
}

async function login(api, email) {
  for (let i = 0; i < 10; i++) {
    const r = await fetch(`${api}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: CLAVE }) })
    // Límite de logins del gateway: se espera a que se reponga el cupo.
    if (r.status === 429) { await new Promise((ok) => setTimeout(ok, 13000)); continue }
    const cookie = (r.headers.getSetCookie?.() || []).find((c) => c.startsWith('SESSION-TOKEN='))
    if (!r.ok || !cookie) throw new Error(`login ${email}: ${r.status}`)
    return cookie.split(';')[0]
  }
  throw new Error(`login ${email}: 429`)
}

async function grabar(api, salida) {
  const cookies = {}
  for (const [rol, email] of Object.entries(CUENTAS)) cookies[rol] = await login(api, email)
  const pedir = (rol, ruta) => fetch(`${api}${ruta}`, { headers: rol ? { Cookie: cookies[rol] } : {} })
  const medicos = await (await pedir(null, '/api/medicos')).json()
  const atendidos = await (await pedir('profesional', '/api/pacientes/atendidos')).json()
  const ids = {
    lucia: medicos.find((m) => m.email === CUENTAS.profesional)?.id,
    paciente: atendidos.find((p) => p.email === CUENTAS.paciente)?.id ?? atendidos[0]?.id,
  }
  const resultado = {}
  for (const [rol, plantilla] of PEDIDOS) {
    const ruta = plantilla.replace(/\{(\w+)\}/g, (_, k) => ids[k])
    const r = await pedir(rol, ruta)
    const texto = await r.text()
    let cuerpo
    try { cuerpo = forma(JSON.parse(texto)) } catch { cuerpo = texto ? 'texto' : 'vacío' }
    resultado[`${rol ?? 'anónimo'} GET ${plantilla}`] = { status: r.status, forma: cuerpo }
  }
  fs.writeFileSync(salida, JSON.stringify(resultado, null, 2) + '\n')
  console.log(`${Object.keys(resultado).length} pedidos grabados en ${salida}`)
}

function comparar(archivoMain, archivoMigracion) {
  const main = JSON.parse(fs.readFileSync(archivoMain, 'utf8'))
  const migracion = JSON.parse(fs.readFileSync(archivoMigracion, 'utf8'))
  let distintos = 0
  for (const [pedido, a] of Object.entries(main)) {
    const b = migracion[pedido]
    const lista = !b ? ['no se grabó en la migración']
      : a.status !== b.status ? [`status ${a.status} en main, ${b.status} en la migración`]
        : diferencias(a.forma, b.forma)
    if (lista.length) {
      distintos++
      console.log(`${pedido}\n  ${lista.join('\n  ')}`)
    }
  }
  console.log(`\n${Object.keys(main).length - distintos} iguales, ${distintos} con diferencias.`)
}

const [paso, a, b] = process.argv.slice(2)
if (paso === 'grabar') await grabar(a, b)
else if (paso === 'comparar') comparar(a, b)
else if (process.argv[1]?.endsWith('golden-master.mjs')) console.log('Uso: grabar <url-api> <salida.json> | comparar <main.json> <migracion.json>')
