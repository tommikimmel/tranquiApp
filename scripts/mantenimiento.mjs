#!/usr/bin/env node
// Prende y apaga el modo mantenimiento de producción (ver docs/entornos/produccion.md) y, al
// apagarlo, manda el mail de novedades (ver docs/novedades.md).
//
//   npm run mantenimiento -- on ["mensaje"] [--novedades archivo.md]
//   npm run mantenimiento -- off
//   npm run mantenimiento -- estado
//   npm run mantenimiento -- novedades-vista archivo.md      vista previa local (no se conecta a nada)
//   npm run mantenimiento -- novedades-prueba archivo.md     manda el mail solo a los administradores
//   npm run mantenimiento -- novedades-estado [id]           estado del último envío (o de uno puntual)
//
// Al prender con --novedades, el texto queda guardado en el VPS y se envía automáticamente al
// apagar, recién cuando el sitio volvió a responder 200. Sin --novedades, apagar no manda nada.
//
// Al prender también se genera un link de acceso de administrador para ver el sitio real mientras
// el resto ve el aviso; deja de servir al apagar.
//
//   npm run mantenimiento -- acceso                          vuelve a mostrar el link de administrador
//
// Credenciales del VPS (ver docs/entornos/secretos.md): VPS_HOST, VPS_USER, VPS_PRIVATE_KEY_PATH y
// VPS_HOST_FINGERPRINT, por variables de entorno o en el archivo .env.vps de la raíz (ignorado por git).
// Desde GitHub Actions el link de administrador no se imprime (los logs pueden ser públicos):
// se obtiene después con `npm run mantenimiento -- acceso` desde tu máquina.

import { createRequire } from 'node:module'
import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const require = createRequire(import.meta.url)

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SITIO = 'https://tranquisalud.com'
const DIR = '/srv/tranqui/mantenimiento'
const CONTENEDOR = 'tranqui-frontend'
// El backend publica 127.0.0.1:8081 en el VPS; el endpoint de novedades rechaza lo que pasa por Traefik.
const API_INTERNA = 'http://127.0.0.1:8081/api/internal/novedades'
const ENV_VPS = '/srv/tranqui/.env'
const EN_ACTIONS = process.env.GITHUB_ACTIONS === 'true'

// ---------------------------------------------------------------------------------------------
// Novedades: formato del archivo
// ---------------------------------------------------------------------------------------------

/**
 * Lee el archivo de novedades:
 *   # Título del mail
 *   ## Para todos        (también "Todos" o "General"; las viñetas antes de cualquier sección van acá)
 *   - viñeta
 *   ## Pacientes
 *   - viñeta
 *   ## Profesionales
 *   - viñeta
 */
function parsearNovedades(texto) {
  let titulo = ''
  const secciones = { general: [], pacientes: [], profesionales: [] }
  let actual = 'general'
  for (const linea of texto.split(/\r?\n/)) {
    const l = linea.trim()
    if (!l) continue
    if (/^#\s+/.test(l) && !/^##/.test(l)) { titulo = l.replace(/^#\s+/, '').trim(); continue }
    const h = /^##\s+(.+)$/.exec(l)
    if (h) {
      const nombre = h[1].toLowerCase()
      if (nombre.includes('paciente')) actual = 'pacientes'
      else if (nombre.includes('profesional')) actual = 'profesionales'
      else if (/(todos|general)/.test(nombre)) actual = 'general'
      else throw new Error(`Sección desconocida: "${h[1]}". Usá "Para todos", "Pacientes" o "Profesionales".`)
      continue
    }
    const v = /^[-*]\s+(.+)$/.exec(l)
    if (v) { secciones[actual].push(v[1].trim()); continue }
    throw new Error(`Línea no reconocida: "${l}". Usá "# Título", "## Sección" y viñetas con "- ".`)
  }
  if (!titulo) throw new Error('Falta el título: la primera línea tiene que ser "# Título del mail".')
  const total = secciones.general.length + secciones.pacientes.length + secciones.profesionales.length
  if (!total) throw new Error('El archivo no tiene ninguna viñeta.')
  return { titulo, ...secciones }
}

function leerNovedades(archivo) {
  if (!archivo) throw new Error('Falta el archivo de novedades.')
  return parsearNovedades(readFileSync(path.resolve(process.cwd(), archivo), 'utf8'))
}

function vistaPrevia(n) {
  const bloque = (publico, items) => items.length
    ? `\n  [${publico}] ${n.titulo}\n` + items.map(i => `    - ${i}`).join('\n')
    : `\n  [${publico}] no recibe mail (sin viñetas para este público)`
  console.log('Vista previa del mail de novedades:')
  console.log(bloque('pacientes', [...n.general, ...n.pacientes]))
  console.log(bloque('profesionales', [...n.general, ...n.profesionales]))
  console.log('')
}

function idEnvio() {
  const d = new Date()
  const p = x => String(x).padStart(2, '0')
  return `mant-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`
}

// ---------------------------------------------------------------------------------------------
// SSH
// ---------------------------------------------------------------------------------------------

// Variables de .env.vps (KEY=valor por línea), si existe; las variables de entorno tienen prioridad.
function leerEnvVps() {
  const archivo = path.join(ROOT, '.env.vps')
  if (!existsSync(archivo)) return {}
  const vars = {}
  for (const linea of readFileSync(archivo, 'utf8').split(/\r?\n/)) {
    const m = /^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/.exec(linea)
    if (m) vars[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
  return vars
}

function configSsh() {
  const archivo = leerEnvVps()
  const v = k => process.env[k] || archivo[k]
  const host = v('VPS_HOST'), username = v('VPS_USER') || 'root', clave = v('VPS_PRIVATE_KEY_PATH')
  if (!host || !clave) {
    throw new Error('Faltan VPS_HOST y VPS_PRIVATE_KEY_PATH (variables de entorno o archivo .env.vps). Ver docs/entornos/secretos.md.')
  }
  const cfg = { host, port: Number(v('VPS_PORT') || 22), username, privateKey: readFileSync(clave.replace(/^~/, process.env.HOME)), readyTimeout: 20000 }
  // Verifica que el servidor sea el nuestro (huella SHA256 de su clave, como la muestra ssh-keygen -l).
  const huella = v('VPS_HOST_FINGERPRINT')
  if (huella) {
    cfg.hostVerifier = clave => `SHA256:${createHash('sha256').update(clave).digest('base64').replace(/=+$/, '')}` === huella.trim()
  } else {
    console.warn('Aviso: sin VPS_HOST_FINGERPRINT no se verifica la identidad del servidor.')
  }
  return cfg
}

function ssh(comando, entrada) {
  const { Client } = require('ssh2')
  return new Promise((resolve, reject) => {
    const c = new Client()
    c.on('ready', () => c.exec(comando, (err, stream) => {
      if (err) { c.end(); return reject(err) }
      let out = '', errOut = ''
      stream.on('data', d => { out += d }).stderr.on('data', d => { errOut += d })
      stream.on('close', code => { c.end(); code === 0 ? resolve(out) : reject(new Error(errOut || out || `código ${code}`)) })
      if (entrada !== undefined) stream.end(entrada)
    })).on('error', reject).connect(configSsh())
  })
}

// Comillas simples seguras para sh.
const sh = s => `'${String(s).replace(/'/g, `'\\''`)}'`

// curl al endpoint interno desde el propio VPS. El token se lee del .env del servidor y nunca sale de él.
const curlNovedades = (args) =>
  `TOKEN=$(grep -E '^NOVEDADES_TOKEN=' ${ENV_VPS} | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'); ` +
  `[ -n "$TOKEN" ] || { echo 'Falta NOVEDADES_TOKEN en ${ENV_VPS}' >&2; exit 3; }; ` +
  `curl -sS -w '\\n%{http_code}' -H 'Content-Type: application/json' -H "X-Novedades-Token: $TOKEN" ${args}`

function respuesta(out) {
  const lineas = out.trim().split('\n')
  const status = Number(lineas.pop())
  let cuerpo = lineas.join('\n')
  try { cuerpo = JSON.parse(cuerpo) } catch { /* texto */ }
  return { status, cuerpo }
}

// ---------------------------------------------------------------------------------------------
// Comandos
// ---------------------------------------------------------------------------------------------

async function verificarMontaje() {
  const montajes = await ssh(`docker inspect -f '{{range .Mounts}}{{.Destination}} {{end}}' ${CONTENEDOR}`)
  if (!montajes.includes('/etc/nginx/mantenimiento')) {
    throw new Error(`El contenedor ${CONTENEDOR} no tiene montada la carpeta de mantenimiento: hace falta desplegar la versión que incluye el modo mantenimiento.`)
  }
}

async function estadoHttp() {
  const r = await fetch(SITIO + '/', { redirect: 'manual', cache: 'no-store' })
  return r.status
}

async function on(mensaje, archivoNovedades) {
  const novedades = archivoNovedades ? { id: idEnvio(), ...leerNovedades(archivoNovedades) } : null
  if (novedades) vistaPrevia(novedades)
  await verificarMontaje()
  const token = randomBytes(24).toString('hex')
  const info = JSON.stringify({ mensaje: mensaje || '' })
  const pasos = [
    `mkdir -p ${DIR}`,
    `printf %s ${sh(info)} > ${DIR}/info.json`,
    `printf 'set $mant_token "%s";\\n' ${sh(token)} > ${DIR}/token.conf`,
  ]
  if (novedades) {
    pasos.push(`echo ${sh(Buffer.from(JSON.stringify(novedades)).toString('base64'))} | base64 -d > ${DIR}/novedades.json`)
  }
  pasos.push(`touch ${DIR}/activo`, `docker exec ${CONTENEDOR} nginx -t`, `docker exec ${CONTENEDOR} nginx -s reload`)
  await ssh(pasos.join(' && '))
  await new Promise(r => setTimeout(r, 1500))
  const status = await estadoHttp()
  console.log(`Modo mantenimiento ACTIVO (el sitio responde ${status}${status === 503 ? ', correcto' : ', se esperaba 503'}).`)
  if (mensaje) console.log(`Mensaje: ${mensaje}`)
  console.log(novedades
    ? `Novedades "${novedades.id}" guardadas: se envían al apagar el mantenimiento.`
    : 'Sin novedades: al apagar no se manda ningún mail.')
  if (EN_ACTIONS) {
    console.log('\nEl link de acceso de administrador no se muestra en GitHub Actions. Obtenelo desde tu máquina con: npm run mantenimiento -- acceso')
  } else {
    mostrarAcceso(token)
  }
}

function mostrarAcceso(token) {
  console.log(`\nAcceso de administrador (abrilo en tu navegador para ver el sitio real):\n  ${SITIO}/__acceso-mantenimiento?token=${token}\n`)
  console.log('No compartas este link. Deja de servir al apagar el mantenimiento.')
}

async function acceso() {
  if (EN_ACTIONS) throw new Error('El link de acceso no se muestra en GitHub Actions: corré este comando desde tu máquina.')
  const conf = await ssh(`cat ${DIR}/token.conf 2>/dev/null || true`)
  const m = /"([0-9a-f]+)"/.exec(conf)
  if (!m) return console.log('El modo mantenimiento no está activo (no hay token de acceso).')
  mostrarAcceso(m[1])
}

async function off() {
  await verificarMontaje()
  await ssh([
    `rm -f ${DIR}/activo ${DIR}/token.conf ${DIR}/info.json`,
    `docker exec ${CONTENEDOR} nginx -t`,
    `docker exec ${CONTENEDOR} nginx -s reload`,
  ].join(' && '))
  await new Promise(r => setTimeout(r, 1500))
  const status = await estadoHttp()
  console.log(`Modo mantenimiento APAGADO (el sitio responde ${status}${status === 200 ? ', correcto' : ', se esperaba 200'}).`)

  const hayNovedades = (await ssh(`test -f ${DIR}/novedades.json && echo si || echo no`)).trim() === 'si'
  if (!hayNovedades) return
  if (status !== 200) {
    console.log('\nNo se mandaron las novedades porque el sitio no respondió 200. Cuando esté arriba: npm run mantenimiento -- novedades-reintentar')
    return
  }
  await enviarNovedadesGuardadas()
}

async function enviarNovedadesGuardadas() {
  const out = await ssh(curlNovedades(`-X POST --data-binary @${DIR}/novedades.json ${API_INTERNA}`))
  const { status, cuerpo } = respuesta(out)
  if (status === 202 || status === 200) {
    const id = cuerpo.id
    await ssh(`mkdir -p ${DIR}/enviadas && mv ${DIR}/novedades.json ${DIR}/enviadas/${id}.json`)
    console.log(cuerpo.yaExistia
      ? `\nLas novedades "${id}" ya se habían enviado (estado ${cuerpo.estado}); no se mandaron de nuevo.`
      : `\nNovedades "${id}" en envío. Para ver cómo terminó: npm run mantenimiento -- novedades-estado ${id}`)
  } else {
    console.log(`\nNo se pudieron enviar las novedades (HTTP ${status}): ${JSON.stringify(cuerpo)}`)
    console.log('Quedan guardadas en el VPS. Para reintentar: npm run mantenimiento -- novedades-reintentar')
  }
}

async function estado() {
  const activo = (await ssh(`test -f ${DIR}/activo && echo si || echo no`)).trim() === 'si'
  const novedades = (await ssh(`test -f ${DIR}/novedades.json && echo si || echo no`)).trim() === 'si'
  console.log(`Modo mantenimiento: ${activo ? 'ACTIVO' : 'apagado'} (el sitio responde ${await estadoHttp()}).`)
  console.log(`Novedades pendientes de envío: ${novedades ? 'sí' : 'no'}.`)
}

async function novedadesPrueba(archivo) {
  const n = { id: 'prueba', ...leerNovedades(archivo) }
  vistaPrevia(n)
  const out = await ssh(curlNovedades(`-X POST --data-binary @- '${API_INTERNA}?prueba=true'`), JSON.stringify(n))
  const { status, cuerpo } = respuesta(out)
  if (status === 200) console.log(`Prueba enviada a los administradores (${cuerpo.mailsEnviados} mails).`)
  else console.log(`No se pudo enviar la prueba (HTTP ${status}): ${JSON.stringify(cuerpo)}`)
}

async function novedadesEstado(id) {
  if (!id) {
    const ultimo = (await ssh(`ls -t ${DIR}/enviadas 2>/dev/null | head -1`)).trim()
    if (!ultimo) return console.log('Todavía no se envió ninguna novedad.')
    id = ultimo.replace(/\.json$/, '')
  }
  const { status, cuerpo } = respuesta(await ssh(curlNovedades(`${API_INTERNA}/${encodeURIComponent(id)}`)))
  if (status === 200) console.log(`Novedades "${id}": ${cuerpo.estado}${cuerpo.detalle ? ` (${cuerpo.detalle})` : ''}`)
  else console.log(`No hay registro de las novedades "${id}".`)
}

// ---------------------------------------------------------------------------------------------

const args = process.argv.slice(2)
const cmd = args.shift()
const opcion = nombre => {
  const i = args.indexOf(nombre)
  if (i < 0) return null
  const valor = args[i + 1]
  args.splice(i, 2)
  return valor
}

try {
  if (cmd === 'on') {
    const archivo = opcion('--novedades')
    await on(args.join(' ').trim(), archivo)
  } else if (cmd === 'off') await off()
  else if (cmd === 'estado') await estado()
  else if (cmd === 'acceso') await acceso()
  else if (cmd === 'novedades-vista') vistaPrevia(leerNovedades(args[0]))
  else if (cmd === 'novedades-prueba') await novedadesPrueba(args[0])
  else if (cmd === 'novedades-reintentar') await enviarNovedadesGuardadas()
  else if (cmd === 'novedades-estado') await novedadesEstado(args[0])
  else {
    console.error('Uso: npm run mantenimiento -- on ["mensaje"] [--novedades archivo.md] | off | estado | acceso |\n' +
      '     novedades-vista archivo.md | novedades-prueba archivo.md | novedades-reintentar | novedades-estado [id]')
    process.exit(1)
  }
} catch (e) {
  console.error('Error:', e.message)
  process.exit(1)
}
