import { useState } from 'react'
import { api } from '../api/api'
import AddressMapPicker from './AddressMapPicker'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

interface LoginPageProps {
  onLoginSuccess: (user: any) => void
  onBack: () => void
}

function IconPatient({ size = 26 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7" />
    </svg>
  )
}

function IconStethoscope({ size = 26 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M4.5 3v6a4.5 4.5 0 0 0 9 0V3" />
      <path d="M8.5 13.5V17a5 5 0 0 0 10 0v-2" />
      <circle cx="18.5" cy="13" r="2" />
    </svg>
  )
}

function IconVideoCall({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" />
    </svg>
  )
}

function IconBuilding({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="4" y="2" width="16" height="20" rx="1" />
      <line x1="9" y1="7" x2="9" y2="7.01" /><line x1="15" y1="7" x2="15" y2="7.01" />
      <line x1="9" y1="11" x2="9" y2="11.01" /><line x1="15" y1="11" x2="15" y2="11.01" />
      <line x1="9" y1="15" x2="9" y2="15.01" /><line x1="15" y1="15" x2="15" y2="15.01" />
      <path d="M9 22v-4h6v4" />
    </svg>
  )
}

const PROVINCIAS_ARGENTINA = [
  "Buenos Aires", "CABA", "Catamarca", "Chaco", "Chubut", "Córdoba", "Corrientes",
  "Entre Ríos", "Formosa", "Jujuy", "La Pampa", "La Rioja", "Mendoza", "Misiones",
  "Neuquén", "Río Negro", "Salta", "San Juan", "San Luis", "Santa Cruz", "Santa Fe",
  "Santiago del Estero", "Tierra del Fuego", "Tucumán"
];

const ESPECIALIDADES_GRUPOS = [
  "Alergia e Inmunología", "Cardiología", "Dermatología", "Endocrinología y Nutrición",
  "Gastroenterología / Hepatología", "Geriatria", "Hematología", "Infectología",
  "Medicina Interna (Clínica Médica)", "Nefrología", "Neumonología", "Neurología",
  "Oncología Médica", "Pediatría", "Psiquiatría", "Psiquiatría Infanto-Juvenil", "Reumatología"
];

export default function LoginPage({ onLoginSuccess, onBack }: LoginPageProps) {
  useDocumentTitle('Iniciar sesión — Tranqui App')
  const [activeTab, setActiveTab] = useState<'login' | 'register' | 'verify' | 'forgot' | 'reset'>('login')
  const [role, setRole] = useState<'PACIENTE' | 'PSIQUIATRA'>('PACIENTE')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  
  const [regStep, setRegStep] = useState(1)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [hasObraSocial, setHasObraSocial] = useState(false)

  // Login form state
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')

  // Signup form state
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nombre, setNombre] = useState('')
  const [apellido, setApellido] = useState('')
  const [sexo, setSexo] = useState('M')
  const [fechaNacimiento, setFechaNacimiento] = useState('')
  const [tipoDocumento, setTipoDocumento] = useState('DNI')
  const [numeroDocumento, setNumeroDocumento] = useState('')
  const [telefono, setTelefono] = useState('')

  // Patient spec
  const [obraSocial, setObraSocial] = useState('')
  const [numAfiliado, setNumAfiliado] = useState('')

  // Pro spec
  const [matricula, setMatricula] = useState('')
  const [titulo, setTitulo] = useState('')
  const [specialty, setSpecialty] = useState('')
  const [cuil, setCuil] = useState('')
  const [domicilioAtencion, setDomicilioAtencion] = useState('')
  const [domicilioProvincia, setDomicilioProvincia] = useState('')
  const [domicilioLat, setDomicilioLat] = useState<number | null>(null)
  const [domicilioLng, setDomicilioLng] = useState<number | null>(null)
  const [codigoReFeps, setCodigoReFeps] = useState('')
  const [matriculaTipo, setMatriculaTipo] = useState('MN')
  const [matriculaProvincia, setMatriculaProvincia] = useState('')
  const [ofreceOnline, setOfreceOnline] = useState(true)
  const [ofrecePresencial, setOfrecePresencial] = useState(false)
  const [fotoUrl, setFotoUrl] = useState('')

  // Email Verification State
  const [pendingEmail, setPendingEmail] = useState('')
  const [verifyCode, setVerifyCode] = useState('')

  // Forgot / Reset Password State
  const [forgotEmail, setForgotEmail] = useState('')
  const [resetCode, setResetCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmNewPassword, setConfirmNewPassword] = useState('')

  const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  const validatePassword = (pass: string): string | null => {
    if (pass.length < 8) return 'La contraseña debe tener al menos 8 caracteres.'
    if (!/[A-Z]/.test(pass)) return 'La contraseña debe incluir al menos una letra mayúscula.'
    if (!/[a-z]/.test(pass)) return 'La contraseña debe incluir al menos una letra minúscula.'
    if (!/[0-9]/.test(pass)) return 'La contraseña debe incluir al menos un número.'
    return null
  }

  const handleGoogleLogin = async (token: string) => {
    setLoading(true)
    setError(null)
    try {
      const user = await api.loginGoogle(token)
      localStorage.setItem('tranqui_user', JSON.stringify(user));
      onLoginSuccess(user)
    } catch (err: any) {
      console.error('Error de autenticación Google:', err)
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

  const handleFormLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!loginEmail || !loginPassword) {
      setError('Por favor, completá todos los campos.')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const user = await api.login({ email: loginEmail.trim().toLowerCase(), password: loginPassword })
      localStorage.setItem('tranqui_user', JSON.stringify(user));
      onLoginSuccess(user)
    } catch (err: any) {
      const errMsg = err.message || 'Credenciales inválidas.'
      setError(errMsg)
      if (errMsg.includes('verificar tu correo')) {
        setPendingEmail(loginEmail.trim().toLowerCase())
      }
    } finally {
      setLoading(false)
    }
  }

  const totalSteps = role === 'PSIQUIATRA' || hasObraSocial ? 3 : 2;

  const handleNextStep = () => {
    setError(null)
    if (regStep === 1) {
      const cleanEmail = email.trim()
      if (!cleanEmail || !password || !confirmPassword) {
        setError('Por favor, completá todos los campos.')
        return
      }
      if (!EMAIL_REGEX.test(cleanEmail)) {
        setError('Por favor, ingresá un formato de email válido (ej: usuario@dominio.com).')
        return
      }
      const passErr = validatePassword(password)
      if (passErr) {
        setError(passErr)
        return
      }
      if (password !== confirmPassword) {
        setError('Las contraseñas no coinciden.')
        return
      }
      setRegStep(2)
    } else if (regStep === 2) {
      if (!nombre || !apellido || !fechaNacimiento || !numeroDocumento || !telefono) {
        setError('Por favor, completá todos los datos personales obligatorios.')
        return
      }
      if (totalSteps > 2) {
        setRegStep(3)
      }
    }
  }

  const handlePrevStep = () => {
    setError(null)
    if (regStep > 1) {
      setRegStep(regStep - 1)
    }
  }

  const handleFormRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    const cleanEmail = email.trim().toLowerCase()
    if (!EMAIL_REGEX.test(cleanEmail)) {
      setError('Por favor, ingresá un formato de email válido.')
      return
    }

    const passErr = validatePassword(password)
    if (passErr) {
      setError(passErr)
      return
    }

    if (password !== confirmPassword) {
      setError('Las contraseñas no coinciden.')
      return
    }

    if (!cleanEmail || !password || !nombre || !apellido || !numeroDocumento || !telefono) {
      setError('Por favor, completá los datos obligatorios.')
      return
    }

    const payload: any = {
      email: cleanEmail,
      password,
      rol: role,
      nombre,
      apellido,
      sexo,
      fechaNacimiento,
      tipoDocumento,
      numeroDocumento: Number(numeroDocumento),
      telefono: `+54 ${telefono.trim()}`,
    }

    if (role === 'PACIENTE') {
      if (hasObraSocial) {
        if (!obraSocial || !numAfiliado) {
          setError('Por favor, completá los datos de tu obra social.')
          return
        }
        payload.obraSocial = obraSocial
        payload.numAfiliado = numAfiliado
      } else {
        payload.obraSocial = null
        payload.numAfiliado = null
      }
    } else {
      if (!titulo || !specialty || !matricula || !codigoReFeps) {
        setError('Por favor, completá los datos profesionales obligatorios.')
        return
      }
      if (!ofreceOnline && !ofrecePresencial) {
        setError('Seleccioná al menos una modalidad de consulta (Online o Presencial).')
        return
      }
      if (ofrecePresencial && !domicilioAtencion) {
        setError('Si ofrecés consultas presenciales, indicá el domicilio de atención.')
        return
      }
      payload.matricula = matricula
      payload.titulo = titulo
      payload.specialty = specialty
      payload.cuit = cuil
      payload.cuil = cuil ? Number(cuil) : null
      payload.domicilioAtencion = ofrecePresencial ? domicilioAtencion : null
      payload.domicilioLat = ofrecePresencial ? domicilioLat : null
      payload.domicilioLng = ofrecePresencial ? domicilioLng : null
      payload.codigoReFeps = codigoReFeps ? Number(codigoReFeps) : null
      payload.matriculaTipo = matriculaTipo
      payload.matriculaProvincia = matriculaProvincia
      payload.matriculaNumero = Number(matricula)
      payload.ofreceOnline = ofreceOnline
      payload.ofrecePresencial = ofrecePresencial
    }

    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      await api.register(payload)
      setPendingEmail(cleanEmail)
      setSuccess('¡Registro exitoso! Enviamos un código de 6 dígitos a tu correo para activar tu cuenta.')
      setActiveTab('verify')
    } catch (err: any) {
      setError(err.message || 'Error al intentar registrarse.')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyEmail = async (e: React.FormEvent) => {
    e.preventDefault()
    const targetEmail = (pendingEmail || loginEmail || email).trim().toLowerCase()
    if (!verifyCode || verifyCode.trim().length !== 6) {
      setError('Ingresá el código de 6 dígitos recibido por correo.')
      return
    }
    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      await api.verifyEmail({ email: targetEmail, codigo: verifyCode.trim() })
      setSuccess('¡Email verificado con éxito! Ya podés iniciar sesión.')
      setLoginEmail(targetEmail)
      setLoginPassword(password)
      setActiveTab('login')
      setVerifyCode('')
    } catch (err: any) {
      setError(err.message || 'Código de verificación incorrecto o expirado.')
    } finally {
      setLoading(false)
    }
  }

  const handleResendCode = async () => {
    const targetEmail = (pendingEmail || loginEmail || email).trim().toLowerCase()
    if (!targetEmail) {
      setError('No hay dirección de email seleccionada.')
      return
    }
    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      await api.resendCode({ email: targetEmail })
      setSuccess('Se envió un nuevo código de verificación a tu correo.')
    } catch (err: any) {
      setError(err.message || 'No se pudo reenviar el código.')
    } finally {
      setLoading(false)
    }
  }

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    const targetEmail = forgotEmail.trim().toLowerCase()
    if (!targetEmail || !EMAIL_REGEX.test(targetEmail)) {
      setError('Ingresá un formato de email válido.')
      return
    }
    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      await api.forgotPassword({ email: targetEmail })
      setSuccess('Si el correo está registrado, recibirás un código de 6 dígitos.')
      setActiveTab('reset')
    } catch (err: any) {
      setError(err.message || 'Error al solicitar la recuperación de contraseña.')
    } finally {
      setLoading(false)
    }
  }

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    const targetEmail = forgotEmail.trim().toLowerCase()
    if (!resetCode || resetCode.trim().length !== 6) {
      setError('Ingresá el código de 6 dígitos enviado a tu correo.')
      return
    }
    const passErr = validatePassword(newPassword)
    if (passErr) {
      setError(passErr)
      return
    }
    if (newPassword !== confirmNewPassword) {
      setError('Las contraseñas no coinciden.')
      return
    }

    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      await api.resetPassword({
        email: targetEmail,
        codigo: resetCode.trim(),
        newPassword
      })
      setSuccess('¡Tu contraseña fue actualizada con éxito! Ya podés iniciar sesión.')
      setActiveTab('login')
      setLoginEmail(targetEmail)
      setLoginPassword(newPassword)
      setResetCode('')
      setNewPassword('')
      setConfirmNewPassword('')
    } catch (err: any) {
      setError(err.message || 'No se pudo restablecer la contraseña.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-container" style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: 'var(--color-bg)',
      padding: 'var(--space-3)'
    }}>
      <div className="card" style={{
        maxWidth: activeTab === 'register' ? '640px' : '440px',
        width: '100%',
        maxHeight: '94vh',
        overflowY: 'auto',
        padding: 'var(--space-3) var(--space-5)',
        boxShadow: 'var(--shadow-lg)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        backgroundColor: 'var(--color-surface)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)',
        transition: 'max-width 0.3s ease-in-out'
      }}>
        {/* Back button */}
        <button
          type="button"
          onClick={onBack}
          style={{
            alignSelf: 'flex-start',
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            fontSize: 'var(--text-xs)',
            color: 'var(--color-text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--space-1)',
            padding: 0,
            marginBottom: 'var(--space-1)'
          }}
        >
          ← Volver a la página principal
        </button>

        {/* Brand Header */}
        <div style={{ textAlign: 'center' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-1)' }}>
            <span style={{ fontSize: 'var(--text-2xl)' }}>🧘</span>
            <span style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-xl)', fontWeight: 'bold', color: 'var(--color-primary)' }}>
              Tranqui App
            </span>
          </div>
          <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>
            {activeTab === 'login' && 'Ingresá a tu cuenta profesional o paciente'}
            {activeTab === 'register' && 'Creá tu perfil en simples pasos'}
            {activeTab === 'verify' && 'Verificá tu correo electrónico'}
            {activeTab === 'forgot' && 'Recuperá el acceso a tu cuenta'}
            {activeTab === 'reset' && 'Creá tu nueva contraseña'}
          </p>
        </div>

        {/* Navigation Tabs (only for Login / Register) */}
        {(activeTab === 'login' || activeTab === 'register') && (
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            backgroundColor: 'var(--neutral-100)',
            borderRadius: 'var(--radius-md)',
            padding: '3px',
            marginTop: 'var(--space-1)'
          }}>
            <button
              type="button"
              onClick={() => { setActiveTab('login'); setError(null); setSuccess(null); }}
              style={{
                padding: 'var(--space-2)',
                border: 'none',
                borderRadius: 'var(--radius-sm)',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '13px',
                backgroundColor: activeTab === 'login' ? '#ffffff' : 'transparent',
                color: activeTab === 'login' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                boxShadow: activeTab === 'login' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none'
              }}
            >
              Iniciar Sesión
            </button>
            <button
              type="button"
              onClick={() => { setActiveTab('register'); setError(null); setSuccess(null); }}
              style={{
                padding: 'var(--space-2)',
                border: 'none',
                borderRadius: 'var(--radius-sm)',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '13px',
                backgroundColor: activeTab === 'register' ? '#ffffff' : 'transparent',
                color: activeTab === 'register' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                boxShadow: activeTab === 'register' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none'
              }}
            >
              Registrarse
            </button>
          </div>
        )}

        {error && (
          <div style={{
            padding: 'var(--space-3) var(--space-4)',
            backgroundColor: 'var(--color-error-bg)',
            color: 'var(--color-error)',
            borderRadius: 'var(--radius-sm)',
            fontSize: 'var(--text-sm)',
            border: '1px solid #fecaca',
            lineHeight: 'var(--line-height-normal)',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px'
          }}>
            <div>{error}</div>
            {error.includes('verificar tu correo') && (
              <button
                type="button"
                className="btn btn--sm btn--primary"
                onClick={() => { setActiveTab('verify'); setError(null); setSuccess(null); }}
                style={{ alignSelf: 'flex-start', fontSize: '12px' }}
              >
                Ingresar código de verificación
              </button>
            )}
          </div>
        )}

        {success && (
          <div style={{
            padding: 'var(--space-3) var(--space-4)',
            backgroundColor: '#f0fdf4',
            color: '#15803d',
            borderRadius: 'var(--radius-sm)',
            fontSize: 'var(--text-sm)',
            border: '1px solid #bbf7d0',
            lineHeight: 'var(--line-height-normal)'
          }}>
            {success}
          </div>
        )}

        {/* ── TAB 1: LOGIN ── */}
        {activeTab === 'login' && (
          <form onSubmit={handleFormLogin} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="login-email">Email</label>
              <input
                id="login-email"
                type="email"
                className="form-input"
                placeholder="ejemplo@correo.com"
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <label className="form-label" htmlFor="login-password">Contraseña</label>
                <button
                  type="button"
                  onClick={() => { setForgotEmail(loginEmail); setActiveTab('forgot'); setError(null); setSuccess(null); }}
                  style={{ background: 'none', border: 'none', color: 'var(--color-primary)', fontSize: '12px', cursor: 'pointer', padding: 0 }}
                >
                  ¿Olvidaste tu contraseña?
                </button>
              </div>
              <input
                id="login-password"
                type="password"
                className="form-input"
                placeholder="********"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                required
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="btn btn--primary"
              style={{ width: '100%', padding: '10px' }}
            >
              {loading ? 'Accediendo...' : 'Iniciar Sesión'}
            </button>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              margin: 'var(--space-2) 0',
              color: 'var(--color-text-secondary)',
              fontSize: '12px'
            }}>
              <span style={{ borderBottom: '1px solid var(--color-border)', flex: 1, marginRight: '10px' }}></span>
              o ingresá con Google
              <span style={{ borderBottom: '1px solid var(--color-border)', flex: 1, marginLeft: '10px' }}></span>
            </div>

            {/* Real Google Button */}
            <div id="google-signin-btn" style={{ display: 'flex', justifyContent: 'center' }}></div>
          </form>
        )}

        {/* ── TAB 2: REGISTER ── */}
        {activeTab === 'register' && (
          <form onSubmit={handleFormRegister} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            
            {/* Step Progress Indicator */}
            <div className="checkout-progress" style={{ padding: '0 var(--space-6)' }}>
              <div className={`checkout-progress__step ${regStep >= 1 ? 'active' : ''}`}>
                <span className="checkout-progress__dot">1</span>
                Credenciales
              </div>
              <div className="checkout-progress__line" />
              <div className={`checkout-progress__step ${regStep >= 2 ? 'active' : ''}`}>
                <span className="checkout-progress__dot">2</span>
                Personales
              </div>
              {(role === 'PSIQUIATRA' || hasObraSocial) && (
                <>
                  <div className="checkout-progress__line" />
                  <div className={`checkout-progress__step ${regStep >= 3 ? 'active' : ''}`}>
                    <span className="checkout-progress__dot">3</span>
                    {role === 'PSIQUIATRA' ? 'Profesionales' : 'Cobertura'}
                  </div>
                </>
              )}
            </div>

            {/* PASO 1: Credenciales & Rol */}
            {regStep === 1 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {/* Role select */}
                <div className="form-group">
                  <label className="form-label">Registrarme como:</label>
                  <div className="role-select" role="radiogroup" aria-label="Registrarme como">
                    <button
                      type="button"
                      role="radio"
                      aria-checked={role === 'PACIENTE'}
                      className={`role-select__card ${role === 'PACIENTE' ? 'active' : ''}`}
                      onClick={() => setRole('PACIENTE')}
                    >
                      <span className="role-select__icon"><IconPatient /></span>
                      Paciente
                    </button>
                    <button
                      type="button"
                      role="radio"
                      aria-checked={role === 'PSIQUIATRA'}
                      className={`role-select__card ${role === 'PSIQUIATRA' ? 'active' : ''}`}
                      onClick={() => setRole('PSIQUIATRA')}
                    >
                      <span className="role-select__icon"><IconStethoscope /></span>
                      Profesional
                    </button>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                  <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                    Credenciales de Acceso
                  </div>
                  <div className="form-group" style={{ gridColumn: 'span 2' }}>
                    <label className="form-label form-label--required">Email</label>
                    <input type="email" className="form-input" placeholder="ejemplo@correo.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label form-label--required">Contraseña</label>
                    <input type="password" className="form-input" placeholder="********" value={password} onChange={(e) => setPassword(e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label form-label--required">Confirmar Contraseña</label>
                    <input type="password" className="form-input" placeholder="********" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
                  </div>
                </div>

                <div style={{ backgroundColor: '#f8fafc', padding: '10px 12px', borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '11px', color: '#64748b' }}>
                  <strong>Requisitos de la contraseña:</strong> Mínimo 8 caracteres, al menos una mayúscula, una minúscula y un número.
                </div>

                <button type="button" className="btn btn--primary" onClick={handleNextStep} style={{ marginTop: 'var(--space-2)' }}>
                  Siguiente paso →
                </button>
              </div>
            )}

            {/* PASO 2: Datos Personales */}
            {regStep === 2 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                <div style={{ fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                  Datos Personales Identificatorios
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                  <div className="form-group">
                    <label className="form-label form-label--required">Nombre</label>
                    <input type="text" className="form-input" placeholder="Juan" value={nombre} onChange={(e) => setNombre(e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label form-label--required">Apellido</label>
                    <input type="text" className="form-input" placeholder="Pérez" value={apellido} onChange={(e) => setApellido(e.target.value)} required />
                  </div>

                  <div className="form-group">
                    <label className="form-label form-label--required">Sexo</label>
                    <select className="form-input" value={sexo} onChange={(e) => setSexo(e.target.value)}>
                      <option value="M">Masculino</option>
                      <option value="F">Femenino</option>
                      <option value="X">Otro / No declara</option>
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label form-label--required">Fecha de Nacimiento</label>
                    <input type="date" className="form-input" value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)} required />
                  </div>

                  <div className="form-group">
                    <label className="form-label form-label--required">Tipo de Documento</label>
                    <select className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
                      <option value="DNI">DNI</option>
                      <option value="PASAPORTE">Pasaporte</option>
                      <option value="LC">Libreta Cívica</option>
                      <option value="LE">Libreta de Enrolamiento</option>
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label form-label--required">Número de Documento</label>
                    <input type="number" className="form-input" placeholder="12345678" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value)} required />
                  </div>

                  <div className="form-group" style={{ gridColumn: 'span 2' }}>
                    <label className="form-label form-label--required">Teléfono (WhatsApp)</label>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                      <span style={{ fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-text-secondary)' }}>+54</span>
                      <input type="tel" className="form-input" placeholder="11 2345-6789" value={telefono} onChange={(e) => setTelefono(e.target.value)} required />
                    </div>
                  </div>

                  {role === 'PACIENTE' && (
                    <div className="form-group" style={{ gridColumn: 'span 2', marginTop: 'var(--space-1)' }}>
                      <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', cursor: 'pointer', userSelect: 'none' }}>
                        <input
                          type="checkbox"
                          checked={hasObraSocial}
                          onChange={(e) => setHasObraSocial(e.target.checked)}
                          style={{ width: '16px', height: '16px', accentColor: 'var(--color-primary)' }}
                        />
                        Tengo Obra Social o Prepaga
                      </label>
                    </div>
                  )}
                </div>

                <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  <button type="button" className="btn btn--ghost" onClick={handlePrevStep} style={{ flex: 1 }}>
                    ← Anterior
                  </button>
                  {totalSteps > 2 ? (
                    <button type="button" className="btn btn--primary" onClick={handleNextStep} style={{ flex: 1 }}>
                      Siguiente paso →
                    </button>
                  ) : (
                    <button type="submit" disabled={loading} className="btn btn--primary" style={{ flex: 1 }}>
                      {loading ? 'Creando cuenta...' : 'Finalizar Registro'}
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* PASO 3: Profesional o Cobertura */}
            {regStep === 3 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {role === 'PACIENTE' ? (
                  <>
                    <div style={{ fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                      Datos de Obra Social / Prepaga
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                      <div className="form-group">
                        <label className="form-label form-label--required">Obra Social / Prepaga</label>
                        <input type="text" className="form-input" placeholder="OSDE, Swiss Medical, etc." value={obraSocial} onChange={(e) => setObraSocial(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Número de Afiliado</label>
                        <input type="text" className="form-input" placeholder="12345678901" value={numAfiliado} onChange={(e) => setNumAfiliado(e.target.value)} required />
                      </div>
                    </div>
                  </>
                ) : (
                  <>
                    <div style={{ fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                      Información Profesional & Matrícula
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                      <div className="form-group">
                        <label className="form-label form-label--required">Título Profesional</label>
                        <input type="text" className="form-input" placeholder="Médico / Médico Psiquiatra" value={titulo} onChange={(e) => setTitulo(e.target.value)} required />
                      </div>

                      <div className="form-group">
                        <label className="form-label form-label--required">Especialidad Principal</label>
                        <select className="form-input" value={specialty} onChange={(e) => setSpecialty(e.target.value)} required>
                          <option value="">Seleccionar especialidad...</option>
                          {ESPECIALIDADES_GRUPOS.map((esp) => (
                            <option key={esp} value={esp}>{esp}</option>
                          ))}
                        </select>
                      </div>

                      <div className="form-group">
                        <label className="form-label form-label--required">Tipo de Matrícula</label>
                        <select className="form-input" value={matriculaTipo} onChange={(e) => setMatriculaTipo(e.target.value)}>
                          <option value="MN">Nacional (MN)</option>
                          <option value="MP">Provincial (MP)</option>
                        </select>
                      </div>

                      {matriculaTipo === 'MP' && (
                        <div className="form-group">
                          <label className="form-label form-label--required">Provincia de Matrícula</label>
                          <select className="form-input" value={matriculaProvincia} onChange={(e) => setMatriculaProvincia(e.target.value)} required>
                            <option value="">Seleccionar provincia...</option>
                            {PROVINCIAS_ARGENTINA.map((p) => (
                              <option key={p} value={p}>{p}</option>
                            ))}
                          </select>
                        </div>
                      )}

                      <div className="form-group">
                        <label className="form-label form-label--required">Número de Matrícula</label>
                        <input type="number" className="form-input" placeholder="123456" value={matricula} onChange={(e) => setMatricula(e.target.value)} required />
                      </div>

                      <div className="form-group">
                        <label className="form-label form-label--required">Código ReFEPS</label>
                        <input type="number" className="form-input" placeholder="12345678" value={codigoReFeps} onChange={(e) => setCodigoReFeps(e.target.value)} required />
                      </div>

                      <div className="form-group">
                        <label className="form-label">CUIL / CUIT</label>
                        <input type="number" className="form-input" placeholder="20123456789" value={cuil} onChange={(e) => setCuil(e.target.value)} />
                      </div>

                      {/* Modalidades de atención */}
                      <div className="form-group" style={{ gridColumn: 'span 2' }}>
                        <label className="form-label form-label--required">Modalidades de Atención</label>
                        <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: '4px' }}>
                          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', fontSize: '13px' }}>
                            <input type="checkbox" checked={ofreceOnline} onChange={(e) => setOfreceOnline(e.target.checked)} />
                            <IconVideoCall size={16} /> Online / Telemedicina
                          </label>
                          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', fontSize: '13px' }}>
                            <input type="checkbox" checked={ofrecePresencial} onChange={(e) => setOfrecePresencial(e.target.checked)} />
                            <IconBuilding size={16} /> Presencial
                          </label>
                        </div>
                      </div>

                      {ofrecePresencial && (
                        <div className="form-group" style={{ gridColumn: 'span 2' }}>
                          <label className="form-label form-label--required">Domicilio de Atención Presencial</label>
                          <AddressMapPicker
                            direccion={domicilioAtencion}
                            onDireccionChange={setDomicilioAtencion}
                            provincia={domicilioProvincia}
                            onProvinciaChange={setDomicilioProvincia}
                            lat={domicilioLat}
                            lng={domicilioLng}
                            onLocationChange={(lat: number, lng: number) => {
                              setDomicilioLat(lat)
                              setDomicilioLng(lng)
                            }}
                            provinciasList={PROVINCIAS_ARGENTINA}
                          />
                        </div>
                      )}
                    </div>
                  </>
                )}

                <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  <button type="button" className="btn btn--ghost" onClick={handlePrevStep} style={{ flex: 1 }}>
                    ← Anterior
                  </button>
                  <button type="submit" disabled={loading} className="btn btn--primary" style={{ flex: 1 }}>
                    {loading ? 'Creando cuenta...' : 'Finalizar Registro'}
                  </button>
                </div>
              </div>
            )}
          </form>
        )}

        {/* ── TAB 3: VERIFY EMAIL ── */}
        {activeTab === 'verify' && (
          <form onSubmit={handleVerifyEmail} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ textAlign: 'center', padding: '10px 0' }}>
              <span style={{ fontSize: '36px' }}>✉️</span>
              <h3 style={{ margin: '8px 0 4px 0', fontSize: '16px', color: 'var(--color-primary)' }}>Confirmación de Email</h3>
              <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
                Enviamos un código de 6 dígitos a <strong>{pendingEmail || loginEmail || email}</strong>. Ingresalo a continuación para activar tu perfil:
              </p>
            </div>

            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="verify-code">Código de Verificación</label>
              <input
                id="verify-code"
                type="text"
                className="form-input"
                placeholder="123456"
                maxLength={6}
                value={verifyCode}
                onChange={(e) => setVerifyCode(e.target.value)}
                style={{ textAlign: 'center', fontSize: '20px', letterSpacing: '6px', fontWeight: 'bold' }}
                required
              />
            </div>

            <button type="submit" disabled={loading} className="btn btn--primary" style={{ width: '100%', padding: '10px' }}>
              {loading ? 'Verificando...' : 'Verificar y Activar Cuenta'}
            </button>

            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
              <button
                type="button"
                onClick={handleResendCode}
                disabled={loading}
                style={{ background: 'none', border: 'none', color: 'var(--color-primary)', fontSize: '12px', cursor: 'pointer', padding: 0 }}
              >
                Reenviar código
              </button>
              <button
                type="button"
                onClick={() => { setActiveTab('login'); setError(null); setSuccess(null); }}
                style={{ background: 'none', border: 'none', color: 'var(--color-text-secondary)', fontSize: '12px', cursor: 'pointer', padding: 0 }}
              >
                Volver a Iniciar Sesión
              </button>
            </div>
          </form>
        )}

        {/* ── TAB 4: FORGOT PASSWORD ── */}
        {activeTab === 'forgot' && (
          <form onSubmit={handleForgotPassword} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ textAlign: 'center', padding: '10px 0' }}>
              <span style={{ fontSize: '36px' }}>🔑</span>
              <h3 style={{ margin: '8px 0 4px 0', fontSize: '16px', color: 'var(--color-primary)' }}>Recuperar Contraseña</h3>
              <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
                Ingresá la dirección de correo con la que te registraste para enviarte un código de recuperación.
              </p>
            </div>

            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="forgot-email">Email Registrado</label>
              <input
                id="forgot-email"
                type="email"
                className="form-input"
                placeholder="ejemplo@correo.com"
                value={forgotEmail}
                onChange={(e) => setForgotEmail(e.target.value)}
                required
              />
            </div>

            <button type="submit" disabled={loading} className="btn btn--primary" style={{ width: '100%', padding: '10px' }}>
              {loading ? 'Enviando...' : 'Enviar Código de Recuperación'}
            </button>

            <button
              type="button"
              onClick={() => { setActiveTab('login'); setError(null); setSuccess(null); }}
              style={{ background: 'none', border: 'none', color: 'var(--color-text-secondary)', fontSize: '12px', cursor: 'pointer', padding: 0, textAlign: 'center' }}
            >
              ← Volver a Iniciar Sesión
            </button>
          </form>
        )}

        {/* ── TAB 5: RESET PASSWORD ── */}
        {activeTab === 'reset' && (
          <form onSubmit={handleResetPassword} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div style={{ textAlign: 'center', padding: '6px 0' }}>
              <span style={{ fontSize: '32px' }}>🔒</span>
              <h3 style={{ margin: '6px 0 2px 0', fontSize: '16px', color: 'var(--color-primary)' }}>Restablecer Contraseña</h3>
              <p style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                Ingresá el código de 6 dígitos recibido y definí tu nueva clave.
              </p>
            </div>

            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="reset-code">Código de 6 dígitos</label>
              <input
                id="reset-code"
                type="text"
                className="form-input"
                placeholder="123456"
                maxLength={6}
                value={resetCode}
                onChange={(e) => setResetCode(e.target.value)}
                style={{ textAlign: 'center', fontSize: '18px', letterSpacing: '4px', fontWeight: 'bold' }}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label form-label--required">Nueva Contraseña</label>
              <input
                type="password"
                className="form-input"
                placeholder="********"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label form-label--required">Confirmar Nueva Contraseña</label>
              <input
                type="password"
                className="form-input"
                placeholder="********"
                value={confirmNewPassword}
                onChange={(e) => setConfirmNewPassword(e.target.value)}
                required
              />
            </div>

            <div style={{ backgroundColor: '#f8fafc', padding: '8px 10px', borderRadius: '6px', border: '1px solid #e2e8f0', fontSize: '11px', color: '#64748b' }}>
              <strong>Requisitos:</strong> Mínimo 8 caracteres, al menos una mayúscula, una minúscula y un número.
            </div>

            <button type="submit" disabled={loading} className="btn btn--primary" style={{ width: '100%', padding: '10px', marginTop: '4px' }}>
              {loading ? 'Restableciendo...' : 'Restablecer Contraseña'}
            </button>

            <button
              type="button"
              onClick={() => { setActiveTab('login'); setError(null); setSuccess(null); }}
              style={{ background: 'none', border: 'none', color: 'var(--color-text-secondary)', fontSize: '12px', cursor: 'pointer', padding: 0, textAlign: 'center' }}
            >
              ← Cancelar y volver a Iniciar Sesión
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
