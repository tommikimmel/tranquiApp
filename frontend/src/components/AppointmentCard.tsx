import { Icon } from './Icon'
import type { Appointment, NavSection } from '../types/dashboard'
import DocumentActions from './DocumentActions'

// `compact` renders the slimmer "upcoming turnos" row (side panel on Inicio); the default
// (non-compact) rendering keeps the fuller boxed row used by the "Diario" calendar tab.
export default function AppointmentCard({ appt, compact, dateLabel, onMarcarDocumentoEnviado, onNavigate, onCancel }: { appt: Appointment; compact?: boolean; dateLabel?: string; onMarcarDocumentoEnviado?: (id: string, archivo: { data: string; nombre: string }) => void; onNavigate?: (section: NavSection, state?: any) => void; onCancel?: (id: string) => void }) {
  const statusMap = {
    confirmed: { label: 'Confirmado', cls: 'badge--success' },
    pending: { label: 'Pago pendiente', cls: 'badge--warning' },
    completed: { label: 'Completado', cls: 'badge--neutral' },
  }
  // Legacy turnos (created before the `modalidad` field existed) fall back to the meetLink
  // heuristic; every turno created since then carries an explicit modalidad from the backend.
  const isOnline = appt.modalidad ? appt.modalidad === 'ONLINE' : !!appt.meetLink
  // Pure document request (receta, certificado, informe) — no consultorio, no videollamada, and
  // its "hour" is just a booking timestamp, not a real scheduled time. See Turno.ocupaAgenda.
  const isDocumentOnly = appt.ocupaAgenda === false
  const isDocumentoEnviado = !!appt.documentoEnviado
  // Computed server-side (TurnoMedicoDto.esReceta) from the real servicioId/tipo, not from the
  // human-readable `type` label — a médico can rename a tariff's label in Honorarios y
  // Servicios, which would silently break a plain string match against "Receta fuera de turno".
  const isReceta = !!appt.esReceta
  // Once a document turno is paid, "Confirmado" doesn't tell the médico anything actionable —
  // whether they still owe the patient the actual file is what matters here.
  const st = (isDocumentOnly && appt.status === 'confirmed')
    ? (isDocumentoEnviado ? { label: 'Documento enviado', cls: 'badge--success' } : { label: 'Documento pendiente', cls: 'badge--warning' })
    : (statusMap[appt.status] || { label: appt.status, cls: 'badge--neutral' })

  // Document-only turnos get their own stacked card (header row + full-width actions row)
  // instead of cramming icon/name/badge/buttons into a single flex row — that's what made the
  // action buttons unreachable/effectively invisible in the narrow "Documentos solicitados"
  // side card. The buttons themselves (and the send-by-mail modal) live in DocumentActions so
  // the "Detalle del Turno" modal (DashboardHome) can render the exact same controls.
  if (isDocumentOnly) {
    return (
      <li className={`appointment-item appointment-item--document ${compact ? 'appointment-item--compact' : ''}`}>
        <div className="document-card__header">
          <span className="document-card__icon"><Icon.FileText size={18} /></span>
          <div className="document-card__info">
            <div className="appointment-item__name">{appt.patientName}</div>
            <span className={`document-card__type-chip ${isReceta ? 'document-card__type-chip--receta' : 'document-card__type-chip--otro'}`}>
              {appt.type}
            </span>
          </div>
          <span className={`badge ${st.cls}`} style={{ fontSize: '9px', flexShrink: 0 }}>{st.label}</span>
        </div>
        {(appt.status === 'confirmed' || appt.status === 'pending') && (
          <div className="document-card__footer">
            {appt.status === 'confirmed' ? (
              <DocumentActions appt={appt} onMarcarDocumentoEnviado={onMarcarDocumentoEnviado} onNavigate={onNavigate} />
            ) : (
              <span style={{ fontSize: '11px', color: 'var(--color-warning)', fontWeight: 'bold' }}>
                Esperando pago
              </span>
            )}
            {!compact && onCancel && (
              <button
                type="button"
                onClick={() => onCancel(appt.id)}
                className="btn btn--ghost btn--sm"
                style={{ color: 'var(--color-danger)', marginLeft: 'auto' }}
              >
                Cancelar
              </button>
            )}
          </div>
        )}
      </li>
    )
  }

  return (
    <li className={`appointment-item ${compact ? 'appointment-item--compact' : ''}`}>
      <div className="appointment-item__time">
        {dateLabel && (
          <div style={{ fontSize: '9px', fontWeight: 'bold', color: 'var(--color-primary)', textTransform: 'uppercase' }}>{dateLabel}</div>
        )}
        <div className="appointment-item__hour">{appt.hour}</div>
        <div className="appointment-item__ampm">{appt.ampm || 'hs'}</div>
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
      <span className={`appointment-item__tag appointment-item__tag--${isOnline ? 'online' : 'presencial'}`}>
        {isOnline ? 'Online' : 'Presencial'}
      </span>
      {!compact && (
        <div className="appointment-item__actions">
          {isOnline && appt.meetLink && appt.status === 'confirmed' && (
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
          {appt.status !== 'completed' && onCancel && (
            <button
              type="button"
              onClick={() => onCancel(appt.id)}
              className="btn btn--ghost btn--sm"
              style={{ color: 'var(--color-danger)' }}
            >
              Cancelar
            </button>
          )}
        </div>
      )}
    </li>
  )
}
