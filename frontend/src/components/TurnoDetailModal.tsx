import { formatDetalleDomicilio } from '../utils/dashboardHelpers'

function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function IconCalendar({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  )
}

function IconLocationPin({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <path d="M12 21s-7-6.1-7-11a7 7 0 0 1 14 0c0 4.9-7 11-7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

function IconVideoCam({ size = 15 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="2" y="6" width="14" height="12" rx="2" />
      <path d="M16 10l6-3v10l-6-3" />
    </svg>
  )
}

const formatDateDDMMYYYY = (dateStr?: string) => {
  if (!dateStr) return '';
  if (dateStr.includes('/')) return dateStr;
  const parts = dateStr.trim().split('T')[0].split('-');
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
  return dateStr;
};

const getTurnoBadge = (appt: { status?: string; ocupaAgenda?: boolean; documentoEnviado?: boolean }) => {
  const isConfirmed = appt.status === 'confirmed';
  if (isConfirmed && appt.ocupaAgenda === false) {
    return appt.documentoEnviado
      ? { label: 'Documento enviado', cls: 'badge--success' }
      : { label: 'Documento pendiente', cls: 'badge--warning' };
  }
  return isConfirmed
    ? { label: 'Confirmado', cls: 'badge--success' }
    : { label: 'Pendiente', cls: 'badge--warning' };
};

// Opened by clicking a row in "Mis Próximos Turnos" / "Mis Documentos Pendientes" on the home
// page. Handles both a real turno and a document-only one (recetas fuera de turno, certificados,
// informes) — those have no modalidad/horario/domicilio, only a booking timestamp.
export default function TurnoDetailModal({ appt, onClose, onCancel }: {
  appt: any
  onClose: () => void
  onCancel: (id: number) => void
}) {
  const isConfirmed = appt.status === 'confirmed';
  const isDocumentOnly = appt.ocupaAgenda === false;
  const isOnline = appt.modalidad ? appt.modalidad === 'ONLINE' : !!appt.meetLink;
  const detalleDomicilio = formatDetalleDomicilio(appt);

  return (
    <div
      style={{
        position: 'fixed',
        top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: 'rgba(0,0,0,0.5)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 'var(--space-4)'
      }}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="card" style={{
        maxWidth: '480px',
        width: '100%',
        maxHeight: '85vh',
        overflowY: 'auto',
        position: 'relative',
        padding: 'var(--space-6)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Detalle del turno</h3>
          <button onClick={onClose} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <span style={{ color: 'var(--color-text-secondary)' }}><IconCalendar size={18} /></span>
            <span style={{ fontSize: 'var(--text-md)', fontWeight: 'bold' }}>
              {isDocumentOnly ? `Solicitado el ${formatDateDDMMYYYY(appt.fecha)}` : `${formatDateDDMMYYYY(appt.fecha)} · ${appt.hour} hs`}
            </span>
            {(() => { const b = getTurnoBadge(appt); return (
              <span className={`badge ${b.cls}`} style={{ fontSize: '11px', marginLeft: 'auto' }}>
                {b.label}
              </span>
            ) })()}
          </div>

          <div style={{ fontSize: 'var(--text-sm)' }}>
            <div>Profesional: <strong>{appt.patientName}</strong></div>
            <div style={{ color: 'var(--color-text-secondary)', marginTop: '2px' }}>{appt.type}</div>
            {!isDocumentOnly && (
              <div style={{ color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                Modalidad: {isOnline ? 'Online (videollamada)' : 'Presencial'}
              </div>
            )}
          </div>

          {!isDocumentOnly && !isOnline && appt.domicilioAtencion && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ fontSize: 'var(--text-sm)' }}>
                <div>Dirección de atención: <strong>{appt.domicilioAtencion}</strong></div>
                {detalleDomicilio && (
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>{detalleDomicilio}</div>
                )}
              </div>
              <a
                href={appt.domicilioLat && appt.domicilioLng
                  ? `https://www.google.com/maps/search/?api=1&query=${appt.domicilioLat},${appt.domicilioLng}`
                  : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(appt.domicilioAtencion)}`
                }
                target="_blank"
                rel="noopener noreferrer"
                className="btn btn--ghost btn--sm"
                style={{
                  fontSize: '11px',
                  padding: 'var(--space-1) var(--space-2)',
                  width: 'fit-content',
                  display: 'inline-flex',
                  gap: '4px',
                  alignItems: 'center',
                  border: '1px solid var(--color-border)',
                  backgroundColor: 'var(--color-surface)',
                  color: 'var(--color-primary)',
                  fontWeight: 'bold'
                }}
              >
                <IconLocationPin size={13} />
                Ver dirección en Google Maps
              </a>
              {appt.domicilioLat != null && appt.domicilioLng != null && (
                <iframe
                  title={`Ubicación del consultorio - turno ${appt.id}`}
                  width="100%"
                  height="180"
                  style={{ border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}
                  loading="lazy"
                  referrerPolicy="no-referrer-when-downgrade"
                  src={`https://www.openstreetmap.org/export/embed.html?bbox=${appt.domicilioLng - 0.006}%2C${appt.domicilioLat - 0.004}%2C${appt.domicilioLng + 0.006}%2C${appt.domicilioLat + 0.004}&layer=mapnik&marker=${appt.domicilioLat}%2C${appt.domicilioLng}`}
                />
              )}
            </div>
          )}

          {!isDocumentOnly && isOnline && appt.meetLink && isConfirmed && (
            <a
              href={appt.meetLink}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn--primary"
              style={{ fontSize: '13px', padding: 'var(--space-2) var(--space-4)', width: 'fit-content', display: 'flex', gap: '6px', alignItems: 'center' }}
            >
              <IconVideoCam size={15} /> Unirse a la videollamada
            </a>
          )}

          {appt.checkoutUrl && !isConfirmed && (
            <a
              href={appt.checkoutUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn"
              style={{
                fontSize: '13px',
                padding: 'var(--space-2) var(--space-5)',
                width: 'fit-content',
                display: 'flex',
                gap: '6px',
                alignItems: 'center',
                textDecoration: 'none',
                backgroundColor: '#009fe3',
                color: 'white',
                borderColor: '#009fe3',
                fontWeight: 'bold',
                borderRadius: 'var(--radius-md)'
              }}
            >
              {isDocumentOnly ? 'Pagar' : (appt.type === 'Copago OSDE' || appt.type === 'Obra Social' ? 'Pagar Copago' : 'Pagar Consulta')}
            </a>
          )}

          {appt.status !== 'completed' && (
            <button
              onClick={() => { onClose(); onCancel(appt.id) }}
              style={{
                background: 'transparent',
                border: '1px solid var(--color-border)',
                color: 'var(--color-danger)',
                fontSize: 'var(--text-xs)',
                fontWeight: 600,
                padding: '8px 14px',
                borderRadius: 'var(--radius-md)',
                cursor: 'pointer',
                width: 'fit-content'
              }}
            >
              Cancelar turno
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
