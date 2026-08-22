import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CheckoutFlow from './CheckoutFlow'
import { api } from '../api/api'

const { showAlertMock } = vi.hoisted(() => ({ showAlertMock: vi.fn() }))

vi.mock('../context/AlertContext', () => ({
  useAlert: () => ({ showAlert: showAlertMock }),
}))

vi.mock('../api/api', () => ({
  api: {
    getTurnosDisponibles: vi.fn(),
    getFinanciadores: vi.fn(),
    reservarTurno: vi.fn(),
    abandonarReservaPendiente: vi.fn(),
    getMisTurnos: vi.fn(),
  },
}))

// Every hour of the day, so "Hoy" always keeps at least one slot regardless of what time the
// test actually runs at (StepSelect filters out past hours for today only).
const ALL_DAY_SLOTS = Array.from({ length: 24 }, (_, h) => `${String(h).padStart(2, '0')}:00:00`)

function baseProfessional(overrides: Record<string, any> = {}) {
  return {
    id: '1',
    name: 'Dra. Ana Pérez',
    degree: 'Médica',
    specialty: 'Psiquiatría',
    matricula: 'MP1234',
    price: 20000,
    nextSlot: '10:00',
    nextSlotDay: 'Hoy',
    ofreceOnline: true,
    ofrecePresencial: false,
    ...overrides,
  }
}

async function waitForCalendarLoaded() {
  await waitFor(() => expect(screen.queryByText('Buscando turnos disponibles...')).not.toBeInTheDocument())
}

async function selectFirstFreeDayAndSlot(user: ReturnType<typeof userEvent.setup>, container: HTMLElement) {
  await waitForCalendarLoaded()
  const freeDayBtn = await waitFor(() => {
    const btn = container.querySelector('button.cal-day.free') as HTMLButtonElement | null
    if (!btn) throw new Error('No free calendar day found yet')
    return btn
  })
  await user.click(freeDayBtn)
  const slotBtn = await waitFor(() => {
    const btn = container.querySelector('.slots .slot') as HTMLButtonElement | null
    if (!btn) throw new Error('No slot button found yet')
    return btn
  })
  await user.click(slotBtn)
}

async function fillPatientData(user: ReturnType<typeof userEvent.setup>, container: HTMLElement) {
  await user.type(screen.getByPlaceholderText('Ej: María Gómez'), 'Juan Paciente')
  await user.type(screen.getByPlaceholderText('Ej: maria.gomez@gmail.com'), 'juan@test.com')
  await user.type(screen.getByPlaceholderText('Ej: 3515998822'), '3511234567')
  const termsCheckbox = container.querySelector('#acceptedTerms') as HTMLInputElement
  await user.click(termsCheckbox)
}

function getPayButton() {
  return screen.getByRole('button', { name: /Confirmar y pagar|Procesando pago/ })
}

describe('CheckoutFlow', () => {
  let originalLocation: Location

  beforeEach(() => {
    vi.clearAllMocks()
    ;(api.getTurnosDisponibles as any).mockResolvedValue(ALL_DAY_SLOTS)
    ;(api.getFinanciadores as any).mockResolvedValue({
      financiadores: [{ idfinanciador: 1, nombreComercial: 'OSDE' }, { idfinanciador: 2, nombreComercial: 'Swiss Medical' }],
    })
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
    originalLocation = window.location
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', { value: originalLocation, writable: true })
  })

  describe('StepSelect — profile details and cached-user prefill', () => {
    afterEach(() => {
      localStorage.clear()
    })

    it('prefills name/email/phone from the cached tranqui_user in localStorage', async () => {
      localStorage.setItem(
        'tranqui_user',
        JSON.stringify({ nombre: 'Cached Name', email: 'cached@test.com', telefono: '+54 3511112222' })
      )
      const professional = baseProfessional({
        tariffs: [{ id: 'informe', label: 'Informe', price: 5000, enabled: true, requiereAgenda: false }],
      })
      const user = userEvent.setup()
      render(<CheckoutFlow professional={professional} onBack={vi.fn()} onComplete={vi.fn()} />)
      await waitForCalendarLoaded()
      await user.click(screen.getByText('Informe'))

      expect(screen.getByPlaceholderText('Ej: María Gómez')).toHaveValue('Cached Name')
      expect(screen.getByPlaceholderText('Ej: maria.gomez@gmail.com')).toHaveValue('cached@test.com')
      expect(screen.getByPlaceholderText('Ej: 3515998822')).toHaveValue('3511112222')
    })

    it('renders the full profile: bio, tags, pacientesAtiende, redes sociales, experiencias and publicaciones', async () => {
      const professional = baseProfessional({
        telefono: '3511234567',
        emailContacto: 'contacto@medico.com',
        descripcionPerfil: 'Bio profesional completa.',
        tags: ['Ansiedad', 'Depresión'],
        pacientesAtiende: ['Adultos', 'Adolescentes'],
        institucionFormacion: 'UNC',
        aniosExperiencia: 12,
        redesSociales: { instagram: 'https://instagram.com/x', linkedin: 'https://linkedin.com/in/x', sitioWeb: 'https://x.com' },
        experiencia: JSON.stringify([{ id: '1', nombreLugar: 'Hospital X', desde: '2015', hasta: '2020', descripcion: 'Guardia' }]),
        publicaciones: JSON.stringify([{ id: 'p1', titulo: 'Un paper', descripcion: 'Resumen', link: 'https://x.com/paper' }]),
      })
      render(<CheckoutFlow professional={professional} onBack={vi.fn()} onComplete={vi.fn()} />)
      await waitForCalendarLoaded()

      expect(screen.getByText('Bio profesional completa.')).toBeInTheDocument()
      expect(screen.getByText('Ansiedad')).toBeInTheDocument()
      expect(screen.getByText('Adultos')).toBeInTheDocument()
      expect(screen.getByText('Hospital X')).toBeInTheDocument()
      expect(screen.getByText('Un paper')).toBeInTheDocument()
      expect(screen.getByText(/Más de 12 años/)).toBeInTheDocument()
      expect(screen.getByLabelText('Instagram')).toBeInTheDocument()
      expect(screen.getByLabelText('LinkedIn')).toBeInTheDocument()
    })
  })

  describe('StepSelect — modalidad and obra social branching', () => {
    it('does not show a modalidad picker when the médico only offers one modalidad', async () => {
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await waitForCalendarLoaded()
      expect(screen.queryByText('Modalidad de la consulta')).not.toBeInTheDocument()
      void container
    })

    it('shows a modalidad picker when the médico offers both online and presencial (ofreceAmbasModalidades)', async () => {
      render(
        <CheckoutFlow
          professional={baseProfessional({ ofrecePresencial: true, ofreceOnline: true, domicilioAtencion: 'Av. Colón 123' })}
          onBack={vi.fn()}
          onComplete={vi.fn()}
        />
      )
      await waitForCalendarLoaded()
      expect(screen.getByText('Modalidad de la consulta')).toBeInTheDocument()
      expect(screen.getByText('Presencial')).toBeInTheDocument()
      expect(screen.getByText('Online')).toBeInTheDocument()
    })

    it('keeps the pay button disabled and lists what is missing until every requirement is met, then enables it', async () => {
      const user = userEvent.setup()
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)

      expect(getPayButton()).toBeDisabled()
      expect(screen.getByText(/Para poder pagar, falta:/)).toBeInTheDocument()

      await fillPatientData(user, container)

      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
    })

    it('OBRA_SOCIAL type (isObraSocialType, generic/not fixed): requires picking an obra social + afiliado', async () => {
      const user = userEvent.setup()
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)

      await user.click(screen.getByText('Obra Social OSDE'))

      // Now obra social + afiliado are required on top of the base fields already filled.
      expect(getPayButton()).toBeDisabled()
      expect(screen.getByText(/tu número de afiliado/)).toBeInTheDocument()

      await user.selectOptions(screen.getByLabelText('Obra Social *'), 'Swiss Medical')
      await user.type(screen.getByLabelText('Número de afiliado de Obra Social *'), '123456')

      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
    })

    it('custom tariff requiring obra social with a fixed obraSocial (isFixedObraSocialType): skips the financiador select', async () => {
      const user = userEvent.setup()
      const professional = baseProfessional({
        tariffs: [
          { id: 'plan-fijo', label: 'Plan Fijo IOMA', price: 15000, enabled: true, requiereObraSocial: true, obraSocial: 'IOMA' },
        ],
      })
      const { container } = render(<CheckoutFlow professional={professional} onBack={vi.fn()} onComplete={vi.fn()} />)
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)

      await user.click(screen.getByText('Plan Fijo IOMA'))

      // Fixed obra social is shown read-only — no <select> for it — só número de afiliado is asked.
      expect(screen.getByText('IOMA')).toBeInTheDocument()
      expect(screen.queryByLabelText('Obra Social *')).not.toBeInTheDocument()
      expect(getPayButton()).toBeDisabled()

      await user.type(screen.getByLabelText('Número de afiliado de Obra Social *'), '987654')

      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
    })

    it('custom tariff requiring obra social without a fixed one: loads financiadores and requires picking one', async () => {
      const user = userEvent.setup()
      const professional = baseProfessional({
        tariffs: [
          { id: 'plan-libre', label: 'Plan a elección', price: 18000, enabled: true, requiereObraSocial: true },
        ],
      })
      const { container } = render(<CheckoutFlow professional={professional} onBack={vi.fn()} onComplete={vi.fn()} />)
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)

      await user.click(screen.getByText('Plan a elección'))

      await waitFor(() => expect(api.getFinanciadores).toHaveBeenCalled())
      expect(getPayButton()).toBeDisabled()

      await user.selectOptions(screen.getByLabelText('Obra Social *'), '1')
      await user.type(screen.getByLabelText('Número de afiliado de Obra Social *'), '112233')

      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
    })

    it('SOBRETUNO requires a valid HH:MM custom time', async () => {
      const user = userEvent.setup()
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)

      await user.click(screen.getByText('Sobre turno'))

      const timeInput = screen.getByPlaceholderText('Ej: 19:30')
      await user.clear(timeInput)
      await user.type(timeInput, '99:99')
      expect(getPayButton()).toBeDisabled()
      expect(screen.getByText(/un horario válido/)).toBeInTheDocument()

      await user.clear(timeInput)
      await user.type(timeInput, '19:30')
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
    })

    it('a document-only custom tariff (isDocumentOnly) skips the day/horario calendar entirely', async () => {
      const user = userEvent.setup()
      const professional = baseProfessional({
        tariffs: [
          { id: 'informe-medico', label: 'Informe médico', price: 12000, enabled: true, requiereAgenda: false },
        ],
      })
      render(<CheckoutFlow professional={professional} onBack={vi.fn()} onComplete={vi.fn()} />)
      await waitForCalendarLoaded()

      await user.click(screen.getByText('Informe médico'))

      expect(screen.queryByText('Elegí día y horario')).not.toBeInTheDocument()
      expect(screen.getByText(/no hace falta elegir día ni hora/)).toBeInTheDocument()

      // The patient-data section renders immediately (no day/slot ever selected).
      expect(getPayButton()).toBeDisabled()
      const { container } = { container: document.body }
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
    })
  })

  describe('handlePay', () => {
    it('shows the mocked Mercado Pago gateway when checkoutUrl contains "mock-preference-id"', async () => {
      const user = userEvent.setup()
      ;(api.reservarTurno as any).mockResolvedValueOnce({
        turnoId: 55,
        checkoutUrl: 'https://api.mercadopago.com/mock-preference-id-abc123',
        precio: 20000,
        fecha: '2026-08-20',
        horaInicio: '10:00',
      })
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())

      await user.click(getPayButton())

      await waitFor(() => expect(api.reservarTurno).toHaveBeenCalledTimes(1))
      expect(await screen.findByText('Simulador de Pago de Turno')).toBeInTheDocument()
      expect(screen.getByText('SANDBOX / TEST')).toBeInTheDocument()
      expect(screen.getByText('#55')).toBeInTheDocument()
    })

    it('redirects the browser via window.location.href when checkoutUrl is a real Mercado Pago preference', async () => {
      const user = userEvent.setup()
      Object.defineProperty(window, 'location', {
        value: { ...originalLocation, href: '' },
        writable: true,
      })
      ;(api.reservarTurno as any).mockResolvedValueOnce({
        turnoId: 56,
        checkoutUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=real-pref-123',
      })
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())

      await user.click(getPayButton())

      await waitFor(() =>
        expect(window.location.href).toBe('https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=real-pref-123')
      )
      expect(screen.queryByText('Simulador de Pago de Turno')).not.toBeInTheDocument()
    })

    it('goes straight to the confirmed step when reservarTurno resolves without a checkoutUrl (e.g. free/zero-cost)', async () => {
      const user = userEvent.setup()
      ;(api.reservarTurno as any).mockResolvedValueOnce({ turnoId: 57, precio: 0 })
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())

      await user.click(getPayButton())

      expect(await screen.findByText('¡Turno confirmado!')).toBeInTheDocument()
    })

    it('confirms a presencial booking showing the office address and next-steps copy', async () => {
      const user = userEvent.setup()
      ;(api.reservarTurno as any).mockResolvedValueOnce({ turnoId: 60, precio: 20000 })
      const professional = baseProfessional({
        ofrecePresencial: true,
        ofreceOnline: false,
        domicilioAtencion: 'Av. Colón 1234',
        domicilioLat: -31.4,
        domicilioLng: -64.18,
      })
      const { container } = render(<CheckoutFlow professional={professional} onBack={vi.fn()} onComplete={vi.fn()} />)
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())

      await user.click(getPayButton())

      expect(await screen.findByText('¡Turno confirmado!')).toBeInTheDocument()
      expect(screen.getByText('Presencial (en consultorio)')).toBeInTheDocument()
      expect(screen.getByText('Dirección del consultorio')).toBeInTheDocument()
      expect(screen.getAllByText('Av. Colón 1234').length).toBeGreaterThan(0)
      expect(screen.getByText('Cómo llegar')).toBeInTheDocument()
    })

    it('shows the booking error message and stays on the select step when reservarTurno rejects', async () => {
      const user = userEvent.setup()
      ;(api.reservarTurno as any).mockRejectedValueOnce(new Error('El horario seleccionado ya no está disponible'))
      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())

      await user.click(getPayButton())

      expect(await screen.findByText('El horario seleccionado ya no está disponible')).toBeInTheDocument()
      expect(screen.queryByText('¡Turno confirmado!')).not.toBeInTheDocument()
    })

    it('simulating an approved webhook in the mock gateway moves to the confirmed step', async () => {
      const user = userEvent.setup()
      ;(api.reservarTurno as any).mockResolvedValueOnce({
        turnoId: 58,
        checkoutUrl: 'https://api.mercadopago.com/mock-preference-id-xyz',
        precio: 20000,
        fecha: '2026-08-20',
        horaInicio: '10:00',
      })
      ;(api.getMisTurnos as any).mockResolvedValueOnce([{ id: 58, meetLink: 'https://meet.google.com/abc-defg-hij' }])
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('{}', { status: 200 }))

      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
      await user.click(getPayButton())
      await screen.findByText('Simulador de Pago de Turno')

      await user.click(screen.getByRole('button', { name: 'Simular Pago Exitoso (Aprobar)' }))

      expect(await screen.findByText('¡Turno confirmado!')).toBeInTheDocument()
      expect(showAlertMock).toHaveBeenCalledWith('Pago acreditado. Webhook simulado con éxito.', 'success')
      fetchSpy.mockRestore()
    })

    it('simulating a rejected webhook releases the pending reservation and shows an error', async () => {
      const user = userEvent.setup()
      ;(api.reservarTurno as any).mockResolvedValueOnce({
        turnoId: 59,
        checkoutUrl: 'https://api.mercadopago.com/mock-preference-id-rej',
        precio: 20000,
      })
      ;(api.abandonarReservaPendiente as any).mockResolvedValueOnce({})

      const { container } = render(
        <CheckoutFlow professional={baseProfessional()} onBack={vi.fn()} onComplete={vi.fn()} />
      )
      await selectFirstFreeDayAndSlot(user, container)
      await fillPatientData(user, container)
      await waitFor(() => expect(getPayButton()).not.toBeDisabled())
      await user.click(getPayButton())
      await screen.findByText('Simulador de Pago de Turno')

      await user.click(screen.getByRole('button', { name: 'Simular Pago Rechazado (Cancelar)' }))

      await waitFor(() => expect(api.abandonarReservaPendiente).toHaveBeenCalledWith(59))
      expect(screen.queryByText('Simulador de Pago de Turno')).not.toBeInTheDocument()
      expect(await screen.findByText('Pago rechazado por el usuario en la simulación.')).toBeInTheDocument()
    })
  })
})
