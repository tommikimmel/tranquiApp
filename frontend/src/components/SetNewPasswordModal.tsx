import { useState } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

interface SetNewPasswordModalProps {
  onComplete: (updatedUser: any) => void
  onLogout: () => void
}

// Shown while currentUser.mustChangePassword is true — an admin reset this account's password
// and emailed a temporary one (AdminController#resetPassword). Blocks use of the app until a
// real password is set, same gating pattern as TermsAcceptanceModal/CompleteProfileModal (see
// App.tsx). Doesn't ask for the temporary password itself: reaching this screen already proves
// the person has it (they just logged in with it).
export default function SetNewPasswordModal({ onComplete, onLogout }: SetNewPasswordModalProps) {
  const { showAlert } = useAlert()
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const passwordValida = newPassword.length >= 8 && /[A-Z]/.test(newPassword) && /[a-z]/.test(newPassword) && /[0-9]/.test(newPassword)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!passwordValida) {
      showAlert('La contraseña debe tener al menos 8 caracteres, con una mayúscula, una minúscula y un número.', 'error')
      return
    }
    if (newPassword !== confirmPassword) {
      showAlert('Las contraseñas no coinciden.', 'error')
      return
    }
    setLoading(true)
    try {
      const updatedUser = await api.setNewPassword(newPassword)
      localStorage.setItem('tranqui_user', JSON.stringify(updatedUser))
      showAlert('Contraseña actualizada correctamente.', 'success')
      onComplete(updatedUser)
    } catch (err: any) {
      showAlert(err.message || 'No se pudo actualizar la contraseña. Intentá de nuevo.', 'error')
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
          Elegí tu nueva contraseña
        </h3>
        <p style={{
          fontSize: 'var(--text-sm)',
          color: 'var(--color-text-secondary)',
          lineHeight: 'var(--line-height-relaxed)',
          margin: '0 0 var(--space-5)'
        }}>
          Tu contraseña fue reseteada por un administrador. Elegí una nueva antes de continuar usando Tranqui App.
        </p>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div>
            <label style={{ display: 'block', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}>
              Contraseña nueva
            </label>
            <input
              type="password"
              className="form-input"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Mínimo 8 caracteres, mayúscula, minúscula y número"
              autoFocus
              style={{ width: '100%' }}
            />
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}>
              Repetir contraseña nueva
            </label>
            <input
              type="password"
              className="form-input"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
            <button type="submit" disabled={loading} className="btn btn--primary" style={{ width: '100%' }}>
              {loading ? 'Guardando...' : 'Guardar y continuar'}
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
        </form>
      </div>
    </div>
  )
}
