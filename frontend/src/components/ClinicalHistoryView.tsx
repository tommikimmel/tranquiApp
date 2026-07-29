export default function ClinicalHistoryView() {
  return (
    <div style={{
      padding: 'var(--space-8)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '60vh',
      textAlign: 'center',
      gap: 'var(--space-4)'
    }}>
      <div style={{
        width: '64px',
        height: '64px',
        borderRadius: '50%',
        backgroundColor: 'rgba(0, 166, 80, 0.1)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: 'var(--color-primary, #00a650)'
      }}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 32, height: 32 }}>
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
          <polyline points="10 9 9 9 8 9" />
        </svg>
      </div>
      <h2 style={{ fontSize: 'var(--text-xl)', fontWeight: 'bold', margin: 0, color: 'var(--color-text-primary)' }}>
        Historia Clínica
      </h2>
      <p style={{ color: 'var(--color-text-secondary)', maxWidth: '450px', margin: 0, fontSize: 'var(--text-base)' }}>
        Próximamente - Esta funcionalidad estará disponible en una futura actualización de Tranqui.
      </p>
    </div>
  )
}
