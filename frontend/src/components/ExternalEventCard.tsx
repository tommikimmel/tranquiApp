import type { ExternalEvent } from '../types/dashboard'

// Compact, read-only counterpart to AppointmentCard for a médico's personal Google Calendar
// events — same row layout so both list types stack cleanly in "Próximos Eventos", but no
// status badge, meet link, or click action since these events aren't editable from the app.
export default function ExternalEventCard({ event, compact, dateLabel }: { event: ExternalEvent; compact?: boolean; dateLabel?: string }) {
  return (
    <li className={`appointment-item appointment-item--external ${compact ? 'appointment-item--compact' : ''}`}>
      <div className="appointment-item__time">
        {dateLabel && (
          <div style={{ fontSize: '9px', fontWeight: 'bold', color: 'var(--color-primary)', textTransform: 'uppercase' }}>{dateLabel}</div>
        )}
        <div className="appointment-item__hour">{event.allDay ? '' : event.hour}</div>
        <div className="appointment-item__ampm">{event.allDay ? 'Todo el día' : 'hs'}</div>
      </div>
      <div className="appointment-item__divider" />
      <div className="appointment-item__info">
        <div className="appointment-item__name">{event.title}</div>
        <div className="appointment-item__meta">Evento personal · Google Calendar</div>
      </div>
    </li>
  )
}
