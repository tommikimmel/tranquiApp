import { useState } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

interface TermsAcceptanceModalProps {
  onComplete: (updatedUser: any) => void
  onLogout: () => void
}

// Shown after a Google sign-in creates a new account (getOrCreateUsuario never asks for this),
// while currentUser.requiereAceptarTerminos is true — see App.tsx. Blocks use of the app until
// the person explicitly accepts, same gating pattern as CompleteProfileModal.
export default function TermsAcceptanceModal({ onComplete, onLogout }: TermsAcceptanceModalProps) {
  const { showAlert } = useAlert()
  const [loading, setLoading] = useState(false)

  const handleAccept = async () => {
    setLoading(true)
    try {
      const updatedUser = await api.aceptarTerminos()
      localStorage.setItem('tranqui_user', JSON.stringify(updatedUser))
      onComplete(updatedUser)
    } catch (err: any) {
      showAlert(err.message || 'No se pudo registrar la aceptación. Intentá de nuevo.', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      backgroundColor: 'rgba(0,0,0,0.5)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: 'var(--space-4)',
      overflowY: 'auto'
    }}>
      <div className="card" style={{
        maxWidth: '440px',
        width: '100%',
        padding: 'var(--space-6)',
        boxShadow: 'var(--shadow-lg)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        backgroundColor: 'var(--color-surface)',
        margin: 'auto'
      }}>
        <h3 style={{
          fontFamily: 'var(--font-heading)',
          fontSize: 'var(--text-lg)',
          fontWeight: 'var(--font-weight-bold)',
          color: 'var(--color-text-primary)',
          margin: '0 0 var(--space-2)'
        }}>
          Antes de continuar
        </h3>
        <p style={{
          fontSize: 'var(--text-sm)',
          color: 'var(--color-text-secondary)',
          lineHeight: 'var(--line-height-relaxed)',
          margin: '0 0 var(--space-5)'
        }}>
          Para usar Tranqui App necesitamos que aceptes nuestros{' '}
          <a href="/terminos" target="_blank" rel="noreferrer">Términos y Condiciones</a> y nuestra{' '}
          <a href="/privacidad" target="_blank" rel="noreferrer">Política de Privacidad</a>.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <button type="button" onClick={handleAccept} disabled={loading} className="btn btn--primary" style={{ width: '100%' }}>
            {loading ? 'Guardando...' : 'Acepto y continúo'}
          </button>
          <button
            type="button"
            onClick={onLogout}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--color-text-secondary)',
              fontSize: 'var(--text-xs)',
              cursor: 'pointer',
              textDecoration: 'underline',
              alignSelf: 'center'
            }}
          >
            Cerrar sesión
          </button>
        </div>
      </div>
    </div>
  )
}
