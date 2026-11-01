#!/usr/bin/env node
// Fase 11, C1 y C2: inventario de una revisión del repo y mapa de main a la migración.
//
//   node scripts/comparacion/inventario.mjs inventario <repo> <salida.json>
//     Endpoints (método, ruta, clase, método Java, @PreAuthorize), tareas programadas y de arranque, mails,
//     integraciones externas (hosts https) y llamadas del frontend (api.ts), de backend/ y services/*.
//   node scripts/comparacion/inventario.mjs mapa <main.json> <migracion.json> <repo-migracion> <salida.md>
//     Para cada endpoint de main: qué regla del gateway lo atiende en la migración, en qué módulo cae (según las
//     TRANQUI_SERVICES_*_URI del compose local) y si ese módulo tiene el mismo método y ruta.
//
// Es un inventario por expresiones regulares: no ve la lógica dentro de los métodos. Por eso C3 es revisión de código.

import fs from 'node:fs'
import path from 'node:path'

const VERBOS = { Get: 'GET', Post: 'POST', Put: 'PUT', Patch: 'PATCH', Delete: 'DELETE' }

function archivos(dir, filtro, salida = []) {
  if (!fs.existsSync(dir)) return salida
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) {
      if (!['node_modules', 'target', 'test', '.git', 'dist'].includes(e.name)) archivos(p, filtro, salida)
    } else if (filtro(p)) salida.push(p)
  }
  return salida
}

/** Las rutas de los argumentos de una anotación de mapeo (sin produces, consumes, params ni headers). */
export function rutasDe(args) {
  if (args === undefined) return ['']
  const limpio = args.replace(/\b(produces|consumes|params|headers|method)\s*=\s*(\{[^}]*\}|"[^"]*"|[\w.]+)/g, '')
  const rutas = [...limpio.matchAll(/"([^"]*)"/g)].map((m) => m[1])
  return rutas.length ? rutas : ['']
}

const unir = (a, b) => (`/${a}/${b}`).replace(/\/+/g, '/').replace(/(.)\/$/, '$1')

/** Endpoints de un archivo Java con @RestController o @Controller. */
export function endpointsDe(fuente, archivo = '') {
  if (!/@(Rest)?Controller\b/.test(fuente)) return []
  const clase = fuente.match(/\bclass\s+(\w+)/)?.[1] ?? ''
  const antesDeClase = fuente.slice(0, fuente.search(/\bclass\s+\w+/))
  const base = rutasDe(antesDeClase.match(/@RequestMapping\s*(?:\(([^)]*)\))?/)?.[1])
  const cuerpo = fuente.slice(antesDeClase.length)
  const salida = []
  const patron = /@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(?:\(((?:[^()]|\([^()]*\))*)\))?([\s\S]*?)\b(?:public|protected|private)?\s*[\w<>[\],.? ]+\s+(\w+)\s*\(/g
  for (const m of cuerpo.matchAll(patron)) {
    const [, tipo, args, entre, metodo] = m
    const verbos = tipo === 'Request'
      ? ([...(args ?? '').matchAll(/RequestMethod\.(\w+)/g)].map((x) => x[1]).filter(Boolean))
      : [VERBOS[tipo]]
    const rol = (entre.match(/@PreAuthorize\(\s*"([^"]+)"/) ?? [])[1] ?? null
    for (const verbo of verbos.length ? verbos : ['ANY']) {
      for (const b of base) for (const r of rutasDe(args)) {
        salida.push({ metodo: verbo, ruta: unir(b, r) || '/', clase, java: metodo, rol, archivo })
      }
    }
  }
  return salida
}

function tareasDe(fuente, archivo) {
  const clase = fuente.match(/\bclass\s+(\w+)/)?.[1] ?? ''
  const salida = []
  for (const m of fuente.matchAll(/@Scheduled\s*\(([^)]*)\)[\s\S]*?\s(\w+)\s*\(/g)) {
    salida.push({ tipo: 'programada', clase, java: m[2], cuando: m[1].replace(/\s+/g, ' ').trim(), archivo })
  }
  if (/implements\s+[^{]*\b(CommandLineRunner|ApplicationRunner)\b/.test(fuente)) salida.push({ tipo: 'arranque', clase, java: 'run', archivo })
  for (const m of fuente.matchAll(/@EventListener\s*\(\s*(ApplicationReadyEvent|ContextRefreshedEvent)[\s\S]*?\s(\w+)\s*\(/g)) {
    salida.push({ tipo: 'arranque', clase, java: m[2], cuando: m[1], archivo })
  }
  return salida
}

function mailsDe(fuente) {
  const clase = fuente.match(/\bclass\s+(\w+)/)?.[1] ?? ''
  return [...fuente.matchAll(/\b(\w*(?:[Ee]mail|[Mm]ail)\w*(?:Service|Sender))\.(\w+)\(/g)]
    .map((m) => ({ servicio: m[1], llamada: m[2], desde: clase }))
}

function hostsDe(fuente) {
  const clase = fuente.match(/\bclass\s+(\w+)/)?.[1] ?? ''
  return [...fuente.matchAll(/"https:\/\/([a-z0-9.-]+)/g)]
    .filter((m) => !/localhost|example|tranqui/.test(m[1]))
    .map((m) => ({ host: m[1], desde: clase }))
}

/** Llamadas de api.ts: nombre, método y ruta (con las expresiones ${...} como {}). */
export function llamadasFrontend(fuente) {
  const salida = []
  for (const m of fuente.matchAll(/^\s+(\w+):\s*\([^)]*\)[^=]*=>\s*(?:\{[\s\S]*?return\s+)?\s*apiFetch\(\s*([`'])([\s\S]*?)\2\s*(,\s*\{[^}]*method:\s*'(\w+)')?/gm)) {
    salida.push({ nombre: m[1], metodo: m[5] ?? 'GET', ruta: '/api' + m[3].replace(/\$\{[^}]*\}/g, '{}').replace(/\?.*$/, '') })
  }
  return salida
}

function inventario(repo) {
  const servicios = fs.existsSync(path.join(repo, 'services')) ? fs.readdirSync(path.join(repo, 'services')) : []
  const modulos = ['backend', ...servicios.map((s) => `services/${s}`)]
    .filter((m) => fs.existsSync(path.join(repo, m, 'src/main/java')))
  const resultado = { endpoints: [], tareas: [], mails: [], integraciones: [], frontend: [] }
  for (const modulo of modulos) {
    for (const archivo of archivos(path.join(repo, modulo, 'src/main/java'), (p) => p.endsWith('.java'))) {
      const fuente = fs.readFileSync(archivo, 'utf8')
      const rel = path.relative(repo, archivo).split(path.sep).join('/')
      resultado.endpoints.push(...endpointsDe(fuente, rel).map((e) => ({ modulo, ...e })))
      resultado.tareas.push(...tareasDe(fuente, rel).map((t) => ({ modulo, ...t })))
      resultado.mails.push(...mailsDe(fuente).map((x) => ({ modulo, ...x })))
      resultado.integraciones.push(...hostsDe(fuente).map((x) => ({ modulo, ...x })))
    }
  }
  const api = path.join(repo, 'frontend/src/api/api.ts')
  if (fs.existsSync(api)) resultado.frontend = llamadasFrontend(fs.readFileSync(api, 'utf8'))
  return resultado
}

// ---- C2: el gateway de la migración ----

/** Ant (Spring) a regex: ** cualquier cosa, * un segmento, {x} un segmento. */
export function antARegex(patron) {
  // Marcadores primero: si no, el * de un .* ya convertido se vuelve a reemplazar.
  const re = patron.replace(/[.+^$()|[\]\\]/g, '\\$&').replace(/\/\*\*$/, '\u0001').replace(/\*\*/g, '\u0002')
    .replace(/\*/g, '[^/]*').replace(/\{[^}]+\}/g, '[^/]+').replace('\u0001', '(?:/.*)?').replace(/\u0002/g, '.*')
  return new RegExp(`^${re}$`)
}

/** Las reglas del gateway en orden: id, propiedad del destino y patrones de Path. */
export function reglasGateway(yml) {
  const reglas = []
  for (const bloque of yml.split(/\n\s+- id:\s*/).slice(1)) {
    const id = bloque.match(/^([\w-]+)/)[1]
    const uri = bloque.match(/\n\s+uri:\s*(\S+)/)?.[1] ?? ''
    const prop = uri.match(/tranqui\.services\.([\w-]+)\.uri/)?.[1]
    const paths = bloque.match(/- Path=([^\n]+)/)?.[1]?.split(',').map((s) => s.trim()) ?? []
    if (prop && paths.length) reglas.push({ id, prop, paths, regex: paths.map(antARegex) })
  }
  return reglas
}

/** De las variables del gateway en el compose: propiedad → módulo (lb://x → services/x, el monolito → backend). */
export function destinos(compose) {
  const mapa = {}
  for (const m of compose.matchAll(/TRANQUI_SERVICES_([A-Z_-]+)_URI:\s*(\S+)/g)) {
    const prop = m[1].toLowerCase().replace(/_/g, '-')
    mapa[prop] = m[2].startsWith('lb://') ? `services/${m[2].slice(5)}` : 'backend'
  }
  return mapa
}

const normal = (ruta) => ruta.replace(/\{[^}]*\}/g, '{}')

function mapa(archivoMain, archivoMigracion, repoMigracion, salida) {
  const main = JSON.parse(fs.readFileSync(archivoMain, 'utf8'))
  const migracion = JSON.parse(fs.readFileSync(archivoMigracion, 'utf8'))
  const reglas = reglasGateway(fs.readFileSync(path.join(repoMigracion, 'gateway/src/main/resources/application.yml'), 'utf8'))
  const destino = destinos(fs.readFileSync(path.join(repoMigracion, 'docker-compose.local.yml'), 'utf8'))
  const tiene = new Set(migracion.endpoints.map((e) => `${e.modulo} ${e.metodo} ${normal(e.ruta)}`))
  const filas = []
  const cuenta = { encontrado: 0, falta: 0, 'sin regla': 0 }
  for (const e of main.endpoints) {
    const concreta = e.ruta.replace(/\{[^}]+\}/g, '1')
    const regla = reglas.find((r) => r.regex.some((x) => x.test(concreta)))
    const modulo = regla ? (destino[regla.prop] ?? 'backend') : null
    const estado = !regla ? 'sin regla' : tiene.has(`${modulo} ${e.metodo} ${normal(e.ruta)}`) ? 'encontrado' : 'falta'
    cuenta[estado]++
    filas.push(`| \`${e.metodo} ${e.ruta}\` | ${e.clase}.${e.java} | ${regla?.id ?? '—'} | ${modulo ?? '—'} | ${estado} |`)
  }
  const tareas = (inv) => inv.tareas.map((t) => `| ${t.modulo} | ${t.tipo} | ${t.clase}.${t.java} | ${t.cuando ?? ''} |`)
  const unicos = (l) => [...new Set(l)].sort()
  const md = [
    '# Inventario de `main` y mapa a la migración (generado)',
    '',
    'Generado con `node scripts/comparacion/inventario.mjs`. No editar a mano: el análisis va en',
    '[comparacion-main.md](comparacion-main.md).',
    '',
    `## Endpoints de main (${main.endpoints.length}): ${cuenta.encontrado} encontrados, ${cuenta.falta} faltan, ${cuenta['sin regla']} sin regla del gateway`,
    '',
    '| Endpoint | En main | Regla del gateway | Módulo en la migración | Estado |',
    '|---|---|---|---|---|',
    ...filas,
    '',
    `## Tareas programadas y de arranque de main (${main.tareas.length})`,
    '',
    '| Módulo | Tipo | Método | Cuándo |', '|---|---|---|---|', ...tareas(main),
    '',
    `## Tareas programadas y de arranque de la migración (${migracion.tareas.length})`,
    '',
    '| Módulo | Tipo | Método | Cuándo |', '|---|---|---|---|', ...tareas(migracion),
    '',
    '## Mails de main (servicio.método ← quién lo llama)',
    '',
    ...unicos(main.mails.map((m) => `- \`${m.servicio}.${m.llamada}\` ← ${m.desde}`)),
    '',
    '## Integraciones externas (hosts https)',
    '',
    '| Host | main | Migración |', '|---|---|---|',
    ...unicos([...main.integraciones, ...migracion.integraciones].map((i) => i.host)).map((h) =>
      `| ${h} | ${unicos(main.integraciones.filter((i) => i.host === h).map((i) => i.desde)).join(', ') || '—'} | ${unicos(migracion.integraciones.filter((i) => i.host === h).map((i) => `${i.modulo}: ${i.desde}`)).join(', ') || '—'} |`),
    '',
    `## Llamadas del frontend de main (${main.frontend.length}) y si la migración las sigue haciendo`,
    '',
    '| Llamada | Método y ruta | En el frontend migrado |', '|---|---|---|',
    ...main.frontend.map((f) => {
      const igual = migracion.frontend.find((g) => g.nombre === f.nombre)
      return `| ${f.nombre} | \`${f.metodo} ${f.ruta}\` | ${!igual ? 'no está' : igual.metodo === f.metodo && igual.ruta === f.ruta ? 'igual' : `\`${igual.metodo} ${igual.ruta}\``} |`
    }),
    '',
  ].join('\n')
  fs.writeFileSync(salida, md)
  console.log(`Endpoints de main: ${main.endpoints.length} (${cuenta.encontrado} encontrados, ${cuenta.falta} faltan, ${cuenta['sin regla']} sin regla).`)
}

const [paso, ...args] = process.argv.slice(2)
if (paso === 'inventario') {
  const inv = inventario(args[0])
  fs.writeFileSync(args[1], JSON.stringify(inv, null, 2) + '\n')
  console.log(Object.entries(inv).map(([k, v]) => `${k}: ${v.length}`).join(', '))
} else if (paso === 'mapa') {
  mapa(...args)
} else if (process.argv[1]?.endsWith('inventario.mjs')) {
  console.log('Uso: inventario <repo> <salida.json> | mapa <main.json> <migracion.json> <repo-migracion> <salida.md>')
}
