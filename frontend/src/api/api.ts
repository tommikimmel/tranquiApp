const API_BASE = window.location.protocol === 'https:'
  ? `https://${window.location.host}/api`
  : `http://${window.location.hostname}:8081/api`;

export function sanitizeErrorMessage(errorText: string, status: number): string {
  if (status === 401) {
    return 'Sesión expirada o credenciales no válidas.';
  }
  if (status >= 500) {
    return 'Ocurrió un inconveniente temporal en el servidor. Por favor, intentá nuevamente en unos momentos.';
  }

  let message = '';
  try {
    const parsed = JSON.parse(errorText);
    if (typeof parsed === 'string') {
      message = parsed;
    } else if (parsed && typeof parsed === 'object') {
      message = parsed.message || parsed.error || '';
    }
  } catch {
    message = errorText;
  }

  if (!message || typeof message !== 'string') {
    return 'No se pudo procesar la solicitud. Por favor, verificá la información e intentá nuevamente.';
  }

  const technicalKeywords = [
    'exception', 'nullpointer', 'sql', 'postgres', 'hibernate', 'stack',
    'trace', 'internal server', 'syntaxerror', 'typeerror', 'referenceerror',
    'org.springframework', 'com.tranqui', 'java.lang', 'at com.', 'at org.', '{', '}',
    'bad request', 'unauthorized', 'forbidden'
  ];

  const lower = message.toLowerCase();
  const isTechnical = technicalKeywords.some((kw) => lower.includes(kw));

  if (isTechnical || message.length > 250) {
    return 'No se pudo completar la operación. Por favor, verificá los datos e intentá de nuevo.';
  }

  return message;
}

// Helper for fetch with credentials
async function apiFetch(endpoint: string, options: RequestInit = {}) {
  const url = `${API_BASE}${endpoint}`;
  options.credentials = 'include'; // crucial for Session Cookie HttpOnly
  
  if (options.body && typeof options.body === 'object') {
    options.body = JSON.stringify(options.body);
    options.headers = {
      ...options.headers,
      'Content-Type': 'application/json',
    };
  }

  const response = await fetch(url, options);

  if (response.status === 401) {
    // Redirect or handle unauthorized
    throw new Error('Unauthorized');
  }

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(sanitizeErrorMessage(errorText, response.status));
  }

  // Handle empty or text responses
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return await response.json();
  }
  const textResponse = await response.text();
  return textResponse ? { message: textResponse } : null;
}

export const api = {
  // Site-wide access gate (temporary — see SiteAccessFilter on the backend)
  getSiteAccessStatus: () => apiFetch('/site-access/estado'),
  verificarSiteAccess: (password: string) => apiFetch('/site-access/verificar', { method: 'POST', body: { password } as any }),

  // Public
  getMedicos: () => apiFetch('/medicos'),
  
  getTurnosDisponibles: (medicoId: number | string, fecha: string, modalidad: 'PRESENCIAL' | 'ONLINE') =>
    apiFetch(`/medicos/${medicoId}/turnos-disponibles?fecha=${fecha}&modalidad=${modalidad}`),

  // Batched counterpart of getTurnosDisponibles — one request for every médicoId's slot count
  // instead of one request per professional (used by the homepage date filter). modalidad is
  // optional here — the homepage shows this badge before the patient picks presencial/online.
  getConteosDisponibilidad: (medicoIds: (number | string)[], fecha: string, modalidad?: 'PRESENCIAL' | 'ONLINE'): Promise<Record<string, number>> =>
    apiFetch(`/medicos/turnos-disponibles-conteo?fecha=${fecha}${modalidad ? `&modalidad=${modalidad}` : ''}&${medicoIds.map(id => `medicoIds=${id}`).join('&')}`),

  reservarTurno: (data: {
    medicoId: number
    fecha: string
    hora: string
    tipo: 'PARTICULAR' | 'OBRA_SOCIAL' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'
    modalidad?: 'PRESENCIAL' | 'ONLINE'
    servicioId?: string
    obraSocial?: string
    idFinanciador?: string
    metadataAfiliado?: string
    nombrePaciente: string
    emailPaciente: string
    telefonoPaciente: string
  }) => apiFetch('/turnos/reservar', { method: 'POST', body: data as any }),

  // Public catalog of obras sociales/financiadores from QBI2 — used at checkout when the
  // médico marked a service as "Requiere Obra Social" (see Honorarios y Servicios).
  getFinanciadores: () => apiFetch('/recetas/financiadores'),

  // Support/complaints area — sends an email to soporte@tranquisalud.com on behalf of the
  // logged-in patient or professional.
  enviarQueja: (data: { asunto: string; mensaje: string }) =>
    apiFetch('/soporte/queja', { method: 'POST', body: data as any }),

  // ARCO (Ley 25.326) — pide al equipo de soporte una copia de los datos personales del usuario.
  solicitarCopiaDatos: () => apiFetch('/soporte/solicitud-datos', { method: 'POST' }),

  // Auth / Login with Google
  loginGoogle: (idToken: string) => 
    apiFetch('/auth/google', { method: 'POST', body: { idToken } as any }),

  login: (data: any) => 
    apiFetch('/auth/login', { method: 'POST', body: data }),
  
  register: (data: any) => 
    apiFetch('/auth/register', { method: 'POST', body: data }),

  verifyEmail: (data: { email: string; codigo: string }) =>
    apiFetch('/auth/verify-email', { method: 'POST', body: data as any }),

  resendCode: (data: { email: string }) =>
    apiFetch('/auth/resend-code', { method: 'POST', body: data as any }),

  forgotPassword: (data: { email: string }) =>
    apiFetch('/auth/forgot-password', { method: 'POST', body: data as any }),

  resetPassword: (data: { email: string; codigo: string; newPassword: string }) =>
    apiFetch('/auth/reset-password', { method: 'POST', body: data as any }),

  getMe: () => apiFetch('/auth/me'),
  logout: () => apiFetch('/auth/logout', { method: 'POST' }),
  aceptarTerminos: () => apiFetch('/auth/aceptar-terminos', { method: 'POST' }),

  // "Mi Cuenta" — configuración de perfil del paciente (también usable por médicos).
  getMiCuenta: () => apiFetch('/auth/mi-cuenta'),
  actualizarMiCuenta: (data: any) => apiFetch('/auth/mi-cuenta', { method: 'PUT', body: data }),
  actualizarPreferenciasNotificacion: (data: { emailHabilitado: boolean; whatsappHabilitado: boolean }) =>
    apiFetch('/auth/mi-cuenta/notificaciones', { method: 'PUT', body: data as any }),
  cambiarPassword: (data: { currentPassword?: string; newPassword: string }) =>
    apiFetch('/auth/mi-cuenta/password', { method: 'POST', body: data as any }),
  eliminarCuenta: (data?: { password?: string }) =>
    apiFetch('/auth/mi-cuenta/eliminar', { method: 'POST', body: (data || {}) as any }),

  // Fills in the Paso 2 data missing after a Google sign-in (see CompleteProfileModal).
  completeProfile: (data: {
    nombre: string
    apellido: string
    sexo: string
    fechaNacimiento: string
    tipoDocumento: string
    numeroDocumento: string
    telefono: string
    obraSocial?: string
    numAfiliado?: string
  }) => apiFetch('/auth/complete-profile', { method: 'POST', body: data as any }),

  // Admin APIs
  getAdminUsers: () => apiFetch('/admin/users'),
  updateUserRol: (id: number | string, rol: string) => 
    apiFetch(`/admin/users/${id}/rol`, { method: 'PUT', body: { rol } as any }),
  verifyProfessional: (id: number | string) => 
    apiFetch(`/admin/users/${id}/verify`, { method: 'POST' }),
  rejectProfessional: (id: number | string) => 
    apiFetch(`/admin/users/${id}/reject`, { method: 'POST' }),

  // Doctor Dashboard
  getPerfil: () => apiFetch('/medicos/perfil'),
  
  getStats: (periodo: 'DIARIO' | 'SEMANAL' | 'MENSUAL' = 'MENSUAL') => apiFetch(`/medicos/stats?periodo=${periodo}`),
  
  actualizarPerfil: (data: any) => 
    apiFetch('/medicos/perfil', { method: 'PUT', body: data }),
  
  getDisponibilidad: (modalidad: 'PRESENCIAL' | 'ONLINE') =>
    apiFetch(`/medicos/disponibilidad?modalidad=${modalidad}`),

  actualizarDisponibilidad: (modalidad: 'PRESENCIAL' | 'ONLINE', data: any[]) =>
    apiFetch(`/medicos/disponibilidad?modalidad=${modalidad}`, { method: 'PUT', body: data as any }),

  // Agenda settings ("Duración de turno" / "Intervalo entre turnos"). Current values also
  // come back on getPerfil() (duracionTurnoMinutos / intervaloEntreTurnosMinutos), so this
  // is only needed to persist changes.
  actualizarConfigAgenda: (data: { duracionTurnoMinutos: number; intervaloEntreTurnosMinutos: number }) =>
    apiFetch('/medicos/disponibilidad-config', { method: 'PUT', body: data as any }),

  getMercadoPagoStatus: (): Promise<{ connected: boolean; mpUserId: string; mercadopagoEnabled: boolean }> =>
    apiFetch('/medicos/mercadopago/status'),

  getMercadoPagoConnectUrl: (): Promise<{ url: string }> =>
    apiFetch('/medicos/mercadopago/connect'),

  simularConexionMercadoPago: () =>
    apiFetch('/medicos/mercadopago/connect-simulado', { method: 'POST' }),

  desconectarMercadoPago: () =>
    apiFetch('/medicos/mercadopago/disconnect', { method: 'POST' }),

  getGoogleCalendarStatus: (): Promise<{ connected: boolean }> =>
    apiFetch('/medicos/google-calendar/status'),

  getGoogleCalendarConnectUrl: (): Promise<{ url: string }> =>
    apiFetch('/medicos/google-calendar/connect'),

  desconectarGoogleCalendar: () =>
    apiFetch('/medicos/google-calendar/disconnect', { method: 'POST' }),

  getEventosExternosGoogleCalendar: () =>
    apiFetch('/medicos/google-calendar/eventos'),

  getTurnosHoy: () => apiFetch('/medicos/turnos/hoy'),
  getTurnos: () => apiFetch('/medicos/turnos'),
  
  enviarReceta: (data: {
    pacienteId: number
    medications: Array<{
      name: string
      dosage: string
      frequency: string
      duration: string
      regNo?: string
      nombreDroga?: string
      noSustituible?: boolean
      laboratorio?: string
    }>
    diagnosis: string
    notes: string
  }) => apiFetch('/recetas/enviar', { method: 'POST', body: data as any }),
  getMisRecetas: () => apiFetch('/recetas/me'),
  getRecetaPorId: (id: number | string) => apiFetch(`/recetas/${id}`),

  // Real QBI2/Innovamed medicamento catalog — replaces the old hardcoded CATALOG_MEDICATIONS
  // mock so prescriptions carry a real regNo instead of a made-up one.
  buscarMedicamentos: (texto: string, pagina = 1) =>
    apiFetch(`/recetas/medicamentos?texto=${encodeURIComponent(texto)}&pagina=${pagina}`),

  // Chat API
  getChatHistorial: (destinatarioId: number | string, page = 0, size = 100) =>
    apiFetch(`/chat/historial/${destinatarioId}?page=${page}&size=${size}`),
  getChatCanales: () => apiFetch('/chat/canales'),
  getTieneNoLeidos: (): Promise<boolean> => apiFetch('/chat/tiene-no-leidos'),
  getChatCanalesVisitadores: () => apiFetch('/chat/canales/visitadores'),

  // Clinical & Notification API
  getNotificaciones: () => apiFetch('/notificaciones'),
  marcarNotificacionesLeidas: () => apiFetch('/notificaciones/marcar-leidas', { method: 'POST' }),
  getPacientesAtendidos: () => apiFetch('/pacientes/atendidos'),
  actualizarPaciente: (pacienteId: number | string, data: any) => 
    apiFetch(`/pacientes/${pacienteId}`, { method: 'PUT', body: data }),
  getSeguimientos: (pacienteId: number | string) => apiFetch(`/pacientes/${pacienteId}/seguimientos`),
  crearSeguimiento: (pacienteId: number | string, data: { estadoAnimo: string; sintomas: string; notas: string; fecha?: string }) => 
    apiFetch(`/pacientes/${pacienteId}/seguimientos`, { method: 'POST', body: data as any }),
  eliminarSeguimiento: (pacienteId: number | string, seguimientoId: number | string) => 
    apiFetch(`/pacientes/${pacienteId}/seguimientos/${seguimientoId}`, { method: 'DELETE' }),
  getInformes: (pacienteId: number | string) => apiFetch(`/pacientes/${pacienteId}/informes`),
  crearInforme: (pacienteId: number | string, data: { tipoInforme: string; planTrabajo?: string; contenido?: string; nombreArchivo?: string }) => {
    return apiFetch(`/pacientes/${pacienteId}/informes`, { method: 'POST', body: data as any });
  },
  editarInforme: (pacienteId: number | string, informeId: number | string, data: { tipoInforme: string; planTrabajo?: string; contenido?: string }) => {
    return apiFetch(`/pacientes/${pacienteId}/informes/${informeId}`, { method: 'PUT', body: data as any });
  },
  eliminarInforme: (pacienteId: number | string, informeId: number | string) => 
    apiFetch(`/pacientes/${pacienteId}/informes/${informeId}`, { method: 'DELETE' }),

  // Patient Portal APIs
  getMisTurnos: () => apiFetch('/pacientes/me/turnos'),
  getMisSeguimientos: () => apiFetch('/pacientes/me/seguimientos'),
  crearMiSeguimiento: (data: { estadoAnimo: string; sintomas: string; notas: string }) => 
    apiFetch('/pacientes/me/seguimientos', { method: 'POST', body: data as any }),
  getMisInformes: () => apiFetch('/pacientes/me/informes'),
  cancelarTurno: (turnoId: number | string) => apiFetch(`/turnos/${turnoId}/cancelar`, { method: 'POST' }),
  abandonarReservaPendiente: (turnoId: number | string) => apiFetch(`/turnos/${turnoId}/abandonar-pago`, { method: 'POST' }),
  actualizarAsistencia: (turnoId: number | string, asistencia: string) => 
    apiFetch(`/turnos/${turnoId}/asistencia?asistencia=${encodeURIComponent(asistencia)}`, { method: 'PUT' }),
  reprogramarTurno: (turnoId: number | string, fecha: string, hora: string) =>
    apiFetch(`/turnos/${turnoId}/reprogramar?fecha=${encodeURIComponent(fecha)}&hora=${encodeURIComponent(hora)}`, { method: 'PUT' }),
  marcarDocumentoEnviado: (turnoId: number | string) =>
    apiFetch(`/turnos/${turnoId}/documento-enviado`, { method: 'POST' }),
  // Fallback reconciliation for when Mercado Pago's webhook is delayed/dropped — see
  // WebhookController#verificarPago. Called right after the patient returns from Checkout Pro.
  verificarPagoTurno: (externalReference: string, paymentId: string) =>
    apiFetch(`/payments/verificar?externalReference=${encodeURIComponent(externalReference)}&paymentId=${encodeURIComponent(paymentId)}`, { method: 'POST' })
};
