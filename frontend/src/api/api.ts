const API_BASE = window.location.protocol === 'https:'
  ? `https://${window.location.host}/api`
  : `http://${window.location.hostname}:8081/api`;

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
    throw new Error(errorText || 'API Error');
  }

  // Handle empty responses
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return await response.json();
  }
  return null;
}

export const api = {
  // Public
  getMedicos: () => apiFetch('/medicos'),
  
  getTurnosDisponibles: (medicoId: number | string, fecha: string) =>
    apiFetch(`/medicos/${medicoId}/turnos-disponibles?fecha=${fecha}`),

  // Batched counterpart of getTurnosDisponibles — one request for every médicoId's slot count
  // instead of one request per professional (used by the homepage date filter).
  getConteosDisponibilidad: (medicoIds: (number | string)[], fecha: string): Promise<Record<string, number>> =>
    apiFetch(`/medicos/turnos-disponibles-conteo?fecha=${fecha}&${medicoIds.map(id => `medicoIds=${id}`).join('&')}`),
  
  reservarTurno: (data: {
    medicoId: number
    fecha: string
    hora: string
    tipo: 'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'
    metadataAfiliado?: string
    nombrePaciente: string
    emailPaciente: string
    telefonoPaciente: string
  }) => apiFetch('/turnos/reservar', { method: 'POST', body: data as any }),

  checkFirstConsultation: (email: string) => 
    apiFetch(`/turnos/check-first-consultation?email=${encodeURIComponent(email)}`),

  // Auth / Login with Google
  loginGoogle: (idToken: string) => 
    apiFetch('/auth/google', { method: 'POST', body: { idToken } as any }),

  login: (data: any) => 
    apiFetch('/auth/login', { method: 'POST', body: data }),
  
  register: (data: any) => 
    apiFetch('/auth/register', { method: 'POST', body: data }),

  getMe: () => apiFetch('/auth/me'),
  logout: () => apiFetch('/auth/logout', { method: 'POST' }),

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
  
  getStats: () => apiFetch('/medicos/stats'),
  
  actualizarPerfil: (data: any) => 
    apiFetch('/medicos/perfil', { method: 'PUT', body: data }),
  
  getDisponibilidad: () => apiFetch('/medicos/disponibilidad'),

  actualizarDisponibilidad: (data: any[]) =>
    apiFetch('/medicos/disponibilidad', { method: 'PUT', body: data as any }),

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

  getTurnosHoy: () => apiFetch('/medicos/turnos/hoy'),
  getTurnos: () => apiFetch('/medicos/turnos'),
  
  enviarReceta: (data: {
    pacienteId: number
    medications: Array<{
      name: string
      dosage: string
      frequency: string
      duration: string
    }>
    diagnosis: string
    notes: string
  }) => apiFetch('/recetas/enviar', { method: 'POST', body: data as any }),

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
    apiFetch(`/turnos/${turnoId}/reprogramar?fecha=${encodeURIComponent(fecha)}&hora=${encodeURIComponent(hora)}`, { method: 'PUT' })
};
