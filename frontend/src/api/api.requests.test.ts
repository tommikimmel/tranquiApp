import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

// These tests cover apiFetch itself (the shared request helper) through the public api.*
// methods, since apiFetch is not exported directly. sanitizeErrorMessage has its own
// dedicated test file (api.test.ts) — not duplicated here.

function jsonResponse(body: any, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function textResponse(body: string, status = 200, contentType = 'text/plain') {
  return new Response(body, { status, headers: { 'content-type': contentType } })
}

describe('apiFetch (via api.* methods)', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch')
  })

  afterEach(() => {
    fetchSpy.mockRestore()
  })

  it('always sends credentials: "include"', async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse({ id: 1 }))
    await api.getMe()
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const [, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
    expect(options.credentials).toBe('include')
  })

  it('builds the request URL from the API base + endpoint', async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse([]))
    await api.getMedicos()
    const [url] = fetchSpy.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8081/api/medicos')
  })

  it('serializes an object body to JSON and sets Content-Type', async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse({ id: 1 }))
    await api.login({ email: 'a@b.com', password: 'x' })
    const [url, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8081/api/auth/login')
    expect(options.method).toBe('POST')
    expect(options.body).toBe(JSON.stringify({ email: 'a@b.com', password: 'x' }))
    expect((options.headers as any)['Content-Type']).toBe('application/json')
  })

  it('does not touch the response body on 401 — throws a fixed "Unauthorized" error', async () => {
    fetchSpy.mockResolvedValueOnce(textResponse('this text is never read', 401))
    await expect(api.getMe()).rejects.toThrow('Unauthorized')
  })

  it('on a 5xx error, throws the sanitized generic server-error message', async () => {
    fetchSpy.mockResolvedValueOnce(textResponse('NullPointerException at com.tranqui.Foo', 500))
    await expect(api.getMedicos()).rejects.toThrow(
      'Ocurrió un inconveniente temporal en el servidor. Por favor, intentá nuevamente en unos momentos.'
    )
  })

  it('on a non-401 error status, throws the sanitized message parsed from the JSON body', async () => {
    fetchSpy.mockResolvedValueOnce(
      textResponse(JSON.stringify({ message: 'Ya tenés un turno activo o pendiente de pago.' }), 409, 'application/json')
    )
    await expect(
      api.reservarTurno({
        medicoId: 1,
        fecha: '2026-08-20',
        hora: '10:00:00',
        tipo: 'PARTICULAR',
        nombrePaciente: 'Juan',
        emailPaciente: 'j@x.com',
        telefonoPaciente: '+54 111',
      })
    ).rejects.toThrow('Ya tenés un turno activo o pendiente de pago.')
  })

  it('parses a JSON response body when content-type is application/json', async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse({ id: 42, nombre: 'Test' }))
    const result = await api.getMe()
    expect(result).toEqual({ id: 42, nombre: 'Test' })
  })

  it('wraps a non-JSON text response body in { message }', async () => {
    fetchSpy.mockResolvedValueOnce(textResponse('OK'))
    const result = await api.logout()
    expect(result).toEqual({ message: 'OK' })
  })

  it('returns null for an empty non-JSON response body', async () => {
    fetchSpy.mockResolvedValueOnce(textResponse(''))
    const result = await api.logout()
    expect(result).toBeNull()
  })

  describe('successful requests for key auth/booking/receta endpoints', () => {
    it('login sends the right URL, method and body, and resolves with the user payload', async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ id: 1, rol: 'PACIENTE' }))
      const result = await api.login({ email: 'x@y.com', password: 'pw' })
      const [url, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('http://localhost:8081/api/auth/login')
      expect(options.method).toBe('POST')
      expect(result).toEqual({ id: 1, rol: 'PACIENTE' })
    })

    it('reservarTurno posts the full booking payload to /turnos/reservar', async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ turnoId: 7, checkoutUrl: 'https://mp.example/x' }))
      const payload = {
        medicoId: 1,
        fecha: '2026-08-20',
        hora: '10:00:00',
        tipo: 'PARTICULAR' as const,
        modalidad: 'ONLINE' as const,
        nombrePaciente: 'Juan',
        emailPaciente: 'j@x.com',
        telefonoPaciente: '+54 111',
      }
      const result = await api.reservarTurno(payload)
      const [url, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('http://localhost:8081/api/turnos/reservar')
      expect(options.method).toBe('POST')
      expect(JSON.parse(options.body as string)).toEqual(payload)
      expect(result).toEqual({ turnoId: 7, checkoutUrl: 'https://mp.example/x' })
    })

    it('enviarReceta posts to /recetas/enviar', async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ id: 5 }))
      const payload = {
        pacienteId: 1,
        medications: [{ name: 'Sertralina', dosage: '50mg', frequency: '1x día', duration: '30 días' }],
        diagnosis: 'Ansiedad',
        notes: '',
      }
      const result = await api.enviarReceta(payload)
      const [url, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('http://localhost:8081/api/recetas/enviar')
      expect(options.method).toBe('POST')
      expect(JSON.parse(options.body as string)).toEqual(payload)
      expect(result).toEqual({ id: 5 })
    })

    it('getMe issues a plain GET to /auth/me', async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ id: 9 }))
      const result = await api.getMe()
      const [url, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('http://localhost:8081/api/auth/me')
      expect(options.method).toBeUndefined()
      expect(result).toEqual({ id: 9 })
    })

    it('logout posts to /auth/logout', async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({}))
      await api.logout()
      const [url, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('http://localhost:8081/api/auth/logout')
      expect(options.method).toBe('POST')
    })
  })

  // The rest of api.ts is a long catalog of thin one-line wrappers around apiFetch (public
  // site-access, admin, doctor-dashboard, chat, patient-portal and billing endpoints). Their
  // request-building logic is identical to what's already exercised above in detail — this
  // smoke-tests every remaining method so each of those lines is actually executed at least
  // once, without re-asserting the (already-covered) URL/credentials/JSON-body mechanics again
  // for each one individually.
  describe('every other api.* method resolves and issues a request', () => {
    const invocations: Array<[string, () => any]> = [
      ['getSiteAccessStatus', () => api.getSiteAccessStatus()],
      ['verificarSiteAccess', () => api.verificarSiteAccess('secret')],
      ['getConteosDisponibilidad', () => api.getConteosDisponibilidad([1, 2], '2026-08-20', 'ONLINE')],
      ['getConteosDisponibilidad (sin modalidad)', () => api.getConteosDisponibilidad([1], '2026-08-20')],
      ['getFinanciadores', () => api.getFinanciadores()],
      ['solicitarCopiaDatos', () => api.solicitarCopiaDatos()],
      ['crearTicket', () => api.crearTicket({ asunto: 'a', mensaje: 'm' })],
      ['getMisTickets', () => api.getMisTickets()],
      ['getTicket', () => api.getTicket(1)],
      ['responderTicket', () => api.responderTicket(1, 'hola')],
      ['loginGoogle', () => api.loginGoogle('id-token')],
      ['register', () => api.register({ email: 'a@b.com' })],
      ['verifyEmail', () => api.verifyEmail({ email: 'a@b.com', codigo: '123456' })],
      ['resendCode', () => api.resendCode({ email: 'a@b.com' })],
      ['forgotPassword', () => api.forgotPassword({ email: 'a@b.com' })],
      ['resetPassword', () => api.resetPassword({ email: 'a@b.com', codigo: '123456', newPassword: 'Sup3rSecret1' })],
      ['aceptarTerminos', () => api.aceptarTerminos()],
      ['getMiCuenta', () => api.getMiCuenta()],
      ['actualizarMiCuenta', () => api.actualizarMiCuenta({ nombre: 'a' })],
      ['actualizarPreferenciasNotificacion', () => api.actualizarPreferenciasNotificacion({ emailHabilitado: true })],
      ['cambiarPassword', () => api.cambiarPassword({ newPassword: 'Sup3rSecret1' })],
      ['eliminarCuenta', () => api.eliminarCuenta({ password: 'x' })],
      ['eliminarCuenta (sin data)', () => api.eliminarCuenta()],
      ['completeProfile', () => api.completeProfile({
        nombre: 'a', apellido: 'b', sexo: 'M', fechaNacimiento: '2000-01-01',
        tipoDocumento: 'DNI', numeroDocumento: '123', telefono: '123',
      })],
      ['getAdminUsers', () => api.getAdminUsers()],
      ['updateUserRol', () => api.updateUserRol(1, 'ADMIN')],
      ['verifyProfessional', () => api.verifyProfessional(1)],
      ['rejectProfessional', () => api.rejectProfessional(1)],
      ['resetUserPassword', () => api.resetUserPassword(1)],
      ['getAdminTickets', () => api.getAdminTickets()],
      ['cambiarEstadoTicket', () => api.cambiarEstadoTicket(1, 'ACTIVO')],
      ['setNewPassword', () => api.setNewPassword('Sup3rSecret1')],
      ['getPerfil', () => api.getPerfil()],
      ['getStats', () => api.getStats('MENSUAL')],
      ['actualizarPerfil', () => api.actualizarPerfil({ nombre: 'a' })],
      ['getDisponibilidad', () => api.getDisponibilidad('ONLINE')],
      ['actualizarDisponibilidad', () => api.actualizarDisponibilidad('ONLINE', [])],
      ['actualizarConfigAgenda', () => api.actualizarConfigAgenda({ duracionTurnoMinutos: 30, intervaloEntreTurnosMinutos: 10 })],
      ['getMercadoPagoStatus', () => api.getMercadoPagoStatus()],
      ['getMercadoPagoConnectUrl', () => api.getMercadoPagoConnectUrl()],
      ['simularConexionMercadoPago', () => api.simularConexionMercadoPago()],
      ['desconectarMercadoPago', () => api.desconectarMercadoPago()],
      ['getGoogleCalendarStatus', () => api.getGoogleCalendarStatus()],
      ['getGoogleCalendarConnectUrl', () => api.getGoogleCalendarConnectUrl()],
      ['desconectarGoogleCalendar', () => api.desconectarGoogleCalendar()],
      ['getEventosExternosGoogleCalendar', () => api.getEventosExternosGoogleCalendar()],
      ['getTurnosHoy', () => api.getTurnosHoy()],
      ['getTurnos', () => api.getTurnos()],
      ['getMisRecetas', () => api.getMisRecetas()],
      ['getRecetaPorId', () => api.getRecetaPorId(1)],
      ['buscarMedicamentos', () => api.buscarMedicamentos('ibuprofeno')],
      ['buscarMedicamentos (con página)', () => api.buscarMedicamentos('ibuprofeno', 2)],
      ['getChatHistorial', () => api.getChatHistorial(1)],
      ['getChatCanales', () => api.getChatCanales()],
      ['getTieneNoLeidos', () => api.getTieneNoLeidos()],
      ['getChatCanalesVisitadores', () => api.getChatCanalesVisitadores()],
      ['getNotificaciones', () => api.getNotificaciones()],
      ['marcarNotificacionesLeidas', () => api.marcarNotificacionesLeidas()],
      ['getPacientesAtendidos', () => api.getPacientesAtendidos()],
      ['actualizarPaciente', () => api.actualizarPaciente(1, { nombre: 'a' })],
      ['getSeguimientos', () => api.getSeguimientos(1)],
      ['crearSeguimiento', () => api.crearSeguimiento(1, { estadoAnimo: 'bien', sintomas: '', notas: '' })],
      ['eliminarSeguimiento', () => api.eliminarSeguimiento(1, 2)],
      ['getInformes', () => api.getInformes(1)],
      ['crearInforme', () => api.crearInforme(1, { tipoInforme: 'x' })],
      ['editarInforme', () => api.editarInforme(1, 2, { tipoInforme: 'x' })],
      ['eliminarInforme', () => api.eliminarInforme(1, 2)],
      ['getMisTurnos', () => api.getMisTurnos()],
      ['getMisSeguimientos', () => api.getMisSeguimientos()],
      ['crearMiSeguimiento', () => api.crearMiSeguimiento({ estadoAnimo: 'bien', sintomas: '', notas: '' })],
      ['getMisInformes', () => api.getMisInformes()],
      ['cancelarTurno', () => api.cancelarTurno(1)],
      ['abandonarReservaPendiente', () => api.abandonarReservaPendiente(1, 'tok')],
      ['actualizarAsistencia', () => api.actualizarAsistencia(1, 'PRESENTE')],
      ['reprogramarTurno', () => api.reprogramarTurno(1, '2026-08-20', '10:00')],
      ['marcarDocumentoEnviado', () => api.marcarDocumentoEnviado(1, { data: 'x', nombre: 'y.pdf' })],
      ['verificarPagoTurno', () => api.verificarPagoTurno(1, 'pay-1')],
      ['verificarPagoTurno (sin paymentId)', () => api.verificarPagoTurno(1)],
      ['getSubscriptionPlans', () => api.getSubscriptionPlans()],
      ['getMySubscription', () => api.getMySubscription()],
      ['iniciarCheckoutSuscripcion', () => api.iniciarCheckoutSuscripcion(1)],
      ['getAdminSubscriptionOverview', () => api.getAdminSubscriptionOverview()],
      ['getAdminSubscriptionsList', () => api.getAdminSubscriptionsList()],
      ['registerAdminManualPayment', () => api.registerAdminManualPayment({ amount: 1 })],
      ['ejecutarReconciliacionAdmin', () => api.ejecutarReconciliacionAdmin()],
      ['updateSubscriptionPlan', () => api.updateSubscriptionPlan(1, { nombre: 'Plan' })],
      ['getSubscriptionEvents', () => api.getSubscriptionEvents()],
      ['getSubscriptionEvents (con subId)', () => api.getSubscriptionEvents(5)],
      ['updateSubscriptionStatus', () => api.updateSubscriptionStatus(1, 'ACTIVE')],
    ]

    it.each(invocations)('%s', async (_name, invoke) => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ ok: true }))
      await expect(invoke()).resolves.toBeDefined()
      expect(fetchSpy).toHaveBeenCalledTimes(1)
      const [, options] = fetchSpy.mock.calls[0] as [string, RequestInit]
      expect(options.credentials).toBe('include')
    })
  })
})
