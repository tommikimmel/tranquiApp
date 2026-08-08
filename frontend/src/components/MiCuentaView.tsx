import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import '../styles/privacy.css'

type MiCuentaTab = 'datos' | 'password' | 'notificaciones' | 'privacidad' | 'eliminar'

const TABS: { id: MiCuentaTab; label: string }[] = [
  { id: 'datos', label: 'Datos personales' },
  { id: 'password', label: 'Contraseña' },
  { id: 'notificaciones', label: 'Notificaciones' },
  { id: 'privacidad', label: 'Acceso a mis datos' },
  { id: 'eliminar', label: 'Eliminar cuenta' },
]

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

  // Eliminar cuenta state
  const [deletePassword, setDeletePassword] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSaveDatos = async () => {
    setSavingDatos(true)
    try {
      const updated = await api.actualizarMiCuenta({
        ...cuenta,
        nombre, apellido, telefono, sexo,
        fechaNacimiento: fechaNacimiento || null,
        tipoDocumento,
        numeroDocumento: numeroDocumento ? Number(numeroDocumento) : null,
        obraSocial, numAfiliado,
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
              {TABS.map(({ id, label }) => (
                <button
                  key={id}
                  type="button"
                  className={`settings-submenu-item ${activeTab === id ? 'active' : ''}`}
                  onClick={() => setActiveTab(id)}
                  aria-current={activeTab === id ? 'true' : undefined}
                >
                  <span className="settings-submenu-text">
                    <span className="settings-submenu-label">{label}</span>
                  </span>
                </button>
              ))}
            </nav>

            <div className="settings-content">
              {activeTab === 'datos' && (
                <div className="card">
                  <div className="card__header">
                    <div>
                      <h2 className="card__title">Datos personales</h2>
                      <p className="card__subtitle">Email: {cuenta?.email}</p>
                    </div>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-nombre">Nombre</label>
                      <input id="mc-nombre" className="form-input" type="text" value={nombre} onChange={(e) => setNombre(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-apellido">Apellido</label>
                      <input id="mc-apellido" className="form-input" type="text" value={apellido} onChange={(e) => setApellido(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-telefono">Teléfono</label>
                      <input id="mc-telefono" className="form-input" type="tel" value={telefono} onChange={(e) => setTelefono(e.target.value.replace(/[^\d]/g, ''))} placeholder="Ej: 3515998822" />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-sexo">Sexo</label>
                      <select id="mc-sexo" className="form-input" value={sexo} onChange={(e) => setSexo(e.target.value)}>
                        <option value="">Sin especificar</option>
                        <option value="M">Masculino (M)</option>
                        <option value="F">Femenino (F)</option>
                        <option value="X">Otro (X)</option>
                      </select>
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-fecha-nac">Fecha de nacimiento</label>
                      <input id="mc-fecha-nac" className="form-input" type="date" value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-tipo-doc">Tipo de documento</label>
                      <select id="mc-tipo-doc" className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
                        <option value="">Sin especificar</option>
                        <option value="DNI">DNI</option>
                        <option value="LC">LC</option>
                        <option value="LE">LE</option>
                        <option value="PASAPORTE">Pasaporte</option>
                      </select>
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-num-doc">Número de documento</label>
                      <input id="mc-num-doc" className="form-input" type="text" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value.replace(/[^\d]/g, ''))} />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-obra-social">Obra Social</label>
                      <input id="mc-obra-social" className="form-input" type="text" value={obraSocial} onChange={(e) => setObraSocial(e.target.value)} placeholder="Ej: OSDE" />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-num-afiliado">Número de afiliado</label>
                      <input id="mc-num-afiliado" className="form-input" type="text" value={numAfiliado} onChange={(e) => setNumAfiliado(e.target.value)} />
                    </div>
                  </div>
                  <div style={{ marginTop: 'var(--space-5)' }}>
                    <button className="btn btn--primary" onClick={handleSaveDatos} disabled={savingDatos}>
                      {savingDatos ? 'Guardando...' : 'Guardar cambios'}
                    </button>
                  </div>
                </div>
              )}

              {activeTab === 'password' && (
                <div className="card">
                  <div className="card__header">
                    <div>
                      <h2 className="card__title">Cambiar contraseña</h2>
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
                        <label className="form-label" htmlFor="mc-current-password">Contraseña actual</label>
                        <input id="mc-current-password" className="form-input" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
                      </div>
                    )}
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-new-password">Nueva contraseña</label>
                      <input id="mc-new-password" className="form-input" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="mc-confirm-password">Confirmar nueva contraseña</label>
                      <input id="mc-confirm-password" className="form-input" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
                    </div>
                    <div>
                      <button
                        className="btn btn--primary"
                        onClick={handleChangePassword}
                        disabled={savingPassword || !newPassword || !confirmPassword || (cuenta?.tienePassword && !currentPassword)}
                      >
                        {savingPassword ? 'Guardando...' : 'Actualizar contraseña'}
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'notificaciones' && (
                <div className="card">
                  <div className="card__header">
                    <h2 className="card__title">Preferencias de notificaciones</h2>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)' }}>
                      <div>
                        <strong style={{ fontSize: 'var(--text-sm)' }}>Notificaciones por email</strong>
                        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          Confirmaciones y recordatorios relacionados a tus turnos.
                        </p>
                      </div>
                      <label className="toggle">
                        <input type="checkbox" checked={notifEmail} disabled={savingNotif} onChange={(e) => handleToggleNotif('email', e.target.checked)} />
                        <span className="toggle__track" />
                      </label>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)' }}>
                      <div>
                        <strong style={{ fontSize: 'var(--text-sm)' }}>Notificaciones por WhatsApp</strong>
                        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          Recordatorios de turno y avisos de cancelación/reprogramación.
                        </p>
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
                    <h2 className="card__title">Acceso a mis datos</h2>
                    <p className="card__subtitle">De acuerdo a la Ley N° 25.326, tenés derecho a acceder, rectificar y suprimir tus datos personales.</p>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                    <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.6, margin: 0 }}>
                      Podés editar o corregir tus datos desde la pestaña "Datos personales", y eliminar tu
                      cuenta en cualquier momento desde "Eliminar cuenta". Para pedir una copia de tus
                      datos, o cualquier otra consulta sobre privacidad, escribinos.
                    </p>
                    <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                      <a className="btn btn--secondary btn--sm" href="mailto:soporte@tranquisalud.com?subject=Acceso%20a%20mis%20datos">
                        Solicitar una copia de mis datos
                      </a>
                      <a className="btn btn--ghost btn--sm" href="/privacidad" target="_blank" rel="noreferrer">
                        Ver Política de Privacidad completa
                      </a>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'eliminar' && (
                <div className="card">
                  <div className="card__header">
                    <h2 className="card__title">Eliminar cuenta</h2>
                    <p className="card__subtitle">
                      Esta acción es irreversible. Tus datos personales se anonimizan; tus turnos e
                      historia clínica se conservan según lo exige la ley, sin tus datos personales.
                    </p>
                  </div>
                  {!confirmingDelete ? (
                    <button className="btn btn--danger" onClick={() => setConfirmingDelete(true)}>
                      Eliminar mi cuenta
                    </button>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxWidth: '360px' }}>
                      {cuenta?.tienePassword && (
                        <div className="form-group">
                          <label className="form-label" htmlFor="mc-delete-password">Confirmá tu contraseña</label>
                          <input id="mc-delete-password" className="form-input" type="password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} />
                        </div>
                      )}
                      <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
                        <button
                          className="btn btn--danger"
                          onClick={handleDeleteAccount}
                          disabled={deleting || (cuenta?.tienePassword && !deletePassword)}
                        >
                          {deleting ? 'Eliminando...' : 'Confirmar eliminación'}
                        </button>
                        <button className="btn btn--secondary" onClick={() => { setConfirmingDelete(false); setDeletePassword('') }}>
                          Cancelar
                        </button>
                      </div>
                    </div>
                  )}
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
