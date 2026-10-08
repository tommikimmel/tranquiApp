#!/usr/bin/env node
// Maneja el entorno local (docker-compose.local.yml). Uso: npm run local[:down|:reset|:logs]
//
//   up     (por defecto) verifica que los puertos estén libres, reconstruye y levanta todo.
//   down   frena el entorno (conserva la base local).
//   reset  frena el entorno, BORRA la base local y lo vuelve a levantar con datos de demo nuevos.
//   logs   muestra los logs del backend.
//
// Antes de cada rebuild revisa los puertos del entorno. Si alguno está ocupado por algo que no es
// este mismo entorno (un contenedor de otro proyecto o un proceso), muestra qué es y pregunta si
// puede frenarlo. Si la respuesta es no, aborta sin tocar nada.

import { execFileSync, spawnSync } from 'node:child_process'
import net from 'node:net'
import readline from 'node:readline/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const COMPOSE = ['compose', '-f', path.join(ROOT, 'docker-compose.local.yml')]
const PROJECT = 'tranqui-local'
const PORTS = [
  { port: 5433, what: 'base de datos' },
  { port: 8081, what: 'backend' },
  { port: 5173, what: 'frontend' },
  { port: 8025, what: 'Mailpit (bandeja web)' },
  { port: 1025, what: 'Mailpit (SMTP)' },
]

const run = (cmd, args, opts = {}) => spawnSync(cmd, args, { stdio: 'inherit', cwd: ROOT, ...opts })
const capture = (cmd, args) => {
  try { return execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }) } catch { return '' }
}

function puertoOcupado(port) {
  return new Promise(resolve => {
    const srv = net.createServer()
    srv.once('error', err => resolve(err.code === 'EADDRINUSE' || err.code === 'EACCES'))
    srv.once('listening', () => srv.close(() => resolve(false)))
    srv.listen(port, '127.0.0.1')
  })
}

// Contenedor de Docker que publica el puerto, si lo hay.
function contenedorEnPuerto(port) {
  const out = capture('docker', ['ps', '--format', '{{.ID}}\t{{.Names}}\t{{.Ports}}\t{{.Label "com.docker.compose.project"}}'])
  for (const line of out.split('\n').filter(Boolean)) {
    const [id, name, ports, project] = line.split('\t')
    if (new RegExp(`:${port}->`).test(ports)) return { id, name, project }
  }
  return null
}

// Proceso (fuera de Docker) que escucha en el puerto: { pid, nombre }.
function procesoEnPuerto(port) {
  if (process.platform === 'win32') {
    const out = capture('netstat', ['-ano', '-p', 'tcp'])
    const line = out.split('\n').find(l => new RegExp(`:${port}\\s`).test(l) && /LISTENING/i.test(l))
    const pid = line?.trim().split(/\s+/).pop()
    if (!pid) return null
    const nombre = capture('tasklist', ['/FI', `PID eq ${pid}`, '/FO', 'CSV', '/NH']).split(',')[0]?.replace(/"/g, '')
    return { pid, nombre: nombre || 'desconocido' }
  }
  const pid = capture('lsof', ['-nP', `-iTCP:${port}`, '-sTCP:LISTEN', '-t']).trim().split('\n')[0]
  if (pid) return { pid, nombre: capture('ps', ['-p', pid, '-o', 'comm=']).trim() || 'desconocido' }
  const ss = capture('ss', ['-ltnpH', `sport = :${port}`])
  const m = /users:\(\("([^"]+)",pid=(\d+)/.exec(ss)
  return m ? { pid: m[2], nombre: m[1] } : null
}

async function preguntar(rl, texto) {
  const r = (await rl.question(`${texto} [s/N] `)).trim().toLowerCase()
  return r === 's' || r === 'si' || r === 'sí' || r === 'y' || r === 'yes'
}

async function liberarPuertos() {
  const ocupados = []
  for (const p of PORTS) if (await puertoOcupado(p.port)) ocupados.push(p)
  if (!ocupados.length) return true

  const ajenos = []
  for (const p of ocupados) {
    const c = contenedorEnPuerto(p.port)
    if (c && c.project === PROJECT) continue // es este mismo entorno: compose lo recrea
    ajenos.push({ ...p, contenedor: c, proceso: c ? null : procesoEnPuerto(p.port) })
  }
  if (!ajenos.length) return true

  if (!process.stdin.isTTY) {
    console.error('Hay puertos ocupados y no hay terminal para preguntar. Liberalos y volvé a intentar:')
    for (const a of ajenos) console.error(`  - ${a.port} (${a.what})`)
    return false
  }

  const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
  try {
    for (const a of ajenos) {
      if (a.contenedor) {
        console.log(`\nEl puerto ${a.port} (${a.what}) lo usa el contenedor "${a.contenedor.name}"${a.contenedor.project ? ` del proyecto "${a.contenedor.project}"` : ''}.`)
        if (!(await preguntar(rl, `¿Freno el contenedor "${a.contenedor.name}"? (docker stop, no borra sus datos)`))) return false
        run('docker', ['stop', a.contenedor.name])
      } else if (a.proceso) {
        console.log(`\nEl puerto ${a.port} (${a.what}) lo usa el proceso "${a.proceso.nombre}" (PID ${a.proceso.pid}).`)
        if (!(await preguntar(rl, `¿Freno el proceso ${a.proceso.pid}?`))) return false
        if (process.platform === 'win32') run('taskkill', ['/PID', a.proceso.pid, '/F'])
        else process.kill(Number(a.proceso.pid), 'SIGTERM')
      } else {
        console.log(`\nEl puerto ${a.port} (${a.what}) está ocupado y no pude identificar por quién. Liberalo a mano y volvé a intentar.`)
        return false
      }
    }
  } finally {
    rl.close()
  }

  await new Promise(r => setTimeout(r, 1500))
  for (const a of ajenos) {
    if (await puertoOcupado(a.port)) {
      console.error(`El puerto ${a.port} sigue ocupado. Liberalo a mano y volvé a intentar.`)
      return false
    }
  }
  return true
}

async function up() {
  if (!(await liberarPuertos())) {
    console.error('\nNo se levantó el entorno local: hay puertos ocupados.')
    process.exit(1)
  }
  const r = run('docker', [...COMPOSE, 'up', '--build', '-d'])
  if (r.status !== 0) process.exit(r.status ?? 1)
  console.log(`
Entorno local levantándose (la primera vez tarda unos minutos: compila el backend e instala el frontend).

  App:       http://localhost:5173
  Backend:   http://localhost:8081
  Mails:     http://localhost:8025
  Base:      localhost:5433 (tranqui / tranqui-local)

Cuentas de demo: ver docs/entornos/local.md (contraseña de todas: admin123).
Logs del backend: npm run local:logs
`)
}

const cmd = process.argv[2] || 'up'
if (cmd === 'up') await up()
else if (cmd === 'down') process.exit(run('docker', [...COMPOSE, 'down']).status ?? 0)
else if (cmd === 'reset') {
  run('docker', [...COMPOSE, 'down', '-v'])
  await up()
} else if (cmd === 'logs') process.exit(run('docker', [...COMPOSE, 'logs', '-f', 'backend']).status ?? 0)
else {
  console.error(`Comando desconocido: ${cmd}. Usá up, down, reset o logs.`)
  process.exit(1)
}
