import { describe, expect, it } from 'vitest'
import { sanitizeErrorMessage } from './api'

describe('sanitizeErrorMessage', () => {
  it('returns a fixed message for 401 regardless of body', () => {
    expect(sanitizeErrorMessage('whatever', 401)).toBe('Sesión expirada o credenciales no válidas.')
  })

  it('returns a fixed message for any 5xx status', () => {
    expect(sanitizeErrorMessage('boom', 500)).toBe(
      'Ocurrió un inconveniente temporal en el servidor. Por favor, intentá nuevamente en unos momentos.'
    )
    expect(sanitizeErrorMessage('boom', 503)).toBe(
      'Ocurrió un inconveniente temporal en el servidor. Por favor, intentá nuevamente en unos momentos.'
    )
  })

  it('passes through a clean, user-facing message from a JSON object body', () => {
    const body = JSON.stringify({ message: 'El horario seleccionado ya no está disponible' })
    expect(sanitizeErrorMessage(body, 409)).toBe('El horario seleccionado ya no está disponible')
  })

  it('falls back to the "error" field when "message" is absent', () => {
    const body = JSON.stringify({ error: 'Ya tenés un turno activo o pendiente de pago.' })
    expect(sanitizeErrorMessage(body, 400)).toBe('Ya tenés un turno activo o pendiente de pago.')
  })

  it('passes through a plain JSON string body', () => {
    const body = JSON.stringify('El código REFEPS debe tener 12 dígitos.')
    expect(sanitizeErrorMessage(body, 400)).toBe('El código REFEPS debe tener 12 dígitos.')
  })

  it('treats non-JSON body text as the raw message', () => {
    expect(sanitizeErrorMessage('El domicilio es demasiado corto.', 400)).toBe('El domicilio es demasiado corto.')
  })

  it('masks a message containing a stack-trace keyword behind a generic one', () => {
    const body = JSON.stringify({ message: 'java.lang.NullPointerException at com.tranqui.app.Foo' });
    expect(sanitizeErrorMessage(body, 400)).toBe(
      'No se pudo completar la operación. Por favor, verificá los datos e intentá de nuevo.'
    )
  })

  it('masks a Spring Boot default error body ("Forbidden") behind a generic message', () => {
    // The exact scenario diagnosed this session: Spring's default /error JSON has an "error"
    // field literally equal to "Forbidden", which without this masking would leak straight
    // through to the user as a raw, unhelpful string.
    const body = JSON.stringify({ timestamp: '...', status: 403, error: 'Forbidden', path: '/api/x' })
    expect(sanitizeErrorMessage(body, 403)).toBe(
      'No se pudo completar la operación. Por favor, verificá los datos e intentá de nuevo.'
    )
  })

  it('masks an overly long message', () => {
    const body = JSON.stringify({ message: 'x'.repeat(300) })
    expect(sanitizeErrorMessage(body, 400)).toBe(
      'No se pudo completar la operación. Por favor, verificá los datos e intentá de nuevo.'
    )
  })

  it('returns a generic fallback when there is no usable message at all', () => {
    expect(sanitizeErrorMessage('', 400)).toBe(
      'No se pudo procesar la solicitud. Por favor, verificá la información e intentá nuevamente.'
    )
    expect(sanitizeErrorMessage('{}', 400)).toBe(
      'No se pudo procesar la solicitud. Por favor, verificá la información e intentá nuevamente.'
    )
  })
})
