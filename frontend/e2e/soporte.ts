import { expect, request, type APIRequestContext, type Page } from '@playwright/test'

// Fase 10, T3: la misma suite corre contra la migración (tranqui-local, por el gateway) y, en la Fase 11, contra main.
export const API = process.env.E2E_API_URL ?? 'http://localhost:8080'
export const MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025'
export const CLAVE = 'admin123'

// Las respuestas de la API van sin tipar: cada test lee los pocos campos que verifica.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Json = any

export const CUENTAS = {
  paciente: 'martina.lopez@demo.tranqui',
  pacienteConHistoria: 'valentina.gomez@demo.tranqui',
  profesional: 'lucia.fernandez@demo.tranqui',
  vencida: 'sofia.medina@demo.tranqui',
  admin: 'admin@tranqui.com'
}

/**
 * Un pedido a /api/auth/* respetando el límite del gateway (ráfaga de 10 y 5 por minuto por IP): ante un 429 espera
 * a que se reponga un lugar, como haría una persona, en vez de aflojar el límite.
 */
export async function auth(ctx: APIRequestContext, ruta: string, data: unknown) {
  for (let i = 0; ; i++) {
    const r = await ctx.post(ruta, { data })
    if (r.status() !== 429 || i === 10) return r
    await new Promise((ok) => setTimeout(ok, 13000))
  }
}

const sesiones = new Map<string, APIRequestContext>()

/** Una sesión por la API (cookie SESSION-TOKEN), una sola vez por cuenta en toda la corrida. */
export async function sesion(email: string, password = CLAVE): Promise<APIRequestContext> {
  const previa = sesiones.get(email)
  if (previa) return previa
  const ctx = await request.newContext({ baseURL: API })
  const r = await auth(ctx, '/api/auth/login', { email, password })
  expect(r.status(), `login de ${email}: ${await r.text()}`).toBe(200)
  sesiones.set(email, ctx)
  return ctx
}

export async function json(r: Awaited<ReturnType<APIRequestContext['get']>>, que: string) {
  const texto = await r.text()
  expect(r.status(), `${que}: ${texto.slice(0, 300)}`).toBeLessThan(400)
  try {
    return JSON.parse(texto)
  } catch {
    return texto
  }
}

export async function pasarPortonSiHace(page: Page) {
  const porton = page.getByPlaceholder('Contraseña de acceso')
  const email = page.locator('#login-email')
  await Promise.race([
    porton.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {}),
    email.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {})
  ])
  if (await porton.isVisible().catch(() => false)) {
    await porton.fill(process.env.E2E_SITE_PASSWORD ?? 'camilabulchi')
    await page.getByRole('button', { name: 'Ingresar' }).click()
    await expect(porton).not.toBeVisible({ timeout: 10000 })
  }
}

/** Entra a la interfaz con la cookie de la sesión de la API (las cookies de localhost no dependen del puerto). */
export async function entrar(page: Page, email: string) {
  const { cookies } = await (await sesion(email)).storageState()
  await page.context().addCookies(cookies)
  await page.goto('/')
  const porton = page.getByPlaceholder('Contraseña de acceso')
  if (await porton.waitFor({ state: 'visible', timeout: 3000 }).then(() => true).catch(() => false)) await pasarPortonSiHace(page)
}

/** Entra por el formulario de login, para probar el formulario mismo. */
export async function entrarConFormulario(page: Page, email: string, password = CLAVE) {
  await page.goto('/login')
  await pasarPortonSiHace(page)
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(password)
  await page.locator('form button[type="submit"]').first().click()
  const terminos = page.getByRole('button', { name: 'Acepto y continúo' })
  if (await terminos.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false)) {
    await terminos.click()
  }
}

/** El código de 6 dígitos del último mail que recibió esa dirección. */
export async function codigoDelMail(email: string): Promise<string> {
  const ctx = await request.newContext({ baseURL: MAILPIT })
  try {
    for (let i = 0; i < 30; i++) {
      const lista = await (await ctx.get(`/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`)).json()
      const ultimo = lista.messages?.[0]
      if (ultimo) {
        const mail = await (await ctx.get(`/api/v1/message/${ultimo.ID}`)).json()
        const codigo = /\b(\d{6})\b/.exec(`${mail.Subject} ${mail.Text}`)?.[1]
        if (codigo) return codigo
      }
      await new Promise((r) => setTimeout(r, 1000))
    }
    throw new Error(`No llegó el mail con el código a ${email}`)
  } finally {
    await ctx.dispose()
  }
}

/** El primer horario libre del profesional desde dentro de {@code dias} días. */
export async function primerHorario(api: APIRequestContext, medicoId: number, modalidad = 'ONLINE', dias = 2) {
  for (let d = dias; d < dias + 21; d++) {
    const f = new Date(Date.now() + d * 86400000).toISOString().slice(0, 10)
    const h = await json(await api.get(`/api/medicos/${medicoId}/turnos-disponibles?fecha=${f}&modalidad=${modalidad}`), 'horarios')
    const lista = (Array.isArray(h) ? h : h.horarios ?? []).map((x: Json) => (typeof x === 'string' ? x : x.hora ?? x.horaInicio))
    if (lista.length) return { fecha: f, hora: String(lista[0]).slice(0, 5) }
  }
  throw new Error(`Sin horarios libres para ${medicoId}`)
}

export async function medicoPorNombre(api: APIRequestContext, nombre: RegExp) {
  const medicos = await json(await api.get('/api/medicos'), 'buscador')
  return medicos.find((m: Json) => nombre.test(`${m.nombre} ${m.apellido ?? ''}`))
}

/** Reserva y paga con el webhook simulado de Mercado Pago, como hace el simulador del checkout. */
export async function reservarYPagar(paciente: APIRequestContext, email: string, medicoId: number) {
  const h = await primerHorario(paciente, medicoId)
  const r = await json(await paciente.post('/api/turnos/reservar', {
    data: { medicoId, fecha: h.fecha, hora: `${h.hora}:00`, tipo: 'PARTICULAR', modalidad: 'ONLINE',
      nombrePaciente: 'E2E', emailPaciente: email, telefonoPaciente: '1122334455' }
  }), 'reserva')
  const turnoId = r.turnoId
  await expect.poll(async () => (await (await paciente.get(`/api/payments/checkout/${turnoId}`)).json()).estado,
    { timeout: 30000 }).not.toBe('PENDIENTE')
  await json(await paciente.post('/api/payments/webhook', {
    data: { external_reference: String(turnoId), transaction_id: `e2e-${Date.now()}` }, headers: { 'x-signature': 'test-signature' }
  }), 'webhook')
  await expect.poll(async () => turnoDe(paciente, turnoId).then((t) => t?.status), { timeout: 30000 }).toBe('confirmed')
  return { turnoId, ...h }
}

export async function turnoDe(paciente: APIRequestContext, turnoId: number) {
  const turnos = await json(await paciente.get('/api/pacientes/me/turnos'), 'mis turnos')
  return (turnos as Json[]).find((t) => t.id === turnoId)
}

/** Deja al paciente sin turnos activos, porque la app no deja reservar con uno activo (los cancelados no se listan). */
export async function sinTurnosActivos(paciente: APIRequestContext) {
  const turnos = await json(await paciente.get('/api/pacientes/me/turnos'), 'mis turnos')
  for (const t of turnos as Json[]) {
    if (t.status === 'confirmed' || t.status === 'pending') {
      await json(await paciente.post(`/api/turnos/${t.id}/cancelar`), 'cancelar turno previo')
    }
  }
}

/** Los mails que recibió esa dirección, del más nuevo al más viejo. */
export async function mailsDe(email: string): Promise<Json[]> {
  const ctx = await request.newContext({ baseURL: MAILPIT })
  try {
    return (await (await ctx.get(`/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}&limit=200`)).json()).messages ?? []
  } finally {
    await ctx.dispose()
  }
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

/** En el calendario de la reserva, el primer día libre a {@code minDias} o más de hoy (puede estar en el mes siguiente). */
export async function elegirDia(page: Page, minDias: number) {
  const desde = new Date()
  desde.setHours(0, 0, 0, 0)
  desde.setDate(desde.getDate() + minDias)
  for (let vuelta = 0; vuelta < 2; vuelta++) {
    await page.locator('.cal-day.free').first().waitFor({ timeout: 15000 }).catch(() => {})
    const [mes, , anio] = ((await page.locator('.cal-head .month').textContent()) ?? '').toLowerCase().split(' ')
    for (const dia of await page.locator('.cal-day.free').all()) {
      if (new Date(Number(anio), MESES.indexOf(mes), parseInt((await dia.textContent()) ?? '', 10)) >= desde) {
        await dia.click()
        return
      }
    }
    await page.locator('.cal-head .cal-nav').last().click()
  }
  throw new Error(`Sin días libres a ${minDias} o más días`)
}
