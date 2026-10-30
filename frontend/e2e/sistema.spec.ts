import { test, expect } from '@playwright/test'
import { API, CUENTAS, auth, codigoDelMail, json, medicoPorNombre, sesion, type Json } from './soporte'
import { request } from '@playwright/test'

// Recorridos que la interfaz todavía no expone (historia clínica y recetas están "en construcción") o que
// necesitan dos cuentas: van por la API, por el mismo gateway que usa el frontend.

test('registro de un paciente con verificación por mail y login', async () => {
  const email = `e2e.${Date.now()}@demo.tranqui`
  const anonimo = await request.newContext({ baseURL: API })
  await json(await auth(anonimo, '/api/auth/register', {
    email, password: 'Clave1234', rol: 'PACIENTE', nombre: 'Prueba', apellido: 'E2E', aceptaTerminos: true
  }), 'registro')
  expect((await auth(anonimo, '/api/auth/login', { email, password: 'Clave1234' })).status()).not.toBe(200)

  await json(await auth(anonimo, '/api/auth/verify-email', { email, codigo: await codigoDelMail(email) }), 'verificación')
  const yo = await json(await (await sesion(email, 'Clave1234')).get('/api/auth/me'), 'me')
  expect(yo.email).toBe(email)
  expect(yo.rol).toBe('PACIENTE')
})

test('verificación de matrícula: el admin aprueba a un profesional recién registrado', async () => {
  const email = `e2e.pro.${Date.now()}@demo.tranqui`
  const anonimo = await request.newContext({ baseURL: API })
  await json(await auth(anonimo, '/api/auth/register', {
    email, password: 'Clave1234', rol: 'PSIQUIATRA', nombre: 'Profesional', apellido: 'E2E', profession: 'psicologo',
    licenseType: 'MN', licenseNumber: '123456', aceptaTerminos: true
  }), 'registro profesional')
  await json(await auth(anonimo, '/api/auth/verify-email', { email, codigo: await codigoDelMail(email) }), 'verificación')

  const admin = await sesion(CUENTAS.admin)
  const buscar = async () => (await json(await admin.get('/api/admin/users'), 'usuarios') as Json[]).find((u) => u.email === email)
  const nuevo = await buscar()
  expect(nuevo?.verificadoAdmin).not.toBe(true)
  await json(await admin.post(`/api/admin/users/${nuevo.id}/verify`), 'verificar')
  await expect.poll(async () => (await buscar())?.verificadoAdmin, { timeout: 15000 }).toBe(true)
})

test('tickets: el paciente abre uno, el admin responde y lo resuelve', async () => {
  const asunto = `E2E ${Date.now()}`
  const paciente = await sesion(CUENTAS.pacienteConHistoria)
  const ticket = await json(await paciente.post('/api/tickets', { data: { asunto, mensaje: 'No me llega el link del turno.' } }), 'crear ticket')

  const admin = await sesion(CUENTAS.admin)
  expect((await json(await admin.get('/api/admin/tickets'), 'tickets admin') as Json[]).some((t) => t.id === ticket.id)).toBe(true)
  await json(await admin.post(`/api/tickets/${ticket.id}/mensajes`, { data: { contenido: 'Ya lo reenviamos.' } }), 'respuesta')
  await json(await admin.put(`/api/admin/tickets/${ticket.id}/estado`, { data: { estado: 'RESUELTO' } }), 'resolver')

  const visto = await json(await paciente.get(`/api/tickets/${ticket.id}`), 'ticket del paciente')
  expect(visto.estado).toBe('RESUELTO')
  expect(JSON.stringify(visto)).toContain('Ya lo reenviamos.')
})

test('suscripción y paywall: la vencida no entra a su panel y la activa sí', async () => {
  const vencida = await sesion(CUENTAS.vencida)
  const bloqueado = await vencida.get('/api/medicos/perfil')
  expect(bloqueado.status()).toBe(403)
  expect(await bloqueado.text()).toContain('SUBSCRIPTION_REQUIRED')
  // Lo que necesita para reactivar sigue abierto.
  expect((await vencida.get('/api/subscriptions/plans')).status()).toBe(200)
  expect((await vencida.get('/api/subscriptions/my-subscription')).status()).toBe(200)

  const activa = await sesion(CUENTAS.profesional)
  expect((await activa.get('/api/medicos/perfil')).status()).toBe(200)
  const sub = await json(await activa.get('/api/subscriptions/my-subscription'), 'mi suscripción')
  expect(JSON.stringify(sub)).toMatch(/ACTIVE|ACTIVA/)
})

test('historia clínica: la profesional anota un seguimiento y el paciente lo ve', async () => {
  const lucia = await sesion(CUENTAS.profesional)
  const atendidos = await json(await lucia.get('/api/pacientes/atendidos'), 'pacientes atendidos') as Json[]
  expect(atendidos.length).toBeGreaterThan(0)
  const paciente = atendidos.find((p) => p.email === CUENTAS.pacienteConHistoria) ?? atendidos[0]
  const notas = `E2E ${Date.now()}`
  await json(await lucia.post(`/api/pacientes/${paciente.id}/seguimientos`, {
    data: { fecha: new Date().toISOString().slice(0, 10), estadoAnimo: 'BIEN', sintomas: 'ninguno', notas }
  }), 'seguimiento')
  const lista = await json(await lucia.get(`/api/pacientes/${paciente.id}/seguimientos`), 'seguimientos')
  expect(JSON.stringify(lista)).toContain(notas)
})

test('perfil público: lo que edita la profesional aparece en el buscador', async () => {
  const lucia = await sesion(CUENTAS.profesional)
  const perfil = await json(await lucia.get('/api/medicos/perfil'), 'perfil')
  const descripcion = `Atención E2E ${Date.now()}`
  await json(await lucia.put('/api/medicos/perfil', { data: { ...perfil, descripcionPerfil: descripcion } }), 'guardar perfil')
  const anonimo = await request.newContext({ baseURL: API })
  await expect.poll(async () => (await medicoPorNombre(anonimo, /Luc[ií]a/))?.descripcionPerfil, { timeout: 60000 }).toBe(descripcion)
})
