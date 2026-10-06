import { useEffect, useMemo, useState } from 'react'
import { Icon } from './Icon'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { getInitials } from '../hooks/usePatients'
import AdminTicketsView from './AdminTicketsView'
import AdminSubscriptionsView from './AdminSubscriptionsView'

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

function IconSearch({ size = 15 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}

function IconUsers({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  )
}

function IconClock({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
    </svg>
  )
}

function IconCheckCircle({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  )
}

function IconHeart({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </svg>
  )
}

interface User {
  id: number
  nombre: string
  apellido?: string
  email: string
  rol: 'ADMIN' | 'PSIQUIATRA' | 'PACIENTE' | 'VISITADOR'
  profession?: string
  verificadoAdmin?: boolean
  titulo?: string
  specialty?: string
  matricula?: string
  cuit?: string
  cuil?: number
  domicilioAtencion?: string
  matriculaTipo?: string
  matriculaProvincia?: string
  matriculaNumero?: number
  licenseType?: string
  licenseNumber?: string
  licenseJurisdiction?: string
  licenseDocumentUrl?: string
  licenseVerifiedAt?: string
  taxIdType?: string
  taxId?: string
  legalName?: string
  ivaConditionId?: number
  fiscalAddress?: string
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
  telefono?: string
  sexo?: string
  fechaNacimiento?: string
  tipoDocumento?: string
  numeroDocumento?: number
  fotoUrl?: string
  descripcionPerfil?: string
  tags?: string // comma-separated, raw entity shape
  pacientesAtiende?: string // comma-separated, raw entity shape
  institucionFormacion?: string
  aniosExperiencia?: number | null
  experiencia?: string // JSON-stringified work-history array
  fechaRegistro?: string
}

// Mirrors isMedicoVerificado() on the backend and getMissingRequirements() in App.tsx (the
// médico-facing checklist) so all three stay in sync — an admin reviewing "Perfil incompleto"
// sees exactly the same list of missing fields the professional themselves would see.
function getMissingProfileFields(u: User): string[] {
  const missing: string[] = []
  if (!u.nombre?.trim()) missing.push('Nombre')
  if (!u.apellido?.trim()) missing.push('Apellido')
  if (!u.sexo?.trim()) missing.push('Sexo')
  if (!u.fechaNacimiento) missing.push('Fecha de nacimiento')
  if (!u.cuil) missing.push('CUIL')
  if (!u.tipoDocumento?.trim() || !u.numeroDocumento) missing.push('Documento (tipo y número)')
  if (u.ofrecePresencial && !u.domicilioAtencion?.trim()) missing.push('Dirección del consultorio')
  if (!u.matriculaTipo?.trim() || !u.matriculaProvincia?.trim() || !u.matricula) missing.push('Matrícula (tipo, provincia y número)')
  if (!u.titulo?.trim()) missing.push('Título profesional')
  if (!u.specialty?.trim()) missing.push('Especialidad')
  if (!u.fotoUrl?.trim()) missing.push('Foto de perfil')
  if (!u.descripcionPerfil?.trim()) missing.push('Descripción de perfil')
  if (!u.tags?.trim()) missing.push('Tratamientos que atiende')
  if (!u.pacientesAtiende?.trim()) missing.push('Tipo de pacientes que atiende')
  if (!u.institucionFormacion?.trim()) missing.push('Institución de formación')
  if (u.aniosExperiencia === null || u.aniosExperiencia === undefined) missing.push('Años de experiencia')
  if (!u.ofreceOnline && !u.ofrecePresencial) missing.push('Modalidad de consulta (online o presencial)')
  let hasExperienciaLaboral = false
  try {
    const parsed = u.experiencia ? JSON.parse(u.experiencia) : []
    hasExperienciaLaboral = Array.isArray(parsed) && parsed.length > 0
  } catch {
    hasExperienciaLaboral = !!u.experiencia?.trim()
  }
  if (!hasExperienciaLaboral) missing.push('Experiencia laboral')
  return missing
}

function isProfileComplete(u: User): boolean {
  return getMissingProfileFields(u).length === 0
}

function splitCsv(value?: string): string[] {
  return (value || '').split(',').map(s => s.trim()).filter(Boolean)
}

// ── Small shared UI pieces ──────────────────────────────────────
function Avatar({ name, fotoUrl, size = 40 }: { name: string; fotoUrl?: string; size?: number }) {
  if (fotoUrl) {
    return (
      <img
        src={fotoUrl}
        alt={name}
        style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', border: '1px solid var(--color-border)', flexShrink: 0 }}
      />
    )
  }
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      backgroundColor: 'var(--neutral-100)', color: 'var(--color-text-secondary)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontWeight: 'var(--font-weight-semi)', fontSize: size <= 32 ? '11px' : '14px',
      border: '1px solid var(--color-border)', flexShrink: 0
    }}>
      {getInitials(name)}
    </div>
  )
}

function StatCard({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: number; tone: 'primary' | 'warning' | 'success' | 'neutral' }) {
  const toneColors: Record<string, { bg: string; fg: string }> = {
    primary: { bg: 'var(--green-50)', fg: 'var(--green-600)' },
    warning: { bg: 'var(--color-warning-bg)', fg: 'var(--color-warning)' },
    success: { bg: 'var(--green-50)', fg: 'var(--green-700)' },
    neutral: { bg: 'var(--neutral-100)', fg: 'var(--color-text-secondary)' },
  }
  const c = toneColors[tone]
  return (
    <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-4) var(--space-5)', border: '1px solid var(--color-border)' }}>
      <div style={{ width: 40, height: 40, borderRadius: 'var(--radius-md)', backgroundColor: c.bg, color: c.fg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {icon}
      </div>
      <div>
        <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', lineHeight: 1.1 }}>{value}</div>
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{label}</div>
      </div>
    </div>
  )
}

function SidebarNavItem({ active, onClick, icon, label, count }: { active: boolean; onClick: () => void; icon: React.ReactNode; label: string; count?: number }) {
  return (
    <button
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        width: '100%',
        boxSizing: 'border-box',
        minHeight: '52px',
        textAlign: 'left',
        padding: '8px 12px',
        border: 'none',
        borderRadius: 'var(--radius-md)',
        backgroundColor: active ? 'var(--green-50)' : 'transparent',
        color: active ? 'var(--color-primary)' : 'var(--color-text-secondary)',
        fontWeight: active ? 700 : 600,
        fontSize: '13.5px',
        lineHeight: 1.25,
        cursor: 'pointer',
        transition: 'background-color 0.15s ease-in-out, color 0.15s ease-in-out'
      }}
      onMouseEnter={(e) => { if (!active) e.currentTarget.style.backgroundColor = 'var(--neutral-100)' }}
      onMouseLeave={(e) => { if (!active) e.currentTarget.style.backgroundColor = 'transparent' }}
    >
      <span style={{ flexShrink: 0, display: 'flex' }}>{icon}</span>
      <span style={{ flex: 1 }}>{label}</span>
      {count !== undefined && (
        <span style={{
          flexShrink: 0,
          fontSize: '11px',
          fontWeight: 700,
          padding: '1px 7px',
          borderRadius: '999px',
          backgroundColor: active ? 'var(--color-primary)' : 'var(--neutral-100)',
          color: active ? 'white' : 'var(--color-text-secondary)'
        }}>
          {count}
        </span>
      )}
    </button>
  )
}

function RoleBadge({ rol }: { rol: User['rol'] }) {
  const cls = rol === 'ADMIN' ? 'badge--warning' : rol === 'PSIQUIATRA' ? 'badge--success' : rol === 'VISITADOR' ? 'badge--info' : 'badge--neutral'
  const label = rol === 'ADMIN' ? 'Administrador' : rol === 'PSIQUIATRA' ? 'Profesional' : rol === 'VISITADOR' ? 'Visitador' : 'Paciente'
  return <span className={`badge ${cls}`}>{label}</span>
}

// Single source of truth for "what does this professional's verification status mean" — used
// both in the users table and the pending list, so the same account never shows two different
// stories depending on which screen you're looking at.
function VerificationStatus({ user }: { user: User }) {
  if (user.rol !== 'PSIQUIATRA') {
    return <span style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)' }}>No aplica</span>
  }
  if (user.verificadoAdmin === false) {
    return <span className="badge badge--error"><IconClock size={12} /> Rechazado</span>
  }
  if (!user.verificadoAdmin) {
    return <span className="badge badge--warning"><IconClock size={12} /> Pendiente de revisión</span>
  }
  if (isProfileComplete(user)) {
    return <span className="badge badge--success"><IconCheckCircle size={12} /> Verificado</span>
  }
  const missing = getMissingProfileFields(user)
  return (
    <span className="badge badge--warning" title={`Le falta completar: ${missing.join(', ')}`}>
      <IconClock size={12} /> Aprobado, perfil incompleto ({missing.length})
    </span>
  )
}

// The same "everything about this professional" panel is used both inline in the pending list
// and inside the "Ver perfil" modal from the users table, so reviewing an account looks and
// works identically no matter which tab you got there from.
function ProfessionalDetailPanel({ pro }: { pro: User }) {
  const missing = getMissingProfileFields(pro)
  const tags = splitCsv(pro.tags)
  const pacientes = splitCsv(pro.pacientesAtiende)

  const ivaMap: Record<number, string> = {
    1: 'IVA Responsable Inscripto',
    4: 'IVA Sujeto Exento',
    5: 'Consumidor Final',
    6: 'Responsable Monotributo',
  }

  const professionLabel =
    pro.profession === 'psiquiatra'
      ? 'Médico Psiquiatra (Habilitado para Recetas Oficiales QBI2)'
      : pro.profession === 'psicologo'
      ? 'Licenciado en Psicología'
      : pro.profession || (pro.rol === 'PSIQUIATRA' ? 'Médico Psiquiatra' : 'Profesional')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {/* Profesión & Matrícula Badge */}
      <div style={{
        backgroundColor: 'var(--color-info-bg)', border: '1px solid #BFDBFE', padding: '10px 14px',
        borderRadius: 'var(--radius-sm)', fontSize: '13px', display: 'flex', justifyContent: 'space-between',
        alignItems: 'center', flexWrap: 'wrap', gap: '6px'
      }}>
        <div>
          <span style={{ color: 'var(--color-info)', fontWeight: 700, display: 'block', fontSize: '11px' }}>PROFESIÓN</span>
          <strong style={{ color: 'var(--color-info)', fontSize: '14px' }}>{professionLabel}</strong>
        </div>
        <div style={{ textAlign: 'right' }}>
          <span style={{ color: 'var(--color-info)', fontWeight: 700, display: 'block', fontSize: '11px' }}>MATRÍCULA</span>
          <span style={{ fontSize: '14px', color: 'var(--color-info)', fontWeight: 900, letterSpacing: '0.03em' }}>
            {pro.licenseType || pro.matriculaTipo || 'MN'} {pro.licenseNumber || pro.matricula || '—'} ({pro.licenseJurisdiction || pro.matriculaProvincia || 'Nacional'})
          </span>
        </div>
      </div>

      {/* Datos Fiscales ARCA RG 5616 (§6) */}
      <div style={{ padding: '10px 14px', backgroundColor: '#F9FAFB', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', fontSize: '12px' }}>
        <strong style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px', color: 'var(--color-text-primary)' }}>
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 21h18M3 10h18M5 10v11M9 10v11M15 10v11M19 10v11M12 2 2 7h20z"/></svg>
          Datos Fiscales ARCA (Facturación Electrónica RG 5616):
        </strong>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px' }}>
          <div>
            <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>CUIT / CUIL Fiscal:</span>
            <strong>{pro.taxId || pro.cuit || pro.cuil || 'Sin CUIT'}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Razón Social / Nombre Fiscal:</span>
            <strong>{pro.legalName || `${pro.nombre} ${pro.apellido || ''}`}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Condición frente al IVA:</span>
            <strong>{pro.ivaConditionId ? (ivaMap[pro.ivaConditionId] || `Condición #${pro.ivaConditionId}`) : 'Responsable Monotributo'}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Domicilio Fiscal:</span>
            <strong>{pro.fiscalAddress || pro.domicilioAtencion || 'No informado'}</strong>
          </div>
        </div>
      </div>

      {pro.licenseDocumentUrl && (
        <div style={{ fontSize: '12px', padding: '6px 10px', backgroundColor: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 'var(--radius-sm)', display: 'flex', alignItems: 'center', gap: '6px' }}>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>
          <strong>Documento / Carnet de Matrícula:</strong>{' '}
          <a href={pro.licenseDocumentUrl} target="_blank" rel="noreferrer" style={{ color: 'var(--color-primary)', textDecoration: 'underline' }}>
            Ver comprobante adjunto
          </a>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--space-3)', fontSize: 'var(--text-xs)' }}>
        <div>
          <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Título profesional</span>
          <strong>{pro.titulo || '—'}</strong>
        </div>
        <div>
          <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Especialidad</span>
          <strong>{pro.specialty || '—'}</strong>
        </div>
        <div>
          <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Documento</span>
          <strong>{pro.tipoDocumento && pro.numeroDocumento ? `${pro.tipoDocumento} ${pro.numeroDocumento}` : '—'}</strong>
        </div>
        <div>
          <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Años de experiencia</span>
          <strong>{pro.aniosExperiencia ?? '—'}</strong>
        </div>
        <div>
          <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Institución de formación</span>
          <strong>{pro.institucionFormacion || '—'}</strong>
        </div>
        <div style={{ gridColumn: 'span 2' }}>
          <span style={{ color: 'var(--color-text-secondary)', display: 'block' }}>Modalidad y consultorio</span>
          <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
            {pro.ofreceOnline && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}><IconVideoCall /> Online</span>}
            {pro.ofreceOnline && pro.ofrecePresencial ? '·' : ''}
            {pro.ofrecePresencial && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                <IconBuilding /> {pro.domicilioAtencion || 'Presencial (sin dirección cargada)'}
              </span>
            )}
            {!pro.ofreceOnline && !pro.ofrecePresencial && '—'}
          </strong>
        </div>
      </div>

      {(tags.length > 0 || pacientes.length > 0) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {tags.length > 0 && (
            <div>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)', display: 'block', marginBottom: '4px' }}>Tratamientos</span>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                {tags.map(t => <span key={t} className="badge badge--neutral">{t}</span>)}
              </div>
            </div>
          )}
          {pacientes.length > 0 && (
            <div>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)', display: 'block', marginBottom: '4px' }}>Atiende a</span>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                {pacientes.map(t => <span key={t} className="badge badge--neutral">{t}</span>)}
              </div>
            </div>
          )}
        </div>
      )}

      {pro.descripcionPerfil && (
        <div>
          <span style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)', display: 'block', marginBottom: '2px' }}>Descripción de perfil</span>
          <p style={{ margin: 0, fontSize: 'var(--text-sm)', whiteSpace: 'pre-wrap' }}>{pro.descripcionPerfil}</p>
        </div>
      )}

      {missing.length > 0 && (
        <div style={{
          display: 'flex', alignItems: 'flex-start', gap: '10px', padding: '12px 14px',
          backgroundColor: 'var(--color-warning-bg)', border: '1px solid #fde68a', borderRadius: 'var(--radius-md)',
          fontSize: '12.5px', color: 'var(--color-warning)'
        }}>
          <IconClock size={16} />
          <div>
            <strong style={{ display: 'block', marginBottom: '2px' }}>Perfil incompleto — le falta cargar:</strong>
            {missing.join(', ')}
          </div>
        </div>
      )}
    </div>
  )
}

interface AdminDashboardProps {
  currentUser: any
  onLogout: () => void
}

export default function AdminDashboard({ currentUser, onLogout }: AdminDashboardProps) {
  useDocumentTitle('Panel de Administración — Tranqui App')
  const { showAlert } = useAlert()
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')
  const [roleFilter, setRoleFilter] = useState<'TODOS' | User['rol']>('TODOS')
  const [activeSection, setActiveSection] = useState<'pending' | 'subscriptions' | 'users' | 'tickets'>('subscriptions')
  const [detailUser, setDetailUser] = useState<User | null>(null)
  const [resettingPasswordFor, setResettingPasswordFor] = useState<number | null>(null)
  const [generatedPassword, setGeneratedPassword] = useState<{ email: string; password: string } | null>(null)

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

  const handleResetPassword = async (user: User) => {
    if (!window.confirm(`¿Resetear la contraseña de ${user.nombre} ${user.apellido || ''}? Se le va a mandar una contraseña temporal por mail y va a tener que cambiarla al ingresar.`)) {
      return
    }
    setResettingPasswordFor(user.id)
    try {
      const res: any = await api.resetUserPassword(user.id)
      setGeneratedPassword({ email: res.email, password: res.password })
    } catch (err: any) {
      showAlert(err.message || 'Error al resetear la contraseña', 'error')
    } finally {
      setResettingPasswordFor(null)
    }
  }

  // Pending professionals: role is PSIQUIATRA and verificadoAdmin is not true (false or null)
  const pendingProfessionals = users.filter(
    (u) => u.rol === 'PSIQUIATRA' && u.verificadoAdmin !== true
  )

  const stats = useMemo(() => {
    const profesionales = users.filter(u => u.rol === 'PSIQUIATRA')
    return {
      total: users.length,
      pendientes: pendingProfessionals.length,
      profesionalesVerificados: profesionales.filter(u => u.verificadoAdmin === true).length,
      pacientes: users.filter(u => u.rol === 'PACIENTE').length,
    }
  }, [users, pendingProfessionals.length])

  const filteredUsers = users.filter((u) => {
    const term = searchTerm.toLowerCase()
    const matchesSearch = !term ||
      u.nombre.toLowerCase().includes(term) ||
      (u.apellido && u.apellido.toLowerCase().includes(term)) ||
      u.email.toLowerCase().includes(term) ||
      u.rol.toLowerCase().includes(term)
    const matchesRole = roleFilter === 'TODOS' || u.rol === roleFilter
    return matchesSearch && matchesRole
  })

  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: 'var(--color-bg)',
      display: 'flex',
      flexDirection: 'column'
    }}>
      {/* Admin Header */}
      <header className="admin-dashboard__header" style={{
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

      {/* Body: Sidebar + Main Content */}
      <div className="admin-dashboard__body" style={{ flex: 1, display: 'flex', alignItems: 'flex-start', width: '100%' }}>

        {/* Sidebar Navigation */}
        <aside className="admin-dashboard__sidebar" style={{
          width: '260px',
          flexShrink: 0,
          borderRight: '1px solid var(--color-border)',
          backgroundColor: 'var(--color-surface)',
          padding: 'var(--space-5) var(--space-3)',
          position: 'sticky',
          top: '65px',
          alignSelf: 'flex-start',
          height: 'calc(100vh - 65px)',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px'
        }}>
          <SidebarNavItem
            active={activeSection === 'subscriptions'}
            onClick={() => setActiveSection('subscriptions')}
            icon={
              <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 21h18M3 10h18M5 10v11M9 10v11M15 10v11M19 10v11M12 2 2 7h20z"/>
              </svg>
            }
            label="Suscripciones"
          />
          <SidebarNavItem
            active={activeSection === 'pending'}
            onClick={() => setActiveSection('pending')}
            icon={<IconClock size={17} />}
            label="Pendientes de Verificación"
            count={pendingProfessionals.length}
          />
          <SidebarNavItem
            active={activeSection === 'users'}
            onClick={() => setActiveSection('users')}
            icon={<IconUsers size={17} />}
            label="Todos los Usuarios"
            count={users.length}
          />
          <SidebarNavItem
            active={activeSection === 'tickets'}
            onClick={() => setActiveSection('tickets')}
            icon={
              <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
              </svg>
            }
            label="Tickets de Soporte"
          />
        </aside>

        {/* Main Content Area */}
        <main className="admin-dashboard__main" style={{
          flex: 1,
          minWidth: 0,
          padding: 'var(--space-8)',
          maxWidth: '1200px',
          width: '100%',
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-6)'
        }}>

        {/* Overview at a glance */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--space-3)' }}>
          <StatCard icon={<IconUsers size={20} />} label="Usuarios totales" value={stats.total} tone="neutral" />
          <StatCard icon={<IconClock size={20} />} label="Pendientes de verificación" value={stats.pendientes} tone="warning" />
          <StatCard icon={<IconCheckCircle size={20} />} label="Profesionales verificados" value={stats.profesionalesVerificados} tone="success" />
          <StatCard icon={<IconHeart size={20} />} label="Pacientes" value={stats.pacientes} tone="primary" />
        </div>

        {/* ── SECTION 0: SUBSCRIPTIONS & ARCA INVOICING ── */}
        {activeSection === 'subscriptions' && (
          <AdminSubscriptionsView users={users} />
        )}

        {/* ── SECTION 1: PENDING VERIFICATION ── */}
        {activeSection === 'pending' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div>
              <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                Profesionales Pendientes de Verificación
              </h2>
              <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                Revisá los datos de cada profesional y aprobá o rechazá su solicitud de registro.
              </p>
            </div>

            {loading ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
                <div className="checkout-spinner" style={{ margin: '0 auto var(--space-3)' }} />
                <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>Cargando profesionales...</p>
              </div>
            ) : pendingProfessionals.length === 0 ? (
              <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center', border: '1px solid var(--color-border)' }}>
                <div style={{ width: '44px', height: '44px', borderRadius: '50%', backgroundColor: 'var(--green-50)', color: 'var(--color-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 8px' }}>
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
                  </svg>
                </div>
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
                      borderLeft: pro.verificadoAdmin === false ? '5px solid var(--color-danger)' : '5px solid var(--color-warning)',
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
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                        <Avatar name={`${pro.nombre} ${pro.apellido || ''}`} fotoUrl={pro.fotoUrl} size={48} />
                        <div>
                          <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                            {pro.nombre} {pro.apellido}
                            <span className={`badge ${pro.verificadoAdmin === false ? 'badge--error' : 'badge--warning'}`}>
                              {pro.verificadoAdmin === false ? 'Rechazado' : 'Pendiente'}
                            </span>
                          </h3>
                          <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                            {pro.email} · {pro.telefono || 'Sin teléfono'}
                          </p>
                        </div>
                      </div>

                      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                        <button
                          onClick={() => handleVerify(pro.id)}
                          className="btn btn--primary btn--sm"
                          style={{ backgroundColor: 'var(--color-success)', borderColor: 'var(--color-success)', color: 'white', fontWeight: 'bold' }}
                        >
                          Aceptar
                        </button>
                        <button
                          onClick={() => handleReject(pro.id)}
                          className="btn btn--danger btn--sm"
                          style={{ backgroundColor: 'var(--color-danger)', borderColor: 'var(--color-danger)', color: 'white', fontWeight: 'bold' }}
                        >
                          Rechazar
                        </button>
                      </div>
                    </div>

                    <div style={{
                      backgroundColor: 'var(--neutral-50)',
                      padding: 'var(--space-4)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--color-border)',
                    }}>
                      <ProfessionalDetailPanel pro={pro} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── SECTION: TICKETS DE SOPORTE ── */}
        {activeSection === 'tickets' && <AdminTicketsView />}

        {/* ── SECTION 2: TODOS LOS USUARIOS ── */}
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
            <div>
              <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                Gestión de Usuarios
              </h2>
              <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                Buscá cualquier cuenta, revisá su perfil completo y cambiá su rol de Paciente a Profesional (o viceversa).
              </p>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                {(['TODOS', 'PSIQUIATRA', 'PACIENTE', 'ADMIN'] as const).map(r => (
                  <button
                    key={r}
                    onClick={() => setRoleFilter(r)}
                    className={`btn btn--sm ${roleFilter === r ? 'btn--primary' : 'btn--ghost'}`}
                  >
                    {r === 'TODOS' ? 'Todos' : r === 'PSIQUIATRA' ? 'Profesionales' : r === 'PACIENTE' ? 'Pacientes' : 'Admins'}
                  </button>
                ))}
              </div>

              <div style={{ position: 'relative' }}>
                <span style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--color-text-secondary)' }}>
                  <IconSearch />
                </span>
                <input
                  type="text"
                  className="admin-dashboard__search-input"
                  placeholder="Buscar por nombre, email o rol..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    padding: '8px 12px 8px 34px',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--color-border)',
                    fontSize: 'var(--text-sm)',
                    minWidth: '260px'
                  }}
                />
              </div>
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
                      <th style={{ padding: '12px var(--space-2)' }}>Usuario</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Email</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Rol</th>
                      <th style={{ padding: '12px var(--space-2)' }}>Estado</th>
                      <th style={{ padding: '12px var(--space-2)', textAlign: 'right' }}>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUsers.map((user) => (
                      <tr key={user.id} style={{ borderBottom: '1px solid var(--color-border)', transition: 'background-color 0.2s' }}>
                        <td style={{ padding: '12px var(--space-2)' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <Avatar name={`${user.nombre} ${user.apellido || ''}`} fotoUrl={user.fotoUrl} size={32} />
                            <div>
                              <div style={{ fontWeight: 'bold' }}>{user.nombre} {user.apellido || ''}</div>
                              <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>#{user.id}</div>
                            </div>
                          </div>
                        </td>
                        <td style={{ padding: '12px var(--space-2)' }}>{user.email}</td>
                        <td style={{ padding: '12px var(--space-2)' }}>
                          <RoleBadge rol={user.rol} />
                        </td>
                        <td style={{ padding: '12px var(--space-2)' }}>
                          <VerificationStatus user={user} />
                        </td>
                        <td style={{ padding: '12px var(--space-2)', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', alignItems: 'center' }}>
                            {user.rol === 'PSIQUIATRA' && (
                              <button className="btn btn--ghost btn--sm" onClick={() => setDetailUser(user)}>
                                Ver perfil
                              </button>
                            )}
                            {user.rol !== 'ADMIN' && (
                              <button
                                className="btn btn--ghost btn--sm"
                                disabled={resettingPasswordFor === user.id}
                                onClick={() => handleResetPassword(user)}
                                title="Generar una contraseña temporal y mandársela por mail"
                              >
                                {resettingPasswordFor === user.id ? 'Reseteando...' : 'Resetear contraseña'}
                              </button>
                            )}
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
                          </div>
                        </td>
                      </tr>
                    ))}
                    {filteredUsers.length === 0 && (
                      <tr>
                        <td colSpan={5} style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-secondary)' }}>
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

      {/* Modal: full professional profile, opened from "Ver perfil" in the users table */}
      {detailUser && (
        <div
          style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 9999, padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setDetailUser(null)}
        >
          <div className="card mobile-modal-card" style={{ maxWidth: '640px', width: '100%', maxHeight: '85vh', overflowY: 'auto', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                <Avatar name={`${detailUser.nombre} ${detailUser.apellido || ''}`} fotoUrl={detailUser.fotoUrl} size={44} />
                <div>
                  <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>{detailUser.nombre} {detailUser.apellido}</h3>
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{detailUser.email}</span>
                </div>
              </div>
              <button className="btn btn--ghost btn--sm mobile-modal-close" onClick={() => setDetailUser(null)} aria-label="Cerrar"><Icon.X /></button>
            </div>
            <ProfessionalDetailPanel pro={detailUser} />
          </div>
        </div>
      )}

      {/* Resultado del reseteo de contraseña — la única vez que este texto plano existe fuera
          del mail que ya se le mandó al usuario. */}
      {generatedPassword && (
        <div
          style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 9999, padding: 'var(--space-4)'
          }}
        >
          <div className="card mobile-modal-card" style={{ maxWidth: '420px', width: '100%', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', textAlign: 'center' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Contraseña reseteada</h3>
            <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
              Se le mandó un mail a <strong>{generatedPassword.email}</strong> con esta contraseña temporal. Es solo por si lo necesitás como respaldo:
            </p>
            <div style={{
              backgroundColor: 'var(--neutral-50)', border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)', padding: 'var(--space-3)',
              fontSize: '20px', fontWeight: 'bold', letterSpacing: '2px', fontFamily: 'monospace'
            }}>
              {generatedPassword.password}
            </div>
            <button className="btn btn--primary" onClick={() => setGeneratedPassword(null)}>
              Listo
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
