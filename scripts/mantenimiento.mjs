#!/usr/bin/env node
// Prende y apaga el modo mantenimiento de producción (ver docs/entornos/produccion.md).
//
//   npm run mantenimiento -- on ["mensaje opcional"]   muestra la página de mantenimiento a todos
//   npm run mantenimiento -- off                       vuelve el sitio a la normalidad
//   npm run mantenimiento -- estado                    muestra si está activo
//
// Al prender, genera un token nuevo y devuelve un link de acceso de administrador: abriéndolo,
// ese navegador ve el sitio real mientras el resto ve el aviso. Al apagar, el token se borra.
//
// Credenciales del VPS: VPS_HOST, VPS_USER y VPS_PRIVATE_KEY_PATH (o VPS_PASSWORD) por variables de
// entorno. Mientras el deploy siga usando los valores por defecto de deploy.js, se leen de ahí para
// no duplicarlos en otro archivo (ver docs/entornos/secretos.md).

import { createRequire } from 'node:module'
import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const require = createRequire(import.meta.url)
const { Client } = require('ssh2')

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SITIO = 'https://tranquisalud.com'
const DIR = '/srv/tranqui/mantenimiento'
const CONTENEDOR = 'tranqui-frontend'

function configSsh() {
  const deploy = readFileSync(path.join(ROOT, 'deploy.js'), 'utf8')
  const porDefecto = clave => (new RegExp(`${clave}:\\s*process\\.env\\.\\w+\\s*\\|\\|\\s*'([^']*)'`).exec(deploy) || [])[1]
  const cfg = {
    host: process.env.VPS_HOST || porDefecto('host'),
    port: Number(process.env.VPS_PORT || 22),
    username: process.env.VPS_USER || porDefecto('username'),
    readyTimeout: 20000,
  }
  if (process.env.VPS_PRIVATE_KEY_PATH) cfg.privateKey = readFileSync(process.env.VPS_PRIVATE_KEY_PATH)
  else cfg.password = process.env.VPS_PASSWORD || porDefecto('password')
  return cfg
}

function ssh(comando) {
  return new Promise((resolve, reject) => {
    const c = new Client()
    c.on('ready', () => c.exec(comando, (err, stream) => {
      if (err) { c.end(); return reject(err) }
      let out = '', errOut = ''
      stream.on('data', d => { out += d }).stderr.on('data', d => { errOut += d })
      stream.on('close', code => { c.end(); code === 0 ? resolve(out) : reject(new Error(errOut || out || `código ${code}`)) })
    })).on('error', reject).connect(configSsh())
  })
}

// Comillas simples seguras para sh.
const sh = s => `'${String(s).replace(/'/g, `'\\''`)}'`

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

async function on(mensaje) {
  await verificarMontaje()
  const token = randomBytes(24).toString('hex')
  const info = JSON.stringify({ mensaje: mensaje || '' })
  await ssh([
    `mkdir -p ${DIR}`,
    `printf %s ${sh(info)} > ${DIR}/info.json`,
    `printf 'set $mant_token "%s";\\n' ${sh(token)} > ${DIR}/token.conf`,
    `touch ${DIR}/activo`,
    `docker exec ${CONTENEDOR} nginx -t`,
    `docker exec ${CONTENEDOR} nginx -s reload`,
  ].join(' && '))
  await new Promise(r => setTimeout(r, 1500))
  const status = await estadoHttp()
  console.log(`Modo mantenimiento ACTIVO (el sitio responde ${status}${status === 503 ? ', correcto' : ', se esperaba 503'}).`)
  if (mensaje) console.log(`Mensaje: ${mensaje}`)
  console.log(`\nAcceso de administrador (abrilo en tu navegador para ver el sitio real):\n  ${SITIO}/__acceso-mantenimiento?token=${token}\n`)
  console.log('No compartas este link. Deja de servir al apagar el mantenimiento.')
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
}

async function estado() {
  const activo = (await ssh(`test -f ${DIR}/activo && echo si || echo no`)).trim() === 'si'
  console.log(`Modo mantenimiento: ${activo ? 'ACTIVO' : 'apagado'} (el sitio responde ${await estadoHttp()}).`)
}

const [cmd, ...resto] = process.argv.slice(2)
try {
  if (cmd === 'on') await on(resto.join(' ').trim())
  else if (cmd === 'off') await off()
  else if (cmd === 'estado') await estado()
  else {
    console.error('Uso: npm run mantenimiento -- on ["mensaje"] | off | estado')
    process.exit(1)
  }
} catch (e) {
  console.error('Error:', e.message)
  process.exit(1)
}
