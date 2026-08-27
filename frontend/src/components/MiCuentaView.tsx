import React, { useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import '../styles/privacy.css'

type MiCuentaTab = 'datos' | 'suscripcion' | 'password' | 'notificaciones' | 'privacidad' | 'eliminar'

// Small inline icon set, kept local to this view (same visual language as the rest of the
// app: 24x24 viewBox, currentColor stroke, 1.75 weight, rounded caps) so Mi Cuenta doesn't
// need to import from App.tsx's private Icon object.
const MCIcon = {
  User: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
    </svg>
  ),
  CreditCard: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="2" y="5" width="20" height="14" rx="2" /><line x1="2" y1="10" x2="22" y2="10" />
    </svg>
  ),
  Lock: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  ),
  Bell: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: size, height: size }}>
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  ),
  Shield: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  ),
  Trash: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <line x1="10" y1="11" x2="10" y2="17" /><line x1="14" y1="11" x2="14" y2="17" />
    </svg>
  ),
  Phone: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>
  ),
  CalendarIcon: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  ),
  IdCard: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="2" y="5" width="20" height="14" rx="2" /><circle cx="8.5" cy="12" r="2" /><line x1="14" y1="10" x2="19" y2="10" /><line x1="14" y1="14" x2="19" y2="14" />
    </svg>
  ),
  HeartPulse: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M20.42 4.58a5.4 5.4 0 0 0-7.65 0l-.77.78-.77-.78a5.4 5.4 0 0 0-7.65 0C1.46 6.7 1.33 10.28 4 13l8 8 8-8c2.67-2.72 2.54-6.3.42-8.42z" />
      <polyline points="4 11 7 11 9 8 11 14 13 11 16 11" />
    </svg>
  ),
  Mail: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="2" y="4" width="20" height="16" rx="2" /><polyline points="2 6 12 13 22 6" />
    </svg>
  ),
  Save: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
      <polyline points="17 21 17 13 7 13 7 21" /><polyline points="7 3 7 8 15 8" />
    </svg>
  ),
  Key: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <circle cx="7.5" cy="15.5" r="5.5" /><path d="M21 2l-9.6 9.6M15.5 7.5L18 10M13 10l2.5 2.5" />
    </svg>
  ),
  AlertTriangle: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: size, height: size }}>
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  ),
  Download: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
    </svg>
  ),
  ExternalLink: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  ),
}

const TABS: { id: MiCuentaTab; label: string; Icon: (props?: { size?: number }) => React.JSX.Element }[] = [
  { id: 'datos', label: 'Datos personales', Icon: MCIcon.User },
  { id: 'suscripcion', label: 'Mi Suscripción y Facturas', Icon: MCIcon.CreditCard },
  { id: 'password', label: 'Contraseña', Icon: MCIcon.Lock },
  { id: 'notificaciones', label: 'Notificaciones', Icon: MCIcon.Bell },
  { id: 'privacidad', label: 'Acceso a mis datos', Icon: MCIcon.Shield },
  { id: 'eliminar', label: 'Eliminar cuenta', Icon: MCIcon.Trash },
]

// Small helper so field labels can carry a leading icon without repeating this markup everywhere.
function FieldLabel({ htmlFor, icon, children }: { htmlFor: string; icon: React.JSX.Element; children: ReactNode }) {
  return (
    <label className="form-label" htmlFor={htmlFor} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <span style={{ color: 'var(--color-text-secondary)', display: 'inline-flex' }}>{icon}</span>
      {children}
    </label>
  )
}

export default function MiCuentaView({ onLogout, onUserUpdated }: {
  onLogout: () => void
  onUserUpdated?: (user: any) => void
}) {
  useDocumentTitle('Mi Cuenta — Tranqui App')
  const navigate = useNavigate()
  const { showAlert } = useAlert()

  const [activeTab, setActiveTab] = useState<MiCuentaTab>('datos')
  const [loading, setLoading] = useState(true)
  const [cuenta, setCuenta] = useState<any>(null)

  // Datos personales form state
  const [nombre, setNombre] = useState('')
  const [apellido, setApellido] = useState('')
  const [telefono, setTelefono] = useState('')
  const [sexo, setSexo] = useState('')
  const [fechaNacimiento, setFechaNacimiento] = useState('')
  const [tipoDocumento, setTipoDocumento] = useState('')
  const [numeroDocumento, setNumeroDocumento] = useState('')
  const [tieneObraSocial, setTieneObraSocial] = useState(false)
  const [obraSocial, setObraSocial] = useState('')
  const [numAfiliado, setNumAfiliado] = useState('')
  const [savingDatos, setSavingDatos] = useState(false)

  // Password form state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)

  // Notificaciones state
  const [notifEmail, setNotifEmail] = useState(true)
  const [notifWhatsapp, setNotifWhatsapp] = useState(true)
  const [savingNotif, setSavingNotif] = useState(false)

  // Privacidad state
  const [solicitandoCopiaDatos, setSolicitandoCopiaDatos] = useState(false)
  const [copiaDatosSolicitada, setCopiaDatosSolicitada] = useState(false)

  // Eliminar cuenta state
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteConfirmText, setDeleteConfirmText] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  // Suscripciones y Facturas ARCA state
  const [mySub, setMySub] = useState<any>(null)
  const [myInvoices, setMyInvoices] = useState<any[]>([])

  useEffect(() => {
    api.getMiCuenta()
      .then((data: any) => {
        setCuenta(data)
        setNombre(data.nombre || '')
        setApellido(data.apellido || '')
        setTelefono((data.telefono || '').replace(/^\+54\s*/, ''))
        setSexo(data.sexo || '')
        setFechaNacimiento(data.fechaNacimiento || '')
        setTipoDocumento(data.tipoDocumento || '')
        setNumeroDocumento(data.numeroDocumento != null ? String(data.numeroDocumento) : '')
        setTieneObraSocial(!!(data.obraSocial || data.numAfiliado))
        setObraSocial(data.obraSocial || '')
        setNumAfiliado(data.numAfiliado || '')
        setNotifEmail(data.notificacionesEmailHabilitadas !== false)
        setNotifWhatsapp(data.notificacionesWhatsappHabilitadas !== false)
      })
      .catch((err) => {
        console.error('Error al cargar Mi Cuenta:', err)
        showAlert('No pudimos cargar los datos de tu cuenta. Intentá de nuevo.', 'error')
      })
      .finally(() => setLoading(false))

    // Cargar suscripción y facturas ARCA
    api.getMySubscription().then((s: any) => setMySub(s)).catch(() => {})
    api.getMyInvoices().then((invs: any) => setMyInvoices(Array.isArray(invs) ? invs : [])).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleToggleObraSocial = (value: boolean) => {
    setTieneObraSocial(value)
    if (!value) {
      // El paciente indicó que no tiene obra social: limpiamos los campos para que
      // no quede guardado un dato que ya no aplica.
      setObraSocial('')
      setNumAfiliado('')
    }
  }

  const handleSaveDatos = async () => {
    setSavingDatos(true)
    try {
      const updated = await api.actualizarMiCuenta({
        ...cuenta,
        nombre, apellido, telefono, sexo,
        fechaNacimiento: fechaNacimiento || null,
        tipoDocumento,
        numeroDocumento: numeroDocumento ? Number(numeroDocumento) : null,
        obraSocial: tieneObraSocial ? obraSocial : null,
        numAfiliado: tieneObraSocial ? numAfiliado : null,
      })
      setCuenta(updated)
      onUserUpdated?.((prev: any) => ({ ...prev, nombre: updated.nombre, telefono: updated.telefono }))
      showAlert('Tus datos se guardaron correctamente ✓', 'success')
    } catch (err: any) {
      showAlert(err.message || 'No pudimos guardar tus datos. Intentá de nuevo.', 'error')
    } finally {
      setSavingDatos(false)
    }
  }

  const handleChangePassword = async () => {
    if (newPassword !== confirmPassword) {
      showAlert('Las contraseñas nuevas no coinciden.', 'error')
      return
    }
    setSavingPassword(true)
    try {
      await api.cambiarPassword({ currentPassword: currentPassword || undefined, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      showAlert('Contraseña actualizada correctamente ✓', 'success')
    } catch (err: any) {
      showAlert(err.message || 'No pudimos actualizar tu contraseña.', 'error')
    } finally {
      setSavingPassword(false)
    }
  }

  const handleToggleNotif = async (which: 'email' | 'whatsapp', value: boolean) => {
    const nextEmail = which === 'email' ? value : notifEmail
    const nextWhatsapp = which === 'whatsapp' ? value : notifWhatsapp
    if (which === 'email') setNotifEmail(value); else setNotifWhatsapp(value)
    setSavingNotif(true)
    try {
      await api.actualizarPreferenciasNotificacion({ emailHabilitado: nextEmail, whatsappHabilitado: nextWhatsapp })
    } catch (err: any) {
      // revert on failure
      if (which === 'email') setNotifEmail(!value); else setNotifWhatsapp(!value)
      showAlert(err.message || 'No pudimos guardar tu preferencia.', 'error')
    } finally {
      setSavingNotif(false)
    }
  }

  const handleSolicitarCopiaDatos = async () => {
    setSolicitandoCopiaDatos(true)
    try {
      await api.solicitarCopiaDatos()
      setCopiaDatosSolicitada(true)
      showAlert('Tu solicitud fue enviada. Te responderemos por email dentro de los próximos 10 días hábiles ✓', 'success')
    } catch (err: any) {
      showAlert(err.message || 'No pudimos enviar tu solicitud. Intentá de nuevo.', 'error')
    } finally {
      setSolicitandoCopiaDatos(false)
    }
  }

  const handleDeleteAccount = async () => {
    setDeleting(true)
    try {
      await api.eliminarCuenta({ password: deletePassword || undefined })
      showAlert('Tu cuenta fue eliminada.', 'success')
      onLogout()
      navigate('/')
    } catch (err: any) {
      showAlert(err.message || 'No pudimos eliminar tu cuenta. Intentá de nuevo.', 'error')
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="privacy-page">
        <div className="privacy-page__main" style={{ textAlign: 'center' }}>
          <div className="checkout-spinner" style={{ margin: '0 auto var(--space-4)' }} />
        </div>
      </div>
    )
  }

  return (
    <div className="privacy-page">
      <header className="privacy-page__header">
        <a href="/" className="privacy-page__logo" aria-label="Tranqui App - Inicio">
          <img src="/tranqui-icon.png" alt="" aria-hidden="true" className="privacy-page__logo-icon" />
          tranqui
        </a>
        <button className="btn btn--sm btn--secondary" onClick={() => navigate('/')}>
          Volver al Inicio
        </button>
      </header>

      <main className="privacy-page__main">
        <div className="privacy-doc" style={{ maxWidth: '900px' }}>
          <h1>Mi Cuenta</h1>
          <p className="privacy-doc__updated">Gestioná tus datos personales, tu contraseña y tus preferencias.</p>

          <div className="settings-layout">
            <nav className="settings-submenu" aria-label="Secciones de mi cuenta">
              {TABS.map(({ id, label, Icon: TabIcon }) => (
                <button
                  key={id}
                  type="button"
                  className={`settings-submenu-item ${activeTab === id ? 'active' : ''}`}
                  onClick={() => setActiveTab(id)}
                  aria-current={activeTab === id ? 'true' : undefined}
                >
                  <span className="settings-submenu-icon"><TabIcon /></span>
                  <span className="settings-submenu-text">
                    <span className="settings-submenu-label">{label}</span>
                  </span>
                </button>
              ))}
            </nav>

            <div className="settings-content">
              {activeTab === 'datos' && (
                <div className="card">
                  <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
                    <div>
                      <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <MCIcon.User size={18} /> Datos personales
                      </h2>
                      <p className="card__subtitle" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <MCIcon.Mail /> {cuenta?.email}
                      </p>
                    </div>
                    <button className="btn btn--primary btn--sm" onClick={handleSaveDatos} disabled={savingDatos} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold' }}>
                      <MCIcon.Save size={14} /> {savingDatos ? 'Guardando...' : 'Guardar cambios'}
                    </button>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)', marginTop: 'var(--space-5)' }}>
                  <div>
                  <div className="settings-section-label" style={{ display: 'flex', alignItems: 'center', gap: '6px', margin: '0 0 var(--space-3)' }}>
                    <MCIcon.User size={13} /> Información personal
                  </div>
                  <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-nombre" icon={<MCIcon.User size={13} />}>Nombre</FieldLabel>
                      <input id="mc-nombre" className="form-input" type="text" value={nombre} onChange={(e) => setNombre(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-apellido" icon={<MCIcon.User size={13} />}>Apellido</FieldLabel>
                      <input id="mc-apellido" className="form-input" type="text" value={apellido} onChange={(e) => setApellido(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-telefono" icon={<MCIcon.Phone />}>Teléfono</FieldLabel>
                      <input id="mc-telefono" className="form-input" type="tel" value={telefono} onChange={(e) => setTelefono(e.target.value.replace(/[^\d]/g, ''))} placeholder="Ej: 3515998822" />
                    </div>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-sexo" icon={<MCIcon.User size={13} />}>Sexo</FieldLabel>
                      <select id="mc-sexo" className="form-input" value={sexo} onChange={(e) => setSexo(e.target.value)}>
                        <option value="">Sin especificar</option>
                        <option value="M">Masculino (M)</option>
                        <option value="F">Femenino (F)</option>
                        <option value="X">Otro (X)</option>
                      </select>
                    </div>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-fecha-nac" icon={<MCIcon.CalendarIcon />}>Fecha de nacimiento</FieldLabel>
                      <input id="mc-fecha-nac" className="form-input" type="date" value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)} />
                    </div>
                  </div>
                  </div>

                  <div>
                  <div className="settings-section-label" style={{ display: 'flex', alignItems: 'center', gap: '6px', margin: '0 0 var(--space-3)' }}>
                    <MCIcon.IdCard size={13} /> Documento de identidad
                  </div>
                  <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-tipo-doc" icon={<MCIcon.IdCard />}>Tipo de documento</FieldLabel>
                      <select id="mc-tipo-doc" className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
                        <option value="">Sin especificar</option>
                        <option value="DNI">DNI</option>
                        <option value="LC">LC</option>
                        <option value="LE">LE</option>
                        <option value="PASAPORTE">Pasaporte</option>
                      </select>
                    </div>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-num-doc" icon={<MCIcon.IdCard />}>Número de documento</FieldLabel>
                      <input id="mc-num-doc" className="form-input" type="text" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value.replace(/[^\d]/g, ''))} />
                    </div>
                  </div>
                  </div>

                  <div>
                  <div className="settings-section-label" style={{ display: 'flex', alignItems: 'center', gap: '6px', margin: '0 0 var(--space-3)' }}>
                    <MCIcon.HeartPulse size={13} /> Obra social
                  </div>
                  <div
                    style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)',
                      background: 'var(--neutral-50)', border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)', padding: 'var(--space-4)',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span style={{
                        width: '34px', height: '34px', borderRadius: 'var(--radius-sm)',
                        background: tieneObraSocial ? 'var(--color-primary)' : 'var(--neutral-100)',
                        color: tieneObraSocial ? 'white' : 'var(--color-primary-hover)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                        transition: 'background var(--transition-fast), color var(--transition-fast)',
                      }}>
                        <MCIcon.HeartPulse size={18} />
                      </span>
                      <div>
                        <strong style={{ fontSize: 'var(--text-sm)' }}>¿Tenés obra social o prepaga?</strong>
                        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          Activá esta opción solo si contás con cobertura médica.
                        </p>
                      </div>
                    </div>
                    <label className="toggle">
                      <input type="checkbox" checked={tieneObraSocial} onChange={(e) => handleToggleObraSocial(e.target.checked)} />
                      <span className="toggle__track" />
                    </label>
                  </div>

                  {tieneObraSocial && (
                    <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)', marginTop: 'var(--space-4)' }}>
                      <div className="form-group">
                        <FieldLabel htmlFor="mc-obra-social" icon={<MCIcon.HeartPulse />}>Obra Social</FieldLabel>
                        <input id="mc-obra-social" className="form-input" type="text" value={obraSocial} onChange={(e) => setObraSocial(e.target.value)} placeholder="Ej: OSDE" />
                      </div>
                      <div className="form-group">
                        <FieldLabel htmlFor="mc-num-afiliado" icon={<MCIcon.IdCard />}>Número de afiliado</FieldLabel>
                        <input id="mc-num-afiliado" className="form-input" type="text" value={numAfiliado} onChange={(e) => setNumAfiliado(e.target.value)} />
                      </div>
                    </div>
                  )}
                  </div>

                  <div>
                    <button className="btn btn--primary" onClick={handleSaveDatos} disabled={savingDatos} style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                      <MCIcon.Save size={15} /> {savingDatos ? 'Guardando...' : 'Guardar cambios'}
                    </button>
                  </div>
                  </div>
                </div>
              )}

              {activeTab === 'suscripcion' && (
                <div className="card">
                  <div className="card__header">
                    <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <MCIcon.CreditCard size={18} /> Mi Suscripción y Facturas ARCA
                    </h2>
                    <p className="card__subtitle">
                      Gestioná tu membresía profesional y descargá tus comprobantes oficiales con CAE y código QR emitidos por ARCA.
                    </p>
                  </div>

                  {mySub ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
                      <div style={{
                        padding: 'var(--space-4)',
                        backgroundColor: 'var(--green-50)',
                        border: '1px solid var(--green-200)',
                        borderRadius: 'var(--radius-md)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: 'var(--space-3)'
                      }}>
                        <div>
                          <span style={{ fontSize: '11px', color: 'var(--color-primary-hover)', fontWeight: 'bold', display: 'block' }}>PLAN PROFESIONAL</span>
                          <strong style={{ fontSize: '18px', color: 'var(--color-primary)' }}>{mySub.plan?.name || 'Plan Profesional'}</strong>
                          <span style={{ display: 'block', fontSize: '13px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                            {mySub.currentPeriodEnd ? `Período cubierto hasta el ${new Date(mySub.currentPeriodEnd).toLocaleDateString('es-AR')}` : 'Sin fecha de vencimiento'}
                          </span>
                        </div>
                        <span className={`badge ${mySub.status === 'ACTIVE' ? 'badge--success' : 'badge--warning'}`} style={{ fontSize: '13px', padding: '6px 12px' }}>
                          {mySub.status === 'ACTIVE' ? 'Suscripción Activa' : mySub.status}
                        </span>
                      </div>

                      {mySub.activeFeatures && mySub.activeFeatures.length > 0 && (
                        <div>
                          <strong style={{ fontSize: '13px', display: 'block', marginBottom: '8px' }}>Funcionalidades habilitadas:</strong>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                            {mySub.activeFeatures.map((feat: string) => (
                              <span key={feat} className="badge badge--neutral" style={{ fontSize: '12px' }}>
                                ✓ {feat.replace(/_/g, ' ')}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      <div style={{ marginTop: 'var(--space-2)' }}>
                        <strong style={{ fontSize: '14px', display: 'block', marginBottom: '8px' }}>Comprobantes y Facturas C:</strong>
                        {myInvoices.length === 0 ? (
                          <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>No hay facturas emitidas todavía.</p>
                        ) : (
                          <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                              <thead>
                                <tr style={{ borderBottom: '2px solid var(--color-border)', color: 'var(--color-text-secondary)' }}>
                                  <th style={{ padding: '8px' }}>Comprobante</th>
                                  <th style={{ padding: '8px' }}>Fecha</th>
                                  <th style={{ padding: '8px' }}>Monto</th>
                                  <th style={{ padding: '8px' }}>CAE</th>
                                  <th style={{ padding: '8px', textAlign: 'right' }}>Descarga</th>
                                </tr>
                              </thead>
                              <tbody>
                                {myInvoices.map((inv: any) => (
                                  <tr key={inv.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                                    <td style={{ padding: '8px' }}>
                                      <strong>{inv.cbteTipoNombre}</strong> #{String(inv.puntoVenta).padStart(5, '0')}-{String(inv.cbteNumero).padStart(8, '0')}
                                    </td>
                                    <td style={{ padding: '8px' }}>{new Date(inv.fechaEmision).toLocaleDateString('es-AR')}</td>
                                    <td style={{ padding: '8px', fontWeight: 'bold' }}>$ {inv.importeTotal?.toLocaleString('es-AR')}</td>
                                    <td style={{ padding: '8px', fontFamily: 'monospace' }}>{inv.cae || '—'}</td>
                                    <td style={{ padding: '8px', textAlign: 'right' }}>
                                      {inv.pdfUrl && (
                                        <a href={api.getInvoicePdfUrl(inv.id)} target="_blank" rel="noreferrer" className="btn btn--secondary btn--sm">
                                          📥 Descargar Factura C
                                        </a>
                                      )}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>
                    </div>
                  ) : (
                    <div style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-secondary)' }}>
                      No contás con una suscripción profesional activa.
                    </div>
                  )}
                </div>
              )}

              {activeTab === 'password' && (
                <div className="card">
                  <div className="card__header">
                    <div>
                      <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <MCIcon.Lock size={18} /> Cambiar contraseña
                      </h2>
                      <p className="card__subtitle">
                        {cuenta?.tienePassword
                          ? 'Ingresá tu contraseña actual y la nueva.'
                          : 'Tu cuenta usa solo Google para iniciar sesión — podés fijar una contraseña para poder entrar también con email y contraseña.'}
                      </p>
                    </div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxWidth: '360px' }}>
                    {cuenta?.tienePassword && (
                      <div className="form-group">
                        <FieldLabel htmlFor="mc-current-password" icon={<MCIcon.Key />}>Contraseña actual</FieldLabel>
                        <input id="mc-current-password" className="form-input" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
                      </div>
                    )}
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-new-password" icon={<MCIcon.Lock />}>Nueva contraseña</FieldLabel>
                      <input id="mc-new-password" className="form-input" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <FieldLabel htmlFor="mc-confirm-password" icon={<MCIcon.Lock />}>Confirmar nueva contraseña</FieldLabel>
                      <input id="mc-confirm-password" className="form-input" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
                    </div>
                    <div>
                      <button
                        className="btn btn--primary"
                        onClick={handleChangePassword}
                        disabled={savingPassword || !newPassword || !confirmPassword || (cuenta?.tienePassword && !currentPassword)}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
                      >
                        <MCIcon.Save size={15} /> {savingPassword ? 'Guardando...' : 'Actualizar contraseña'}
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'notificaciones' && (
                <div className="card">
                  <div className="card__header">
                    <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <MCIcon.Bell size={18} /> Preferencias de notificaciones
                    </h2>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ width: '34px', height: '34px', borderRadius: 'var(--radius-sm)', background: 'var(--neutral-100)', color: 'var(--color-primary-hover)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <MCIcon.Mail size={16} />
                        </span>
                        <div>
                          <strong style={{ fontSize: 'var(--text-sm)' }}>Notificaciones por email</strong>
                          <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                            Confirmaciones y recordatorios relacionados a tus turnos.
                          </p>
                        </div>
                      </div>
                      <label className="toggle">
                        <input type="checkbox" checked={notifEmail} disabled={savingNotif} onChange={(e) => handleToggleNotif('email', e.target.checked)} />
                        <span className="toggle__track" />
                      </label>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ width: '34px', height: '34px', borderRadius: 'var(--radius-sm)', background: 'var(--neutral-100)', color: 'var(--color-primary-hover)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <MCIcon.Phone size={16} />
                        </span>
                        <div>
                          <strong style={{ fontSize: 'var(--text-sm)' }}>Notificaciones por WhatsApp</strong>
                          <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                            Recordatorios de turno y avisos de cancelación/reprogramación.
                          </p>
                        </div>
                      </div>
                      <label className="toggle">
                        <input type="checkbox" checked={notifWhatsapp} disabled={savingNotif} onChange={(e) => handleToggleNotif('whatsapp', e.target.checked)} />
                        <span className="toggle__track" />
                      </label>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'privacidad' && (
                <div className="card">
                  <div className="card__header">
                    <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <MCIcon.Shield size={18} /> Acceso a mis datos
                    </h2>
                    <p className="card__subtitle">De acuerdo a la Ley N° 25.326, tenés derecho a acceder, rectificar y suprimir tus datos personales.</p>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                    <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.6, margin: 0 }}>
                      Podés editar o corregir tus datos desde la pestaña "Datos personales", y eliminar tu
                      cuenta en cualquier momento desde "Eliminar cuenta". Para pedir una copia de tus
                      datos, tocá el botón de abajo: le avisamos a nuestro equipo de soporte con tu
                      email de contacto y te responden por ese medio.
                    </p>
                    <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                      <button
                        type="button"
                        className="btn btn--secondary btn--sm"
                        onClick={handleSolicitarCopiaDatos}
                        disabled={solicitandoCopiaDatos || copiaDatosSolicitada}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                      >
                        <MCIcon.Download />
                        {copiaDatosSolicitada
                          ? 'Solicitud enviada ✓'
                          : solicitandoCopiaDatos
                            ? 'Enviando...'
                            : 'Solicitar una copia de mis datos'}
                      </button>
                      <a className="btn btn--ghost btn--sm" href="/privacidad" target="_blank" rel="noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                        <MCIcon.ExternalLink /> Ver Política de Privacidad completa
                      </a>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'eliminar' && (
                <div className="card">
                  <div className="card__header">
                    <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-danger, #dc2626)' }}>
                      <MCIcon.AlertTriangle size={18} /> Eliminar cuenta
                    </h2>
                    <p className="card__subtitle">Esta acción es permanente y no se puede deshacer.</p>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
                    <div style={{
                      backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: 'var(--radius-md)',
                      padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)'
                    }}>
                      <div>
                        <strong style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--text-sm)', color: '#991b1b' }}>
                          <MCIcon.Trash size={14} /> Qué pasa al eliminar tu cuenta
                        </strong>
                        <ul style={{ margin: '8px 0 0', paddingLeft: '20px', display: 'flex', flexDirection: 'column', gap: '4px', fontSize: 'var(--text-sm)', color: '#7f1d1d' }}>
                          <li>Se anonimizan tus datos personales: nombre, email, teléfono y documento dejan de estar asociados a vos.</li>
                          <li>Perdés el acceso a la cuenta de inmediato — no vas a poder volver a iniciar sesión con este email.</li>
                          <li>Cualquier turno futuro que tengas agendado queda cancelado.</li>
                        </ul>
                      </div>
                      <div style={{ borderTop: '1px solid #fecaca', paddingTop: 'var(--space-3)' }}>
                        <strong style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--text-sm)', color: '#166534' }}>
                          <MCIcon.Shield size={14} /> Qué se conserva (por ley)
                        </strong>
                        <p style={{ margin: '6px 0 0', fontSize: 'var(--text-sm)', color: '#166534', lineHeight: '1.4' }}>
                          Tu historia clínica y el historial de turnos ya realizados se conservan de forma anonimizada, tal como lo exige la normativa vigente — sin ningún dato que te identifique.
                        </p>
                      </div>
                    </div>

                    {!confirmingDelete ? (
                      <button
                        className="btn btn--danger"
                        onClick={() => setConfirmingDelete(true)}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', alignSelf: 'flex-start' }}
                      >
                        <MCIcon.Trash size={15} /> Quiero eliminar mi cuenta
                      </button>
                    ) : (
                      <div style={{
                        display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxWidth: '400px',
                        backgroundColor: 'var(--neutral-50)', border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)', padding: 'var(--space-4)'
                      }}>
                        <div className="form-group">
                          <FieldLabel htmlFor="mc-delete-confirm-text" icon={<MCIcon.AlertTriangle size={13} />}>
                            Escribí <strong>ELIMINAR</strong> para confirmar
                          </FieldLabel>
                          <input
                            id="mc-delete-confirm-text"
                            className="form-input"
                            type="text"
                            value={deleteConfirmText}
                            onChange={(e) => setDeleteConfirmText(e.target.value)}
                            placeholder="ELIMINAR"
                            autoComplete="off"
                          />
                        </div>
                        {cuenta?.tienePassword && (
                          <div className="form-group">
                            <FieldLabel htmlFor="mc-delete-password" icon={<MCIcon.Key />}>Confirmá tu contraseña</FieldLabel>
                            <input id="mc-delete-password" className="form-input" type="password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} />
                          </div>
                        )}
                        <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
                          <button
                            className="btn btn--danger"
                            onClick={handleDeleteAccount}
                            disabled={deleting || deleteConfirmText.trim().toUpperCase() !== 'ELIMINAR' || (cuenta?.tienePassword && !deletePassword)}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
                          >
                            <MCIcon.Trash size={15} /> {deleting ? 'Eliminando...' : 'Eliminar definitivamente'}
                          </button>
                          <button
                            className="btn btn--secondary"
                            onClick={() => { setConfirmingDelete(false); setDeletePassword(''); setDeleteConfirmText('') }}
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>

      <footer className="privacy-page__footer">
        © {new Date().getFullYear()} Tranqui App. Todos los derechos reservados.
      </footer>
    </div>
  )
}
