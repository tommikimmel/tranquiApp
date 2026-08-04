import { useState } from 'react'
import { api } from '../api/api'
import AddressMapPicker from './AddressMapPicker'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useAlert } from '../context/AlertContext'

interface LoginPageProps {
  onLoginSuccess: (user: any) => void
  onBack: () => void
}

function IconBrandLogo({ size = 28 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, color: 'var(--color-primary)' }}>
      <circle cx="12" cy="12" r="10" />
      <path d="M8 12h8M12 8v8" />
    </svg>
  )
}

function IconMail({ size = 36 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, color: 'var(--color-primary)' }}>
      <rect x="2" y="4" width="20" height="16" rx="2" />
      <path d="M22 6l-10 7L2 6" />
    </svg>
  )
}

function IconKey({ size = 36 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, color: 'var(--color-primary)' }}>
      <circle cx="7.5" cy="15.5" r="5.5" />
      <path d="M11.5 11.5L21 2M16 7l2 2M19 4l2 2" />
    </svg>
  )
}

function IconLock({ size = 36 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, color: 'var(--color-primary)' }}>
      <rect x="3" y="11" width="18" height="11" rx="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  )
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
  const { showAlert } = useAlert()
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
      showAlert('Por favor, completá todos los campos.', 'warning')
      return
    }
    setLoading(true)
    try {
      const user = await api.login({ email: loginEmail.trim().toLowerCase(), password: loginPassword })
      localStorage.setItem('tranqui_user', JSON.stringify(user));
      onLoginSuccess(user)
    } catch (err: any) {
      const errMsg = err.message || 'Credenciales no válidas.'
      showAlert(errMsg, 'error')
      if (errMsg.includes('verificar tu correo')) {
        setPendingEmail(loginEmail.trim().toLowerCase())
        setActiveTab('verify')
      }
    } finally {
      setLoading(false)
    }
  }

  const totalSteps = role === 'PSIQUIATRA' || hasObraSocial ? 3 : 2;

  const handleNextStep = () => {
    if (regStep === 1) {
      const cleanEmail = email.trim()
      if (!cleanEmail || !password || !confirmPassword) {
        showAlert('Por favor, completá todos los campos.', 'warning')
        return
      }
      if (!EMAIL_REGEX.test(cleanEmail)) {
        showAlert('Por favor, ingresá un formato de email válido.', 'warning')
        return
      }
      const passErr = validatePassword(password)
      if (passErr) {
        showAlert(passErr, 'warning')
        return
      }
      if (password !== confirmPassword) {
        showAlert('Las contraseñas no coinciden.', 'warning')
        return
      }
      setRegStep(2)
    } else if (regStep === 2) {
      if (!nombre || !apellido || !fechaNacimiento || !numeroDocumento || !telefono) {
        showAlert('Por favor, completá todos los datos personales obligatorios.', 'warning')
        return
      }
      const todayStr = new Date().toISOString().split('T')[0]
      if (fechaNacimiento > todayStr || fechaNacimiento < '1900-01-01') {
        showAlert('La fecha de nacimiento debe ser una fecha verídica (entre 1900 y hoy).', 'warning')
        return
      }
      if (String(numeroDocumento).length < 7 || String(numeroDocumento).length > 8) {
        showAlert('El número de documento debe tener entre 7 y 8 dígitos.', 'warning')
        return
      }
      const cleanPhone = telefono.replace(/[^\d]/g, '')
      if (cleanPhone.length < 8 || cleanPhone.length > 11) {
        showAlert('El número de teléfono debe tener entre 8 y 11 dígitos.', 'warning')
        return
      }
      if (totalSteps > 2) {
        setRegStep(3)
      }
    }
  }

  const handlePrevStep = () => {
    if (regStep > 1) {
      setRegStep(regStep - 1)
    }
  }

  const handleFormRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    const cleanEmail = email.trim().toLowerCase()
    if (!EMAIL_REGEX.test(cleanEmail)) {
      showAlert('Por favor, ingresá un formato de email válido.', 'warning')
      return
    }

    const passErr = validatePassword(password)
    if (passErr) {
      showAlert(passErr, 'warning')
      return
    }

    if (password !== confirmPassword) {
      showAlert('Las contraseñas no coinciden.', 'warning')
      return
    }

    if (!cleanEmail || !password || !nombre || !apellido || !numeroDocumento || !telefono) {
      showAlert('Por favor, completá los datos obligatorios.', 'warning')
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
      telefono: `+54 ${telefono.trim().replace(/^\+54\s*/, '')}`,
    }

    if (role === 'PACIENTE') {
      if (hasObraSocial) {
        if (!obraSocial || !numAfiliado) {
          showAlert('Por favor, completá los datos de tu obra social.', 'warning')
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
        showAlert('Por favor, completá los datos profesionales obligatorios.', 'warning')
        return
      }
      if (!ofreceOnline && !ofrecePresencial) {
        showAlert('Seleccioná al menos una modalidad de consulta (Online o Presencial).', 'warning')
        return
      }
      if (ofrecePresencial && !domicilioAtencion) {
        showAlert('Si ofrecés consultas presenciales, indicá el domicilio de atención.', 'warning')
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
    try {
      await api.register(payload)
      setPendingEmail(cleanEmail)
      showAlert('¡Registro exitoso! Enviamos un código de 6 dígitos a tu correo para activar tu cuenta.', 'success')
      setActiveTab('verify')
    } catch (err: any) {
      showAlert(err.message || 'Error al intentar registrarse.', 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyEmail = async (e: React.FormEvent) => {
    e.preventDefault()
    const targetEmail = (pendingEmail || loginEmail || email).trim().toLowerCase()
    if (!verifyCode || verifyCode.trim().length !== 6) {
      showAlert('Ingresá el código de 6 dígitos recibido por correo.', 'warning')
      return
    }
    setLoading(true)
    try {
      await api.verifyEmail({ email: targetEmail, codigo: verifyCode.trim() })
      showAlert('¡Email verificado con éxito! Ya podés iniciar sesión.', 'success')
      setLoginEmail(targetEmail)
      setLoginPassword(password)
      setActiveTab('login')
      setVerifyCode('')
    } catch (err: any) {
      showAlert(err.message || 'Código de verificación incorrecto o expirado.', 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleResendCode = async (e?: React.MouseEvent) => {
    e?.preventDefault()
    const targetEmail = (pendingEmail || loginEmail || email).trim().toLowerCase()
    if (!targetEmail) {
      showAlert('No se encontró una dirección de correo válida para reenviar el código.', 'error')
      return
    }
    setLoading(true)
    try {
      await api.resendCode({ email: targetEmail })
      showAlert('Se envió un nuevo código de verificación a tu correo.', 'success')
    } catch (err: any) {
      showAlert(err.message || 'No se pudo reenviar el código.', 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    const targetEmail = forgotEmail.trim().toLowerCase()
    if (!targetEmail || !EMAIL_REGEX.test(targetEmail)) {
      showAlert('Ingresá un formato de email válido.', 'warning')
      return
    }
    setLoading(true)
    try {
      await api.forgotPassword({ email: targetEmail })
      showAlert('Si el correo está registrado, recibirás un código de 6 dígitos.', 'success')
      setActiveTab('reset')
    } catch (err: any) {
      showAlert(err.message || 'Error al solicitar la recuperación de contraseña.', 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    const targetEmail = forgotEmail.trim().toLowerCase()
    if (!resetCode || resetCode.trim().length !== 6) {
      showAlert('Ingresá el código de 6 dígitos enviado a tu correo.', 'warning')
      return
    }
    const passErr = validatePassword(newPassword)
    if (passErr) {
      showAlert(passErr, 'warning')
      return
    }
    if (newPassword !== confirmNewPassword) {
      showAlert('Las contraseñas no coinciden.', 'warning')
      return
    }

    setLoading(true)
    try {
      await api.resetPassword({
        email: targetEmail,
        codigo: resetCode.trim(),
        newPassword
      })
      showAlert('¡Tu contraseña fue actualizada con éxito! Ya podés iniciar sesión.', 'success')
      setActiveTab('login')
      setLoginEmail(targetEmail)
      setLoginPassword(newPassword)
      setResetCode('')
      setNewPassword('')
      setConfirmNewPassword('')
    } catch (err: any) {
      showAlert(err.message || 'No se pudo restablecer la contraseña.', 'error')
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

        <div style={{ textAlign: 'center' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-1)' }}>
            <IconBrandLogo size={28} />
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
          <div role="alert" style={{
            padding: '12px 14px',
            backgroundColor: '#fef2f2',
            color: '#991b1b',
            borderRadius: 'var(--radius-sm)',
            fontSize: '13px',
            border: '1px solid #fca5a5',
            lineHeight: '1.4',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px'
          }}>
            <span style={{ fontSize: '16px', lineHeight: 1 }}>⚠️</span>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '6px' }}>
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
                  onClick={() => { setActiveTab('forgot'); setError(null); setSuccess(null); }}
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
              {loading ? 'Iniciando sesión...' : 'Iniciar Sesión'}
            </button>

            <div style={{ display: 'flex', alignItems: 'center', margin: 'var(--space-2) 0' }}>
              <div style={{ flex: 1, height: '1px', backgroundColor: 'var(--color-border)' }} />
              <span style={{ padding: '0 var(--space-3)', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>o ingresar con</span>
              <div style={{ flex: 1, height: '1px', backgroundColor: 'var(--color-border)' }} />
            </div>

            <div id="google-signin-btn" style={{ display: 'flex', justifyContent: 'center' }} />
          </form>
        )}

        {/* ── TAB 2: REGISTER ── */}
        {activeTab === 'register' && (
          <form onSubmit={handleFormRegister} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-1)' }}>
              <span style={{ fontSize: 'var(--text-xs)', fontWeight: '600', color: 'var(--color-primary)' }}>
                Paso {regStep} de {totalSteps}
              </span>
              <div style={{ display: 'flex', gap: '4px' }}>
                {Array.from({ length: totalSteps }).map((_, i) => (
                  <div
                    key={i}
                    style={{
                      width: '24px',
                      height: '4px',
                      borderRadius: '2px',
                      backgroundColor: i + 1 <= regStep ? 'var(--color-primary)' : 'var(--neutral-200)',
                      transition: 'background-color 0.2s'
                    }}
                  />
                ))}
              </div>
            </div>

            {regStep === 1 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                <div className="form-group">
                  <label className="form-label form-label--required">Tipo de Usuario</label>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)' }}>
                    <button
                      type="button"
                      onClick={() => setRole('PACIENTE')}
                      style={{
                        padding: 'var(--space-3)',
                        border: role === 'PACIENTE' ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)',
                        backgroundColor: role === 'PACIENTE' ? '#f0f9ff' : 'var(--color-surface)',
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '4px'
                      }}
                    >
                      <IconPatient size={24} />
                      <span style={{ fontSize: '13px', fontWeight: '600' }}>Paciente</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setRole('PSIQUIATRA')}
                      style={{
                        padding: 'var(--space-3)',
                        border: role === 'PSIQUIATRA' ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)',
                        backgroundColor: role === 'PSIQUIATRA' ? '#f0f9ff' : 'var(--color-surface)',
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '4px'
                      }}
                    >
                      <IconStethoscope size={24} />
                      <span style={{ fontSize: '13px', fontWeight: '600' }}>Profesional</span>
                    </button>
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label form-label--required">Email</label>
                  <input
                    type="email"
                    className="form-input"
                    placeholder="ejemplo@correo.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label form-label--required">Contraseña</label>
                  <input
                    type="password"
                    className="form-input"
                    placeholder="********"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label form-label--required">Confirmar Contraseña</label>
                  <input
                    type="password"
                    className="form-input"
                    placeholder="********"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                  />
                </div>

                <button type="button" className="btn btn--primary" onClick={handleNextStep} style={{ width: '100%', marginTop: 'var(--space-2)' }}>
                  Siguiente →
                </button>
              </div>
            )}

            {regStep === 2 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
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

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
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
                    <input
                      type="date"
                      className="form-input"
                      min="1900-01-01"
                      max={new Date().toISOString().split('T')[0]}
                      value={fechaNacimiento}
                      onChange={(e) => setFechaNacimiento(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 'var(--space-3)' }}>
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
                  <label className="form-label form-label--required">Teléfono Móvil (WhatsApp)</label>
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
                      style={{
                        borderRadius: '0 var(--radius-md) var(--radius-md) 0'
                      }}
                      required
                    />
                  </div>
                </div>

                {role === 'PACIENTE' && (
                  <div className="form-group">
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px' }}>
                      <input
                        type="checkbox"
                        checked={hasObraSocial}
                        onChange={(e) => setHasObraSocial(e.target.checked)}
                      />
                      Tengo Cobertura / Obra Social o Prepaga
                    </label>
                  </div>
                )}

                <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  <button type="button" className="btn btn--ghost" onClick={handlePrevStep} style={{ flex: 1 }}>
                    ← Anterior
                  </button>
                  {totalSteps > 2 ? (
                    <button type="button" className="btn btn--primary" onClick={handleNextStep} style={{ flex: 1 }}>
                      Siguiente →
                    </button>
                  ) : (
                    <button type="submit" disabled={loading} className="btn btn--primary" style={{ flex: 1 }}>
                      {loading ? 'Creando cuenta...' : 'Finalizar Registro'}
                    </button>
                  )}
                </div>
              </div>
            )}

            {regStep === 3 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {role === 'PACIENTE' ? (
                  <>
                    <div className="form-group">
                      <label className="form-label form-label--required">Obra Social / Prepaga</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="OSDE, Swiss Medical, Galeno, etc."
                        value={obraSocial}
                        onChange={(e) => setObraSocial(e.target.value)}
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label form-label--required">Número de Afiliado</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="Ej: 123456789/01"
                        value={numAfiliado}
                        onChange={(e) => setNumAfiliado(e.target.value)}
                        required
                      />
                    </div>
                  </>
                ) : (
                  <>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                      <div className="form-group">
                        <label className="form-label form-label--required">Título Profesional</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="Médico Psiquiatra"
                          value={titulo}
                          onChange={(e) => setTitulo(e.target.value)}
                          required
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Especialidad</label>
                        <select className="form-select" value={specialty} onChange={(e) => setSpecialty(e.target.value)} required>
                          <option value="">Seleccionar...</option>
                          {ESPECIALIDADES_GRUPOS.map((esp) => (
                            <option key={esp} value={esp}>{esp}</option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-2)' }}>
                      <div className="form-group">
                        <label className="form-label form-label--required">Tipo Matrícula</label>
                        <select className="form-select" value={matriculaTipo} onChange={(e) => setMatriculaTipo(e.target.value)}>
                          <option value="MN">MN - Matrícula Nacional</option>
                          <option value="MP">MP - Matrícula Provincial</option>
                          <option value="MN_MP">MN / MP - Nacional y Provincial</option>
                        </select>
                      </div>
                      {(matriculaTipo === 'MP' || matriculaTipo === 'MN_MP') && (
                        <div className="form-group">
                          <label className="form-label form-label--required">Provincia</label>
                          <select className="form-select" value={matriculaProvincia} onChange={(e) => setMatriculaProvincia(e.target.value)}>
                            <option value="">Provincia...</option>
                            {PROVINCIAS_ARGENTINA.map((p) => (
                              <option key={p} value={p}>{p}</option>
                            ))}
                          </select>
                        </div>
                      )}
                      <div className="form-group" style={{ gridColumn: (matriculaTipo === 'MP' || matriculaTipo === 'MN_MP') ? 'span 1' : 'span 2' }}>
                        <label className="form-label form-label--required">N° Matrícula</label>
                        <input
                          type="text"
                          inputMode="numeric"
                          maxLength={10}
                          className="form-input"
                          placeholder="123456"
                          value={matricula}
                          onChange={(e) => setMatricula(e.target.value.replace(/[^\d]/g, '').slice(0, 10))}
                          required
                        />
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                      <div className="form-group">
                        <label className="form-label">CUIT / CUIL</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="20123456789"
                          value={cuil}
                          onChange={(e) => setCuil(e.target.value)}
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Código REFEPS</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="123456"
                          value={codigoReFeps}
                          onChange={(e) => setCodigoReFeps(e.target.value)}
                          required
                        />
                      </div>

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
              <div style={{ display: 'inline-flex', padding: '12px', backgroundColor: '#e0f2fe', borderRadius: '50%', marginBottom: '8px' }}>
                <IconMail size={32} />
              </div>
              <h3 style={{ margin: '4px 0', fontSize: '16px', color: 'var(--color-primary)' }}>Confirmación de Email</h3>
              <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
                Ingresá el código de 6 dígitos enviado a tu casilla:
              </p>
              <div style={{ backgroundColor: '#f1f5f9', padding: '8px 12px', borderRadius: '8px', border: '1px solid #e2e8f0', marginTop: '8px', fontSize: '13px', fontWeight: 'bold', color: '#1e293b', wordBreak: 'break-all' }}>
                {pendingEmail || loginEmail || email || 'tu casilla de correo'}
              </div>
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
                style={{ background: 'none', border: 'none', color: 'var(--color-primary)', fontSize: '12px', cursor: 'pointer', padding: 0, fontWeight: '600' }}
              >
                {loading ? 'Enviando...' : 'Reenviar código'}
              </button>
              <button
                type="button"
                onClick={() => { setActiveTab('login'); }}
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
              <div style={{ display: 'inline-flex', padding: '12px', backgroundColor: '#eff6ff', borderRadius: '50%', marginBottom: '8px' }}>
                <IconKey size={32} />
              </div>
              <h3 style={{ margin: '4px 0', fontSize: '16px', color: 'var(--color-primary)' }}>Recuperar Contraseña</h3>
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
              onClick={() => { setActiveTab('login'); }}
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
              <div style={{ display: 'inline-flex', padding: '12px', backgroundColor: '#f0fdf4', borderRadius: '50%', marginBottom: '8px' }}>
                <IconLock size={32} />
              </div>
              <h3 style={{ margin: '4px 0', fontSize: '16px', color: 'var(--color-primary)' }}>Restablecer Contraseña</h3>
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
              onClick={() => { setActiveTab('login'); }}
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
