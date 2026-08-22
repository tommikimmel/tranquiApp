import { describe, expect, it } from 'vitest'
import {
  getDoctorSlug,
  normalizeToIsoDate,
  getNotificationVisual,
  getPatientInitials,
  calcAge,
  formatDetalleDomicilio,
  formatDateDDMMYYYY,
} from './dashboardHelpers'

describe('getDoctorSlug', () => {
  it('returns an empty string for falsy input', () => {
    expect(getDoctorSlug('')).toBe('')
    expect(getDoctorSlug(undefined as unknown as string)).toBe('')
  })

  it('lowercases, strips accents and replaces non-alphanumerics with dashes', () => {
    expect(getDoctorSlug('Dra. María José Pérez Núñez')).toBe('dra-maria-jose-perez-nunez')
  })

  it('trims leading/trailing dashes', () => {
    expect(getDoctorSlug('  Juan Pérez!!  ')).toBe('juan-perez')
  })

  it('collapses consecutive separators into a single dash', () => {
    expect(getDoctorSlug('Juan   ---   Pérez')).toBe('juan-perez')
  })
})

describe('normalizeToIsoDate', () => {
  it('returns an empty string for falsy input', () => {
    expect(normalizeToIsoDate(null)).toBe('')
    expect(normalizeToIsoDate(undefined)).toBe('')
    expect(normalizeToIsoDate('')).toBe('')
  })

  it('converts dd/MM/yyyy to ISO', () => {
    expect(normalizeToIsoDate('25/12/1990')).toBe('1990-12-25')
  })

  it('passes through a value that is already ISO (or any other shape) unchanged', () => {
    expect(normalizeToIsoDate('1990-12-25')).toBe('1990-12-25')
    expect(normalizeToIsoDate('not-a-date')).toBe('not-a-date')
  })
})

describe('getNotificationVisual', () => {
  it('maps TURNO_RESERVADO and TURNO_CONFIRMADO to the same success visual', () => {
    const reservado = getNotificationVisual('TURNO_RESERVADO')
    const confirmado = getNotificationVisual('TURNO_CONFIRMADO')
    expect(reservado.color).toBe('var(--color-success)')
    expect(confirmado.color).toBe('var(--color-success)')
    expect(reservado.Icon).toBe(confirmado.Icon)
  })

  it('maps TURNO_CANCELADO to the danger visual', () => {
    expect(getNotificationVisual('TURNO_CANCELADO').color).toBe('var(--color-danger)')
  })

  it('maps SEGUIMIENTO, INFORME, DOCUMENTO_PENDIENTE and DOCUMENTO_ENVIADO to distinct visuals', () => {
    expect(getNotificationVisual('SEGUIMIENTO').color).toBe('#3b82f6')
    expect(getNotificationVisual('INFORME').color).toBe('#8b5cf6')
    expect(getNotificationVisual('DOCUMENTO_PENDIENTE').color).toBe('var(--color-warning)')
    expect(getNotificationVisual('DOCUMENTO_ENVIADO').color).toBe('var(--color-success)')
  })

  it('maps NUEVO_MENSAJE to the primary visual', () => {
    expect(getNotificationVisual('NUEVO_MENSAJE').color).toBe('var(--color-primary)')
  })

  it('maps TICKET_NUEVO and TICKET_RESPUESTA to the same visual', () => {
    expect(getNotificationVisual('TICKET_NUEVO').color).toBe('#8b5cf6')
    expect(getNotificationVisual('TICKET_RESPUESTA').color).toBe('#8b5cf6')
  })

  it('falls back to the default bell visual for unknown/undefined tipo', () => {
    expect(getNotificationVisual(undefined).color).toBe('var(--color-primary)')
    expect(getNotificationVisual('ALGO_INEXISTENTE').color).toBe('var(--color-primary)')
  })
})

describe('getPatientInitials', () => {
  it('returns "?" for an empty/whitespace-only name', () => {
    expect(getPatientInitials('')).toBe('?')
    expect(getPatientInitials('   ')).toBe('?')
  })

  it('returns the first two letters uppercased for a single-word name', () => {
    expect(getPatientInitials('Madonna')).toBe('MA')
  })

  it('returns first-letter-of-first-name + first-letter-of-last-name for multi-word names', () => {
    expect(getPatientInitials('Juan Pérez')).toBe('JP')
    expect(getPatientInitials('  Juan   Carlos   Pérez  ')).toBe('JP')
  })
})

describe('calcAge', () => {
  it('returns null when no birth date is provided', () => {
    expect(calcAge(undefined)).toBeNull()
    expect(calcAge(null)).toBeNull()
    expect(calcAge('')).toBeNull()
  })

  it('returns null for an invalid date string', () => {
    expect(calcAge('not-a-date')).toBeNull()
  })

  it('returns null for a birth date in the future', () => {
    const future = new Date()
    future.setFullYear(future.getFullYear() + 1)
    expect(calcAge(future.toISOString())).toBeNull()
  })

  it('computes age correctly, accounting for whether the birthday already happened this year', () => {
    const today = new Date()
    const turns30Today = new Date(today.getFullYear() - 30, today.getMonth(), today.getDate())
    expect(calcAge(turns30Today.toISOString())).toBe(30)

    // Birthday tomorrow: hasn't turned this year's age yet.
    const almost30 = new Date(today.getFullYear() - 30, today.getMonth(), today.getDate() + 1)
    expect(calcAge(almost30.toISOString())).toBe(29)
  })

  it('returns null for an implausibly old date (>=130 years)', () => {
    const today = new Date()
    const tooOld = new Date(today.getFullYear() - 131, today.getMonth(), today.getDate())
    expect(calcAge(tooOld.toISOString())).toBeNull()
  })
})

describe('formatDetalleDomicilio', () => {
  it('returns an empty string when no optional fields are set', () => {
    expect(formatDetalleDomicilio({})).toBe('')
  })

  it('joins only the provided fields with " · "', () => {
    expect(formatDetalleDomicilio({ domicilioAtencionPiso: '3', domicilioAtencionDepto: 'B' }))
      .toBe('Piso 3 · Depto B')
  })

  it('strips a redundant leading "Torre" word from the torre field', () => {
    expect(formatDetalleDomicilio({ domicilioAtencionTorre: 'Torre B' })).toBe('Torre B')
    expect(formatDetalleDomicilio({ domicilioAtencionTorre: 'B' })).toBe('Torre B')
  })

  it('includes all four fields in order when present', () => {
    expect(formatDetalleDomicilio({
      domicilioAtencionTorre: 'A',
      domicilioAtencionPiso: '2',
      domicilioAtencionDepto: '4',
      domicilioAtencionBarrio: 'Palermo',
    })).toBe('Torre A · Piso 2 · Depto 4 · Barrio Palermo')
  })
})

describe('formatDateDDMMYYYY', () => {
  it('returns "Sin fecha" for a falsy value', () => {
    expect(formatDateDDMMYYYY(null)).toBe('Sin fecha')
    expect(formatDateDDMMYYYY(undefined)).toBe('Sin fecha')
    expect(formatDateDDMMYYYY('')).toBe('Sin fecha')
  })

  it('formats an ISO datetime string (with T) to dd/MM/yyyy', () => {
    expect(formatDateDDMMYYYY('2024-03-05T10:30:00')).toBe('05/03/2024')
  })

  it('formats a plain ISO date string (yyyy-MM-dd) to dd/MM/yyyy', () => {
    expect(formatDateDDMMYYYY('2024-03-05')).toBe('05/03/2024')
  })

  it('falls back to String(dateVal) for a shape it does not recognize', () => {
    expect(formatDateDDMMYYYY(12345)).toBe('12345')
    expect(formatDateDDMMYYYY('not-a-date-at-all')).toBe('not-a-date-at-all')
  })
})
