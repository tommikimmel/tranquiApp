const formatDateDDMMYYYY = (dateStr?: string) => {
  if (!dateStr) return '';
  if (dateStr.includes('/')) return dateStr;
  const parts = dateStr.trim().split('T')[0].split('-');
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
  return dateStr;
};

// Patient-side "are you sure?" before cancelling a turno — warns about the 48hs refund policy
// up front so it's never a surprise after confirming. Split out of LandingPage.tsx purely for
// componentization; not lazy-loaded since it depends on no heavy libraries.
export default function CancelTurnoConfirmModal({ appt, onClose, onConfirm }: {
  appt: { patientName: string; fecha: string; hour: string; horaInicio?: string; type: string; status: string }
  onClose: () => void
  onConfirm: () => void
}) {
  // Same 48hs rule enforced server-side in ReembolsoService — computed here purely to warn the
  // patient *before* they confirm, so "no cambia nada en mi plata" doesn't come as a surprise
  // after the fact. `status === 'confirmed'` is the reliable signal the turno was actually paid
  // (PagoWebhookHandler only sets CONFIRMADO after approval).
  const wasPaid = appt.status === 'confirmed'
  let hoursUntilAppt: number | null = null
  if (appt.fecha && appt.hour) {
    const [y, m, d] = appt.fecha.split('-').map(Number)
    const timeStr = appt.horaInicio || `${appt.hour}:00`
    const [hh, mm] = timeStr.split(':').map(Number)
    const apptDate = new Date(y, (m || 1) - 1, d, hh || 0, mm || 0)
    hoursUntilAppt = (apptDate.getTime() - Date.now()) / (1000 * 60 * 60)
  }
  const within48h = hoursUntilAppt !== null && hoursUntilAppt < 48

  return (
    <div className="app-modal-overlay" style={{
      position: 'fixed',
      top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.5)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 10000,
      padding: 'var(--space-4)'
    }}>
      <div className="card app-modal-card" style={{
        maxWidth: '420px',
        width: '100%',
        padding: 'var(--space-6)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)',
        boxShadow: 'var(--shadow-xl)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border)',
        backgroundColor: '#ffffff',
        textAlign: 'center'
      }}>
        <div style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          backgroundColor: 'rgba(239, 68, 68, 0.1)',
          color: 'var(--color-danger)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto var(--space-2)'
        }}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 28, height: 28 }}>
            <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>

        <div>
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
            ¿Cancelar este turno?
          </h3>
          <p style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
            Esta acción no se puede deshacer.
          </p>
        </div>

        <div style={{
          backgroundColor: 'var(--neutral-50)',
          padding: 'var(--space-3) var(--space-4)',
          borderRadius: 'var(--radius-md)',
          border: '1px solid var(--color-border)',
          textAlign: 'left',
          fontSize: 'var(--text-sm)',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px'
        }}>
          <div><strong>Profesional:</strong> {appt.patientName}</div>
          <div><strong>Fecha:</strong> {formatDateDDMMYYYY(appt.fecha)}</div>
          <div><strong>Horario:</strong> {appt.hour} hs</div>
          <div><strong>Modalidad:</strong> {appt.type}</div>
        </div>

        {wasPaid && (
          <div style={{
            backgroundColor: within48h ? '#fef2f2' : '#f0fdf4',
            border: `1px solid ${within48h ? '#fecaca' : '#bbf7d0'}`,
            borderRadius: 'var(--radius-md)',
            padding: 'var(--space-3) var(--space-4)',
            textAlign: 'left',
            fontSize: 'var(--text-sm)',
            color: within48h ? '#991b1b' : '#166534',
            lineHeight: '1.4'
          }}>
            {within48h
              ? <><strong>No corresponde reembolso:</strong> este turno es en menos de 48 horas, así que según la política de cancelación tu pago no se devuelve automáticamente.</>
              : <><strong>Se te reembolsará el pago:</strong> como faltan más de 48 horas para el turno, el dinero se devuelve automáticamente a tu medio de pago original al confirmar la cancelación.</>}
          </div>
        )}

        <div className="app-modal-actions" style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
          <button
            type="button"
            className="btn btn--secondary app-modal-btn"
            onClick={onClose}
            style={{ flex: 1, height: '42px', justifyContent: 'center' }}
          >
            No, mantener
          </button>
          <button
            type="button"
            className="btn btn--danger app-modal-btn"
            onClick={onConfirm}
            style={{ flex: 1, height: '42px', justifyContent: 'center' }}
          >
            Sí, cancelar
          </button>
        </div>
      </div>
    </div>
  )
}
