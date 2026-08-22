import { test, expect, type Page } from '@playwright/test'

const PACIENTE = { email: 'paciente.completo@gmail.com', password: 'admin123' }
const MEDICO = { email: 'medico.verificado@gmail.com', password: 'admin123' }

const SITE_ACCESS_PASSWORD = 'camilabulchi'

async function passSiteAccessGateIfPresent(page: Page) {
  const gateInput = page.getByPlaceholder('Contraseña de acceso')
  const loginEmail = page.locator('#login-email')
  // getSiteAccessStatus() resolves async on mount — the gate (or the real page) may not have
  // rendered yet right after goto(), so wait for whichever of the two shows up first instead of
  // sampling isVisible() once.
  await Promise.race([
    gateInput.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {}),
    loginEmail.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {})
  ])
  if (await gateInput.isVisible().catch(() => false)) {
    await gateInput.fill(SITE_ACCESS_PASSWORD)
    await page.getByRole('button', { name: 'Ingresar' }).click()
    await expect(gateInput).not.toBeVisible({ timeout: 10000 })
  }
}

async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await passSiteAccessGateIfPresent(page)
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(password)
  await page.locator('form button[type="submit"]').first().click()

  // Cuentas semilla nuevas piden aceptar términos la primera vez que entran.
  const acceptTerms = page.getByRole('button', { name: 'Acepto y continúo' })
  const termsShowedUp = await acceptTerms.waitFor({ state: 'visible', timeout: 20000 }).then(() => true).catch(() => false)
  if (termsShowedUp) {
    await acceptTerms.click()
    await acceptTerms.waitFor({ state: 'hidden', timeout: 15000 })
  }
}

// These three tests share real backend state on purpose (the turno booked by the patient in
// test 1 is what makes the patient show up in the médico's "pacientes atendidos" list in test 2,
// and test 3 cleans it up) — hence describe.serial instead of independent tests.
test.describe.serial('Flujo paciente + profesional', () => {
  test('paciente reserva y paga un turno con el Dr. Carlos Perez', async ({ page }) => {
    await login(page, PACIENTE.email, PACIENTE.password)
    await expect(page).toHaveURL('/')

    await page.getByRole('article', { name: /Carlos Perez/i }).click()
    await expect(page).toHaveURL(/\/reserva\//)

    // El médico ofrece ambas modalidades; queda seleccionada Presencial por defecto (el
    // profesional la tiene configurada como modalidad primaria) — se deja así, cubre también el
    // camino de dirección/mapa del consultorio en StepConfirmed.

    // Primer día habilitado del calendario, luego primer horario libre.
    await page.locator('.cal-day.free').first().click()
    await page.locator('.slots .slot').first().click()

    // Los datos personales suelen venir precargados (usuario ya logueado) — completamos lo que
    // falte para no depender de qué tan completo esté el perfil semilla.
    const nameInput = page.getByPlaceholder('Ej: María Gómez')
    if ((await nameInput.inputValue()) === '') await nameInput.fill('Juan Paciente')
    const emailInput = page.getByPlaceholder('Ej: maria.gomez@gmail.com')
    if ((await emailInput.inputValue()) === '') await emailInput.fill(PACIENTE.email)
    const phoneInput = page.getByPlaceholder('Ej: 3515998822')
    if ((await phoneInput.inputValue()) === '') await phoneInput.fill('3515998822')

    await page.locator('#acceptedTerms').check()

    const payButton = page.getByRole('button', { name: /Confirmar y pagar/i })
    await expect(payButton).toBeEnabled({ timeout: 10000 })
    await payButton.click()

    // Gateway mock de Mercado Pago (PAYMENT_SIMULATION_ENABLED=true / mercadopago.enabled=false).
    await expect(page.getByText('Simulador de Pago de Turno')).toBeVisible({ timeout: 15000 })
    await page.getByRole('button', { name: /Simular Pago Exitoso/i }).click()

    await expect(page.getByText(/pago acreditado/i)).toBeVisible({ timeout: 15000 })

    // "Mis Turnos" debe mostrar el turno recién confirmado (StepConfirmed queda en /reserva/:id,
    // hay que volver a la landing primero).
    await page.goto('/')
    await page.getByText('Mis Turnos', { exact: true }).first().click()
    await expect(page.getByText('Carlos Perez')).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('Confirmado').first()).toBeVisible()
  })

  test('profesional edita agenda y emite una receta', async ({ page }) => {
    await login(page, MEDICO.email, MEDICO.password)
    await expect(page).toHaveURL(/\/panel/)

    // Agenda: agregar un horario y guardar.
    await page.getByRole('button', { name: 'Agenda', exact: true }).click()
    const addSlotButton = page.getByRole('button', { name: /^Agregar horario/ }).first()
    await addSlotButton.click()
    await page.locator('#btn-save-availability').click()
    await expect(page.locator('#btn-save-availability')).toHaveText(/Guardar cambios/, { timeout: 15000 })

    // Recetas: buscar al paciente que acaba de reservar, agregar medicación y emitir.
    await page.getByRole('button', { name: 'Recetas', exact: true }).click()
    await page.locator('#rx-patient-search').fill('Juan')
    await expect(page.getByText(/no se encontraron pacientes/i)).not.toBeVisible({ timeout: 8000 }).catch(() => {})
    await page.getByText('Juan Paciente', { exact: false }).first().click()

    await page.locator('#rx-diagnosis').fill('F41.1 - Trastorno de ansiedad generalizada')
    // Buscamos contra el catálogo real de QBI2 (QBI2_RECIPE_ENABLED=true en este entorno) sin el
    // texto de dosis — un término que no matchea ningún resultado real cae a "agregar
    // personalizado" (sin regNo), que QBI2 rechaza con QBI105 "CODIGO INFORMADO INEXISTENTE". Con
    // resultados reales visibles, Enter agrega automáticamente el primero (handleMedKeyDown).
    await page.locator('#rx-med-search').fill('sertralina')
    await expect(page.getByText('sertralina', { exact: false }).first()).toBeVisible({ timeout: 10000 })
    await page.locator('#rx-med-search').press('Enter')

    const sendButton = page.locator('#btn-send-prescription')
    await expect(sendButton).toBeEnabled({ timeout: 10000 })
    await sendButton.click()

    await expect(page.locator('#btn-new-prescription')).toBeVisible({ timeout: 20000 })

    await page.getByRole('button', { name: /Historial Emitidas/ }).click()
    await expect(page.getByText('Juan Paciente', { exact: false }).first()).toBeVisible({ timeout: 10000 })
  })

  test('paciente cancela el turno (limpieza de datos para permitir re-correr la suite)', async ({ page }) => {
    await login(page, PACIENTE.email, PACIENTE.password)
    await expect(page).toHaveURL('/')

    await page.getByText('Mis Turnos', { exact: true }).first().click()
    await page.getByRole('button', { name: 'Cancelar', exact: true }).first().click()
    // Confirmación de cancelación, si el modal la pide.
    const confirmBtn = page.getByRole('button', { name: /confirmar|sí, cancelar/i })
    if (await confirmBtn.isVisible().catch(() => false)) {
      await confirmBtn.click()
    }
    await expect(page.getByText('Cancelado').first()).toBeVisible({ timeout: 10000 })
  })
})
