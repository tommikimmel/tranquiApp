import { useState, useEffect } from 'react'
import { Icon } from './Icon'
import { api } from '../api/api'
import AddressMapPicker from './AddressMapPicker'
import DateInputDDMMYYYY from './DateInputDDMMYYYY'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useAlert } from '../context/AlertContext'
import { OBRAS_SOCIALES } from '../constants/obrasSociales'
import ChoosePlanView from './ChoosePlanView'

interface LoginPageProps {
  onLoginSuccess: (user: any) => void
  onBack: () => void
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

function IconAlertTriangle({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: size, height: size, flexShrink: 0 }}>
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

function IconSparkle({ size = 14 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, flexShrink: 0 }}>
      <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6.3 6.3l2.1 2.1M15.6 15.6l2.1 2.1M17.7 6.3l-2.1 2.1M8.4 15.6l-2.1 2.1" />
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
  const [activeTab, setActiveTab] = useState<'login' | 'register' | 'verify' | 'forgot' | 'reset' | 'choose-plan'>('login')
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
  const [customObraSocial, setCustomObraSocial] = useState('')
  const [numAfiliado, setNumAfiliado] = useState('')

  // Pro spec
  const [matricula, setMatricula] = useState('')
  const [titulo, setTitulo] = useState('')
  const [specialty, setSpecialty] = useState('')
  const [cuil, setCuil] = useState('')
  const [domicilioAtencion, setDomicilioAtencion] = useState('')
  const [domicilioLat, setDomicilioLat] = useState<number | null>(null)
  const [domicilioLng, setDomicilioLng] = useState<number | null>(null)
  const [domicilioAtencionTorre, setDomicilioAtencionTorre] = useState('')
  const [domicilioAtencionPiso, setDomicilioAtencionPiso] = useState('')
  const [domicilioAtencionDepto, setDomicilioAtencionDepto] = useState('')
  const [domicilioAtencionBarrio, setDomicilioAtencionBarrio] = useState('')
  const [matriculaTipo, setMatriculaTipo] = useState('MN')
  const [matriculaProvincia, setMatriculaProvincia] = useState('')
  const [ofreceOnline, setOfreceOnline] = useState(true)
  const [ofrecePresencial, setOfrecePresencial] = useState(false)
  const [fotoUrl, setFotoUrl] = useState('')
  const [acceptedTerms, setAcceptedTerms] = useState(false)

  // Subscription & Fiscal state (§6)
  const [profession, setProfession] = useState<'psiquiatra' | 'psicologo' | 'otro'>('psiquiatra')
  const [taxId, setTaxId] = useState('')
  const [legalName, setLegalName] = useState('')
  const [ivaConditionId, setIvaConditionId] = useState<number>(6) // 6 = Monotributo
  const [fiscalAddress, setFiscalAddress] = useState('')
  const [licenseDocumentUrl, setLicenseDocumentUrl] = useState('')

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

  // Precios del plan sugerido: siempre del catálogo real (los edita el admin), nunca hardcodeados.
  const [preciosPlanes, setPreciosPlanes] = useState<Record<string, number>>({})
  useEffect(() => {
    let cancelado = false
    Promise.resolve()
      .then(() => api.getSubscriptionPlans())
      .then((planes: any) => {
        if (cancelado || !Array.isArray(planes)) return
        const precios: Record<string, number> = {}
        planes.forEach((p: any) => { if (p?.code && typeof p.priceArs === 'number') precios[p.code] = p.priceArs })
        setPreciosPlanes(precios)
      })
      .catch(() => {})
    return () => { cancelado = true }
  }, [])
  const precioPlan = (code: string) =>
    preciosPlanes[code] != null ? ` ($${preciosPlanes[code].toLocaleString('es-AR')} ARS/mes)` : ''

  // Load Google Identity Services and (re)render the button every time the login tab's
  // <div id="google-signin-btn"> mounts. That div only exists while activeTab === 'login' —
  // switching to "Registrarse" unmounts it (destroying the GSI iframe inside), and GSI never
  // redraws it on its own when the div reappears, so this must re-run on every switch back to
  // "Iniciar Sesión", not just once on the component's first mount.
  useEffect(() => {
    if (activeTab !== 'login') return

    const scriptId = 'google-gsi-client'
    const renderGoogleButton = () => {
      // @ts-ignore
      if (window.google) {
        // @ts-ignore
        window.google.accounts.id.initialize({
          // Must match the backend's GOOGLE_CLIENT_ID (google.client-id) — otherwise the ID
          // token's audience won't match what GoogleAuthService verifies and every Google
          // sign-in attempt is rejected with 401 "Token de Google inválido".
          client_id: "468339217921-8i4vi6uhsu09rf87f1sovgiltd9rdc61.apps.googleusercontent.com",
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
      script.onload = renderGoogleButton
      document.body.appendChild(script)
    } else {
      setTimeout(renderGoogleButton, 100)
    }
  }, [activeTab])

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

    if (!acceptedTerms) {
      showAlert('Debés aceptar los términos y condiciones para registrarte.', 'warning')
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
      aceptaTerminos: acceptedTerms,
    }

    if (role === 'PACIENTE') {
      if (hasObraSocial) {
        const effectiveObraSocial = obraSocial === 'Otra' ? customObraSocial.trim() : obraSocial
        if (!effectiveObraSocial || !numAfiliado) {
          showAlert('Por favor, completá los datos de tu obra social.', 'warning')
          return
        }
        payload.obraSocial = effectiveObraSocial
        payload.numAfiliado = numAfiliado
      } else {
        payload.obraSocial = null
        payload.numAfiliado = null
      }
    } else {
      if (!titulo || !specialty || !matricula) {
        showAlert('Por favor, completá los datos profesionales obligatorios.', 'warning')
        return
      }
      if (!ofreceOnline && !ofrecePresencial) {
        showAlert('Seleccioná al menos una modalidad de consulta (Online o Presencial).', 'warning')
        return
      }
      if (!domicilioAtencion) {
        showAlert('Indicá tu dirección profesional: QBI2/Innovamed la exige para emitir recetas electrónicas, incluso si atendés 100% online.', 'warning')
        return
      }
      if (domicilioAtencion.trim().length < 8 || domicilioAtencion.trim().length > 140) {
        showAlert('El domicilio de atención debe tener entre 8 y 140 caracteres. Usá el buscador y elegí una sugerencia en vez de pegar la dirección completa.', 'warning')
        return
      }
      payload.matricula = matricula
      payload.titulo = titulo
      payload.specialty = specialty
      payload.cuit = taxId.trim() || cuil.trim()
      payload.cuil = (taxId.trim() || cuil.trim()) ? Number((taxId.trim() || cuil.trim()).replace(/[^\d]/g, '')) : null
      payload.domicilioAtencion = domicilioAtencion
      payload.domicilioLat = domicilioLat
      payload.domicilioLng = domicilioLng
      payload.domicilioAtencionTorre = ofrecePresencial ? domicilioAtencionTorre.trim() : ''
      payload.domicilioAtencionPiso = ofrecePresencial ? domicilioAtencionPiso.trim() : ''
      payload.domicilioAtencionDepto = ofrecePresencial ? domicilioAtencionDepto.trim() : ''
      payload.domicilioAtencionBarrio = ofrecePresencial ? domicilioAtencionBarrio.trim() : ''
      payload.matriculaTipo = matriculaTipo
      payload.matriculaProvincia = matriculaProvincia
      payload.matriculaNumero = Number(matricula)
      payload.ofreceOnline = ofreceOnline
      payload.ofrecePresencial = ofrecePresencial

      // Plan Suscripciones (§6)
      payload.profession = profession
      payload.licenseType = matriculaTipo
      payload.licenseNumber = matricula
      payload.licenseJurisdiction = matriculaProvincia || 'Nacional'
      payload.licenseDocumentUrl = licenseDocumentUrl || null
      payload.taxIdType = 'CUIT'
      payload.taxId = taxId.trim() || cuil.trim()
      payload.legalName = legalName.trim() || `${nombre} ${apellido}`.trim()
      payload.ivaConditionId = Number(ivaConditionId) || 6
      payload.fiscalAddress = fiscalAddress.trim() || domicilioAtencion.trim()
    }

    setLoading(true)
    try {
      await api.register(payload)
      setPendingEmail(cleanEmail)
      if (role === 'PSIQUIATRA') {
        // Los profesionales ven el catálogo de planes antes que nada — recién al continuar pasan
        // a verificar el email (ver ChoosePlanView, variant "post-register").
        setActiveTab('choose-plan')
      } else {
        showAlert('¡Registro exitoso! Enviamos un código de 6 dígitos a tu correo para activar tu cuenta.', 'success')
        setActiveTab('verify')
      }
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

  if (activeTab === 'choose-plan') {
    return (
      <ChoosePlanView
        variant="post-register"
        onContinue={() => {
          showAlert('¡Registro exitoso! Enviamos un código de 6 dígitos a tu correo para activar tu cuenta.', 'success')
          setActiveTab('verify')
        }}
      />
    )
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
          <Icon.ArrowLeft /> Volver a la página principal
        </button>

        <div style={{ textAlign: 'center' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-1)' }}>
            <img src="/logoTranquiApp.webp" alt="Tranqui App" width={28} height={28} style={{ display: 'block' }} />
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
            <IconAlertTriangle size={16} />
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
            <p style={{ fontSize: '11px', color: 'var(--color-text-secondary)', textAlign: 'center', margin: 'var(--space-2) 0 0' }}>
              Al continuar con Google, aceptás nuestra{' '}
              <a href="/privacidad" target="_blank" rel="noreferrer" style={{ color: 'var(--color-primary)' }}>
                Política de Privacidad
              </a>.
            </p>
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
                  <div className="role-select">
                    <button
                      type="button"
                      className={`role-select__card ${role === 'PACIENTE' ? 'active' : ''}`}
                      onClick={() => setRole('PACIENTE')}
                    >
                      <span className="role-select__icon"><IconPatient size={24} /></span>
                      Paciente
                    </button>
                    <button
                      type="button"
                      className={`role-select__card ${role === 'PSIQUIATRA' ? 'active' : ''}`}
                      onClick={() => setRole('PSIQUIATRA')}
                    >
                      <span className="role-select__icon"><IconStethoscope size={24} /></span>
                      Profesional
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
                  Siguiente <Icon.ArrowRight />
                </button>
              </div>
            )}

            {regStep === 2 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
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

                <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
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

                <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 'var(--space-3)' }}>
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
                  <div className="phone-input">
                    <span className="phone-input__prefix">+54</span>
                    <input
                      type="tel"
                      className="form-input phone-input__field"
                      placeholder="1112345678"
                      maxLength={11}
                      value={telefono.replace(/^\+54\s*/, '')}
                      onChange={(e) => setTelefono(e.target.value.replace(/[^\d]/g, '').slice(0, 11))}
                      required
                    />
                  </div>
                </div>

                {role === 'PACIENTE' && (
                  <div className="form-group">
                    <label className={`check-chip ${hasObraSocial ? 'active' : ''}`}>
                      <input
                        type="checkbox"
                        checked={hasObraSocial}
                        onChange={(e) => setHasObraSocial(e.target.checked)}
                      />
                      Tengo Cobertura / Obra Social o Prepaga
                    </label>
                  </div>
                )}

                {totalSteps <= 2 && (
                  <div className="form-group form-group--checkbox" style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                    <input
                      id="acceptedTermsRegisterStep2"
                      type="checkbox"
                      checked={acceptedTerms}
                      onChange={(e) => setAcceptedTerms(e.target.checked)}
                      style={{ marginTop: '3px' }}
                    />
                    <label htmlFor="acceptedTermsRegisterStep2" style={{ fontSize: '12px', color: 'var(--color-text-secondary)', lineHeight: '1.4', cursor: 'pointer' }}>
                      Acepto los <a href="/terminos" target="_blank" rel="noreferrer">términos de servicio</a> y la{' '}
                      <a href="/privacidad" target="_blank" rel="noreferrer">política de privacidad</a> de Tranqui App.
                    </label>
                  </div>
                )}

                <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  <button type="button" className="btn btn--ghost" onClick={handlePrevStep} style={{ flex: 1 }}>
                    <Icon.ArrowLeft /> Anterior
                  </button>
                  {totalSteps > 2 ? (
                    <button type="button" className="btn btn--primary" onClick={handleNextStep} style={{ flex: 1 }}>
                      Siguiente <Icon.ArrowRight />
                    </button>
                  ) : (
                    <button type="submit" disabled={loading || !acceptedTerms} className="btn btn--primary" style={{ flex: 1 }}>
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
                      <select
                        className="form-select"
                        value={OBRAS_SOCIALES.includes(obraSocial) ? obraSocial : (obraSocial ? 'Otra' : '')}
                        onChange={(e) => {
                          const val = e.target.value
                          setObraSocial(val)
                          if (val !== 'Otra') {
                            setCustomObraSocial('')
                          }
                        }}
                        required
                      >
                        <option value="">Seleccionar Obra Social...</option>
                        {OBRAS_SOCIALES.map((os) => (
                          <option key={os} value={os}>{os}</option>
                        ))}
                      </select>
                    </div>
                    {(obraSocial === 'Otra' || (!OBRAS_SOCIALES.includes(obraSocial) && obraSocial !== '')) && (
                      <div className="form-group">
                        <label className="form-label form-label--required">Nombre de tu Obra Social</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="Ej: OSAPM, Mutualidad, etc."
                          value={customObraSocial || (OBRAS_SOCIALES.includes(obraSocial) ? '' : obraSocial)}
                          onChange={(e) => {
                            setCustomObraSocial(e.target.value)
                            setObraSocial('Otra')
                          }}
                          required
                        />
                      </div>
                    )}
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
                    {/* 1. Profesión y Plan Asociado (§6) */}
                    <div className="form-group">
                      <label className="form-label form-label--required">Profesión Principal</label>
                      <select
                        className="form-select"
                        value={profession}
                        onChange={(e) => {
                          const prof = e.target.value as any
                          setProfession(prof)
                          if (prof === 'psiquiatra') {
                            setTitulo('Médico Psiquiatra')
                            setSpecialty('Psiquiatría')
                          } else if (prof === 'psicologo') {
                            setTitulo('Licenciado en Psicología')
                            setSpecialty('Psicología Clínica')
                          }
                        }}
                        required
                      >
                        <option value="psiquiatra">Médico Psiquiatra (Emisión de Recetas Electrónicas Oficiales QBI2)</option>
                        <option value="psicologo">Licenciado en Psicología (Consultorio y Turnos)</option>
                        <option value="otro">Otro Profesional de la Salud Mental</option>
                      </select>
                    </div>

                    {/* Banner informativo de Plan y Cupos */}
                    <div style={{ padding: '10px 14px', backgroundColor: 'var(--green-50)', border: '1px solid var(--green-200)', borderRadius: 'var(--radius-sm)', fontSize: '12px', display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                      <span style={{ color: 'var(--color-primary)', marginTop: '2px' }}><IconSparkle size={14} /></span>
                      <span><strong>Plan sugerido para tu profesión:</strong>{' '}
                      {profession === 'psiquiatra' ? (
                        <span>
                          <strong>Plan Clínico{precioPlan('clinico')}</strong> · Incluye módulo oficial de Recetas Electrónicas QBI2 con psicofármacos y firma digital.
                        </span>
                      ) : (
                        <span>
                          <strong>Plan Consultorio{precioPlan('consultorio')}</strong> · Incluye adquisición de pacientes por zona geográfica, agenda y turnero online.
                        </span>
                      )}
                      </span>
                    </div>

                    <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                      <div className="form-group">
                        <label className="form-label form-label--required">Título Profesional</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder={profession === 'psiquiatra' ? 'Médico Psiquiatra' : 'Lic. en Psicología'}
                          value={titulo}
                          onChange={(e) => setTitulo(e.target.value)}
                          required
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Especialidad</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="Psiquiatría Adultos / TCC / etc."
                          value={specialty}
                          onChange={(e) => setSpecialty(e.target.value)}
                          required
                        />
                      </div>
                    </div>

                    <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-2)' }}>
                      <div className="form-group">
                        <label className="form-label form-label--required">Tipo Matrícula</label>
                        <select className="form-select" value={matriculaTipo} onChange={(e) => setMatriculaTipo(e.target.value)}>
                          <option value="MN">MN - Matrícula Nacional</option>
                          <option value="MP">MP - Matrícula Provincial</option>
                          <option value="MP_psico">MP - Colegio de Psicólogos</option>
                        </select>
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Provincia / Jurisdicción</label>
                        <select className="form-select" value={matriculaProvincia} onChange={(e) => setMatriculaProvincia(e.target.value)} required>
                          <option value="">Provincia...</option>
                          {PROVINCIAS_ARGENTINA.map((p) => (
                            <option key={p} value={p}>{p}</option>
                          ))}
                        </select>
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">N° Matrícula</label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="123456"
                          value={matricula}
                          onChange={(e) => setMatricula(e.target.value.replace(/[^\d]/g, '').slice(0, 10))}
                          required
                        />
                      </div>
                    </div>

                    {/* 2. Datos Fiscales Obligatorios ARCA RG 5616 (§6) */}
                    <div style={{ padding: '12px', backgroundColor: '#F9FAFB', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                      <strong style={{ fontSize: '13px', color: 'var(--color-text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 21h18M3 10h18M5 10v11M9 10v11M15 10v11M19 10v11M12 2 2 7h20z"/></svg>
                        Datos Fiscales ARCA (Facturación Electrónica RG 5616)
                      </strong>

                      <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label form-label--required">CUIT / CUIL Fiscal</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="20123456789"
                            maxLength={11}
                            value={taxId || cuil}
                            onChange={(e) => {
                              const val = e.target.value.replace(/[^\d]/g, '').slice(0, 11)
                              setTaxId(val)
                              setCuil(val)
                            }}
                            required
                          />
                        </div>

                        <div className="form-group">
                          <label className="form-label form-label--required">Razón Social / Nombre Fiscal</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="Nombre como figura en AFIP/ARCA"
                            value={legalName || (nombre ? `${nombre} ${apellido}` : '')}
                            onChange={(e) => setLegalName(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label form-label--required">Condición frente al IVA</label>
                          <select
                            className="form-select"
                            value={ivaConditionId}
                            onChange={(e) => setIvaConditionId(Number(e.target.value))}
                          >
                            <option value={6}>Responsable Monotributo</option>
                            <option value={1}>IVA Responsable Inscripto</option>
                            <option value={4}>IVA Sujeto Exento</option>
                          </select>
                        </div>

                        <div className="form-group">
                          <label className="form-label form-label--required">Domicilio Fiscal</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="Domicilio registrado en ARCA"
                            value={fiscalAddress || domicilioAtencion}
                            onChange={(e) => setFiscalAddress(e.target.value)}
                            required
                          />
                        </div>
                      </div>
                    </div>

                    <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                      <div className="form-group" style={{ gridColumn: 'span 2' }}>
                        <label className="form-label form-label--required">Modalidades de Atención</label>
                        <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: '4px' }}>
                          <label className={`check-chip check-chip--auto ${ofreceOnline ? 'active' : ''}`}>
                            <input type="checkbox" checked={ofreceOnline} onChange={(e) => setOfreceOnline(e.target.checked)} />
                            <span className="check-chip__icon"><IconVideoCall size={16} /></span> Online / Telemedicina
                          </label>
                          <label className={`check-chip check-chip--auto ${ofrecePresencial ? 'active' : ''}`}>
                            <input type="checkbox" checked={ofrecePresencial} onChange={(e) => setOfrecePresencial(e.target.checked)} />
                            <span className="check-chip__icon"><IconBuilding size={16} /></span> Presencial
                          </label>
                        </div>
                      </div>

                      <div className="form-group" style={{ gridColumn: 'span 2' }}>
                        <label className="form-label form-label--required">
                          {ofrecePresencial ? 'Domicilio de Atención Presencial' : 'Dirección Profesional'}
                        </label>
                        {!ofrecePresencial && (
                          <p style={{ fontSize: '12px', color: 'var(--color-text-secondary)', margin: '0 0 6px' }}>
                            QBI2/Innovamed exige un domicilio profesional para emitir recetas electrónicas
                            aunque atiendas 100% online (puede ser tu domicilio particular; no se muestra a pacientes).
                          </p>
                        )}
                        <AddressMapPicker
                          direccion={domicilioAtencion}
                          onDireccionChange={setDomicilioAtencion}
                          lat={domicilioLat}
                          lng={domicilioLng}
                          onLocationChange={(lat: number, lng: number) => {
                            setDomicilioLat(lat)
                            setDomicilioLng(lng)
                          }}
                        />
                      </div>

                      {ofrecePresencial && (
                        <>
                          <div className="form-group">
                            <label className="form-label" htmlFor="reg-domicilio-torre">Torre</label>
                            <input id="reg-domicilio-torre" className="form-input" type="text" maxLength={50} placeholder="Ej: B" value={domicilioAtencionTorre} onChange={(e) => setDomicilioAtencionTorre(e.target.value)} />
                          </div>
                          <div className="form-group">
                            <label className="form-label" htmlFor="reg-domicilio-piso">Piso</label>
                            <input id="reg-domicilio-piso" className="form-input" type="text" maxLength={20} placeholder="Ej: 3" value={domicilioAtencionPiso} onChange={(e) => setDomicilioAtencionPiso(e.target.value)} />
                          </div>
                          <div className="form-group">
                            <label className="form-label" htmlFor="reg-domicilio-depto">Depto</label>
                            <input id="reg-domicilio-depto" className="form-input" type="text" maxLength={20} placeholder="Ej: A" value={domicilioAtencionDepto} onChange={(e) => setDomicilioAtencionDepto(e.target.value)} />
                          </div>
                          <div className="form-group">
                            <label className="form-label" htmlFor="reg-domicilio-barrio">Barrio</label>
                            <input id="reg-domicilio-barrio" className="form-input" type="text" maxLength={100} placeholder="Ej: Nueva Córdoba" value={domicilioAtencionBarrio} onChange={(e) => setDomicilioAtencionBarrio(e.target.value)} />
                          </div>
                        </>
                      )}
                    </div>
                  </>
                )}

                <div className="form-group form-group--checkbox" style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                  <input
                    id="acceptedTermsRegisterStep3"
                    type="checkbox"
                    checked={acceptedTerms}
                    onChange={(e) => setAcceptedTerms(e.target.checked)}
                    style={{ marginTop: '3px' }}
                  />
                  <label htmlFor="acceptedTermsRegisterStep3" style={{ fontSize: '12px', color: 'var(--color-text-secondary)', lineHeight: '1.4', cursor: 'pointer' }}>
                    Acepto los <a href="/terminos" target="_blank" rel="noreferrer">términos de servicio</a> y la{' '}
                    <a href="/privacidad" target="_blank" rel="noreferrer">política de privacidad</a> de Tranqui App.
                  </label>
                </div>

                <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  <button type="button" className="btn btn--ghost" onClick={handlePrevStep} style={{ flex: 1 }}>
                    <Icon.ArrowLeft /> Anterior
                  </button>
                  <button type="submit" disabled={loading || !acceptedTerms} className="btn btn--primary" style={{ flex: 1 }}>
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
              <Icon.ArrowLeft /> Volver a Iniciar Sesión
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
              <Icon.ArrowLeft /> Cancelar y volver a Iniciar Sesión
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
