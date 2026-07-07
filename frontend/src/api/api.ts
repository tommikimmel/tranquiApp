const API_BASE = 'http://localhost:8081/api';

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

  getMe: () => apiFetch('/auth/me'),
  logout: () => apiFetch('/auth/logout', { method: 'POST' }),

  // Doctor Dashboard
  getPerfil: () => apiFetch('/medicos/perfil'),
  
  getStats: () => apiFetch('/medicos/stats'),
  
  actualizarPerfil: (data: any) => 
    apiFetch('/medicos/perfil', { method: 'PUT', body: data }),
  
  getDisponibilidad: () => apiFetch('/medicos/disponibilidad'),
  
  actualizarDisponibilidad: (data: any[]) => 
    apiFetch('/medicos/disponibilidad', { method: 'PUT', body: data as any }),
  
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
  getChatHistorial: (destinatarioId: number | string) => apiFetch(`/chat/historial/${destinatarioId}`),
  getChatCanales: () => apiFetch('/chat/canales'),
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
  getInformes: (pacienteId: number | string) => apiFetch(`/pacientes/${pacienteId}/informes`),
  crearInforme: (pacienteId: number | string, data: { tipoInforme: string; planTrabajo?: string; contenido?: string; nombreArchivo?: string }) => {
    const q = new URLSearchParams();
    q.append('tipoInforme', data.tipoInforme);
    if (data.planTrabajo) q.append('planTrabajo', data.planTrabajo);
    if (data.contenido) q.append('contenido', data.contenido);
    if (data.nombreArchivo) q.append('nombreArchivo', data.nombreArchivo);
    return apiFetch(`/pacientes/${pacienteId}/informes?${q.toString()}`, { method: 'POST' });
  },

  // Patient Portal APIs
  getMisTurnos: () => apiFetch('/pacientes/me/turnos'),
  getMisSeguimientos: () => apiFetch('/pacientes/me/seguimientos'),
  crearMiSeguimiento: (data: { estadoAnimo: string; sintomas: string; notas: string }) => 
    apiFetch('/pacientes/me/seguimientos', { method: 'POST', body: data as any }),
  getMisInformes: () => apiFetch('/pacientes/me/informes'),
  cancelarTurno: (turnoId: number | string) => apiFetch(`/turnos/${turnoId}/cancelar`, { method: 'POST' }),
  actualizarAsistencia: (turnoId: number | string, asistencia: string) => 
    apiFetch(`/turnos/${turnoId}/asistencia?asistencia=${encodeURIComponent(asistencia)}`, { method: 'PUT' }),
  reprogramarTurno: (turnoId: number | string, fecha: string, hora: string) => 
    apiFetch(`/turnos/${turnoId}/reprogramar?fecha=${encodeURIComponent(fecha)}&hora=${encodeURIComponent(hora)}`, { method: 'PUT' })
};
