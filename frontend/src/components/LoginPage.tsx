import { useState } from 'react'
import { api } from '../api/api'

interface LoginPageProps {
  onLoginSuccess: (user: any) => void
  onBack: () => void
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
  const [activeTab, setActiveTab] = useState<'login' | 'register'>('login')
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
  const [codigoReFeps, setCodigoReFeps] = useState('')
  const [matriculaTipo, setMatriculaTipo] = useState('MN')
  const [matriculaProvincia, setMatriculaProvincia] = useState('')
  const [ofreceOnline, setOfreceOnline] = useState(true)
  const [ofrecePresencial, setOfrecePresencial] = useState(false)
  const [fotoUrl, setFotoUrl] = useState('')

  // Dev simulation state
  const [customEmail, setCustomEmail] = useState('')

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
      const user = await api.login({ email: loginEmail, password: loginPassword })
      localStorage.setItem('tranqui_user', JSON.stringify(user));
      onLoginSuccess(user)
    } catch (err: any) {
      setError(err.message || 'Credenciales inválidas.')
    } finally {
      setLoading(false)
    }
  }

  const totalSteps = role === 'PSIQUIATRA' || hasObraSocial ? 3 : 2;

  const handleNextStep = () => {
    setError(null)
    if (regStep === 1) {
      if (!email || !password || !confirmPassword) {
        setError('Por favor, completá todos los campos.')
        return
      }
      if (!email.includes('@')) {
        setError('Por favor, ingresá un email válido.')
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
    if (password !== confirmPassword) {
      setError('Las contraseñas no coinciden.')
      return
    }

    if (!email || !password || !nombre || !apellido || !numeroDocumento || !telefono) {
      setError('Por favor, completá los datos obligatorios.')
      return
    }

    const payload: any = {
      email,
      password,
      rol: role,
      nombre,
      apellido,
      sexo,
      fechaNacimiento,
      tipoDocumento,
      numeroDocumento: Number(numeroDocumento),
      telefono,
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
      if (!titulo || !specialty || !matricula || !domicilioAtencion || !codigoReFeps) {
        setError('Por favor, completá los datos profesionales obligatorios.')
        return
      }
      payload.matricula = matricula
      payload.titulo = titulo
      payload.specialty = specialty
      payload.cuit = cuil
      payload.cuil = cuil ? Number(cuil) : null
      payload.domicilioAtencion = domicilioAtencion
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
      setSuccess('¡Registro exitoso! Ya podés iniciar sesión con tus credenciales.')
      setActiveTab('login')
      setLoginEmail(email)
      setLoginPassword(password)
      setRegStep(1)
      setConfirmPassword('')
      setHasObraSocial(false)
    } catch (err: any) {
      setError(err.message || 'Error al intentar registrarse.')
    } finally {
      setLoading(false)
    }
  }

  const handleSimulatedLogin = () => {
    if (!customEmail || !customEmail.includes('@')) {
      setError('Por favor, ingresá un email válido para simular.')
      return
    }
    handleGoogleLogin(`mock-${customEmail}`)
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
        maxWidth: activeTab === 'register' ? '640px' : '440px',
        width: '100%',
        padding: 'var(--space-8)',
        boxShadow: 'var(--shadow-lg)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        backgroundColor: 'var(--color-surface)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-6)',
        transition: 'max-width 0.3s ease-in-out'
      }}>
        {/* Header / Logo */}
        <div style={{ textAlign: 'center' }}>
          <img 
            src="/tranqui-icon.webp" 
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
            {activeTab === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}
          </h2>
          <p style={{ 
            fontSize: 'var(--text-sm)',
            color: 'var(--color-text-secondary)'
          }}>
            Gestioná tus turnos, agenda y pacientes en un solo lugar.
          </p>
        </div>

        {/* Tab switchers */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          backgroundColor: 'var(--neutral-100)',
          padding: '4px',
          borderRadius: 'var(--radius-md)'
        }}>
          <button
            onClick={() => { setActiveTab('login'); setError(null); }}
            style={{
              padding: '8px',
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
            onClick={() => { setActiveTab('register'); setError(null); }}
            style={{
              padding: '8px',
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
              <label className="form-label" htmlFor="login-password">Contraseña</label>
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
              justifyContent: 'center',
              margin: 'var(--space-2) 0',
              color: 'var(--color-text-secondary)',
              fontSize: '11px',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              <span style={{ borderBottom: '1px solid var(--color-border)', flex: 1, marginRight: '10px' }}></span>
              o continuar con
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
            <div className="checkout-progress" style={{ marginBottom: 'var(--space-2)' }}>
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
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                {/* Role select */}
                <div className="form-group">
                  <label className="form-label">Registrarme como:</label>
                  <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: '4px' }}>
                    <label style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px', fontWeight: '600' }}>
                      <input
                        type="radio"
                        name="role"
                        value="PACIENTE"
                        checked={role === 'PACIENTE'}
                        onChange={() => setRole('PACIENTE')}
                      />
                      🩺 Paciente
                    </label>
                    <label style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px', fontWeight: '600' }}>
                      <input
                        type="radio"
                        name="role"
                        value="PSIQUIATRA"
                        checked={role === 'PSIQUIATRA'}
                        onChange={() => setRole('PSIQUIATRA')}
                      />
                      ⚕️ Profesional
                    </label>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
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
              </div>
            )}

            {/* PASO 2: Datos Personales */}
            {regStep === 2 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
                  <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                    Datos Personales Básicos
                  </div>
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
                      <option value="M">Masculino (M)</option>
                      <option value="F">Femenino (F)</option>
                      <option value="Otro">Otro</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label form-label--required">Fecha Nacimiento</label>
                    <input type="date" className="form-input" value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label form-label--required">Tipo Documento</label>
                    <select className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
                      <option value="DNI">DNI</option>
                      <option value="LC">Libreta Cívica (LC)</option>
                      <option value="LE">Libreta de Enrolamiento (LE)</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label form-label--required">Número Documento</label>
                    <input type="number" className="form-input" placeholder="12345678" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value)} required />
                  </div>
                  <div className="form-group" style={{ gridColumn: 'span 2' }}>
                    <label className="form-label form-label--required">Teléfono</label>
                    <input type="text" className="form-input" placeholder="+54 9 351 1234567" value={telefono} onChange={(e) => setTelefono(e.target.value)} required />
                  </div>

                  {role === 'PACIENTE' && (
                    <div className="form-group" style={{ gridColumn: 'span 2', marginTop: 'var(--space-2)' }}>
                      <label style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', fontSize: '13px', cursor: 'pointer', fontWeight: '600' }}>
                        <input
                          type="checkbox"
                          checked={hasObraSocial}
                          onChange={(e) => setHasObraSocial(e.target.checked)}
                        />
                        ¿Poseés Obra Social o Prepaga?
                      </label>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* PASO 3: Cobertura o Profesionales */}
            {regStep === 3 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 'var(--space-4)',
                  maxHeight: '300px',
                  overflowY: 'auto',
                  paddingRight: '6px'
                }}>
                  {role === 'PACIENTE' ? (
                    <>
                      <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                        Cobertura Médica
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Obra Social / Prepaga</label>
                        <input type="text" className="form-input" placeholder="Ej: OSDE" value={obraSocial} onChange={(e) => setObraSocial(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Nro. Afiliado</label>
                        <input type="text" className="form-input" placeholder="Ej: 123456789" value={numAfiliado} onChange={(e) => setNumAfiliado(e.target.value)} required />
                      </div>
                    </>
                  ) : (
                    <>
                      <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: '12px', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>
                        Registro Nacional y Datos Profesionales
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Título Profesional</label>
                        <input type="text" className="form-input" placeholder="Ej: Médico Psiquiatra" value={titulo} onChange={(e) => setTitulo(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Especialidad</label>
                        <select className="form-input" value={specialty} onChange={(e) => setSpecialty(e.target.value)} required>
                          <option value="">Seleccioná especialidad</option>
                          {ESPECIALIDADES_GRUPOS.map(esp => (
                            <option key={esp} value={esp}>{esp}</option>
                          ))}
                        </select>
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">CUIL / CUIT</label>
                        <input type="number" className="form-input" placeholder="Ej: 20301234567" value={cuil} onChange={(e) => setCuil(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Código ReFeps</label>
                        <input type="number" className="form-input" placeholder="Ej: 123456" value={codigoReFeps} onChange={(e) => setCodigoReFeps(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Domicilio de Atención</label>
                        <input type="text" className="form-input" placeholder="Ej: Av. Colón 123, Córdoba" value={domicilioAtencion} onChange={(e) => setDomicilioAtencion(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Número de Matrícula</label>
                        <input type="number" className="form-input" placeholder="Ej: 49281" value={matricula} onChange={(e) => setMatricula(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Tipo Matrícula</label>
                        <input type="text" className="form-input" placeholder="Ej: MN o MP" value={matriculaTipo} onChange={(e) => setMatriculaTipo(e.target.value)} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label form-label--required">Provincia de Matrícula</label>
                        <select className="form-input" value={matriculaProvincia} onChange={(e) => setMatriculaProvincia(e.target.value)} required>
                          <option value="">Seleccioná provincia</option>
                          {PROVINCIAS_ARGENTINA.map(p => (
                            <option key={p} value={p}>{p}</option>
                          ))}
                        </select>
                      </div>

                      <div className="form-group" style={{ gridColumn: 'span 2', display: 'flex', gap: '16px', marginTop: '8px' }}>
                        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
                          <input type="checkbox" checked={ofreceOnline} onChange={(e) => setOfreceOnline(e.target.checked)} />
                          💻 Consulta Online
                        </label>
                        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
                          <input type="checkbox" checked={ofrecePresencial} onChange={(e) => setOfrecePresencial(e.target.checked)} />
                          🏢 Consulta Presencial
                        </label>
                      </div>

                      <div className="form-group" style={{ gridColumn: 'span 2' }}>
                        <label className="form-label">Foto de Perfil (Opcional - URL)</label>
                        <input type="text" className="form-input" placeholder="https://ejemplo.com/foto.jpg" value={fotoUrl} onChange={(e) => setFotoUrl(e.target.value)} />
                      </div>
                    </>
                  )}
                </div>
              </div>
            )}

            {/* Wizard Navigation Buttons */}
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
              {regStep > 1 && (
                <button
                  type="button"
                  onClick={handlePrevStep}
                  className="btn btn--secondary"
                  style={{ flex: 1, padding: '10px' }}
                >
                  Anterior
                </button>
              )}
              {regStep < totalSteps ? (
                <button
                  type="button"
                  onClick={handleNextStep}
                  className="btn btn--primary"
                  style={{ flex: 1, padding: '10px' }}
                >
                  Siguiente
                </button>
              ) : (
                <button
                  type="submit"
                  disabled={loading}
                  className="btn btn--primary"
                  style={{ flex: 1, padding: '10px' }}
                >
                  {loading ? 'Creando cuenta...' : 'Finalizar Registro'}
                </button>
              )}
            </div>

          </form>
        )}

        {/* Localhost notice */}
        <p style={{
          fontSize: 'var(--text-xs)',
          color: 'var(--color-text-secondary)',
          textAlign: 'center',
          marginTop: '-var(--space-2)',
          lineHeight: 'var(--line-height-normal)'
        }}>
          💡 <strong>Tip para Pruebas:</strong> Podes registrarte como Paciente o Profesional, y usar la cuenta de administrador <strong>admin@tranqui.com</strong> / clave <strong>admin123</strong> para validaciones.
        </p>

        {/* Development Bypass Card */}
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
            ⚙️ Entorno de Desarrollo (Simulación Google)
          </div>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
            <label htmlFor="custom-email-input" style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-medium)' }}>
              Ingresar email para simulación:
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
                fontFamily: 'var(--font-body)',
                backgroundColor: 'var(--neutral-0)'
              }}
            />
          </div>

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
