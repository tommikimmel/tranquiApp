import { useState } from 'react'
import { api } from '../api/api'

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
      localStorage.setItem('tranqui_user', JSON.stringify(user));
      onLoginSuccess(user)
    } catch (err: any) {
      console.error('Error de autenticación:', err)
      setError(err.message || 'No se pudo iniciar sesión. Por favor, intentá de nuevo.')
    } finally {
      setLoading(false)
    }
  }

  // Load Google Identity Services dynamically
  useState(() => {
    const scriptId = 'google-gsi-client'
    const initGoogle = () => {
      // @ts-ignore
      if (window.google) {
        // @ts-ignore
        window.google.accounts.id.initialize({
          client_id: "224301140079-2pa672f7sqcner9nut04j0g99md88n3p.apps.googleusercontent.com",
          callback: (response: any) => {
            handleGoogleLogin(response.credential)
          }
        })
        // @ts-ignore
        window.google.accounts.id.renderButton(
          document.getElementById("google-signin-btn"),
          { theme: "outline", size: "large", width: 376 }
        )
      }
    }

    if (!document.getElementById(scriptId)) {
      const script = document.createElement('script')
      script.id = scriptId
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true;
      script.defer = true;
      script.onload = initGoogle
      document.body.appendChild(script)
    } else {
      setTimeout(initGoogle, 100)
    }
  })

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
            Iniciar sesión
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
        <div id="google-signin-btn" style={{ display: 'flex', justifyContent: 'center' }}></div>

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
                <option value="mateo.b@gmail.com">Mateo Benítez (Paciente Demo)</option>
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
