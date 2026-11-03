import { test, expect, request } from '@playwright/test'
import { execSync } from 'node:child_process'
import { API, CUENTAS, MAILPIT, json, medicoPorNombre, reservarYPagar, sesion, sinTurnosActivos, type Json } from './soporte'

// Apagan contenedores del stack de la migración: solo con E2E_RESILIENCIA=1 y E2E_COMPOSE apuntando a ese stack
// (por ejemplo "docker compose -p tranqui-local -f docker-compose.local.yml"). Contra main no aplican.
const COMPOSE = process.env.E2E_COMPOSE ?? ''
test.skip(process.env.E2E_RESILIENCIA !== '1' || !COMPOSE, 'Sin E2E_RESILIENCIA=1 y E2E_COMPOSE')
test.describe.configure({ mode: 'serial' })

const compose = (args: string) => execSync(`${COMPOSE} ${args}`, { stdio: 'pipe', timeout: 180000 })

async function esperarSano(ruta: string) {
  const ctx = await request.newContext({ baseURL: API })
  await expect.poll(async () => (await ctx.get(ruta)).status(), { timeout: 180000, intervals: [3000] }).toBeLessThan(500)
}

test('un servicio caído responde 503 claro y el resto sigue andando', async () => {
  const paciente = await sesion(CUENTAS.paciente)
  compose('stop chat')
  try {
    const r = await paciente.get('/api/chat/canales')
    expect(r.status()).toBe(503)
    expect(await r.json()).toMatchObject({ error: 'servicio_no_disponible' })
    expect((await paciente.get('/api/medicos')).status()).toBe(200)
  } finally {
    compose('start chat')
  }
  await esperarSano('/api/medicos')
  await expect.poll(async () => (await paciente.get('/api/chat/canales')).status(), { timeout: 180000, intervals: [3000] }).toBe(200)
})

test('con notificaciones apagado, los eventos esperan y al volver salen una sola vez', async () => {
  const paciente = await sesion(CUENTAS.paciente)
  await sinTurnosActivos(paciente)
  const mailpit = await request.newContext({ baseURL: MAILPIT })
  const mails = async () => (await (await mailpit.get(`/api/v1/search?query=${encodeURIComponent(`to:"${CUENTAS.paciente}"`)}`)).json()).messages ?? []
  const antes = (await mails()).length

  compose('stop notificaciones')
  let turnoId: number
  try {
    const lucia = await medicoPorNombre(paciente, /Luc[ií]a/)
    turnoId = (await reservarYPagar(paciente, CUENTAS.paciente, lucia.id)).turnoId
    await new Promise((r) => setTimeout(r, 5000))
    expect((await mails()).length).toBe(antes)
  } finally {
    compose('start notificaciones')
  }
  await expect.poll(async () => (await mails()).length, { timeout: 180000, intervals: [3000] }).toBeGreaterThan(antes)
  await new Promise((r) => setTimeout(r, 15000))
  const nuevos = (await mails()).slice(0, (await mails()).length - antes)
  const asuntos = nuevos.map((m: Json) => m.Subject)
  expect(new Set(asuntos).size, `mails repetidos: ${asuntos.join(' | ')}`).toBe(asuntos.length)
  await json(await paciente.post(`/api/turnos/${turnoId!}/cancelar`), 'limpieza')
})

test('al final la DLQ está vacía', async () => {
  const rabbit = await request.newContext({
    baseURL: process.env.E2E_RABBIT_URL ?? 'http://localhost:15672',
    httpCredentials: { username: 'tranqui', password: process.env.E2E_RABBIT_PASSWORD ?? 'tranqui-local' }
  })
  const colas = await json(await rabbit.get('/api/queues'), 'colas de RabbitMQ') as Json[]
  const dlq = colas.filter((c) => /dlq/i.test(c.name))
  expect(dlq.length).toBeGreaterThan(0)
  expect(dlq.filter((c) => c.messages > 0).map((c) => `${c.name}: ${c.messages}`)).toEqual([])
})
