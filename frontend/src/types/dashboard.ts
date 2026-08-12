export interface Appointment {
  id: string
  patientName: string
  hour: string
  ampm: string
  type: string
  status: 'confirmed' | 'pending' | 'completed'
  meetLink: string
  modalidad?: 'PRESENCIAL' | 'ONLINE'
  // False for pure document services (recetas, certificados, informes) — the booking has no real
  // scheduled time, so views that bucket/sort by hour must exclude these. See Turno.ocupaAgenda.
  ocupaAgenda?: boolean
}

// A médico's personal Google Calendar event (read-only, never a Turno) — see
// GoogleCalendarSyncService.obtenerEventosExternosCacheados on the backend.
export interface ExternalEvent {
  id: string
  title: string
  fecha: string
  hour: string
  endHour: string
  allDay: boolean
}

export type NavSection = 'dashboard' | 'agenda' | 'patients' | 'clinical-history' | 'prescriptions' | 'visitors' | 'payments' | 'settings'
