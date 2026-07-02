import { useState } from 'react'
import { api } from './api'

interface LoginPageProps {
  onLoginSuccess: (user: any) => void
  onBack: () => void
}

export default function LoginPage({ onLoginSuccess, onBack }: LoginPageProps) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  // Dev simulation states
  const [showDevOptions] = useState(true)
  const [devEmail, setDevEmail] = useState('paula@tranqui.com')
  const [customEmail, setCustomEmail] = useState('')

  const handleGoogleLogin = async (token: string) => {
    setLoading(true)
    setError(null)
    try {
      const user = await api.loginGoogle(token)
      onLoginSuccess(user)
    } catch (err: any) {
      console.error('Error de autenticación:', err)
      setError(err.message || 'No se pudo iniciar sesión. Por favor, intentá de nuevo.')
    } finally {
      setLoading(false)
    }
  }

  const triggerRealGoogleLogin = () => {
    setError('La integración con Google OAuth requiere configurar CLIENT_ID de producción. Usá el selector de desarrollo a continuación para probar la aplicación localmente.')
  }

  const handleSimulatedLogin = () => {
    const emailToUse = devEmail === 'custom' ? customEmail : devEmail
    if (!emailToUse || !emailToUse.includes('@')) {
      setError('Por favor, ingresá un email válido para simular.')
      return
    }
    handleGoogleLogin(`mock-${emailToUse}`)
  }

  return (
    <div className="login-container" style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: 'var(--color-bg)',
      padding: 'var(--space-4)'
    }}>
      <div className="card" style={{
        maxWidth: '440px',
        width: '100%',
        padding: 'var(--space-8)',
        boxShadow: 'var(--shadow-lg)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        backgroundColor: 'var(--color-surface)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-6)'
      }}>
        {/* Header / Logo */}
        <div style={{ textAlign: 'center' }}>
          <img 
            src="/logo-tranqui.png" 
            alt="Tranqui Logo" 
            style={{ 
              height: '48px', 
              margin: '0 auto var(--space-4)',
              display: 'block'
            }} 
          />
          <h2 style={{ 
            fontFamily: 'var(--font-heading)',
            fontSize: 'var(--text-xl)',
            fontWeight: 'var(--font-weight-bold)',
            color: 'var(--color-text-primary)',
            marginBottom: 'var(--space-1)'
          }}>
            Ingreso de Profesionales
          </h2>
          <p style={{ 
            fontSize: 'var(--text-sm)',
            color: 'var(--color-text-secondary)'
          }}>
            Gestioná tus turnos, agenda y pacientes en un solo lugar.
          </p>
        </div>

        {error && (
          <div style={{
            padding: 'var(--space-3) var(--space-4)',
            backgroundColor: 'var(--color-error-bg)',
            color: 'var(--color-error)',
            borderRadius: 'var(--radius-sm)',
            fontSize: 'var(--text-sm)',
            border: '1px solid #fecaca',
            lineHeight: 'var(--line-height-normal)'
          }}>
            {error}
          </div>
        )}

        {/* Real Google Button */}
        <button
          onClick={triggerRealGoogleLogin}
          disabled={loading}
          className="btn"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 'var(--space-3)',
            backgroundColor: 'var(--neutral-0)',
            color: 'var(--neutral-700)',
            border: '1px solid var(--neutral-300)',
            borderRadius: 'var(--radius-md)',
            padding: 'var(--space-3)',
            fontSize: 'var(--text-base)',
            fontWeight: 'var(--font-weight-medium)',
            cursor: 'pointer',
            transition: 'background-color 0.2s',
            boxShadow: 'var(--shadow-sm)'
          }}
          onMouseOver={(e) => (e.currentTarget.style.backgroundColor = 'var(--neutral-50)')}
          onMouseOut={(e) => (e.currentTarget.style.backgroundColor = 'var(--neutral-0)')}
        >
          <svg width="18" height="18" viewBox="0 0 18 18">
            <path fill="#4285F4" d="M17.64 9.2c0-.63-.06-1.25-.16-1.84H9v3.5h4.84c-.21 1.12-.84 2.07-1.79 2.7l2.8 2.17c1.63-1.51 2.58-3.73 2.58-6.39z"/>
            <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.8-2.17c-.78.52-1.78.83-3.16.83-2.43 0-4.49-1.64-5.22-3.85l-2.9 2.24C2.35 15.52 5.4 18 9 18z"/>
            <path fill="#FBBC05" d="M3.78 10.63c-.19-.58-.3-1.2-.3-1.83s.11-1.25.3-1.83l-2.9-2.24C.31 5.96 0 7.45 0 9s.31 3.04.88 4.27l2.9-2.24z"/>
            <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.47.9 11.43 0 9 0 5.4 0 2.35 2.48.88 5.13l2.9 2.24c.73-2.21 2.79-3.85 5.22-3.85z"/>
          </svg>
          {loading ? 'Iniciando sesión...' : 'Iniciar sesión con Google'}
        </button>

        {/* Development Bypass Card */}
        {showDevOptions && (
          <div style={{
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)',
            padding: 'var(--space-4)',
            backgroundColor: 'var(--neutral-50)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-3)'
          }}>
            <div style={{
              fontSize: 'var(--text-xs)',
              fontWeight: 'var(--font-weight-bold)',
              color: 'var(--color-primary)',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              ⚙️ Entorno de Desarrollo (Simulación)
            </div>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <label htmlFor="dev-role-select" style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-medium)' }}>
                Seleccionar perfil de prueba:
              </label>
              <select
                id="dev-role-select"
                value={devEmail}
                onChange={(e) => setDevEmail(e.target.value)}
                style={{
                  padding: 'var(--space-2)',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--color-border)',
                  backgroundColor: 'var(--neutral-0)',
                  fontFamily: 'var(--font-body)',
                  fontSize: 'var(--text-sm)',
                  color: 'var(--color-text-primary)'
                }}
              >
                <option value="paula@tranqui.com">Lic. María Paula Rossi (Profesional / Psiquiatra)</option>
                <option value="valentina.m@gmail.com">Valentina Moreno (Paciente Demo)</option>
                <option value="matias.r@gmail.com">Matías Rodríguez (Paciente Demo)</option>
                <option value="custom">Ingresar otro email...</option>
              </select>
            </div>

            {devEmail === 'custom' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
                <label htmlFor="custom-email-input" style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Email personalizado:
                </label>
                <input
                  id="custom-email-input"
                  type="email"
                  placeholder="ejemplo@correo.com"
                  value={customEmail}
                  onChange={(e) => setCustomEmail(e.target.value)}
                  style={{
                    padding: 'var(--space-2)',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--color-border)',
                    fontSize: 'var(--text-sm)',
                    fontFamily: 'var(--font-body)'
                  }}
                />
              </div>
            )}

            <button
              onClick={handleSimulatedLogin}
              disabled={loading}
              className="btn btn--primary"
              style={{
                width: '100%',
                padding: 'var(--space-2) var(--space-4)',
                fontSize: 'var(--text-sm)',
                fontWeight: 'var(--font-weight-medium)',
                marginTop: 'var(--space-2)'
              }}
            >
              {loading ? 'Accediendo...' : 'Ingresar con cuenta simulada'}
            </button>
          </div>
        )}

        <div style={{
          display: 'flex',
          justifyContent: 'center',
          borderTop: '1px solid var(--color-border)',
          paddingTop: 'var(--space-4)'
        }}>
          <button
            onClick={onBack}
            className="btn btn--ghost"
            style={{
              fontSize: 'var(--text-sm)',
              color: 'var(--color-text-secondary)',
              cursor: 'pointer'
            }}
          >
            ← Volver al inicio
          </button>
        </div>
      </div>
    </div>
  )
}
