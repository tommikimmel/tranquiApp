import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PrescriptionView from './PrescriptionView'
import { AlertProvider } from '../context/AlertContext'
import { api } from '../api/api'

vi.mock('../api/api', () => ({
  api: {
    getPacientesAtendidos: vi.fn(),
    getMisRecetas: vi.fn(),
    buscarMedicamentos: vi.fn(),
    getFinanciadores: vi.fn(),
  },
}))

const mockedApi = vi.mocked(api)

const MEDICO_INFO = { nombre: 'Ana', apellido: 'García', matricula: '12345' }

// A fully-complete patient (no missing QBI2 fields) used across the matching-logic tests so
// the "selected patient" panel + banners render deterministically.
const JUAN = {
  id: 1,
  name: 'Juan Pérez',
  nombre: 'Juan',
  apellido: 'Pérez',
  email: 'juan@example.com',
  dni: '30111222',
  telefono: '+54 9 11 5555-0001',
  direccion: 'Calle Falsa 123',
  fechaNacimiento: '1990-01-01',
  obraSocial: 'Particular',
}
const LUCIA = {
  id: 2,
  name: 'Lucía Fernández',
  nombre: 'Lucía',
  apellido: 'Fernández',
  email: 'lucia@example.com',
  dni: '30333444',
  telefono: '+54 9 11 5555-0002',
  direccion: 'Calle Falsa 456',
  fechaNacimiento: '1985-05-05',
  obraSocial: 'Particular',
}
const PATIENTS_LIST = [JUAN, LUCIA]

// The patient's display name is rendered twice once selected (once in the "selected patient"
// header panel, once again in the "Confirmar y enviar" summary card) — scope name assertions
// to the header panel so an exact-text query doesn't ambiguously match both.
async function selectedPatientPanel() {
  const cambiar = await screen.findByText('Cambiar')
  return within(cambiar.closest('.rx-selected-patient') as HTMLElement)
}

function renderRx(opts?: { initialState?: any; onSend?: any }) {
  const onSend = opts?.onSend ?? vi.fn().mockResolvedValue(undefined)
  const entry = opts?.initialState ? { pathname: '/recetas', state: opts.initialState } : '/recetas'
  const utils = render(
    <MemoryRouter initialEntries={[entry]}>
      <AlertProvider>
        <PrescriptionView onSend={onSend} medicoInfo={MEDICO_INFO} />
      </AlertProvider>
    </MemoryRouter>
  )
  return { ...utils, onSend }
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedApi.getPacientesAtendidos.mockResolvedValue(PATIENTS_LIST)
  mockedApi.getMisRecetas.mockResolvedValue([])
  mockedApi.buscarMedicamentos.mockResolvedValue({ medicamentos: [] })
  mockedApi.getFinanciadores.mockResolvedValue([])
})

describe('PrescriptionView — patient auto-selection from location.state', () => {
  it('shows the patient search box when there is no location.state at all', async () => {
    renderRx()
    await waitFor(() => expect(mockedApi.getPacientesAtendidos).toHaveBeenCalled())
    expect(screen.getByPlaceholderText(/Ingresá al menos 3 letras/)).toBeInTheDocument()
  })

  it('matches location.state.patient by id and merges statePatient fields over the found record', async () => {
    renderRx({ initialState: { patient: { id: 1, name: 'Juan (apodo)' } } })

    // Merged: found (full JUAN record) spread first, then statePatient overrides `name`.
    const panel = await selectedPatientPanel()
    expect(panel.getByText('Juan (apodo)')).toBeInTheDocument()
    expect(panel.getByText(/juan@example.com/)).toBeInTheDocument()
  })

  it('matches location.state.patient by email (case-insensitive)', async () => {
    renderRx({ initialState: { patient: { email: 'LUCIA@EXAMPLE.COM' } } })
    const panel = await selectedPatientPanel()
    expect(panel.getByText('Lucía Fernández')).toBeInTheDocument()
  })

  it('matches location.state.patient by dni', async () => {
    renderRx({ initialState: { patient: { dni: '30333444' } } })
    const panel = await selectedPatientPanel()
    expect(panel.getByText('Lucía Fernández')).toBeInTheDocument()
  })

  it('matches location.state.patient by nombre+apellido combo', async () => {
    renderRx({ initialState: { patient: { nombre: 'juan', apellido: 'pérez' } } })
    const panel = await selectedPatientPanel()
    expect(panel.getByText('Juan Pérez')).toBeInTheDocument()
  })

  it('uses the raw statePatient (unmerged) when no match is found in the fetched list, and flags missing fields', async () => {
    renderRx({ initialState: { patient: { id: 999, name: 'Paciente Nuevo' } } })

    const panel = await selectedPatientPanel()
    expect(panel.getByText('Paciente Nuevo')).toBeInTheDocument()
    expect(screen.getByText(/no tiene todos los datos necesarios/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Completar datos' })).toBeInTheDocument()
  })

  it('matches by location.state.patientId', async () => {
    renderRx({ initialState: { patientId: 2 } })
    const panel = await selectedPatientPanel()
    expect(panel.getByText('Lucía Fernández')).toBeInTheDocument()
  })

  it('matches by location.state.patientName (case-insensitive)', async () => {
    renderRx({ initialState: { patientName: 'juan pérez' } })
    const panel = await selectedPatientPanel()
    expect(panel.getByText('Juan Pérez')).toBeInTheDocument()
  })

  it('falls back to building a patient from location.state.appt when nothing else matches', async () => {
    renderRx({
      initialState: {
        appt: {
          pacienteId: 55,
          patientName: 'Paciente Sin Historial',
          patientInfo: {
            nombre: 'Paciente',
            apellido: 'Sin Historial',
            email: 'nuevo@example.com',
            telefono: '+54 9 11 0000-0000',
            dni: '40555666',
            fechaNacimiento: '1995-03-03',
          },
        },
      },
    })

    const panel = await selectedPatientPanel()
    expect(panel.getByText('Paciente Sin Historial')).toBeInTheDocument()
    expect(panel.getByText(/40555666/)).toBeInTheDocument()
  })

  it('shows the "fuera de turno" banner only when fromPendingDocument is set and the patient has no missing fields', async () => {
    renderRx({ initialState: { patient: { id: 1 }, fromPendingDocument: true } })
    expect(await screen.findByText(/Receta fuera de turno seleccionada automáticamente/)).toBeInTheDocument()
  })

  it('does not show the "fuera de turno" banner when the resolved patient still has missing fields', async () => {
    renderRx({ initialState: { patient: { id: 999, name: 'Incompleto' }, fromPendingDocument: true } })
    await selectedPatientPanel()
    expect(screen.queryByText(/Receta fuera de turno seleccionada automáticamente/)).not.toBeInTheDocument()
  })

  it('falls back to the built-in MOCK_PATIENTS list when the backend returns an empty array', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValueOnce([])
    const user = userEvent.setup()
    renderRx()

    await waitFor(() => expect(mockedApi.getPacientesAtendidos).toHaveBeenCalled())
    await user.type(screen.getByPlaceholderText(/Ingresá al menos 3 letras/), 'Mateo')
    expect(await screen.findByText('Mateo Benítez')).toBeInTheDocument()
  })

  it('falls back to MOCK_PATIENTS when the backend request rejects', async () => {
    mockedApi.getPacientesAtendidos.mockRejectedValueOnce(new Error('network error'))
    const user = userEvent.setup()
    renderRx()

    await user.type(screen.getByPlaceholderText(/Ingresá al menos 3 letras/), 'Rodríguez')
    expect(await screen.findByText('Matías Rodríguez')).toBeInTheDocument()
  })
})

describe('PrescriptionView — patient search & selection', () => {
  it('searches only once 3+ characters are typed and lets the user pick a result', async () => {
    const user = userEvent.setup()
    renderRx()
    await waitFor(() => expect(mockedApi.getPacientesAtendidos).toHaveBeenCalled())

    const input = screen.getByPlaceholderText(/Ingresá al menos 3 letras/)
    await user.type(input, 'lu')
    expect(screen.queryByText('Lucía Fernández')).not.toBeInTheDocument()

    await user.type(input, 'c')
    expect(await screen.findByText('Lucía Fernández')).toBeInTheDocument()

    await user.click(screen.getByText('Lucía Fernández'))
    expect(screen.getByText('Cambiar')).toBeInTheDocument()
  })

  it('shows a "no results" message when the search does not match any patient', async () => {
    const user = userEvent.setup()
    renderRx()
    await waitFor(() => expect(mockedApi.getPacientesAtendidos).toHaveBeenCalled())

    await user.type(screen.getByPlaceholderText(/Ingresá al menos 3 letras/), 'zzzz')
    expect(await screen.findByText(/No se encontraron pacientes que coincidan con "zzzz"/)).toBeInTheDocument()
  })

  it('lets the user clear the selected patient and go back to searching', async () => {
    const user = userEvent.setup()
    renderRx({ initialState: { patient: { id: 1 } } })
    await screen.findByText('Cambiar')

    await user.click(screen.getByText('Cambiar'))
    expect(screen.getByPlaceholderText(/Ingresá al menos 3 letras/)).toBeInTheDocument()
  })
})

describe('PrescriptionView — medication search & addition', () => {
  it('does not call the QBI2 catalog search for a query shorter than 2 characters', async () => {
    const user = userEvent.setup()
    renderRx()
    await user.type(screen.getByPlaceholderText(/Escitalopram 10mg/), 'e')
    expect(mockedApi.buscarMedicamentos).not.toHaveBeenCalled()
  })

  it('debounces and calls the QBI2 catalog search once 2+ characters are typed', async () => {
    mockedApi.buscarMedicamentos.mockResolvedValue({
      medicamentos: [{ nombreProducto: 'Escitalopram 10mg', nombreDroga: 'Escitalopram', presentacion: 'x30', regNo: 'REG1' }],
    })
    const user = userEvent.setup()
    renderRx()

    await user.type(screen.getByPlaceholderText(/Escitalopram 10mg/), 'esc')
    await waitFor(() => expect(mockedApi.buscarMedicamentos).toHaveBeenCalledWith('esc', 1), { timeout: 1000 })
    expect(await screen.findByText('+ Escitalopram 10mg')).toBeInTheDocument()
  })

  it('adds a medication from a catalog search result on click, clearing the search input', async () => {
    mockedApi.buscarMedicamentos.mockResolvedValue({
      medicamentos: [{ nombreProducto: 'Sertralina 50mg', nombreDroga: 'Sertralina', presentacion: 'x30', regNo: 'REG2' }],
    })
    const user = userEvent.setup()
    renderRx()

    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, 'sert')
    await screen.findByText('+ Sertralina 50mg')
    await user.click(screen.getByText('+ Sertralina 50mg'))

    expect(screen.getByText('1. Sertralina 50mg')).toBeInTheDocument()
    expect((input as HTMLInputElement).value).toBe('')
  })

  it('pressing Enter with catalog results present adds the first result', async () => {
    mockedApi.buscarMedicamentos.mockResolvedValue({
      medicamentos: [
        { nombreProducto: 'Clonazepam 0.5mg', nombreDroga: 'Clonazepam', presentacion: 'x30', regNo: 'REG3' },
        { nombreProducto: 'Otro', nombreDroga: 'Otro', presentacion: 'x30', regNo: 'REG4' },
      ],
    })
    const user = userEvent.setup()
    renderRx()

    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, 'clona')
    await waitFor(() => expect(mockedApi.buscarMedicamentos).toHaveBeenCalled())
    await user.keyboard('{Enter}')

    expect(screen.getByText('1. Clonazepam 0.5mg')).toBeInTheDocument()
  })

  it('adds a free-text medication (no regNo) when nothing matches the catalog, flagging it as such', async () => {
    mockedApi.buscarMedicamentos.mockResolvedValue({ medicamentos: [] })
    const user = userEvent.setup()
    renderRx()

    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, 'Medicamento Casero')
    await user.click(screen.getByRole('button', { name: /Añadir/ }))

    expect(screen.getByText('1. Medicamento Casero')).toBeInTheDocument()
    expect(screen.getByText('Sin registro QBI2')).toBeInTheDocument()
  })

  it('recovers gracefully (empty results) when the catalog search rejects', async () => {
    mockedApi.buscarMedicamentos.mockRejectedValue(new Error('qbi2 down'))
    const user = userEvent.setup()
    renderRx()

    await user.type(screen.getByPlaceholderText(/Escitalopram 10mg/), 'algo')
    await waitFor(() => expect(mockedApi.buscarMedicamentos).toHaveBeenCalled())
    expect(await screen.findByText(/No se encontró en el catálogo de QBI2/)).toBeInTheDocument()
  })

  it('pressing Enter with no catalog results and free text present adds it as a custom medication', async () => {
    mockedApi.buscarMedicamentos.mockResolvedValue({ medicamentos: [] })
    const user = userEvent.setup()
    renderRx()

    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, 'Medicamento X')
    await waitFor(() => expect(mockedApi.buscarMedicamentos).toHaveBeenCalled())
    await user.keyboard('{Enter}')

    expect(screen.getByText('1. Medicamento X')).toBeInTheDocument()
  })

  it('pressing Enter on an empty medication search field does nothing', async () => {
    const user = userEvent.setup()
    renderRx()

    screen.getByPlaceholderText(/Escitalopram 10mg/).focus()
    await user.keyboard('{Enter}')

    expect(screen.getByText(/No hay medicamentos añadidos aún/)).toBeInTheDocument()
  })

  it('clicking the "no matches" dropdown row also adds the custom medication', async () => {
    mockedApi.buscarMedicamentos.mockResolvedValue({ medicamentos: [] })
    const user = userEvent.setup()
    renderRx()

    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, 'Producto Raro')
    await screen.findByText(/No se encontró en el catálogo de QBI2/)
    await user.click(screen.getByText(/No se encontró en el catálogo de QBI2/))

    expect(screen.getByText('1. Producto Raro')).toBeInTheDocument()
  })

  it('lets the médico fill in dosage, frequency/duration (incl. quick chips), laboratorio and noSustituible', async () => {
    const user = userEvent.setup()
    renderRx()

    const medInput = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(medInput, 'Quetiapina 25mg')
    await user.click(screen.getByRole('button', { name: /Añadir/ }))

    await user.type(screen.getByPlaceholderText('Ej: 1 comp'), '1 comprimido')
    await user.type(screen.getByPlaceholderText('Ej: cada 24hs'), 'cada 8hs')
    await user.click(screen.getByRole('button', { name: 'Cada 12hs' }))
    await user.type(screen.getByPlaceholderText('Ej: 30 días'), '10 días')
    await user.click(screen.getByRole('button', { name: '30 días' }))
    await user.selectOptions(screen.getByRole('combobox'), 'Bagó')
    await user.click(screen.getByRole('checkbox', { name: /No sustituible/ }))

    expect(screen.getByPlaceholderText('Ej: 1 comp')).toHaveValue('1 comprimido')
    expect(screen.getByPlaceholderText('Ej: cada 24hs')).toHaveValue('Cada 12hs')
    expect(screen.getByPlaceholderText('Ej: 30 días')).toHaveValue('30 días')
    expect(screen.getByRole('checkbox', { name: /No sustituible/ })).toBeChecked()
    expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('Bagó')
  })

  it('removes a medication from the list', async () => {
    const user = userEvent.setup()
    renderRx()

    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, 'Melatonina 3mg')
    await user.click(screen.getByRole('button', { name: /Añadir/ }))
    expect(screen.getByText('1. Melatonina 3mg')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Quitar medicación'))
    expect(screen.queryByText('1. Melatonina 3mg')).not.toBeInTheDocument()
  })
})

describe('PrescriptionView — submit flow', () => {
  async function addFreeTextMed(user: ReturnType<typeof userEvent.setup>, name = 'Melatonina 3mg') {
    const input = screen.getByPlaceholderText(/Escitalopram 10mg/)
    await user.type(input, name)
    await user.click(screen.getByRole('button', { name: /Añadir/ }))
  }

  it('disables send until a patient and at least one medication are set', async () => {
    renderRx({ initialState: { patient: { id: 1 } } })
    await screen.findByText('Cambiar')

    const sendBtn = screen.getByRole('button', { name: /Emitir receta y enviar al paciente/ })
    expect(sendBtn).toBeDisabled()

    const user = userEvent.setup()
    await addFreeTextMed(user)
    expect(sendBtn).toBeEnabled()
  })

  it('calls onSend with the expected payload (incl. notes) and shows the success screen', async () => {
    const onSend = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderRx({ initialState: { patient: { id: 1 } }, onSend })
    await screen.findByText('Cambiar')

    await addFreeTextMed(user)
    await user.type(screen.getByLabelText('Diagnóstico (CIE-10)'), 'F41.1')
    await user.type(screen.getByLabelText('Indicaciones para el paciente'), 'Volver en 30 días')
    await user.click(screen.getByRole('button', { name: /Emitir receta y enviar al paciente/ }))

    await waitFor(() => expect(onSend).toHaveBeenCalledTimes(1))
    expect(onSend).toHaveBeenCalledWith(expect.objectContaining({
      pacienteId: 1,
      diagnosis: 'F41.1',
      notes: 'Volver en 30 días',
      medications: expect.arrayContaining([expect.objectContaining({ name: 'Melatonina 3mg' })]),
    }))

    expect(await screen.findByText('Receta emitida exitosamente')).toBeInTheDocument()
    // loadHistory() is triggered again after a successful send.
    expect(mockedApi.getMisRecetas).toHaveBeenCalledTimes(2)
  })

  it('"Ver Historial de Recetas" on the success screen resets the form and switches to the history tab', async () => {
    const onSend = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderRx({ initialState: { patient: { id: 1 } }, onSend })
    await screen.findByText('Cambiar')
    await addFreeTextMed(user)
    await user.click(screen.getByRole('button', { name: /Emitir receta y enviar al paciente/ }))
    await screen.findByText('Receta emitida exitosamente')

    await user.click(screen.getByText('Ver Historial de Recetas'))
    expect(screen.getByText('Historial de Recetas Emitidas')).toBeInTheDocument()
  })

  it('shows an error alert and stays on the form when onSend rejects', async () => {
    const onSend = vi.fn().mockRejectedValue(new Error('El paciente no tiene domicilio cargado'))
    const user = userEvent.setup()
    renderRx({ initialState: { patient: { id: 1 } }, onSend })
    await screen.findByText('Cambiar')

    await addFreeTextMed(user)
    await user.click(screen.getByRole('button', { name: /Emitir receta y enviar al paciente/ }))

    expect(await screen.findByText('El paciente no tiene domicilio cargado')).toBeInTheDocument()
    expect(screen.queryByText('Receta emitida exitosamente')).not.toBeInTheDocument()
  })

  it('"Nueva receta" resets the form back to a blank state', async () => {
    const onSend = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderRx({ initialState: { patient: { id: 1 } }, onSend })
    await screen.findByText('Cambiar')
    await addFreeTextMed(user)
    await user.click(screen.getByRole('button', { name: /Emitir receta y enviar al paciente/ }))
    await screen.findByText('Receta emitida exitosamente')

    await user.click(screen.getByText('Nueva receta'))
    expect(screen.getByPlaceholderText(/Ingresá al menos 3 letras/)).toBeInTheDocument()
  })
})

describe('PrescriptionView — history tab', () => {
  it('recovers when getMisRecetas rejects on initial load', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    mockedApi.getMisRecetas.mockRejectedValueOnce(new Error('boom'))
    renderRx()

    await waitFor(() => expect(consoleSpy).toHaveBeenCalledWith('Error al cargar historial de recetas:', expect.any(Error)))
    consoleSpy.mockRestore()
  })

  it('switching to the history tab and back to "Nueva Receta" preserves the in-progress form tab', async () => {
    const user = userEvent.setup()
    renderRx()

    await user.click(screen.getByRole('button', { name: /Historial Emitidas/ }))
    expect(screen.getByText('Historial de Recetas Emitidas')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Nueva Receta/ }))
    expect(screen.getByText('Paciente')).toBeInTheDocument()
  })

  it('shows an alert when clicking "Ver PDF oficial" for a receta with no pdfUrl yet', async () => {
    mockedApi.getMisRecetas.mockResolvedValue([
      { id: 10, paciente: { nombre: 'Juan', apellido: 'Pérez' }, medicamentos: 'x', fechaEmision: '2026-01-01' },
    ])
    const user = userEvent.setup()
    renderRx()

    await user.click(screen.getByRole('button', { name: /Historial Emitidas/ }))
    await user.click(await screen.findByRole('button', { name: /Ver PDF oficial/ }))

    expect(await screen.findByText(/todavía no tiene el documento oficial de QBI2\/Innovamed/)).toBeInTheDocument()
  })

  it('loads and lists emitted prescriptions, with the count reflected on the tab', async () => {
    mockedApi.getMisRecetas.mockResolvedValue([
      { id: 10, paciente: { nombre: 'Juan', apellido: 'Pérez', dni: '30111222' }, medicamentos: 'Escitalopram 10mg', diagnostico: 'F41.1', fechaEmision: '2026-01-01' },
    ])
    renderRx()

    expect(await screen.findByText(/Historial Emitidas \(1\)/)).toBeInTheDocument()
  })

  it('filters history results by the search box', async () => {
    mockedApi.getMisRecetas.mockResolvedValue([
      { id: 10, paciente: { nombre: 'Juan', apellido: 'Pérez', dni: '30111222' }, medicamentos: 'Escitalopram 10mg', fechaEmision: '2026-01-01' },
      { id: 11, paciente: { nombre: 'Lucía', apellido: 'Fernández', dni: '30333444' }, medicamentos: 'Sertralina 50mg', fechaEmision: '2026-01-02' },
    ])
    const user = userEvent.setup()
    renderRx()

    await user.click(screen.getByRole('button', { name: /Historial Emitidas/ }))
    await screen.findByText('Juan Pérez')

    await user.type(screen.getByPlaceholderText(/Buscar por paciente, DNI o medicamento/), 'Sertralina')
    expect(screen.queryByText('Juan Pérez')).not.toBeInTheDocument()
    expect(screen.getByText('Lucía Fernández')).toBeInTheDocument()
  })

  it('shows a laboratorio breakdown panel when prescriptions carry laboratorio data', async () => {
    mockedApi.getMisRecetas.mockResolvedValue([
      { id: 10, paciente: { nombre: 'Juan', apellido: 'Pérez' }, medicamentos: 'x', fechaEmision: '2026-01-01', laboratorios: ['Bagó', 'Bagó', 'Gador'] },
    ])
    const user = userEvent.setup()
    renderRx()
    await user.click(screen.getByRole('button', { name: /Historial Emitidas/ }))

    expect(await screen.findByText('Medicamentos por laboratorio')).toBeInTheDocument()
    expect(screen.getByText('Bagó')).toBeInTheDocument()
    expect(screen.getByText('Gador')).toBeInTheDocument()
  })

  it('opens and closes the receta detail modal', async () => {
    mockedApi.getMisRecetas.mockResolvedValue([
      { id: 10, paciente: { nombre: 'Juan', apellido: 'Pérez', dni: '30111222' }, medicamentos: 'Escitalopram 10mg', diagnostico: 'F41.1', fechaEmision: '2026-01-01' },
    ])
    const user = userEvent.setup()
    renderRx()

    await user.click(screen.getByRole('button', { name: /Historial Emitidas/ }))
    await user.click(screen.getByRole('button', { name: /Ver detalle/ }))

    expect(screen.getByText('Detalle de Receta #10')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Ver PDF oficial de Receta/ }))
    expect(await screen.findByText(/todavía no tiene el documento oficial de QBI2\/Innovamed/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(screen.queryByText('Detalle de Receta #10')).not.toBeInTheDocument()
  })

  it('shows an empty state when there are no emitted prescriptions', async () => {
    renderRx()
    await user_clicksHistoryTab()
    expect(await screen.findByText('No se encontraron recetas emitidas.')).toBeInTheDocument()

    async function user_clicksHistoryTab() {
      const user = userEvent.setup()
      await user.click(screen.getByRole('button', { name: /Historial Emitidas/ }))
    }
  })
})

describe('PrescriptionView — missing-fields banner interaction', () => {
  it('opens EditPatientModal when "Completar datos" is clicked', async () => {
    const user = userEvent.setup()
    renderRx({ initialState: { patient: { id: 999, name: 'Incompleto', nombre: 'Incompleto' } } })
    await screen.findByRole('button', { name: 'Completar datos' })

    await user.click(screen.getByRole('button', { name: 'Completar datos' }))
    expect(await screen.findByText('Editar datos de Incompleto')).toBeInTheDocument()
  })
})
