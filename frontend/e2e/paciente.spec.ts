import { test, expect } from '@playwright/test'
import { CUENTAS, elegirDia, entrar, entrarConFormulario, json, mailsDe, sesion, sinTurnosActivos, turnoDe, type Json } from './soporte'

// Comparten el turno que reserva el primero, por eso van en serie.
test.describe.serial('Paciente: buscador, perfil, reserva, pago y cancelación', () => {
  let turnoId: number

  test.beforeAll(async () => {
    await sinTurnosActivos(await sesion(CUENTAS.paciente))
  })

  test('el buscador muestra a los profesionales activos y no a la de suscripción vencida', async ({ page }) => {
    await entrarConFormulario(page, CUENTAS.paciente)
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('article', { name: /Lucía Fernández/i })).toBeVisible({ timeout: 15000 })
    await expect(page.getByRole('article', { name: /Sofía Medina/i })).toHaveCount(0)
  })

  test('reserva y paga un turno con el simulador', async ({ page }) => {
    await entrar(page, CUENTAS.paciente)
    await page.getByRole('article', { name: /Lucía Fernández/i }).click()
    await expect(page).toHaveURL(/\/reserva\//)

    // A más de 48 h, para que la cancelación del último test tenga reembolso.
    await elegirDia(page, 3)
    await page.locator('.slots .slot').first().click()

    const nombre = page.getByPlaceholder('Ej: María Gómez')
    if ((await nombre.inputValue()) === '') await nombre.fill('Martina López')
    const email = page.getByPlaceholder('Ej: maria.gomez@gmail.com')
    if ((await email.inputValue()) === '') await email.fill(CUENTAS.paciente)
    const telefono = page.getByPlaceholder('Ej: 3515998822')
    if ((await telefono.inputValue()) === '') await telefono.fill('3515998822')
    await page.locator('#acceptedTerms').check()

    const pagar = page.getByRole('button', { name: /Confirmar y pagar/i })
    await expect(pagar).toBeEnabled({ timeout: 10000 })
    await pagar.click()
    await expect(page.getByText('Simulador de Pago de Turno')).toBeVisible({ timeout: 30000 })
    await page.getByRole('button', { name: /Simular Pago Exitoso/i }).click()
    await expect(page.getByText(/pago acreditado/i)).toBeVisible({ timeout: 30000 })

    await page.goto('/')
    await page.getByText('Mis Turnos', { exact: true }).first().click()
    await expect(page.getByText(/Lucía Fernández/).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('Confirmado').first()).toBeVisible()

    const turnos = await json(await (await sesion(CUENTAS.paciente)).get('/api/pacientes/me/turnos'), 'mis turnos')
    turnoId = (turnos as Json[]).find((t) => t.status === 'confirmed')?.id
    expect(turnoId).toBeTruthy()
  })

  test('la profesional ve el turno en su agenda', async () => {
    const lucia = await sesion(CUENTAS.profesional)
    const turnos = await json(await lucia.get('/api/medicos/turnos'), 'turnos de la profesional')
    expect((turnos as Json[]).some((t) => t.id === turnoId)).toBe(true)
  })

  test('cancela el turno con más de 48 h y le llega el mail del reembolso', async ({ page }) => {
    const antes = (await mailsDe(CUENTAS.paciente)).length
    await entrar(page, CUENTAS.paciente)
    await page.getByText('Mis Turnos', { exact: true }).first().click()
    await page.getByRole('button', { name: 'Cancelar', exact: true }).first().click()
    const confirmar = page.getByRole('button', { name: /confirmar|sí, cancelar/i })
    if (await confirmar.isVisible().catch(() => false)) await confirmar.click()
    await expect(page.getByText('Cancelado').first()).toBeVisible({ timeout: 15000 })

    const paciente = await sesion(CUENTAS.paciente)
    await expect.poll(async () => (await turnoDe(paciente, turnoId))?.status, { timeout: 30000 }).toBeUndefined()
    await expect.poll(async () => (await mailsDe(CUENTAS.paciente)).slice(0, (await mailsDe(CUENTAS.paciente)).length - antes)
      .some((m) => /reembols/i.test(`${m.Subject} ${m.Snippet}`)), { timeout: 60000 }).toBe(true)
  })
})

test('Mi Cuenta: cambia el teléfono y se ve al volver a entrar', async ({ page }) => {
  const telefono = `351${String(Date.now()).slice(-7)}`
  const paciente = await sesion(CUENTAS.pacienteConHistoria)
  const cuenta = await json(await paciente.get('/api/auth/mi-cuenta'), 'mi cuenta')
  await json(await paciente.put('/api/auth/mi-cuenta', { data: { ...cuenta, telefono } }), 'guardar mi cuenta')

  await entrar(page, CUENTAS.pacienteConHistoria)
  await page.goto('/mi-cuenta')
  await expect(page.locator(`input[value*="${telefono}"]`).first()).toBeVisible({ timeout: 15000 })
})
