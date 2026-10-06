import type { ReactNode } from 'react'

// Confirmación genérica con la misma estética que CancelTurnoConfirmModal — reemplaza a
// window.confirm, que no respeta el diseño de la app, no se puede leer bien en mobile y no
// admite formato (fechas destacadas, avisos).
export default function ConfirmDialog({
  title,
  children,
  confirmLabel,
  cancelLabel = 'Volver',
  danger = false,
  busy = false,
  onConfirm,
  onClose,
}: {
  title: string
  children?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <div
      className="app-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
      style={{
        position: 'fixed',
        top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: 'rgba(0,0,0,0.5)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10000,
        padding: 'var(--space-4)',
      }}
      onClick={(e) => { if (e.target === e.currentTarget && !busy) onClose() }}
    >
      <div className="card app-modal-card" style={{
        maxWidth: '440px',
        width: '100%',
        padding: 'var(--space-6)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)',
        boxShadow: 'var(--shadow-xl)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border)',
        backgroundColor: '#ffffff',
        textAlign: 'center',
      }}>
        <div style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          backgroundColor: danger ? 'rgba(239, 68, 68, 0.1)' : 'rgba(217, 119, 6, 0.1)',
          color: danger ? 'var(--color-danger)' : '#d97706',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto var(--space-2)',
        }}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 28, height: 28 }} aria-hidden="true">
            <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>

        <h3 id="confirm-dialog-title" style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
          {title}
        </h3>

        {children && (
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5, textAlign: 'left' }}>
            {children}
          </div>
        )}

        <div className="app-modal-actions" style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
          <button
            type="button"
            className="btn btn--secondary app-modal-btn"
            onClick={onClose}
            disabled={busy}
            style={{ flex: 1, height: '42px', justifyContent: 'center' }}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`btn ${danger ? 'btn--danger' : 'btn--primary'} app-modal-btn`}
            onClick={onConfirm}
            disabled={busy}
            style={{ flex: 1, height: '42px', justifyContent: 'center' }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
