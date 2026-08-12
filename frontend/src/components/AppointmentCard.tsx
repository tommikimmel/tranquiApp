import { Icon } from './Icon'
import type { Appointment } from '../types/dashboard'

// `compact` renders the slimmer "upcoming turnos" row (side panel on Inicio); the default
// (non-compact) rendering keeps the fuller boxed row used by the "Diario" calendar tab.
export default function AppointmentCard({ appt, compact, dateLabel }: { appt: Appointment; compact?: boolean; dateLabel?: string }) {
  const statusMap = {
    confirmed: { label: 'Confirmado', cls: 'badge--success' },
    pending: { label: 'Pago pendiente', cls: 'badge--warning' },
    completed: { label: 'Completado', cls: 'badge--neutral' },
  }
  const st = statusMap[appt.status] || { label: appt.status, cls: 'badge--neutral' }
  // Legacy turnos (created before the `modalidad` field existed) fall back to the meetLink
  // heuristic; every turno created since then carries an explicit modalidad from the backend.
  const isOnline = appt.modalidad ? appt.modalidad === 'ONLINE' : !!appt.meetLink
  // Pure document request (receta, certificado, informe) — no consultorio, no videollamada, and
  // its "hour" is just a booking timestamp, not a real scheduled time. See Turno.ocupaAgenda.
  const isDocumentOnly = appt.ocupaAgenda === false

  return (
    <li className={`appointment-item ${compact ? 'appointment-item--compact' : ''}`}>
      <div className="appointment-item__time">
        {dateLabel && (
          <div style={{ fontSize: '9px', fontWeight: 'bold', color: 'var(--color-primary)', textTransform: 'uppercase' }}>{dateLabel}</div>
        )}
        {isDocumentOnly ? (
          <Icon.FileText size={18} />
        ) : (
          <>
            <div className="appointment-item__hour">{appt.hour}</div>
            <div className="appointment-item__ampm">{appt.ampm || 'hs'}</div>
          </>
        )}
      </div>
      <div className="appointment-item__divider" />
      <div className="appointment-item__info">
        <div className="appointment-item__name">{appt.patientName}</div>
        <div className="appointment-item__meta">
          {appt.type}
          {!compact && (
            <>
              {' · '}<span className={`badge ${st.cls}`} style={{ fontSize: '9px', padding: '1px 6px' }}>{st.label}</span>
            </>
          )}
        </div>
      </div>
      {isDocumentOnly ? (
        <span className="appointment-item__tag appointment-item__tag--presencial">Documento</span>
      ) : (
        <span className={`appointment-item__tag appointment-item__tag--${isOnline ? 'online' : 'presencial'}`}>
          {isOnline ? 'Online' : 'Presencial'}
        </span>
      )}
      {!compact && (
        <div className="appointment-item__actions">
          {!isDocumentOnly && isOnline && appt.meetLink && appt.status === 'confirmed' && (
            <a
              href={appt.meetLink}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn--primary btn--sm"
              style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-1)' }}
            >
              <Icon.Video /> Unirse
            </a>
          )}
          {appt.status === 'pending' && (
            <span style={{ fontSize: '11px', color: 'var(--color-warning)', fontWeight: 'bold' }}>
              Esperando pago
            </span>
          )}
        </div>
      )}
    </li>
  )
}
