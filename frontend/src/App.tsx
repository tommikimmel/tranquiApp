import { useState, useEffect } from 'react'
import './styles/index.css'
import './styles/dashboard.css'
import LandingPage from './components/LandingPage'
import CheckoutFlow from './components/CheckoutFlow'
import LoginPage from './components/LoginPage'
import PatientsView from './components/PatientsView'
import VisitorsView from './components/VisitorsView'
import { api } from './api/api'

type AppView = 'landing' | 'checkout' | 'dashboard' | 'login'

interface CheckoutTarget {
  id: string
  name: string
  degree: string
  specialty: string
  matricula: string
  price: number
  nextSlot: string
  nextSlotDay: string
}

// ── Types ──────────────────────────────────────────────────────
interface Appointment {
  id: string
  patientName: string
  hour: string
  ampm: string
  type: string
  status: 'confirmed' | 'pending' | 'completed'
  meetLink: string
}



type NavSection = 'dashboard' | 'agenda' | 'patients' | 'prescriptions' | 'visitors' | 'payments' | 'settings'

// ── Mock Data (Removido ya que se usan APIs reales) ───────────────────────────────────

// ── Icons (inline SVG) ─────────────────────────────────────────
const Icon = {
  Dashboard: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" />
    </svg>
  ),
  Calendar: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  ),
  Users: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  ),
  CreditCard: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="4" width="22" height="16" rx="2" /><line x1="1" y1="10" x2="23" y2="10" />
    </svg>
  ),
  Settings: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  ),
  Video: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
      <polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" />
    </svg>
  ),
  ArrowUp: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 12, height: 12 }}>
      <polyline points="18 15 12 9 6 15" />
    </svg>
  ),
  ArrowDown: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 12, height: 12 }}>
      <polyline points="6 9 12 15 18 9" />
    </svg>
  ),
  Check: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 18, height: 18 }}>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  ),
  AlertTriangle: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: 20, height: 20 }}>
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  ),
  Plus: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 16, height: 16 }}>
      <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  ),
  Bell: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: 20, height: 20 }}>
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  ),
  Prescription: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <path d="M9 15l2 2 4-4" />
    </svg>
  ),
}

// ── Sidebar Component ──────────────────────────────────────────
function Sidebar({ activeNav, onNavChange, medicoInfo }: { activeNav: NavSection; onNavChange: (s: NavSection) => void; medicoInfo: any }) {
  const navItems = [
    { id: 'dashboard' as NavSection, label: 'Inicio', Icon: Icon.Dashboard },
    { id: 'agenda' as NavSection, label: 'Agenda', Icon: Icon.Calendar, badge: 4 },
    { id: 'patients' as NavSection, label: 'Pacientes', Icon: Icon.Users },
    { id: 'visitors' as NavSection, label: 'Visitadores', Icon: Icon.Users },
    { id: 'payments' as NavSection, label: 'Cobros', Icon: Icon.CreditCard },
    { id: 'settings' as NavSection, label: 'Configuración', Icon: Icon.Settings },
  ]

  return (
    <aside className="sidebar">
      <div className="sidebar__logo">
        <div className="sidebar__logo-btn" style={{ cursor: 'default' }}>
          <img src="/logo-tranqui.png" alt="Tranqui" className="sidebar__logo-img" />
          <span className="sidebar__logo-text">tranqui</span>
        </div>
      </div>

      <nav className="sidebar__nav" role="navigation" aria-label="Navegación principal">
        <span className="sidebar__nav-section-title">Panel</span>
        {navItems.map(({ id, label, Icon: NavIcon, badge }) => (
          <button
            key={id}
            className={`sidebar__nav-item ${activeNav === id ? 'active' : ''}`}
            onClick={() => onNavChange(id)}
            aria-current={activeNav === id ? 'page' : undefined}
          >
            <NavIcon />
            {label}
            {badge && <span className="sidebar__badge">{badge}</span>}
          </button>
        ))}
      </nav>

      <div className="sidebar__footer">
        <div className="sidebar__user" role="button" tabIndex={0}>
          <div className="sidebar__avatar" aria-hidden="true">
            {medicoInfo?.initials || 'LP'}
          </div>
          <div className="sidebar__user-info">
            <div className="sidebar__user-name">{medicoInfo?.name || 'Lic. Paula Rossi'}</div>
            <div className="sidebar__user-role">
              {medicoInfo?.degree || 'Psicóloga'} — {medicoInfo?.matricula ? `MN ${medicoInfo.matricula}` : 'MN 49281'}
            </div>
          </div>
        </div>
      </div>
    </aside>
  )
}

// ── MP Connect Banner ──────────────────────────────────────────
function MPConnectBanner({ connected, onConnect }: { connected: boolean; onConnect: () => void }) {
  if (connected) {
    return (
      <div className="mp-connect-banner mp-connect-banner--connected" role="status">
        <div className="mp-connect-banner__icon">
          <img src="/logo-mp.png" alt="Mercado Pago" className="mp-connect-banner__logo" />
        </div>
        <div className="mp-connect-banner__content">
          <h2 className="mp-connect-banner__title">Mercado Pago conectado ✓</h2>
          <p className="mp-connect-banner__body">
            Tu cuenta está vinculada. Los pagos se acreditan automáticamente en tu cuenta de Mercado Pago
            al confirmarse cada sesión. Tranqui es 100% libre de comisiones.
          </p>
        </div>
        <button className="btn btn--ghost btn--sm">Desconectar</button>
      </div>
    )
  }

  return (
    <div className="mp-connect-banner" role="alert">
      <div className="mp-connect-banner__icon">
          <img src="/logo-mp.png" alt="Mercado Pago" className="mp-connect-banner__logo" />
        </div>
      <div className="mp-connect-banner__content">
        <h2 className="mp-connect-banner__title">Conectá tu cuenta de Mercado Pago</h2>
        <p className="mp-connect-banner__body">
          Para que los pacientes puedan pagarte directamente, necesitás vincular tu cuenta de Mercado Pago.
          El proceso toma menos de 2 minutos. Sin esto, tu perfil no aparece en las búsquedas públicas.
        </p>
      </div>
      <button className="btn btn--primary" onClick={onConnect} id="btn-connect-mp">
        Conectar Mercado Pago
      </button>
    </div>
  )
}

// ── Stats Overview ─────────────────────────────────────────────
function StatsOverview({ stats }: { stats: any }) {
  if (!stats) {
    return (
      <div className="stats-grid">
        {[1, 2, 3, 4].map((i) => (
          <article key={i} className="stat-card" style={{ opacity: 0.6 }}>
            <div style={{ height: '24px', backgroundColor: 'var(--color-border)', width: '60%', borderRadius: '4px', marginBottom: '8px' }} />
            <div style={{ height: '32px', backgroundColor: 'var(--color-border)', width: '40%', borderRadius: '4px' }} />
          </article>
        ))}
      </div>
    )
  }

  const getChangeCls = (changeStr: string) => {
    return changeStr && changeStr.startsWith('-') ? 'stat-card__change--down' : 'stat-card__change--up'
  }

  const renderIcon = (changeStr: string) => {
    if (!changeStr) return null
    if (changeStr.includes('Política')) return <Icon.ArrowUp />
    return changeStr.startsWith('-') ? <Icon.ArrowDown /> : <Icon.ArrowUp />
  }

  return (
    <div className="stats-grid">
      <article className="stat-card">
        <div className="stat-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
            <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" />
            <line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
          </svg>
        </div>
        <div className="stat-card__value">{stats.sessionsToday}</div>
        <div className="stat-card__label">Sesiones hoy</div>
        <div className={`stat-card__change ${getChangeCls(stats.sessionsTodayChange)}`}>
          {renderIcon(stats.sessionsTodayChange)} {stats.sessionsTodayChange}
        </div>
      </article>

      <article className="stat-card">
        <div className="stat-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
            <line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
          </svg>
        </div>
        <div className="stat-card__value">${(stats.earningsThisWeek || 0).toLocaleString('es-AR')}</div>
        <div className="stat-card__label">Liquidado esta semana</div>
        <div className={`stat-card__change ${getChangeCls(stats.earningsThisWeekChange)}`}>
          {renderIcon(stats.earningsThisWeekChange)} {stats.earningsThisWeekChange}
        </div>
      </article>

      <article className="stat-card">
        <div className="stat-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
          </svg>
        </div>
        <div className="stat-card__value">{stats.activePatients}</div>
        <div className="stat-card__label">Pacientes activos</div>
        <div className="stat-card__change stat-card__change--up">
          <Icon.ArrowUp /> {stats.activePatientsChange}
        </div>
      </article>

      <article className="stat-card">
        <div className="stat-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
            <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
          </svg>
        </div>
        <div className="stat-card__value">{stats.noShowsThisMonth}</div>
        <div className="stat-card__label">No-shows este mes</div>
        <div className="stat-card__change stat-card__change--up">
          <Icon.ArrowUp /> {stats.noShowsChange}
        </div>
      </article>
    </div>
  )
}

// ── Appointment Item ───────────────────────────────────────────
function AppointmentCard({ appt }: { appt: Appointment }) {
  const statusMap = {
    confirmed: { label: 'Confirmado', cls: 'badge--success' },
    pending: { label: 'Pago pendiente', cls: 'badge--warning' },
    completed: { label: 'Completado', cls: 'badge--neutral' },
  }
  const st = statusMap[appt.status]

  return (
    <li className="appointment-item" role="listitem">
      <div className="appointment-item__time">
        <div className="appointment-item__hour">{appt.hour}:00</div>
        <div className="appointment-item__ampm">{appt.ampm}</div>
      </div>

      <div className="appointment-item__divider" aria-hidden="true" />

      <div className="appointment-item__info">
        <div className="appointment-item__name">{appt.patientName}</div>
        <div className="appointment-item__meta">
          {appt.type} · 50 min
          <span className={`badge ${st.cls}`} style={{ marginLeft: 8 }}>{st.label}</span>
        </div>
      </div>

      <div className="appointment-item__actions">
        {appt.meetLink && appt.status === 'confirmed' && (
          <a
            href={appt.meetLink}
            target="_blank"
            rel="noopener noreferrer"
            className="btn btn--primary btn--sm"
            id={`btn-meet-${appt.id}`}
          >
            <Icon.Video />
            Unirse
          </a>
        )}
        {appt.status === 'pending' && (
          <span className="btn btn--ghost btn--sm" style={{ color: 'var(--color-warning)', cursor: 'default' }}>
            Esperando pago
          </span>
        )}
      </div>
    </li>
  )
}

function AgendaView({ initialAvailability, onSave }: { initialAvailability: any[]; onSave: (data: any[]) => Promise<void> }) {
  const weekdays = [
    { name: 'Lunes', abbr: 'Lun', num: 1 },
    { name: 'Martes', abbr: 'Mar', num: 2 },
    { name: 'Miércoles', abbr: 'Mié', num: 3 },
    { name: 'Jueves', abbr: 'Jue', num: 4 },
    { name: 'Viernes', abbr: 'Vie', num: 5 },
  ]
  const baseSlots = ['09:00', '10:00', '11:00', '14:00', '15:00', '16:00']

  // Internal state of active slots per day
  const [activeSlots, setActiveSlots] = useState<{ [key: number]: { [key: string]: boolean } }>(() => {
    const state: { [key: number]: { [key: string]: boolean } } = { 1: {}, 2: {}, 3: {}, 4: {}, 5: {} }
    
    // Populate active slots from API availability
    initialAvailability.forEach((disp) => {
      const dayNum = disp.diaSemana
      if (dayNum >= 1 && dayNum <= 5) {
        const start = parseInt(disp.horaInicio.split(':')[0])
        const end = parseInt(disp.horaFin.split(':')[0])
        
        baseSlots.forEach((slot) => {
          const hour = parseInt(slot.split(':')[0])
          if (hour >= start && hour < end) {
            state[dayNum][slot] = true
          }
        })
      }
    })
    return state
  })

  const [saving, setSaving] = useState(false)

  const toggleSlot = (dayNum: number, slot: string) => {
    setActiveSlots(prev => ({
      ...prev,
      [dayNum]: {
        ...prev[dayNum],
        [slot]: !prev[dayNum][slot]
      }
    }))
  }

  const handleSave = async () => {
    setSaving(true)
    const dtos: any[] = []
    
    weekdays.forEach((day) => {
      baseSlots.forEach((slot) => {
        if (activeSlots[day.num][slot]) {
          const startHour = parseInt(slot.split(':')[0])
          const endHour = startHour + 1
          const startStr = `${String(startHour).padStart(2, '0')}:00`
          const endStr = `${String(endHour).padStart(2, '0')}:00`
          
          dtos.push({
            diaSemana: day.num,
            horaInicio: startStr,
            horaFin: endStr
          })
        }
      })
    })

    try {
      await onSave(dtos)
      alert("Disponibilidad guardada correctamente ✓")
    } catch (err) {
      console.error(err)
      alert("Error al guardar disponibilidad")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="card">
      <div className="card__header">
        <div>
          <h2 className="card__title">Disponibilidad semanal</h2>
          <p className="card__subtitle">Hacé clic en un horario para habilitarlo o deshabilitarlo</p>
        </div>
        <button className="btn btn--primary btn--sm" onClick={handleSave} disabled={saving} id="btn-save-availability">
          {saving ? 'Guardando...' : 'Guardar cambios'}
        </button>
      </div>

      <div className="availability-grid">
        {weekdays.map((day) => {
          const isActive = baseSlots.some(slot => activeSlots[day.num][slot])
          return (
            <div className="day-column" key={day.num}>
              <div className={`day-column__header ${isActive ? 'day-column__active' : ''}`}>
                {day.abbr}
              </div>
              {baseSlots.map((slot) => {
                const isSlotActive = !!activeSlots[day.num][slot]
                return (
                  <button
                    key={slot}
                    className={`time-slot ${isSlotActive ? 'time-slot--available' : 'time-slot--booked'}`}
                    onClick={() => toggleSlot(day.num, slot)}
                    style={{ cursor: 'pointer' }}
                    aria-label={`${day.name} ${slot} ${isSlotActive ? '- disponible' : '- inactivo'}`}
                  >
                    {slot}
                  </button>
                )
              })}
            </div>
          )
        })}
      </div>
    </div>
  )
}



// ── Prescription View ─────────────────────────────────────────
const MOCK_PATIENTS = [
  { id: '1', name: 'Valentina Moreno', email: 'valentina.m@gmail.com' },
  { id: '2', name: 'Matías Rodríguez', email: 'matias.r@gmail.com' },
  { id: '3', name: 'Lucía Fernández', email: 'lucia.f@gmail.com' },
  { id: '4', name: 'Santiago Torres', email: 'santiago.t@gmail.com' },
]

const COMMON_MEDS = [
  'Escitalopram 10mg',
  'Sertralina 50mg',
  'Clonazepam 0.5mg',
  'Alprazolam 0.25mg',
  'Quetiapina 25mg',
  'Risperidona 1mg',
  'Melatonina 3mg',
  'Pregabalina 75mg',
]

function PrescriptionView({ onSend }: { onSend: (data: any) => Promise<void> }) {
  const [selectedPatient, setSelectedPatient] = useState('')
  const [medications, setMedications] = useState([{ name: '', dosage: '', frequency: '', duration: '' }])
  const [diagnosis, setDiagnosis] = useState('')
  const [notes, setNotes] = useState('')
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)

  const addMedication = () => {
    setMedications([...medications, { name: '', dosage: '', frequency: '', duration: '' }])
  }

  const updateMedication = (index: number, field: string, value: string) => {
    const updated = [...medications]
    updated[index] = { ...updated[index], [field]: value }
    setMedications(updated)
  }

  const removeMedication = (index: number) => {
    if (medications.length > 1) {
      setMedications(medications.filter((_, i) => i !== index))
    }
  }

  const canSend = selectedPatient && medications[0].name.trim().length > 0

  const handleSend = () => {
    setSending(true)
    onSend({
      pacienteId: Number(selectedPatient),
      medications,
      diagnosis,
      notes
    })
    .then(() => {
      setSent(true)
    })
    .catch((err) => {
      console.error("Error al emitir receta:", err)
      alert("Error al emitir y enviar receta")
    })
    .finally(() => {
      setSending(false)
    })
  }

  const handleReset = () => {
    setSelectedPatient('')
    setMedications([{ name: '', dosage: '', frequency: '', duration: '' }])
    setDiagnosis('')
    setNotes('')
    setSent(false)
  }

  if (sent) {
    const patient = MOCK_PATIENTS.find(p => p.id === selectedPatient)
    return (
      <div className="card" style={{ textAlign: 'center', padding: 'var(--space-10)' }}>
        <div style={{
          width: 64, height: 64, borderRadius: 'var(--radius-full)',
          background: 'var(--green-50)', border: '2px solid var(--green-300)',
          color: 'var(--green-600)', display: 'flex', alignItems: 'center',
          justifyContent: 'center', margin: '0 auto var(--space-5)',
        }}>
          <Icon.Check />
        </div>
        <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-2xl)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-3)' }}>
          Receta enviada
        </h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
          Se envió la receta a <strong>{patient?.name}</strong>
        </p>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)', marginBottom: 'var(--space-8)' }}>
          {patient?.email} · PDF adjunto por email y WhatsApp
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center' }}>
          <button className="btn btn--primary" onClick={handleReset} id="btn-new-prescription">
            Nueva receta
          </button>
        </div>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* Header info */}
      <div className="alert-banner alert-banner--success" role="status">
        <span className="alert-banner__icon">
          <Icon.Prescription />
        </span>
        <div className="alert-banner__content">
          <div className="alert-banner__title">Receta electrónica asistida</div>
          <div className="alert-banner__body">
            Completá los datos, generamos el PDF y lo enviamos directo al paciente por email y WhatsApp.
            La firma digital del documento requiere tu certificado de firma electrónica.
          </div>
        </div>
      </div>

      {/* Patient select */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Paciente</h2>
        </div>
        <div className="form-group">
          <label className="form-label form-label--required" htmlFor="rx-patient">Seleccionar paciente</label>
          <select
            id="rx-patient"
            className="form-input"
            value={selectedPatient}
            onChange={(e) => setSelectedPatient(e.target.value)}
          >
            <option value="">Elegir paciente...</option>
            {MOCK_PATIENTS.map(p => (
              <option key={p.id} value={p.id}>{p.name} — {p.email}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Medications */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Medicación</h2>
          <button className="btn btn--secondary btn--sm" onClick={addMedication} id="btn-add-med">
            <Icon.Plus /> Agregar
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          {medications.map((med, i) => (
            <div key={i} style={{
              display: 'flex', flexDirection: 'column', gap: 'var(--space-3)',
              padding: 'var(--space-4)', background: 'var(--neutral-50)',
              borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
              position: 'relative',
            }}>
              {medications.length > 1 && (
                <button
                  onClick={() => removeMedication(i)}
                  style={{
                    position: 'absolute', top: 8, right: 8,
                    background: 'none', border: 'none', color: 'var(--color-text-secondary)',
                    fontSize: 'var(--text-lg)', cursor: 'pointer', lineHeight: 1,
                  }}
                  aria-label="Quitar medicación"
                >
                  ×
                </button>
              )}
              <div className="form-group">
                <label className="form-label form-label--required">Medicamento</label>
                <input
                  className="form-input"
                  type="text"
                  list={`meds-list-${i}`}
                  placeholder="Ej: Escitalopram 10mg"
                  value={med.name}
                  onChange={(e) => updateMedication(i, 'name', e.target.value)}
                />
                <datalist id={`meds-list-${i}`}>
                  {COMMON_MEDS.map(m => <option key={m} value={m} />)}
                </datalist>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-3)' }}>
                <div className="form-group">
                  <label className="form-label">Dosis</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej: 1 comp"
                    value={med.dosage}
                    onChange={(e) => updateMedication(i, 'dosage', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Frecuencia</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej: cada 24hs"
                    value={med.frequency}
                    onChange={(e) => updateMedication(i, 'frequency', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Duración</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej: 30 días"
                    value={med.duration}
                    onChange={(e) => updateMedication(i, 'duration', e.target.value)}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Diagnosis + Notes */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Diagnóstico e indicaciones</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label" htmlFor="rx-diagnosis">Diagnóstico (CIE-10)</label>
            <input
              id="rx-diagnosis"
              className="form-input"
              type="text"
              placeholder="Ej: F41.1 — Trastorno de ansiedad generalizada"
              value={diagnosis}
              onChange={(e) => setDiagnosis(e.target.value)}
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="rx-notes">Indicaciones para el paciente</label>
            <textarea
              id="rx-notes"
              className="form-input"
              rows={3}
              placeholder="Instrucciones adicionales, controles, próximo turno..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              style={{ resize: 'vertical', fontFamily: 'var(--font-body)' }}
            />
          </div>
        </div>
      </div>

      {/* Send */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Enviar receta</h2>
        </div>
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)' }}>
          Se genera un PDF con tus datos profesionales (Lic. Paula Rossi · MN 49281) y se envía al paciente por email y WhatsApp.
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <button
            className={`btn btn--primary ${sending ? 'btn--loading' : ''}`}
            disabled={!canSend || sending}
            onClick={handleSend}
            id="btn-send-prescription"
          >
            {sending ? 'Enviando...' : 'Generar PDF y enviar al paciente'}
          </button>
          <button className="btn btn--ghost" disabled={sending} onClick={handleReset}>
            Limpiar
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Settings: Tariff & Profile ─────────────────────────────────
// DEFAULT_TARIFFS mock removed since values are loaded from API

function SettingsView({ medicoInfo, onSave }: { medicoInfo: any; onSave: (updated: any) => Promise<void> }) {
  const [name, setName] = useState(medicoInfo?.name || '')
  const [degree, setDegree] = useState(medicoInfo?.degree || '')
  const [specialty, setSpecialty] = useState(medicoInfo?.specialty || '')
  const [matricula, setMatricula] = useState(medicoInfo?.matricula || '')
  const [cuit, setCuit] = useState(medicoInfo?.cuit || '')
  const [tariffs, setTariffs] = useState<any[]>(medicoInfo?.tariffs || [])

  const updateTariff = (id: string, field: 'price' | 'enabled', value: number | boolean) => {
    setTariffs(tariffs.map(t => t.id === id ? { ...t, [field]: value } : t))
  }

  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    setSaving(true)
    try {
      await onSave({
        ...medicoInfo,
        name,
        degree,
        specialty,
        matricula,
        cuit,
        tariffs
      })
      alert("Configuración guardada con éxito ✓")
    } catch (err) {
      console.error(err)
      alert("Error al guardar la configuración")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* Profile */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Perfil profesional</h2>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-name">Nombre completo</label>
            <input id="input-name" className="form-input" type="text" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-degree">Título profesional</label>
            <input id="input-degree" className="form-input" type="text" value={degree} onChange={(e) => setDegree(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-specialty">Especialidad</label>
            <input id="input-specialty" className="form-input" type="text" value={specialty} onChange={(e) => setSpecialty(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-matricula">Matrícula Nacional</label>
            <input id="input-matricula" className="form-input" type="text" value={matricula} onChange={(e) => setMatricula(e.target.value)} />
            <span className="form-helper">Verificada ✓</span>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="input-cuit">CUIT</label>
            <input id="input-cuit" className="form-input" type="text" value={cuit} onChange={(e) => setCuit(e.target.value)} />
          </div>
        </div>
        <div style={{ marginTop: 'var(--space-6)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-profile">
            {saving ? 'Guardando...' : 'Guardar perfil'}
          </button>
        </div>
      </div>

      {/* Tariffs */}
      <div className="card">
        <div className="card__header">
          <div>
            <h2 className="card__title">Honorarios y servicios</h2>
            <p className="card__subtitle">Configurá los precios de cada tipo de consulta. Solo los servicios habilitados se muestran al paciente.</p>
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          {/* Table header */}
          <div style={{
            display: 'grid', gridTemplateColumns: '44px 1fr 140px',
            gap: 'var(--space-3)', padding: 'var(--space-2) var(--space-3)',
            fontSize: 'var(--text-xs)', fontWeight: 'var(--font-weight-semi)',
            color: 'var(--color-text-secondary)', textTransform: 'uppercase' as const,
            letterSpacing: '0.05em', borderBottom: '1px solid var(--color-border)',
          }}>
            <span></span>
            <span>Servicio</span>
            <span>Valor (ARS)</span>
          </div>
          {tariffs.map((t) => (
            <div
              key={t.id}
              style={{
                display: 'grid', gridTemplateColumns: '44px 1fr 140px',
                gap: 'var(--space-3)', padding: 'var(--space-3)',
                alignItems: 'center', borderBottom: '1px solid var(--color-border)',
                opacity: t.enabled ? 1 : 0.5,
                transition: 'opacity 150ms',
              }}
            >
              <label className="toggle" style={{ transform: 'scale(0.8)' }}>
                <input
                  type="checkbox"
                  checked={t.enabled}
                  onChange={(e) => updateTariff(t.id, 'enabled', e.target.checked)}
                />
                <span className="toggle__track" />
              </label>
              <span style={{
                fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)',
                fontWeight: t.enabled ? 'var(--font-weight-medium)' : 'var(--font-weight-regular)',
              }}>
                {t.label}
              </span>
              <input
                className="form-input"
                type="number"
                value={t.price}
                onChange={(e) => updateTariff(t.id, 'price', Number(e.target.value))}
                disabled={!t.enabled}
                style={{ padding: 'var(--space-2) var(--space-3)', fontSize: 'var(--text-sm)' }}
              />
            </div>
          ))}
        </div>
        <div style={{ marginTop: 'var(--space-5)', display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-tariffs">
            {saving ? 'Guardando...' : 'Guardar honorarios'}
          </button>
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            Tranqui es 100% libre de comisiones, por lo que recibís la totalidad de tus honorarios.
          </span>
        </div>
      </div>

      {/* Notifications */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Notificaciones</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          {[
            { id: 'notif-new-booking', label: 'Nueva reserva', desc: 'Te avisamos por email y WhatsApp cuando un paciente agenda.' },
            { id: 'notif-cancel', label: 'Cancelaciones', desc: 'Notificación cuando un paciente cancela o reprograma.' },
            { id: 'notif-reminder', label: 'Recordatorio de sesión', desc: '1 hora antes del inicio de cada sesión.' },
          ].map(({ id, label, desc }) => (
            <div key={id} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
              <label className="toggle">
                <input type="checkbox" defaultChecked id={id} />
                <span className="toggle__track" />
              </label>
              <div>
                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--font-weight-medium)' }}>{label}</div>
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function DashboardHome({ mpConnected, onConnect, appointments, stats }: { mpConnected: boolean; onConnect: () => void; appointments: Appointment[]; stats: any }) {
  const dateStr = new Date().toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' });
  const capitalizedDate = dateStr.charAt(0).toUpperCase() + dateStr.slice(1);

  return (
    <>
      <MPConnectBanner connected={mpConnected} onConnect={onConnect} />

      <StatsOverview stats={stats} />

      <div className="card">
        <div className="card__header">
          <div>
            <h2 className="card__title">Sesiones de hoy</h2>
            <p className="card__subtitle">{capitalizedDate} · {appointments.length} sesion{appointments.length !== 1 ? 'es' : ''} programada{appointments.length !== 1 ? 's' : ''}</p>
          </div>
          <button className="btn btn--secondary btn--sm" id="btn-add-slot">
            <Icon.Plus />
            Agregar horario
          </button>
        </div>

        {appointments.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', padding: 'var(--space-6)', textAlign: 'center' }}>
            No tenés sesiones programadas para el día de hoy.
          </p>
        ) : (
          <ul className="appointment-list" role="list" aria-label="Sesiones de hoy">
            {appointments.map((appt) => (
              <AppointmentCard key={appt.id} appt={appt} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

// ── Root App ───────────────────────────────────────────────────
export default function App() {
  const [view, setView] = useState<AppView>('landing')
  const [activeNav, setActiveNav] = useState<NavSection>('dashboard')
  const [mpConnected, setMpConnected] = useState(false)
  const [checkoutTarget, setCheckoutTarget] = useState<CheckoutTarget | null>(null)

  // API states
  const [currentUser, setCurrentUser] = useState<any>(null)
  const [medicoInfo, setMedicoInfo] = useState<any>(null)
  const [todayAppointments, setTodayAppointments] = useState<any[]>([])
  const [availability, setAvailability] = useState<any[]>([])
  const [stats, setStats] = useState<any>(null)
  const [loadingDashboard, setLoadingDashboard] = useState(false)

  // Check user session on app load
  useEffect(() => {
    api.getMe()
      .then((user) => {
        setCurrentUser(user)
        if (user && (user.rol === 'PSIQUIATRA' || user.rol === 'MEDICO')) {
          setView('dashboard')
        } else {
          setView('landing')
        }
      })
      .catch(() => {
        setCurrentUser(null)
      })
  }, [])

  // Route protection guard
  useEffect(() => {
    if (currentUser) {
      const isPro = currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO'
      if (isPro && (view === 'landing' || view === 'login')) {
        setView('dashboard')
      } else if (!isPro && view === 'dashboard') {
        setView('landing')
      }
    }
  }, [currentUser, view])

  useEffect(() => {
    if (view === 'dashboard') {
      setLoadingDashboard(true)
      Promise.all([
        api.getPerfil(),
        api.getTurnosHoy(),
        api.getDisponibilidad(),
        api.getStats()
      ])
        .then(([perfil, turnos, disp, statsData]) => {
          setMedicoInfo(perfil)
          setTodayAppointments(turnos || [])
          setAvailability(disp || [])
          setStats(statsData)
        })
        .catch((err) => {
          console.error("Error al inicializar dashboard:", err)
          setView('login')
        })
        .finally(() => {
          setLoadingDashboard(false)
        })
    }
  }, [view])

  const handleSaveAvailability = async (data: any[]) => {
    const updated = await api.actualizarDisponibilidad(data)
    setAvailability(updated || data)
  }

  const handleSaveSettings = async (data: any) => {
    const updated = await api.actualizarPerfil(data)
    setMedicoInfo(updated)
  }

  const handleSendPrescription = async (data: any) => {
    await api.enviarReceta(data)
  }

  const handleConnect = () => {
    alert('En producción: redirige a Mercado Pago OAuth para vincular tu cuenta.')
    setMpConnected(true)
  }

  const handleBook = (pro: CheckoutTarget) => {
    setCheckoutTarget(pro)
    setView('checkout')
  }

  const handleLogout = async () => {
    try {
      await api.logout()
    } catch (err) {
      console.error("Error al cerrar sesión:", err)
    }
    setCurrentUser(null)
    setView('landing')
  }

  const pageTitle: Record<NavSection, string> = {
    dashboard: 'Inicio',
    agenda: 'Mi agenda',
    patients: 'Pacientes',
    prescriptions: 'Recetas',
    visitors: 'Visitadores médicos',
    payments: 'Cobros y liquidaciones',
    settings: 'Configuración',
  }

  const renderContent = () => {
    if (loadingDashboard) {
      return (
        <div style={{ textAlign: 'center', padding: 'var(--space-20)' }}>
          <div className="checkout-spinner" style={{ margin: '0 auto var(--space-4)' }} />
          <p style={{ color: 'var(--color-text-secondary)' }}>Cargando panel de control...</p>
        </div>
      )
    }

    switch (activeNav) {
      case 'dashboard': 
        return <DashboardHome mpConnected={mpConnected} onConnect={handleConnect} appointments={todayAppointments} stats={stats} />
      case 'agenda': 
        return <AgendaView initialAvailability={availability} onSave={handleSaveAvailability} />
      case 'patients': 
        return <PatientsView />
      case 'prescriptions': 
        return <PrescriptionView onSend={handleSendPrescription} />
      case 'visitors': 
        return <VisitorsView />
      case 'payments': return (
        <div className="card">
          <div className="card__header"><h2 className="card__title">Cobros y liquidaciones</h2></div>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>Próximamente — historial de pagos recibidos (Tranqui es 100% libre de comisiones).</p>
        </div>
      )
      case 'settings': 
        return <SettingsView medicoInfo={medicoInfo} onSave={handleSaveSettings} />
    }
  }

  if (view === 'landing') {
    return (
      <LandingPage
        currentUser={currentUser}
        onNavigateToDashboard={() => setView('login')}
        onBook={handleBook}
        onLogout={handleLogout}
        onGoToDashboard={() => setView('dashboard')}
      />
    )
  }

  if (view === 'login') {
    return (
      <LoginPage
        onLoginSuccess={(user) => {
          setCurrentUser(user)
          if (user.rol === 'PSIQUIATRA' || user.rol === 'MEDICO') {
            setView('dashboard')
          } else {
            setView('landing')
          }
        }}
        onBack={() => setView('landing')}
      />
    )
  }

  if (view === 'checkout' && checkoutTarget) {
    return (
      <CheckoutFlow
        professional={checkoutTarget}
        onBack={() => setView('landing')}
        onComplete={() => setView('landing')}
      />
    )
  }

  return (
    <div className="dashboard-layout">
      <Sidebar activeNav={activeNav} onNavChange={setActiveNav} medicoInfo={medicoInfo} />

      <header className="dashboard-header" role="banner">
        <h1 className="dashboard-header__title">{pageTitle[activeNav]}</h1>
        <div className="dashboard-header__actions">
          <button className="btn btn--icon btn--ghost" aria-label="Notificaciones">
            <Icon.Bell />
          </button>
          <div className="sidebar__avatar" aria-label="Menú de perfil" role="button" tabIndex={0} style={{ cursor: 'pointer' }}>
            {medicoInfo?.initials || 'LP'}
          </div>
        </div>
      </header>

      <main className="dashboard-main" role="main" id="main-content">
        {renderContent()}
      </main>
    </div>
  )
}
