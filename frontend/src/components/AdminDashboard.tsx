import { useState, useEffect } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

function IconVideoCall({ size = 13 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" />
    </svg>
  )
}

function IconBuilding({ size = 13 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="4" y="2" width="16" height="20" rx="1" />
      <line x1="9" y1="7" x2="9" y2="7.01" /><line x1="15" y1="7" x2="15" y2="7.01" />
      <line x1="9" y1="11" x2="9" y2="11.01" /><line x1="15" y1="11" x2="15" y2="11.01" />
      <line x1="9" y1="15" x2="9" y2="15.01" /><line x1="15" y1="15" x2="15" y2="15.01" />
      <path d="M9 22v-4h6v4" />
    </svg>
  )
}

interface User {
  id: number
  nombre: string
  apellido?: string
  email: string
  rol: 'ADMIN' | 'PSIQUIATRA' | 'PACIENTE' | 'VISITADOR'
  verificadoAdmin?: boolean
  titulo?: string
  specialty?: string
  matricula?: string
  cuit?: string
  cuil?: number
  domicilioAtencion?: string
  codigoReFeps?: number
  matriculaTipo?: string
  matriculaProvincia?: string
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
  telefono?: string
}

interface AdminDashboardProps {
  currentUser: any
  onLogout: () => void
}

export default function AdminDashboard({ currentUser, onLogout }: AdminDashboardProps) {
  const { showAlert } = useAlert()
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')
  const [activeSection, setActiveSection] = useState<'pending' | 'users'>('pending')

  const fetchUsers = async () => {
    setLoading(true)
    try {
      const data = await api.getAdminUsers()
      setUsers(data || [])
    } catch (err: any) {
      console.error(err)
      showAlert('Error al cargar la lista de usuarios', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchUsers()
  }, [])

  const handleVerify = async (userId: number) => {
    try {
      await api.verifyProfessional(userId)
      showAlert('Profesional verificado y aceptado con éxito.', 'success')
      fetchUsers()
    } catch (err: any) {
      showAlert('Error al verificar profesional', 'error')
    }
  }

  const handleReject = async (userId: number) => {
    if (window.confirm('¿Estás seguro de que deseas rechazar la verificación de este profesional?')) {
      try {
        await api.rejectProfessional(userId)
        showAlert('Profesional rechazado / declinado.', 'warning')
        fetchUsers()
      } catch (err: any) {
        showAlert('Error al rechazar profesional', 'error')
      }
    }
  }

  const handleRoleChange = async (userId: number, newRole: string) => {
    try {
      await api.updateUserRol(userId, newRole)
      showAlert(`Rol actualizado con éxito a ${newRole === 'PSIQUIATRA' ? 'Profesional' : 'Paciente'}.`, 'success')
      fetchUsers()
    } catch (err: any) {
      showAlert('Error al actualizar el rol', 'error')
    }
  }

  // Pending professionals: role is PSIQUIATRA and verificadoAdmin is not true (false or null)
  const pendingProfessionals = users.filter(
    (u) => u.rol === 'PSIQUIATRA' && u.verificadoAdmin !== true
  )

  const filteredUsers = users.filter((u) => {
    const term = searchTerm.toLowerCase()
    return (
      u.nombre.toLowerCase().includes(term) ||
      (u.apellido && u.apellido.toLowerCase().includes(term)) ||
      u.email.toLowerCase().includes(term) ||
      u.rol.toLowerCase().includes(term)
    )
  })

  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: 'var(--color-bg)',
      display: 'flex',
      flexDirection: 'column'
    }}>
      {/* Admin Header */}
      <header style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: 'var(--space-4) var(--space-8)',
        backgroundColor: 'var(--color-surface)',
        borderBottom: '1px solid var(--color-border)',
        position: 'sticky',
        top: 0,
        zIndex: 100
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          <img src="/tranqui-icon.webp" alt="Tranqui" style={{ height: '32px' }} />
          <h1 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
            Tranqui · Panel de Administración
          </h1>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
            Hola, <strong>{currentUser.nombre}</strong> (Admin)
          </div>
          <button onClick={onLogout} className="btn btn--secondary btn--sm">
            Cerrar Sesión
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main style={{
        flex: 1,
        padding: 'var(--space-8)',
        maxWidth: '1200px',
        width: '100%',
        margin: '0 auto',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-6)'
      }}>

        {/* Section Tabs */}
        <div style={{
          display: 'flex',
          gap: 'var(--space-4)',
          borderBottom: '1px solid var(--color-border)',
          paddingBottom: '2px'
        }}>
          <button
            onClick={() => setActiveSection('pending')}
            style={{
              padding: '8px 16px',
              border: 'none',
              background: 'none',
              borderBottom: activeSection === 'pending' ? '3px solid var(--color-primary)' : '3px solid transparent',
              color: activeSection === 'pending' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              fontWeight: '700',
              cursor: 'pointer',
              fontSize: '14px',
              transition: 'all 0.2s ease-in-out'
            }}
          >
            Pendientes de Verificación ({pendingProfessionals.length})
          </button>
          <button
            onClick={() => setActiveSection('users')}
            style={{
              padding: '8px 16px',
              border: 'none',
              background: 'none',
              borderBottom: activeSection === 'users' ? '3px solid var(--color-primary)' : '3px solid transparent',
              color: activeSection === 'users' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              fontWeight: '700',
              cursor: 'pointer',
              fontSize: '14px',
              transition: 'all 0.2s ease-in-out'
            }}
          >
            Lista de Usuarios
          </button>
        </div>

        {/* ── SECTION 1: PENDING VERIFICATION ── */}
        {activeSection === 'pending' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Profesionales Pendientes de Verificación
                </h2>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Aprobá o rechazá las solicitudes de registro de profesionales médicos.
                </p>
              </div>
            </div>

            {loading ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
                <div className="checkout-spinner" style={{ margin: '0 auto var(--space-3)' }} />
                <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>Cargando profesionales...</p>
              </div>
            ) : pendingProfessionals.length === 0 ? (
              <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center', border: '1px solid var(--color-border)' }}>
                <span style={{ fontSize: '24px', display: 'block', marginBottom: '8px' }}>🎉</span>
                <p style={{ margin: 0, color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
                  No hay profesionales pendientes de verificación en este momento.
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                {pendingProfessionals.map((pro) => (
                  <div
                    key={pro.id}
                    className="card"
                    style={{
                      border: '1px solid var(--color-border)',
                      borderLeft: pro.verificadoAdmin === false ? '5px solid var(--color-danger)' : '5px solid #f59e0b',
                      padding: 'var(--space-5)',
                      backgroundColor: 'var(--color-surface)',
                      borderRadius: 'var(--radius-lg)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 'var(--space-4)',
                      animation: 'fadeInUp 0.3s ease-out'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
                      <div>
                        <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                          {pro.nombre} {pro.apellido}
                          <span className={`badge ${pro.verificadoAdmin === false ? 'badge--danger' : 'badge--warning'}`}>
                            {pro.verificadoAdmin === false ? 'Rechazado' : 'Pendiente'}
                          </span>
                        </h3>
                        <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          Email: {pro.email} · Teléfono: {pro.telefono || 'No especificado'}
                        </p>
                      </div>
                      
                      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                        <button
                          onClick={() => handleVerify(pro.id)}
                          className="btn btn--primary btn--sm"
                          style={{ backgroundColor: '#10b981', borderColor: '#10b981', color: 'white', fontWeight: 'bold' }}
                        >
                          Aceptar
                        </button>
                        <button
                          onClick={() => handleReject(pro.id)}
                          className="btn btn--danger btn--sm"
                          style={{ backgroundColor: '#ef4444', borderColor: '#ef4444', color: 'white', fontWeight: 'bold' }}
                        >
                          Rechazar
                        </button>
                      </div>
                    </div>

                    <div style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                      gap: 'var(--space-3)',
                      backgroundColor: 'var(--neutral-50)',
                      padding: 'var(--space-4)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--color-border)',
                      fontSize: 'var(--text-xs)'
                    }}>
                      <div style={{
                        gridColumn: 'span 2',
                        backgroundColor: '#eff6ff',
                        border: '1px solid #bfdbfe',
                        padding: '8px 12px',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: '13px',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                      }}>
                        <span style={{ color: '#1e40af', fontWeight: '700' }}>NÚMERO DE MATRÍCULA:</span>
                        <span style={{ fontSize: '15px', color: '#1e3a8a', fontWeight: '900', letterSpacing: '0.05em' }}>
                          {pro.matriculaTipo} {pro.matricula} ({pro.matriculaProvincia})
                        </span>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Título Profesional:</span>
                        <strong>{pro.titulo || '-'}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Especialidad:</span>
                        <strong>{pro.specialty || '-'}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Código ReFeps:</span>
                        <strong>{pro.codigoReFeps || '-'}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>CUIT / CUIL:</span>
                        <strong>{pro.cuit || pro.cuil || '-'}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Consultorio / Domicilio:</span>
                        <strong>{pro.domicilioAtencion || '-'}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Modalidad de Atención:</span>
                        <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          {pro.ofreceOnline && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}><IconVideoCall /> Online</span>}
                          {pro.ofreceOnline && pro.ofrecePresencial ? '·' : ''}
                          {pro.ofrecePresencial && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}><IconBuilding /> Presencial</span>}
                        </strong>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── SECTION 2: LISTA DE USUARIOS ── */}
        {activeSection === 'users' && (
          <div className="card" style={{
            padding: 'var(--space-6)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            backgroundColor: 'var(--color-surface)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-4)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
              <div>
                <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Gestión de Roles de Usuarios
                </h2>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Cambia roles dinámicamente de Paciente a Profesional o viceversa.
                </p>
              </div>
              
              <input
                type="text"
                placeholder="Buscar por nombre, email o rol..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{
                  padding: '8px 12px',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--color-border)',
                  fontSize: 'var(--text-sm)',
                  minWidth: '260px'
                }}
              />
            </div>

            {loading ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
                <div className="checkout-spinner" style={{ margin: '0 auto var(--space-3)' }} />
                <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>Cargando usuarios...</p>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{
                  width: '100%',
                  borderCollapse: 'collapse',
                  fontSize: 'var(--text-sm)',
                  textAlign: 'left'
                }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid var(--color-border)', color: 'var(--color-text-secondary)' }}>
                      <th style={{ padding: '12px var(--space-2)' }}>ID</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Usuario</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Email</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Rol Actual</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Estado Admin</th>
                      <th style={{ padding: '12px var(--space-2)', textAlign: 'right' }}>Cambiar Rol</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUsers.map((user) => (
                      <tr key={user.id} style={{ borderBottom: '1px solid var(--color-border)', transition: 'background-color 0.2s' }}>
                        <td style={{ padding: '12px var(--space-2)', color: 'var(--color-text-secondary)' }}>#{user.id}</td>
                        <td style={{ padding: '12px var(--space-2)', fontWeight: 'bold' }}>
                          {user.nombre} {user.apellido || ''}
                        </td>
                        <td style={{ padding: '12px var(--space-2)' }}>{user.email}</td>
                        <td style={{ padding: '12px var(--space-2)' }}>
                          <span className={`badge ${
                            user.rol === 'ADMIN' 
                              ? 'badge--warning' 
                              : user.rol === 'PSIQUIATRA' 
                                ? 'badge--success' 
                                : 'badge--neutral'
                          }`}>
                            {user.rol === 'ADMIN' ? 'Administrador' : user.rol === 'PSIQUIATRA' ? 'Profesional' : 'Paciente'}
                          </span>
                        </td>
                        <td style={{ padding: '12px var(--space-2)' }}>
                          {user.rol === 'PSIQUIATRA' ? (
                            user.verificadoAdmin ? (
                              <span style={{ color: '#10b981', fontWeight: 'bold' }}>✓ Verificado</span>
                            ) : user.verificadoAdmin === false ? (
                              <span style={{ color: 'var(--color-danger)', fontWeight: 'bold' }}>✕ Rechazado</span>
                            ) : (
                              <span style={{ color: '#f59e0b', fontWeight: 'bold' }}>⏳ Pendiente</span>
                            )
                          ) : (
                            <span style={{ color: 'var(--color-text-secondary)' }}>N/A</span>
                          )}
                        </td>
                        <td style={{ padding: '12px var(--space-2)', textAlign: 'right' }}>
                          {user.rol === 'ADMIN' ? (
                            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>No modificable</span>
                          ) : (
                            <select
                              value={user.rol}
                              onChange={(e) => handleRoleChange(user.id, e.target.value)}
                              style={{
                                padding: '4px 8px',
                                borderRadius: 'var(--radius-sm)',
                                border: '1px solid var(--color-border)',
                                fontSize: '12px',
                                backgroundColor: '#ffffff',
                                fontWeight: '600',
                                cursor: 'pointer'
                              }}
                            >
                              <option value="PACIENTE">Paciente</option>
                              <option value="PSIQUIATRA">Profesional</option>
                            </select>
                          )}
                        </td>
                      </tr>
                    ))}
                    {filteredUsers.length === 0 && (
                      <tr>
                        <td colSpan={6} style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-secondary)' }}>
                          No se encontraron usuarios que coincidan con la búsqueda.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
