import { Icon } from './Icon'
import type { Appointment } from '../types/dashboard'

// `compact` renders the slimmer "upcoming turnos" row (side panel on Inicio); the default
// (non-compact) rendering keeps the fuller boxed row used by the "Diario" calendar tab.
export default function AppointmentCard({ appt, compact, dateLabel, onMarcarDocumentoEnviado }: { appt: Appointment; compact?: boolean; dateLabel?: string; onMarcarDocumentoEnviado?: (id: string) => void }) {
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
  // Once a document turno is paid, "Confirmado" doesn't tell the médico anything actionable —
  // whether they still owe the patient the actual file is what matters here.
  const st = (isDocumentOnly && appt.status === 'confirmed')
    ? (isDocumentoEnviado ? { label: 'Documento enviado', cls: 'badge--success' } : { label: 'Documento pendiente', cls: 'badge--warning' })
    : (statusMap[appt.status] || { label: appt.status, cls: 'badge--neutral' })

  const patientEmail = appt.patientInfo?.email
  const patientPhoneDigits = (appt.patientInfo?.telefono || '').replace(/[^\d]/g, '')
  const emailHref = patientEmail
    ? `mailto:${patientEmail}?subject=${encodeURIComponent('Tu ' + appt.type)}&body=${encodeURIComponent(`Hola ${appt.patientName}, te enviamos adjunto tu ${appt.type.toLowerCase()}. Saludos.`)}`
    : undefined
  const whatsappHref = patientPhoneDigits
    ? `https://wa.me/${patientPhoneDigits}?text=${encodeURIComponent(`Hola ${appt.patientName}, te enviamos tu ${appt.type.toLowerCase()} adjunto en este mensaje.`)}`
    : undefined

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
        <span className="appointment-item__tag appointment-item__tag--presencial">
          {appt.status === 'confirmed' ? st.label : 'Documento'}
        </span>
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
      {/* Not gated by `!compact`: this is exactly what the (compact) "Documentos solicitados"
          card on Inicio needs to let the médico act on a paid document request. */}
      {isDocumentOnly && appt.status === 'confirmed' && (
        <div className="appointment-item__actions" style={{ flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {emailHref && (
            <a href={emailHref} className="btn btn--ghost btn--sm" title="Enviar por email" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <Icon.Mail size={14} /> Email
            </a>
          )}
          {whatsappHref && (
            <a href={whatsappHref} target="_blank" rel="noopener noreferrer" className="btn btn--ghost btn--sm" title="Enviar por WhatsApp" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <Icon.MessageCircle size={14} /> WhatsApp
            </a>
          )}
          {!isDocumentoEnviado && onMarcarDocumentoEnviado && (
            <button
              type="button"
              onClick={() => onMarcarDocumentoEnviado(appt.id)}
              className="btn btn--primary btn--sm"
            >
              Documento enviado
            </button>
          )}
        </div>
      )}
    </li>
  )
}
