function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
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

// A confirmed (paid) document-only turno isn't waiting on anything scheduled — what matters to
// the patient is whether the médico already sent it. Kept in sync with LandingPage's own
// getTurnoBadge (duplicated rather than shared to keep this extraction self-contained).
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

// "Mis Turnos Reservados" — only real turnos (ocupaAgenda !== false); document-only requests
// (recetas fuera de turno, certificados, informes) live in "Mis Documentos Pendientes" on the
// home page instead, since they have no modalidad/horario to show here.
export default function MisTurnosModal({ appointments, loading, onClose, onCancel }: {
  appointments: any[]
  loading: boolean
  onClose: () => void
  onCancel: (id: number) => void
}) {
  const turnos = appointments.filter(a => a.ocupaAgenda !== false)

  return (
    <div style={{
      position: 'fixed',
      top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.5)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: 'var(--space-4)'
    }}>
      <div className="card" style={{
        maxWidth: '600px',
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
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Mis Turnos Reservados</h3>
          <button onClick={onClose} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
        </div>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}><div className="checkout-spinner" style={{ margin: 'auto' }} /></div>
        ) : turnos.length === 0 ? (
          <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-4)' }}>No tenés turnos programados.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            {turnos.map(appt => {
              const isConfirmed = appt.status === 'confirmed';
              return (
                <div key={appt.id} style={{
                  padding: 'var(--space-3)',
                  borderRadius: 'var(--radius-md)',
                  backgroundColor: isConfirmed ? '#ecfdf5' : '#fffbeb',
                  border: isConfirmed ? '1px solid #a7f3d0' : '1px solid #fef3c7',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 'var(--space-2)'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)' }}>
                      {formatDateDDMMYYYY(appt.fecha)} · {appt.hour} hs
                    </span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                      {(() => { const b = getTurnoBadge(appt); return (
                        <span className={`badge ${b.cls}`} style={{ fontSize: '9px' }}>
                          {b.label}
                        </span>
                      ) })()}
                      {appt.status !== 'completed' && (
                        <button
                          onClick={() => onCancel(appt.id)}
                          style={{
                            border: 'none',
                            background: 'none',
                            cursor: 'pointer',
                            color: 'var(--color-danger)',
                            fontSize: '12px',
                            fontWeight: 'bold'
                          }}
                          title="Cancelar Turno"
                        >
                          Cancelar
                        </button>
                      )}
                    </div>
                  </div>
                  <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'bold' }}>
                    Profesional: {appt.patientName}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                    Modalidad: {(appt.modalidad ? appt.modalidad === 'ONLINE' : !!appt.meetLink) ? 'Online' : 'Presencial'}
                  </div>
                  {appt.domicilioAtencion && (
                    <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <div>Dirección de atención: <strong>{appt.domicilioAtencion}</strong></div>
                      <a
                        href={appt.domicilioLat && appt.domicilioLng
                          ? `https://www.google.com/maps/search/?api=1&query=${appt.domicilioLat},${appt.domicilioLng}`
                          : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(appt.domicilioAtencion)}`
                        }
                        target="_blank"
                        rel="noopener noreferrer"
                        className="btn btn--ghost btn--sm"
                        style={{
                          fontSize: '10px',
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
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 12, height: 12 }}>
                          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                          <circle cx="12" cy="10" r="3" />
                        </svg>
                        Ver dirección en Google Maps
                      </a>
                      {appt.domicilioLat != null && appt.domicilioLng != null && (
                        <iframe
                          title={`Ubicación del consultorio - turno ${appt.id}`}
                          width="100%"
                          height="140"
                          style={{ border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}
                          loading="lazy"
                          referrerPolicy="no-referrer-when-downgrade"
                          src={`https://www.openstreetmap.org/export/embed.html?bbox=${appt.domicilioLng - 0.006}%2C${appt.domicilioLat - 0.004}%2C${appt.domicilioLng + 0.006}%2C${appt.domicilioLat + 0.004}&layer=mapnik&marker=${appt.domicilioLat}%2C${appt.domicilioLng}`}
                        />
                      )}
                    </div>
                  )}
                  {(appt.modalidad ? appt.modalidad === 'ONLINE' : !!appt.meetLink) && appt.meetLink && isConfirmed && (
                    <a
                      href={appt.meetLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn--primary"
                      style={{ fontSize: '11px', padding: 'var(--space-2) var(--space-4)', width: 'fit-content', display: 'flex', gap: '4px', alignItems: 'center', marginTop: 'var(--space-1)' }}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 14, height: 14 }}><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" ry="2" /></svg>
                      Unirse a la videollamada
                    </a>
                  )}
                  {!isConfirmed && appt.checkoutUrl && (
                    <a
                      href={appt.checkoutUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn"
                      style={{
                        fontSize: '11px',
                        padding: 'var(--space-2) var(--space-5)',
                        width: 'fit-content',
                        display: 'flex',
                        gap: '6px',
                        alignItems: 'center',
                        marginTop: 'var(--space-1)',
                        textDecoration: 'none',
                        backgroundColor: '#009fe3',
                        color: 'white',
                        borderColor: '#009fe3',
                        fontWeight: 'bold',
                        borderRadius: 'var(--radius-md)',
                        boxShadow: '0 2px 4px rgba(0,158,227,0.15)'
                      }}
                    >
                      Pagar Turno
                    </a>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  )
}
