import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePatients, getMissingPatientFields, getInitials, calcAge, formatDomicilio, type Patient } from './usePatients'
import { api } from '../api/api'

vi.mock('../api/api', () => ({
  api: {
    getPacientesAtendidos: vi.fn(),
    getChatCanales: vi.fn(),
  },
}))

const mockedApi = vi.mocked(api)

function makePatient(overrides: Partial<Patient>): Patient {
  return {
    id: 1,
    nombre: 'Paciente',
    email: 'p@example.com',
    telefono: '111',
    dni: '1',
    direccion: 'Calle 1',
    obraSocial: 'Particular',
    numAfiliado: 'N/A',
    ultimaVisita: '',
    prioridadClinica: 'baja',
    fechaNacimiento: '1990-01-01',
    ...overrides,
  }
}

describe('usePatients', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches patients on mount and exposes them once loaded', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValue([makePatient({ id: 1, nombre: 'Beta' })])
    mockedApi.getChatCanales.mockResolvedValue([])

    const { result } = renderHook(() => usePatients())

    expect(result.current.loadingPatients).toBe(true)

    await waitFor(() => expect(result.current.loadingPatients).toBe(false))
    expect(result.current.patients).toHaveLength(1)
    expect(result.current.patients[0].nombre).toBe('Beta')
  })

  it('sorts patients with an active chat channel first, in the channel order returned by the backend', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValue([
      makePatient({ id: 1, nombre: 'Ana' }),
      makePatient({ id: 2, nombre: 'Beto' }),
      makePatient({ id: 3, nombre: 'Carla' }),
    ])
    // Backend returns canales ordered DESC by last message; id 3 chatted most recently.
    mockedApi.getChatCanales.mockResolvedValue([{ id: 3 }, { id: 1 }])

    const { result } = renderHook(() => usePatients())
    await waitFor(() => expect(result.current.loadingPatients).toBe(false))

    expect(result.current.patients.map(p => p.id)).toEqual([3, 1, 2])
  })

  it('matches channel order by email when id does not match', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValue([
      makePatient({ id: 1, nombre: 'Ana', email: 'ana@example.com' }),
      makePatient({ id: 2, nombre: 'Beto', email: 'beto@example.com' }),
    ])
    mockedApi.getChatCanales.mockResolvedValue([{ id: 999, email: 'BETO@EXAMPLE.COM' }])

    const { result } = renderHook(() => usePatients())
    await waitFor(() => expect(result.current.loadingPatients).toBe(false))

    expect(result.current.patients.map(p => p.id)).toEqual([2, 1])
  })

  it('falls back to sorting by unread count, then alphabetically, when no channel matches', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValue([
      makePatient({ id: 1, nombre: 'Zeta', unreadMessagesCount: 0 }),
      makePatient({ id: 2, nombre: 'Alfa', unreadMessagesCount: 3 }),
      makePatient({ id: 3, nombre: 'Beta', unreadMessagesCount: 0 }),
    ])
    mockedApi.getChatCanales.mockResolvedValue([])

    const { result } = renderHook(() => usePatients())
    await waitFor(() => expect(result.current.loadingPatients).toBe(false))

    // Alfa (3 unread) first, then Beta/Zeta alphabetically (both 0 unread).
    expect(result.current.patients.map(p => p.nombre)).toEqual(['Alfa', 'Beta', 'Zeta'])
  })

  it('resolves to an empty list and stops loading when both requests fail', async () => {
    mockedApi.getPacientesAtendidos.mockRejectedValue(new Error('network error'))
    mockedApi.getChatCanales.mockRejectedValue(new Error('network error'))

    const { result } = renderHook(() => usePatients())
    await waitFor(() => expect(result.current.loadingPatients).toBe(false))

    expect(result.current.patients).toEqual([])
  })

  it('filters patients by nombre, email or dni via searchQuery', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValue([
      makePatient({ id: 1, nombre: 'Juan Pérez', email: 'juan@example.com', dni: '30111222' }),
      makePatient({ id: 2, nombre: 'María López', email: 'maria@example.com', dni: '30333444' }),
    ])
    mockedApi.getChatCanales.mockResolvedValue([])

    const { result } = renderHook(() => usePatients())
    await waitFor(() => expect(result.current.loadingPatients).toBe(false))

    act(() => result.current.setSearchQuery('maria'))
    expect(result.current.filteredPatients.map(p => p.id)).toEqual([2])

    act(() => result.current.setSearchQuery('30111'))
    expect(result.current.filteredPatients.map(p => p.id)).toEqual([1])

    act(() => result.current.setSearchQuery('example.com'))
    expect(result.current.filteredPatients).toHaveLength(2)

    act(() => result.current.setSearchQuery('nomatch'))
    expect(result.current.filteredPatients).toHaveLength(0)
  })

  it('fetchPatients can be called again manually to refresh the list', async () => {
    mockedApi.getPacientesAtendidos.mockResolvedValueOnce([makePatient({ id: 1, nombre: 'Primero' })])
    mockedApi.getChatCanales.mockResolvedValue([])

    const { result } = renderHook(() => usePatients())
    await waitFor(() => expect(result.current.loadingPatients).toBe(false))
    expect(result.current.patients).toHaveLength(1)

    mockedApi.getPacientesAtendidos.mockResolvedValueOnce([
      makePatient({ id: 1, nombre: 'Primero' }),
      makePatient({ id: 2, nombre: 'Segundo' }),
    ])
    await act(async () => {
      await result.current.fetchPatients()
    })

    expect(result.current.patients).toHaveLength(2)
  })
})

describe('getMissingPatientFields', () => {
  it('returns no missing fields for a fully complete "particular" patient', () => {
    expect(getMissingPatientFields(makePatient({}))).toEqual([])
  })

  it('flags every field missing for a bare-minimum patient object', () => {
    const bare = makePatient({ dni: '', numeroDocumento: null, fechaNacimiento: null, telefono: '', direccion: '' })
    const missing = getMissingPatientFields(bare)
    expect(missing).toContain('DNI / Documento')
    expect(missing).toContain('Fecha de Nacimiento')
    expect(missing).toContain('Teléfono')
    expect(missing).toContain('Dirección')
  })

  it('accepts numeroDocumento as an alternative to dni', () => {
    const p = makePatient({ dni: '', numeroDocumento: 12345678, fechaNacimiento: '1990-01-01' })
    expect(getMissingPatientFields(p)).not.toContain('DNI / Documento')
  })

  it('treats the placeholder strings "Sin teléfono" and "No cargada" as missing', () => {
    const p = makePatient({ telefono: 'Sin teléfono', direccion: 'No cargada', fechaNacimiento: '1990-01-01' })
    const missing = getMissingPatientFields(p)
    expect(missing).toContain('Teléfono')
    expect(missing).toContain('Dirección')
  })

  it('requires N° de Afiliado only when obraSocial is set and is not "particular"', () => {
    const withOS = makePatient({ obraSocial: 'OSDE', numAfiliado: '', fechaNacimiento: '1990-01-01' })
    expect(getMissingPatientFields(withOS)).toContain('N° de Afiliado')

    const particular = makePatient({ obraSocial: 'Particular', numAfiliado: '', fechaNacimiento: '1990-01-01' })
    expect(getMissingPatientFields(particular)).not.toContain('N° de Afiliado')

    const withOSAndAfiliado = makePatient({ obraSocial: 'OSDE', numAfiliado: '12345', fechaNacimiento: '1990-01-01' })
    expect(getMissingPatientFields(withOSAndAfiliado)).not.toContain('N° de Afiliado')
  })
})

describe('getInitials', () => {
  it('returns "P" for an empty/falsy name', () => {
    expect(getInitials('')).toBe('P')
  })

  it('returns up to 2 uppercase initials from the name parts', () => {
    expect(getInitials('juan perez')).toBe('JP')
    expect(getInitials('madonna')).toBe('M')
    expect(getInitials('Juan Carlos Perez')).toBe('JC')
  })
})

describe('calcAge (usePatients copy)', () => {
  it('returns null for missing/invalid dates and a plausible age otherwise', () => {
    expect(calcAge(undefined)).toBeNull()
    expect(calcAge('garbage')).toBeNull()
    expect(calcAge('2000-01-01')).toBeGreaterThan(0)
  })
})

describe('formatDomicilio', () => {
  it('returns null when neither domicilio nor direccion are set', () => {
    expect(formatDomicilio(makePatient({ direccion: '' }))).toBeNull()
  })

  it('falls back to the free-text direccion when domicilio is absent', () => {
    expect(formatDomicilio(makePatient({ direccion: 'Calle Falsa 123' }))).toBe('Calle Falsa 123')
  })

  it('builds a formatted address from a structured domicilio, skipping missing sub-fields', () => {
    const p = makePatient({
      direccion: '',
      domicilio: { calle: 'Av. Siempreviva', numero: '742', localidad: 'Springfield', provincia: 'BA', codigoPostal: '1000' },
    })
    expect(formatDomicilio(p)).toBe('Av. Siempreviva 742 · Springfield, BA · CP 1000')
  })

  it('omits the piso/dpto segment entirely when neither is present', () => {
    const p = makePatient({ direccion: '', domicilio: { calle: 'Calle X', localidad: 'CABA' } })
    expect(formatDomicilio(p)).toBe('Calle X · CABA')
  })

  it('does not treat a domicilio with neither calle nor localidad as structured (falls back to direccion)', () => {
    const p = makePatient({ direccion: 'Direccion libre', domicilio: { piso: '2' } })
    expect(formatDomicilio(p)).toBe('Direccion libre')
  })
})
