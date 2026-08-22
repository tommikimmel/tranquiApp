import React from 'react'
import { Icon } from '../components/Icon'

// ── Helper to convert professional name to SEO-friendly slug ────
export function getDoctorSlug(name: string): string {
  if (!name) return ''
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(new RegExp('[\\u0300-\\u036f]', 'g'), "")
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

// Some backend DTOs serialize LocalDate as dd/MM/yyyy directly instead of ISO (see backend's
// DateConfig.java — MedicoDto.fechaNacimiento passes the raw LocalDate through Jackson's global
// custom serializer, unlike PacienteDto/TurnoMedicoDto which convert to ISO via .toString()
// first). Normalize to ISO right when reading from the API so every date input/validation in
// the app can keep assuming ISO internally, regardless of which format that particular field
// happens to arrive in.
export function normalizeToIsoDate(value: string | null | undefined): string {
  if (!value) return ''
  const ddmm = value.match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (ddmm) return `${ddmm[3]}-${ddmm[2]}-${ddmm[1]}`
  return value
}

// Maps a persisted Notificacion's `tipo` (backend/.../NotificacionService.crearNotificacion
// callers) to the icon + accent color shown in the notification bell dropdown.
export function getNotificationVisual(tipo: string | undefined): { Icon: (props: { size?: number }) => React.JSX.Element; color: string } {
  switch (tipo) {
    case 'TURNO_RESERVADO':
    case 'TURNO_CONFIRMADO':
      return { Icon: Icon.CalendarCheck, color: 'var(--color-success)' }
    case 'TURNO_CANCELADO':
      return { Icon: Icon.CalendarX, color: 'var(--color-danger)' }
    case 'SEGUIMIENTO':
      return { Icon: Icon.Activity, color: '#3b82f6' }
    case 'INFORME':
      return { Icon: Icon.FileText, color: '#8b5cf6' }
    case 'DOCUMENTO_PENDIENTE':
      return { Icon: Icon.FileText, color: 'var(--color-warning)' }
    case 'DOCUMENTO_ENVIADO':
      return { Icon: Icon.FileText, color: 'var(--color-success)' }
    case 'NUEVO_MENSAJE':
      return { Icon: Icon.MessageCircle, color: 'var(--color-primary)' }
    case 'TICKET_NUEVO':
    case 'TICKET_RESPUESTA':
      return { Icon: Icon.MessageCircle, color: '#8b5cf6' }
    default:
      return { Icon: Icon.BellSimple, color: 'var(--color-primary)' }
  }
}

export const getPatientInitials = (fullName: string) => {
  const parts = (fullName || '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
};

export const calcAge = (fechaNacimiento?: string | null) => {
  if (!fechaNacimiento) return null;
  const birth = new Date(fechaNacimiento);
  if (isNaN(birth.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
  return age >= 0 && age < 130 ? age : null;
};

// Builds a "Torre X, Piso Y, Depto Z, Barrio W" secondary line for a consultation address,
// skipping whichever of the 4 optional fields the médico didn't fill in.
export const formatDetalleDomicilio = (info: {
  domicilioAtencionTorre?: string | null
  domicilioAtencionPiso?: string | null
  domicilioAtencionDepto?: string | null
  domicilioAtencionBarrio?: string | null
}): string => {
  const partes: string[] = []
  // Strip a redundant leading "Torre" in case it was saved with the old placeholder ("Ej: Torre B")
  // that encouraged médicos to type the word twice.
  const torre = info.domicilioAtencionTorre?.replace(/^torre\s+/i, '')
  if (torre) partes.push(`Torre ${torre}`)
  if (info.domicilioAtencionPiso) partes.push(`Piso ${info.domicilioAtencionPiso}`)
  if (info.domicilioAtencionDepto) partes.push(`Depto ${info.domicilioAtencionDepto}`)
  if (info.domicilioAtencionBarrio) partes.push(`Barrio ${info.domicilioAtencionBarrio}`)
  return partes.join(' · ')
}

export const formatDateDDMMYYYY = (dateVal: any) => {
  if (!dateVal) return 'Sin fecha';
  if (typeof dateVal === 'string') {
    if (dateVal.includes('T')) {
      const parts = dateVal.split('T')[0].split('-');
      if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    if (dateVal.includes('-')) {
      const parts = dateVal.split('-');
      if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
  }
  return String(dateVal);
};
