import { useState } from 'react'
import { api } from '../api/api'
import DateInputDDMMYYYY from './DateInputDDMMYYYY'
import { useAlert } from '../context/AlertContext'

interface CompleteProfileModalProps {
  user: any
  onComplete: (updatedUser: any) => void
  onLogout: () => void
}

// Shown on the main screen after a Google sign-in, which only gives us email+nombre.
// The account stays gated (perfilCompleto=false, see Usuario.java) until this Paso 2 data
// is filled in and saved through /auth/complete-profile.
export default function CompleteProfileModal({ user, onComplete, onLogout }: CompleteProfileModalProps) {
  const { showAlert } = useAlert()
  const [loading, setLoading] = useState(false)

  const [nombre, setNombre] = useState(user?.nombre || '')
  const [apellido, setApellido] = useState('')
  const [sexo, setSexo] = useState('M')
  const [fechaNacimiento, setFechaNacimiento] = useState('')
  const [tipoDocumento, setTipoDocumento] = useState('DNI')
  const [numeroDocumento, setNumeroDocumento] = useState('')
  const [telefono, setTelefono] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!nombre.trim() || !apellido.trim() || !fechaNacimiento || !numeroDocumento.trim() || !telefono.trim()) {
      showAlert('Por favor, completá todos los campos.', 'warning')
      return
    }
    setLoading(true)
    try {
      const updatedUser = await api.completeProfile({
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        sexo,
        fechaNacimiento,
        tipoDocumento,
        numeroDocumento,
        telefono,
      })
      localStorage.setItem('tranqui_user', JSON.stringify(updatedUser))
      onComplete(updatedUser)
    } catch (err: any) {
      showAlert(err.message || 'No se pudo guardar tu perfil. Intentá de nuevo.', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app-modal-overlay" style={{
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
      <div className="card app-modal-card" style={{
        maxWidth: '480px',
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
          ¡Ya casi terminamos!
        </h3>
        <p style={{
          fontSize: 'var(--text-sm)',
          color: 'var(--color-text-secondary)',
          lineHeight: 'var(--line-height-relaxed)',
          margin: '0 0 var(--space-5)'
        }}>
          Con tu cuenta de Google solo obtuvimos tu email. Completá estos datos para terminar de crear tu cuenta como paciente.
        </p>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div className="modal-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
            <div className="form-group">
              <label className="form-label form-label--required">Nombre</label>
              <input
                type="text"
                className="form-input"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label form-label--required">Apellido</label>
              <input
                type="text"
                className="form-input"
                value={apellido}
                onChange={(e) => setApellido(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="modal-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
            <div className="form-group">
              <label className="form-label form-label--required">Sexo</label>
              <select className="form-select" value={sexo} onChange={(e) => setSexo(e.target.value)}>
                <option value="M">Masculino</option>
                <option value="F">Femenino</option>
                <option value="X">Otro / No especifica</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label form-label--required">Fecha de Nacimiento (DD/MM/AAAA)</label>
              <DateInputDDMMYYYY
                className="form-input"
                min="1900-01-01"
                max={new Date().toISOString().split('T')[0]}
                value={fechaNacimiento}
                onChange={setFechaNacimiento}
                required
              />
            </div>
          </div>

          <div className="modal-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 'var(--space-3)' }}>
            <div className="form-group">
              <label className="form-label form-label--required">Documento</label>
              <select className="form-select" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
                <option value="DNI">DNI</option>
                <option value="PASAPORTE">Pasaporte</option>
                <option value="LC">LC</option>
                <option value="LE">LE</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label form-label--required">Número</label>
              <input
                type="text"
                inputMode="numeric"
                maxLength={8}
                className="form-input"
                placeholder="12345678"
                value={numeroDocumento}
                onChange={(e) => setNumeroDocumento(e.target.value.replace(/[^\d]/g, '').slice(0, 8))}
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label form-label--required">Teléfono móvil</label>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <span style={{
                padding: '10px 12px',
                backgroundColor: 'var(--color-bg-secondary, #f0f4f1)',
                border: '1px solid var(--color-border)',
                borderRight: 'none',
                borderRadius: 'var(--radius-md) 0 0 var(--radius-md)',
                fontWeight: 'bold',
                fontSize: '14px',
                color: 'var(--color-text-secondary, #555)',
                userSelect: 'none',
                display: 'flex',
                alignItems: 'center'
              }}>
                +54
              </span>
              <input
                type="tel"
                className="form-input"
                placeholder="1112345678"
                maxLength={11}
                value={telefono.replace(/^\+54\s*/, '')}
                onChange={(e) => setTelefono(e.target.value.replace(/[^\d]/g, '').slice(0, 11))}
                style={{ borderRadius: '0 var(--radius-md) var(--radius-md) 0' }}
                required
              />
            </div>
          </div>

          <button type="submit" disabled={loading} className="btn btn--primary app-modal-btn" style={{ width: '100%', marginTop: 'var(--space-2)' }}>
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
        </form>
      </div>
    </div>
  )
}
