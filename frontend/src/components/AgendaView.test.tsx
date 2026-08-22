import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AgendaView from './AgendaView'
import { AlertProvider } from '../context/AlertContext'

// AgendaView doesn't call `api` directly, but mock it anyway (as instructed) in case any
// sibling module pulled in transitively ever starts doing network calls at import time.
vi.mock('../api/api', () => ({
  api: {},
}))

function renderAgenda(props?: Partial<React.ComponentProps<typeof AgendaView>>) {
  const onSave = vi.fn().mockResolvedValue(undefined)
  const onSaveConfig = vi.fn().mockResolvedValue(undefined)
  const utils = render(
    <AlertProvider>
      <AgendaView
        medicoInfo={{ duracionTurnoMinutos: 60, intervaloEntreTurnosMinutos: 0, ofrecePresencial: true, ofreceOnline: false }}
        initialAvailabilityPresencial={[]}
        initialAvailabilityOnline={[]}
        onSave={onSave}
        onSaveConfig={onSaveConfig}
        {...props}
      />
    </AlertProvider>
  )
  return { ...utils, onSave, onSaveConfig }
}

// With duracionTurno=60 and intervaloTurno=0, candidate starts are every full hour from
// 07:00 to 20:00 inclusive (20:00 + 60min = 21:00, the grid's end) — see AgendaModalidadGrid's
// computeCandidateStarts. This makes the expected DTOs easy to reason about by hand.
function slotButton(day: string, time: string) {
  return screen.getByRole('button', { name: new RegExp(`horario ${time} de ${day}`) })
}

describe('AgendaView / AgendaModalidadGrid — bookable slot grid', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders every hourly candidate slot, all initially empty (not pressed)', () => {
    renderAgenda()
    const btn = slotButton('Lunes', '09:00')
    expect(btn).toHaveAttribute('aria-pressed', 'false')
    expect(btn.className).toMatch(/agenda-view-agenda-slot--empty/)
  })

  it('clicking an empty slot selects it, and clicking again deselects it', async () => {
    const user = userEvent.setup()
    renderAgenda()
    const btn = slotButton('Lunes', '09:00')

    await user.click(btn)
    expect(btn).toHaveAttribute('aria-pressed', 'true')

    await user.click(btn)
    expect(btn).toHaveAttribute('aria-pressed', 'false')
  })

  it('saves 3 contiguous selected slots as a single merged range', async () => {
    const user = userEvent.setup()
    const { onSave } = renderAgenda()

    await user.click(slotButton('Lunes', '09:00'))
    await user.click(slotButton('Lunes', '10:00'))
    await user.click(slotButton('Lunes', '11:00'))

    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(onSave).toHaveBeenCalledWith('PRESENCIAL', [
      { diaSemana: 1, horaInicio: '09:00', horaFin: '12:00' },
    ])
  })

  it('saves two non-contiguous selected slots as two separate ranges', async () => {
    const user = userEvent.setup()
    const { onSave } = renderAgenda()

    await user.click(slotButton('Lunes', '09:00'))
    await user.click(slotButton('Lunes', '11:00')) // gap at 10:00

    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(onSave).toHaveBeenCalledWith('PRESENCIAL', [
      { diaSemana: 1, horaInicio: '09:00', horaFin: '10:00' },
      { diaSemana: 1, horaInicio: '11:00', horaFin: '12:00' },
    ])
  })

  it('saves an empty range list for a day with nothing selected', async () => {
    const user = userEvent.setup()
    const { onSave } = renderAgenda()

    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(onSave).toHaveBeenCalledWith('PRESENCIAL', [])
  })

  it('also saves the duración/intervalo config alongside the slot ranges', async () => {
    const user = userEvent.setup()
    const { onSaveConfig } = renderAgenda()

    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(onSaveConfig).toHaveBeenCalledWith({ duracionTurnoMinutos: 60, intervaloEntreTurnosMinutos: 0 })
  })

  it('imports previously saved availability ranges into pre-selected grid slots on mount', () => {
    renderAgenda({
      initialAvailabilityPresencial: [
        { diaSemana: 1, horaInicio: '09:00', horaFin: '11:00' },
      ],
    })

    // 09:00 and 10:00 both fully fit inside [09:00, 11:00) with a 60-minute slot.
    expect(slotButton('Lunes', '09:00')).toHaveAttribute('aria-pressed', 'true')
    expect(slotButton('Lunes', '10:00')).toHaveAttribute('aria-pressed', 'true')
    // 11:00 would end at 12:00, past the saved range's end — must stay unselected.
    expect(slotButton('Lunes', '11:00')).toHaveAttribute('aria-pressed', 'false')
  })

  it('ignores a saved range for a weekend day (diaSemana outside 1-5)', () => {
    renderAgenda({
      initialAvailabilityPresencial: [
        { diaSemana: 6, horaInicio: '09:00', horaFin: '11:00' },
      ],
    })
    // Nothing should be selected anywhere since diaSemana=6 (Saturday) is dropped entirely.
    expect(slotButton('Lunes', '09:00')).toHaveAttribute('aria-pressed', 'false')
  })

  it('"Copiar horario a todos los días" duplicates the source day\'s slots onto every weekday', async () => {
    const user = userEvent.setup()
    renderAgenda()

    await user.click(slotButton('Lunes', '09:00'))
    await user.click(screen.getByRole('button', { name: /Copiar horario de Lunes a todos los días/ }))

    expect(slotButton('Martes', '09:00')).toHaveAttribute('aria-pressed', 'true')
    expect(slotButton('Miércoles', '09:00')).toHaveAttribute('aria-pressed', 'true')
    expect(slotButton('Jueves', '09:00')).toHaveAttribute('aria-pressed', 'true')
    expect(slotButton('Viernes', '09:00')).toHaveAttribute('aria-pressed', 'true')
  })

  it('re-fits existing selected ranges onto a new grid when duración changes', async () => {
    const user = userEvent.setup()
    renderAgenda()

    // Select a 1-hour block (09:00-10:00) under the initial 60-minute grid.
    await user.click(slotButton('Lunes', '09:00'))
    expect(slotButton('Lunes', '09:00')).toHaveAttribute('aria-pressed', 'true')

    // Switch duración to 30 minutes — candidates become half-hourly, and the previously
    // selected 09:00-10:00 range should be translated onto the new grid rather than wiped.
    const durSelect = screen.getByDisplayValue('60 min') as HTMLSelectElement
    await user.selectOptions(durSelect, '30')

    expect(slotButton('Lunes', '09:00')).toHaveAttribute('aria-pressed', 'true')
    expect(slotButton('Lunes', '09:30')).toHaveAttribute('aria-pressed', 'true')
    // 10:00 was the exclusive end of the old range and should not be part of the new selection.
    expect(slotButton('Lunes', '10:00')).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows a success alert after a successful save', async () => {
    const user = userEvent.setup()
    renderAgenda()

    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(await screen.findByText(/Disponibilidad guardada correctamente/)).toBeInTheDocument()
  })

  it('shows an error alert when saving fails', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockRejectedValue(new Error('network down'))
    const onSaveConfig = vi.fn().mockResolvedValue(undefined)
    render(
      <AlertProvider>
        <AgendaView
          medicoInfo={{ duracionTurnoMinutos: 60, intervaloEntreTurnosMinutos: 0, ofrecePresencial: true, ofreceOnline: false }}
          initialAvailabilityPresencial={[]}
          initialAvailabilityOnline={[]}
          onSave={onSave}
          onSaveConfig={onSaveConfig}
        />
      </AlertProvider>
    )

    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(await screen.findByText(/Error al guardar disponibilidad/)).toBeInTheDocument()
  })
})

describe('AgendaView — dual modalidad (presencial + online)', () => {
  it('lets the médico switch tabs and copy slots from the other modalidad agenda', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    const onSaveConfig = vi.fn().mockResolvedValue(undefined)
    render(
      <AlertProvider>
        <AgendaView
          medicoInfo={{ duracionTurnoMinutos: 60, intervaloEntreTurnosMinutos: 0, ofrecePresencial: true, ofreceOnline: true }}
          initialAvailabilityPresencial={[]}
          initialAvailabilityOnline={[{ diaSemana: 1, horaInicio: '09:00', horaFin: '10:00' }]}
          onSave={onSave}
          onSaveConfig={onSaveConfig}
        />
      </AlertProvider>
    )

    // Starts on Presencial tab (ofrecePresencial true takes priority).
    await user.click(screen.getByRole('button', { name: /Copiar horarios de la agenda online/ }))
    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(onSave).toHaveBeenCalledWith('PRESENCIAL', [
      { diaSemana: 1, horaInicio: '09:00', horaFin: '10:00' },
    ])
    expect(onSave).toHaveBeenCalledWith('ONLINE', [
      { diaSemana: 1, horaInicio: '09:00', horaFin: '10:00' },
    ])
  })

  it('does nothing when copying from an other-modalidad grid that has no slots', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    const onSaveConfig = vi.fn().mockResolvedValue(undefined)
    render(
      <AlertProvider>
        <AgendaView
          medicoInfo={{ duracionTurnoMinutos: 60, intervaloEntreTurnosMinutos: 0, ofrecePresencial: true, ofreceOnline: true }}
          initialAvailabilityPresencial={[]}
          initialAvailabilityOnline={[]}
          onSave={onSave}
          onSaveConfig={onSaveConfig}
        />
      </AlertProvider>
    )

    await user.click(screen.getByRole('button', { name: /Copiar horarios de la agenda online/ }))
    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))

    expect(onSave).toHaveBeenCalledWith('PRESENCIAL', [])
  })
})

describe('AgendaView — notas y pendientes (to-do list)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('shows an empty state when there are no notes', () => {
    renderAgenda()
    expect(screen.getByText('No tenés notas pendientes.')).toBeInTheDocument()
  })

  it('adds a new note and it appears in the list', async () => {
    const user = userEvent.setup()
    renderAgenda()

    await user.type(screen.getByPlaceholderText('Ej. Llamar a prepaga Rossi...'), 'Llamar a Juan')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    expect(screen.getByText('Llamar a Juan')).toBeInTheDocument()
    expect(screen.queryByText('No tenés notas pendientes.')).not.toBeInTheDocument()
  })

  it('does not add a note when the text is blank', async () => {
    const user = userEvent.setup()
    renderAgenda()

    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    expect(screen.getByText('No tenés notas pendientes.')).toBeInTheDocument()
  })

  it('toggles a note as completed and can delete it', async () => {
    const user = userEvent.setup()
    renderAgenda()

    await user.type(screen.getByPlaceholderText('Ej. Llamar a prepaga Rossi...'), 'Nota de prueba')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    const checkbox = screen.getByRole('checkbox')
    await user.click(checkbox)
    expect(checkbox).toBeChecked()

    const deleteIcon = screen.getByTitle('Eliminar nota')
    await user.click(deleteIcon)
    expect(screen.getByText('No tenés notas pendientes.')).toBeInTheDocument()
  })

  it('paginates notes when there are more than 5', async () => {
    const user = userEvent.setup()
    renderAgenda()

    for (let i = 1; i <= 6; i++) {
      await user.type(screen.getByPlaceholderText('Ej. Llamar a prepaga Rossi...'), `Nota ${i}`)
      await user.click(screen.getByRole('button', { name: 'Añadir' }))
    }

    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Siguiente/ }))
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Anterior/ }))
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
  })

  it('marks urgent notes first regardless of insertion order', async () => {
    const user = userEvent.setup()
    renderAgenda()

    await user.type(screen.getByPlaceholderText('Ej. Llamar a prepaga Rossi...'), 'Nota clínica normal')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    await user.selectOptions(screen.getByDisplayValue('Nota Clínica'), 'urgent')
    await user.type(screen.getByPlaceholderText('Ej. Llamar a prepaga Rossi...'), 'Nota urgente')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    const items = screen.getAllByText(/Nota (clínica normal|urgente)/)
    expect(items[0]).toHaveTextContent('Nota urgente')
  })
})
