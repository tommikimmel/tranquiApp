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
    tipo: 'PARTICULAR' | 'OSDE'
    metadataAfiliado?: string
    nombrePaciente: string
    emailPaciente: string
    telefonoPaciente: string
  }) => apiFetch('/turnos/reservar', { method: 'POST', body: data as any }),

  // Auth / Login with Google
  loginGoogle: (idToken: string) => 
    apiFetch('/auth/google', { method: 'POST', body: { idToken } as any }),

  // Doctor Dashboard
  getPerfil: () => apiFetch('/medicos/perfil'),
  
  getStats: () => apiFetch('/medicos/stats'),
  
  actualizarPerfil: (data: any) => 
    apiFetch('/medicos/perfil', { method: 'PUT', body: data }),
  
  getDisponibilidad: () => apiFetch('/medicos/disponibilidad'),
  
  actualizarDisponibilidad: (data: any[]) => 
    apiFetch('/medicos/disponibilidad', { method: 'PUT', body: data as any }),
  
  getTurnosHoy: () => apiFetch('/medicos/turnos/hoy'),
  
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
};
