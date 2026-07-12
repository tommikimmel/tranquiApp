import React, { useState, useEffect, useMemo } from 'react'
import './styles/index.css'
import './styles/dashboard.css'
import LandingPage from './components/LandingPage'
import CheckoutFlow from './components/CheckoutFlow'
import LoginPage from './components/LoginPage'
import PatientsView from './components/PatientsView'
import VisitorsView from './components/VisitorsView'
import AdminDashboard from './components/AdminDashboard'
import AddressMapPicker from './components/AddressMapPicker'
import { api } from './api/api'
import { useAlert } from './context/AlertContext'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

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
  Video: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" />
    </svg>
  ),
  Building: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="4" y="2" width="16" height="20" rx="1" />
      <line x1="9" y1="7" x2="9" y2="7.01" /><line x1="15" y1="7" x2="15" y2="7.01" />
      <line x1="9" y1="11" x2="9" y2="11.01" /><line x1="15" y1="11" x2="15" y2="11.01" />
      <line x1="9" y1="15" x2="9" y2="15.01" /><line x1="15" y1="15" x2="15" y2="15.01" />
      <path d="M9 22v-4h6v4" />
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
  Eye: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  ),
  EyeOff: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  ),
  Bell: ({ hasUnread }: { hasUnread?: boolean }) => (
    <div style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: 20, height: 20 }}>
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      {hasUnread && (
        <span style={{
          position: 'absolute',
          top: '0px',
          right: '0px',
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          backgroundColor: '#ef4444',
          border: '1px solid white'
        }} />
      )}
    </div>
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
          <img src="/tranqui-icon.webp" alt="Tranqui" className="sidebar__logo-img" />
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
function MPConnectBanner({ connected, onConnect, onDisconnect }: { connected: boolean; onConnect: () => void; onDisconnect: () => void }) {
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
        <button className="btn btn--ghost btn--sm" onClick={onDisconnect}>Desconectar</button>
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
  const { showAlert } = useAlert();
  const weekdays = [
    { name: 'Lunes', abbr: 'Lun', num: 1 },
    { name: 'Martes', abbr: 'Mar', num: 2 },
    { name: 'Miércoles', abbr: 'Mié', num: 3 },
    { name: 'Jueves', abbr: 'Jue', num: 4 },
    { name: 'Viernes', abbr: 'Vie', num: 5 },
  ]
  const baseSlots = ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00']

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

  // To-Do List state and hooks
  const [tasks, setTasks] = useState<{ id: string; text: string; completed: boolean; category: 'clinical' | 'admin' | 'urgent' }[]>(() => {
    try {
      const saved = localStorage.getItem('tranqui_medico_tasks');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.error("Error reading tasks from localStorage", e);
    }
    return [
      { id: '1', text: 'Revisar ficha clínica de Rossi', completed: false, category: 'clinical' },
      { id: '2', text: 'Enviar copagos de OSDE del mes', completed: false, category: 'admin' },
      { id: '3', text: 'Renovar firma digital', completed: true, category: 'urgent' },
    ];
  });

  const [newTaskText, setNewTaskText] = useState('');
  const [newTaskCategory, setNewTaskCategory] = useState<'clinical' | 'admin' | 'urgent'>('clinical');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  const sortedTasks = React.useMemo(() => {
    return [...tasks].sort((a, b) => {
      if (a.category === 'urgent' && b.category !== 'urgent') return -1;
      if (a.category !== 'urgent' && b.category === 'urgent') return 1;
      return 0;
    });
  }, [tasks]);

  const totalPages = Math.ceil(sortedTasks.length / itemsPerPage);
  const validCurrentPage = Math.min(currentPage, Math.max(1, totalPages));
  const startIndex = (validCurrentPage - 1) * itemsPerPage;
  const paginatedTasks = sortedTasks.slice(startIndex, startIndex + itemsPerPage);

  // Persist tasks on change
  useEffect(() => {
    try {
      localStorage.setItem('tranqui_medico_tasks', JSON.stringify(tasks));
    } catch (e) {
      console.error("Error saving tasks to localStorage", e);
    }
  }, [tasks]);

  const handleAddTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskText.trim()) return;
    const newTask = {
      id: String(Date.now()),
      text: newTaskText.trim(),
      completed: false,
      category: newTaskCategory,
    };
    setTasks([...tasks, newTask]);
    setNewTaskText('');
  };

  const handleToggleTask = (id: string) => {
    setTasks(tasks.map(t => t.id === id ? { ...t, completed: !t.completed } : t));
  };

  const handleDeleteTask = (id: string) => {
    setTasks(tasks.filter(t => t.id !== id));
  };

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
      showAlert("Disponibilidad guardada correctamente ✓", "success")
    } catch (err) {
      console.error(err)
      showAlert("Error al guardar disponibilidad", "error")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-6)', alignItems: 'start', width: '100%' }}>
      {/* Availability Grid */}
      <div className="card" style={{ width: '100%', maxWidth: 'none', margin: 0 }}>
        <div className="card__header">
          <div>
            <h2 className="card__title">Disponibilidad semanal</h2>
            <p className="card__subtitle">Hacé clic en un horario para habilitarlo o deshabilitarlo</p>
          </div>
          <button className="btn btn--primary btn--sm" onClick={handleSave} disabled={saving} id="btn-save-availability">
            {saving ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>

        <div className="availability-grid" style={{ gridTemplateColumns: 'repeat(5, 1fr)', width: '100%' }}>
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

      {/* To-Do List Card */}
      <div className="card" style={{ width: '100%', maxWidth: 'none', margin: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', boxShadow: 'var(--shadow-md)' }}>
        <div className="card__header" style={{ paddingBottom: 'var(--space-2)', borderBottom: '1px solid var(--color-border)' }}>
          <div>
            <h2 className="card__title" style={{ fontSize: 'var(--text-md)', fontWeight: 'bold' }}>Notas y Pendientes</h2>
            <p className="card__subtitle" style={{ fontSize: 'var(--text-xs)' }}>Recordatorios clínicos y administrativos</p>
          </div>
        </div>

        {/* Add Task Form */}
        <form onSubmit={handleAddTask} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', backgroundColor: 'var(--neutral-50)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label" style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Descripción de la nota</label>
            <input 
              type="text" 
              className="form-input" 
              placeholder="Ej. Llamar a prepaga Rossi..." 
              value={newTaskText} 
              onChange={(e) => setNewTaskText(e.target.value)}
              style={{ fontSize: 'var(--text-sm)', backgroundColor: '#ffffff' }}
            />
          </div>
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end' }}>
            <div className="form-group" style={{ margin: 0, flex: 1 }}>
              <label className="form-label" style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Categoría</label>
              <select 
                className="form-input" 
                value={newTaskCategory} 
                onChange={(e: any) => setNewTaskCategory(e.target.value)}
                style={{ 
                  fontSize: 'var(--text-xs)', 
                  padding: 'var(--space-2)', 
                  backgroundColor: '#ffffff',
                  border: '1px solid var(--color-border)',
                  cursor: 'pointer'
                }}
              >
                <option value="clinical">🩺 Nota Clínica</option>
                <option value="admin">📋 Nota Administrativa</option>
                <option value="urgent">⚠️ Prioridad Urgente</option>
              </select>
            </div>
            <button type="submit" className="btn btn--primary" style={{ padding: 'var(--space-2) var(--space-4)', fontSize: 'var(--text-xs)', height: '36px' }}>
              Añadir
            </button>
          </div>
        </form>

        {/* Tasks List */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
          {tasks.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-secondary)' }}>
              <span style={{ fontSize: '24px', display: 'block', marginBottom: 'var(--space-2)' }}>📝</span>
              <p style={{ fontSize: 'var(--text-xs)', fontStyle: 'italic', margin: 0 }}>No tenés notas pendientes.</p>
            </div>
          ) : (
            paginatedTasks.map((task) => {
              // Premium note card design based on category
              const isUrgent = task.category === 'urgent';
              const isAdmin = task.category === 'admin';

              const cardBg = isUrgent ? '#fff5f5' : isAdmin ? '#f0f7ff' : '#f0fdf4';
              const cardBorder = isUrgent ? '1px solid #fee2e2' : isAdmin ? '1px solid #e0f2fe' : '1px solid #dcfce7';
              const accentColor = isUrgent ? '#ef4444' : isAdmin ? '#3b82f6' : 'var(--color-primary)';
              const badgeLabel = isUrgent ? '⚠️ Urgente' : isAdmin ? '📋 Admin' : '🩺 Clínica';
              const badgeText = isUrgent ? '#991b1b' : isAdmin ? '#1d4ed8' : '#047857';
              const badgeBg = isUrgent ? '#fee2e2' : isAdmin ? '#dbeafe' : '#d1fae5';

              return (
                <div 
                  key={task.id} 
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    justifyContent: 'space-between',
                    padding: 'var(--space-3) var(--space-4)',
                    backgroundColor: cardBg,
                    border: cardBorder,
                    borderLeft: `4px solid ${accentColor}`,
                    borderRadius: 'var(--radius-lg)',
                    gap: 'var(--space-3)',
                    transition: 'all 0.2s ease',
                    opacity: task.completed ? 0.55 : 1,
                    textDecoration: task.completed ? 'line-through' : 'none',
                    boxShadow: '0 2px 4px rgba(0, 0, 0, 0.02)'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 'var(--space-3)', flex: 1 }}>
                    <input 
                      type="checkbox" 
                      checked={task.completed} 
                      onChange={() => handleToggleTask(task.id)}
                      style={{ 
                        cursor: 'pointer', 
                        width: '18px', 
                        height: '18px', 
                        marginTop: '2px',
                        accentColor: accentColor
                      }}
                    />
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ 
                        fontSize: 'var(--text-sm)', 
                        color: 'var(--color-text-primary)',
                        fontWeight: '600',
                        lineHeight: '1.4'
                      }}>
                        {task.text}
                      </span>
                      <span style={{
                        alignSelf: 'flex-start',
                        backgroundColor: badgeBg,
                        color: badgeText,
                        fontSize: '9px',
                        fontWeight: 'bold',
                        padding: '1px 6px',
                        borderRadius: '4px',
                        textTransform: 'uppercase',
                        letterSpacing: '0.02em'
                      }}>
                        {badgeLabel}
                      </span>
                    </div>
                  </div>
                  <button 
                    onClick={() => handleDeleteTask(task.id)}
                    style={{
                      border: 'none',
                      background: 'none',
                      cursor: 'pointer',
                      color: 'var(--color-danger)',
                      fontSize: '14px',
                      padding: '2px',
                      lineHeight: 1,
                      display: 'flex',
                      alignItems: 'center',
                      opacity: 0.7
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.opacity = '1'}
                    onMouseLeave={(e) => e.currentTarget.style.opacity = '0.7'}
                    title="Eliminar nota"
                  >
                    🗑️
                  </button>
                </div>
              );
            })
          )}
        </div>

        {/* Pagination Controls */}
        {totalPages > 1 && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            paddingTop: 'var(--space-3)',
            borderTop: '1px solid var(--color-border)',
            marginTop: 'var(--space-2)'
          }}>
            <button
              onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
              disabled={validCurrentPage === 1}
              className="btn btn--secondary btn--sm"
              style={{
                fontSize: '11px',
                padding: 'var(--space-1.5) var(--space-3)',
                opacity: validCurrentPage === 1 ? 0.5 : 1,
                cursor: validCurrentPage === 1 ? 'not-allowed' : 'pointer'
              }}
            >
              ◀ Anterior
            </button>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', fontWeight: '600' }}>
              Página {validCurrentPage} de {totalPages}
            </span>
            <button
              onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
              disabled={validCurrentPage === totalPages}
              className="btn btn--secondary btn--sm"
              style={{
                fontSize: '11px',
                padding: 'var(--space-1.5) var(--space-3)',
                opacity: validCurrentPage === totalPages ? 0.5 : 1,
                cursor: validCurrentPage === totalPages ? 'not-allowed' : 'pointer'
              }}
            >
              Siguiente ▶
            </button>
          </div>
        )}
      </div>
    </div>
  )
}



// ── Prescription View ─────────────────────────────────────────
const MOCK_PATIENTS = [
  { id: '1', name: 'Mateo Benítez', email: 'mateo.b@gmail.com' },
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
  const { showAlert } = useAlert();
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
      showAlert("Error al emitir y enviar receta", "error")
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

const PROVINCIAS_ARGENTINA = [
  "Buenos Aires",
  "CABA",
  "Catamarca",
  "Chaco",
  "Chubut",
  "Córdoba",
  "Corrientes",
  "Entre Ríos",
  "Formosa",
  "Jujuy",
  "La Pampa",
  "La Rioja",
  "Mendoza",
  "Misiones",
  "Neuquén",
  "Río Negro",
  "Salta",
  "San Juan",
  "San Luis",
  "Santa Cruz",
  "Santa Fe",
  "Santiago del Estero",
  "Tierra del Fuego",
  "Tucumán"
];

const ESPECIALIDADES_GRUPOS = [
  {
    "id": "clinicas",
    "nombreGrupo": "Especialidades Clínicas",
    "especialidades": [
      {"id": "alergia_inmuno", "nombre": "Alergia e Inmunología"},
      {"id": "cardiologia", "nombre": "Cardiología"},
      {"id": "dermatologia", "nombre": "Dermatología"},
      {"id": "endocrinologia", "nombre": "Endocrinología y Nutrición"},
      {"id": "gastroenterologia", "nombre": "Gastroenterología / Hepatología"},
      {"id": "geriatria", "nombre": "Geriatria"},
      {"id": "hematologia", "nombre": "Hematología"},
      {"id": "infectologia", "nombre": "Infectología"},
      {"id": "medicina_interna", "nombre": "Medicina Interna (Clínica Médica)"},
      {"id": "nefrologia", "nombre": "Nefrología"},
      {"id": "neumonologia", "nombre": "Neumonología"},
      {"id": "neurologia", "nombre": "Neurología"},
      {"id": "oncologia_medica", "nombre": "Oncología Médica"},
      {"id": "pediatria", "nombre": "Pediatría"},
      {"id": "psiquiatria", "nombre": "Psiquiatría"},
      {"id": "psiquiatria_infantil", "nombre": "Psiquiatría Infanto-Juvenil"},
      {"id": "reumatologia", "nombre": "Reumatología"}
    ]
  },
  {
    "id": "quirurgicas",
    "nombreGrupo": "Especialidades Quirúrgicas",
    "especialidades": [
      {"id": "cirugia_cardiovascular", "nombre": "Cirugía Cardiovascular"},
      {"id": "cirugia_general", "nombre": "Cirugía General y del Aparato Digestivo"},
      {"id": "cirugia_maxilofacial", "nombre": "Cirugía Oral y Maxilofacial"},
      {"id": "cirugia_traumatologia", "nombre": "Cirugía Ortopédica y Traumatología"},
      {"id": "cirugia_pediatrica", "nombre": "Cirugía Pediátrica"},
      {"id": "cirugia_plastica", "nombre": "Cirugía Plástica, Estética y Reparadora"},
      {"id": "cirugia_toracica", "nombre": "Cirugía Torácica"},
      {"id": "cirugia_vascular", "nombre": "Cirugía Vascular / Angiología"},
      {"id": "neurocirugia", "nombre": "Neurocirugía"}
    ]
  },
  {
    "id": "mixtas",
    "nombreGrupo": "Especialidades Médico-Quirúrgicas",
    "especialidades": [
      {"id": "ginecologia_obstetricia", "nombre": "Ginecología y Obstetricia (Tocoginecología)"},
      {"id": "oftalmologia", "nombre": "Oftalmología"},
      {"id": "otorrinolaringologia", "nombre": "Otorrinolaringología"},
      {"id": "urologia", "nombre": "Urología"}
    ]
  },
  {
    "id": "diagnostico_soporte",
    "nombreGrupo": "Diagnóstico, Soporte y Emergencias",
    "especialidades": [
      {"id": "anestesiologia", "nombre": "Anestesiología, Reanimación y Dolor"},
      {"id": "anatomia_patologica", "nombre": "Anatomía Patológica"},
      {"id": "diagnostico_imagenes", "nombre": "Diagnóstico por Imágenes / Radiología"},
      {"id": "medicina_deporte", "nombre": "Medicina del Deporte"},
      {"id": "medicina_emergencias", "nombre": "Medicina de Emergencias / Urgencias"},
      {"id": "medicina_intensiva", "nombre": "Medicina Intensiva / Terapia Intensiva"},
      {"id": "medicina_legal", "nombre": "Medicina Legal y Forense"},
      {"id": "medicina_nuclear", "nombre": "Medicina Nuclear"},
      {"id": "medicina_fisica_rehab", "nombre": "Medicina Física y Rehabilitación (Fisiatría)"},
      {"id": "toxicologia", "nombre": "Toxicología Médica"}
    ]
  },
  {
    "id": "salud_publica_comunitaria",
    "nombreGrupo": "Salud Pública y Atención Comunitaria",
    "especialidades": [
      {"id": "medicina_familiar", "nombre": "Medicina Familiar y General"},
      {"id": "medicina_trabajo", "nombre": "Medicina del Trabajo / Laboral"},
      {"id": "salud_publica", "nombre": "Salud Pública y Administración Sanitaria"}
    ]
  }
];

function SettingsView({ medicoInfo, onSave }: { medicoInfo: any; onSave: (updated: any) => Promise<void> }) {
  const { showAlert } = useAlert();
  const [name, setName] = useState(medicoInfo?.name || '')
  const [apellido, setApellido] = useState(medicoInfo?.apellido || '')
  const [sexo, setSexo] = useState(medicoInfo?.sexo || 'M')
  const [fechaNacimiento, setFechaNacimiento] = useState(medicoInfo?.fechaNacimiento || '')
  const [cuil, setCuil] = useState(medicoInfo?.cuil || '')
  const [tipoDocumento, setTipoDocumento] = useState(medicoInfo?.tipoDocumento || 'DNI')
  const [numeroDocumento, setNumeroDocumento] = useState(medicoInfo?.numeroDocumento || '')
  const [domicilioAtencion, setDomicilioAtencion] = useState(medicoInfo?.domicilioAtencion || '')
  const [domicilioProvincia, setDomicilioProvincia] = useState('')
  const [domicilioLat, setDomicilioLat] = useState<number | null>(medicoInfo?.domicilioLat ?? null)
  const [domicilioLng, setDomicilioLng] = useState<number | null>(medicoInfo?.domicilioLng ?? null)
  const [codigoReFeps, setCodigoReFeps] = useState(medicoInfo?.codigoReFeps || '')

  // MatriculaInfo
  const [matTipo, setMatTipo] = useState(medicoInfo?.matriculaInfo?.tipo || 'MN')
  const [matProvincia, setMatProvincia] = useState(medicoInfo?.matriculaInfo?.provincia || '')

  const [degree, setDegree] = useState(medicoInfo?.degree || '')

  const initialSpecialty = medicoInfo?.specialty || medicoInfo?.matriculaInfo?.especialidad?.textoLibre || '';

  const initialGroup = ESPECIALIDADES_GRUPOS.find(g => 
    g.especialidades.some(esp => esp.nombre === initialSpecialty)
  )?.id || '';

  const [selectedGroup, setSelectedGroup] = useState(initialGroup);
  const [specialty, setSpecialty] = useState(initialSpecialty)

  const handleGroupChange = (groupId: string) => {
    setSelectedGroup(groupId);
    setSpecialty('');
  }

  const [matricula, setMatricula] = useState(medicoInfo?.matricula || (medicoInfo?.matriculaInfo?.numero ? String(medicoInfo.matriculaInfo.numero) : ''))
  const [tariffs, setTariffs] = useState<any[]>(medicoInfo?.tariffs || [])
  const [fotoUrl, setFotoUrl] = useState(medicoInfo?.fotoUrl || '')
  const [ofreceOnline, setOfreceOnline] = useState(medicoInfo?.ofreceOnline !== undefined ? medicoInfo.ofreceOnline : true)
  const [ofrecePresencial, setOfrecePresencial] = useState(medicoInfo?.ofrecePresencial !== undefined ? medicoInfo.ofrecePresencial : false)

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
        apellido,
        sexo,
        fechaNacimiento,
        cuil: cuil ? Number(cuil) : null,
        tipoDocumento,
        numeroDocumento: numeroDocumento ? Number(numeroDocumento) : null,
        domicilioAtencion: ofrecePresencial ? domicilioAtencion : '',
        domicilioLat: ofrecePresencial ? domicilioLat : null,
        domicilioLng: ofrecePresencial ? domicilioLng : null,
        codigoReFeps: codigoReFeps ? Number(codigoReFeps) : null,
        matriculaInfo: {
          tipo: matTipo,
          provincia: matProvincia,
          numero: matricula ? Number(matricula) : null,
          especialidad: {
            textoLibre: specialty
          },
          asociada: {
            tipo: medicoInfo?.matriculaInfo?.asociada?.tipo || 'MN',
            provincia: medicoInfo?.matriculaInfo?.asociada?.provincia || '',
            numero: medicoInfo?.matriculaInfo?.asociada?.numero || null
          }
        },
        degree,
        specialty,
        matricula,
        cuit: cuil ? String(cuil) : '',
        tariffs,
        fotoUrl,
        tags: specialty ? [specialty] : [],
        ofreceOnline,
        ofrecePresencial
      })
      showAlert("Configuración guardada con éxito ✓", "success")
    } catch (err) {
      console.error(err)
      showAlert("Error al guardar la configuración", "error")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* Verification status banner */}
      {medicoInfo?.verificado ? (
        <div style={{
          backgroundColor: '#ecfdf5',
          border: '1px solid #a7f3d0',
          borderLeft: '5px solid var(--color-primary)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
          color: 'var(--color-primary)',
          fontSize: 'var(--text-sm)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)'
        }}>
          <strong>✓ Cuenta Verificada:</strong> Tu perfil profesional cumple con todos los requisitos y es visible públicamente para reserva de turnos.
        </div>
      ) : (
        <div style={{
          backgroundColor: '#fffbeb',
          border: '1px solid #fef3c7',
          borderLeft: '5px solid #d97706',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
          color: '#b45309',
          fontSize: 'var(--text-sm)'
        }}>
          <strong>⚠️ Cuenta No Verificada:</strong> Para aparecer en la lista de profesionales disponibles de la aplicación y que los pacientes puedan agendar turnos, debés completar todos tus datos demográficos, ReFeps, matrícula y subir una foto de perfil.
        </div>
      )}

      {/* Profile */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Perfil profesional</h2>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
          {/* Profile Photo Uploader */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', gridColumn: 'span 2', paddingBottom: 'var(--space-4)', borderBottom: '1px solid var(--color-border)' }}>
            <div style={{
              width: '80px',
              height: '80px',
              borderRadius: '50%',
              backgroundColor: '#e5e7eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              border: '2px solid var(--color-primary)'
            }}>
              {fotoUrl ? (
                <img src={fotoUrl} alt="Foto de perfil" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <span style={{ fontSize: '24px', color: '#9ca3af' }}>👤</span>
              )}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <label style={{
                cursor: 'pointer',
                backgroundColor: 'var(--color-primary)',
                color: 'white',
                padding: 'var(--space-2) var(--space-4)',
                borderRadius: 'var(--radius-md)',
                fontSize: 'var(--text-xs)',
                fontWeight: 'bold',
                textAlign: 'center'
              }}>
                Subir foto
                <input 
                  type="file" 
                  accept="image/*" 
                  style={{ display: 'none' }} 
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) {
                      const reader = new FileReader();
                      reader.onloadend = () => {
                        setFotoUrl(reader.result as string);
                      };
                      reader.readAsDataURL(file);
                    }
                  }}
                />
              </label>
              {fotoUrl && (
                <button 
                  onClick={() => setFotoUrl('')}
                  className="btn btn--danger btn--sm"
                  style={{ fontSize: 'var(--text-xs)' }}
                >
                  Eliminar foto
                </button>
              )}
            </div>
          </div>

          <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', marginTop: 'var(--space-2)' }}>
            Datos Demográficos Básicos
          </div>

          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-name">Nombre</label>
            <input id="input-name" className="form-input" type="text" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-apellido">Apellido</label>
            <input id="input-apellido" className="form-input" type="text" value={apellido} onChange={(e) => setApellido(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-sexo">Sexo</label>
            <select id="input-sexo" className="form-input" value={sexo} onChange={(e) => setSexo(e.target.value)}>
              <option value="M">Masculino (M)</option>
              <option value="F">Femenino (F)</option>
              <option value="Otro">Otro</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-nacimiento">Fecha de Nacimiento</label>
            <input id="input-nacimiento" className="form-input" type="date" value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-tipo-doc">Tipo Documento</label>
            <select id="input-tipo-doc" className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
              <option value="DNI">DNI</option>
              <option value="LC">Libreta Cívica (LC)</option>
              <option value="LE">Libreta de Enrolamiento (LE)</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-num-doc">Número de Documento</label>
            <input id="input-num-doc" className="form-input" type="number" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-cuil">CUIL/CUIT</label>
            <input id="input-cuil" className="form-input" type="number" placeholder="Ej. 27123456780" value={cuil} onChange={(e) => setCuil(e.target.value)} />
          </div>
          {/* Modalities selector */}
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label form-label--required">Modalidades de Consulta</label>
            <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
              <label className={`check-chip check-chip--auto ${ofreceOnline ? 'active' : ''}`}>
                <input
                  type="checkbox"
                  checked={ofreceOnline}
                  onChange={(e) => setOfreceOnline(e.target.checked)}
                />
                <span className="check-chip__icon"><Icon.Video /></span>
                Consulta Online (Videollamada Meet)
              </label>
              <label className={`check-chip check-chip--auto ${ofrecePresencial ? 'active' : ''}`}>
                <input
                  type="checkbox"
                  checked={ofrecePresencial}
                  onChange={(e) => setOfrecePresencial(e.target.checked)}
                />
                <span className="check-chip__icon"><Icon.Building /></span>
                Consulta Presencial (Consultorio)
              </label>
            </div>
          </div>

          {ofrecePresencial && (
            <AddressMapPicker
              provincia={domicilioProvincia}
              onProvinciaChange={setDomicilioProvincia}
              direccion={domicilioAtencion}
              onDireccionChange={setDomicilioAtencion}
              lat={domicilioLat}
              lng={domicilioLng}
              onLocationChange={(lat, lng) => { setDomicilioLat(lat); setDomicilioLng(lng) }}
              provinciasList={PROVINCIAS_ARGENTINA}
            />
          )}
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-degree">Título profesional</label>
            <input id="input-degree" className="form-input" type="text" value={degree} onChange={(e) => setDegree(e.target.value)} />
          </div>

          <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', marginTop: 'var(--space-2)' }}>
            Registro Nacional (ReFeps)
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-refeps">Código ReFeps</label>
            <input id="input-refeps" className="form-input" type="number" placeholder="Ej. 123456789012" value={codigoReFeps} onChange={(e) => setCodigoReFeps(e.target.value)} />
          </div>

          <div style={{ gridColumn: 'span 2', fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', marginTop: 'var(--space-2)' }}>
            Matrícula
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-mat-tipo">Tipo de Matrícula</label>
            <input id="input-mat-tipo" className="form-input" type="text" placeholder="Ej. MN, MP" value={matTipo} onChange={(e) => setMatTipo(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-mat-provincia">Provincia</label>
            <select id="input-mat-provincia" className="form-input" value={matProvincia} onChange={(e) => setMatProvincia(e.target.value)}>
              <option value="">Seleccioná una provincia</option>
              {PROVINCIAS_ARGENTINA.map(p => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-specialty-group">Grupo de Especialidad</label>
            <select 
              id="input-specialty-group" 
              className="form-input" 
              value={selectedGroup} 
              onChange={(e) => handleGroupChange(e.target.value)}
            >
              <option value="">Seleccioná un grupo</option>
              {ESPECIALIDADES_GRUPOS.map(g => (
                <option key={g.id} value={g.id}>{g.nombreGrupo}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-specialty">Especialidad</label>
            <select 
              id="input-specialty" 
              className="form-input" 
              value={specialty} 
              onChange={(e) => setSpecialty(e.target.value)}
              disabled={!selectedGroup}
            >
              <option value="">{selectedGroup ? 'Seleccioná una especialidad' : 'Primero seleccioná un grupo'}</option>
              {selectedGroup && ESPECIALIDADES_GRUPOS.find(g => g.id === selectedGroup)?.especialidades.map(esp => (
                <option key={esp.id} value={esp.nombre}>{esp.nombre}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-matricula">Número de Matrícula</label>
            <input id="input-matricula" className="form-input" type="text" value={matricula} onChange={(e) => setMatricula(e.target.value)} />
            <span className="form-helper">Verificada ✓</span>
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

function DashboardHome({
  mpConnected,
  onConnect,
  onDisconnect,
  appointments,
  allAppointments,
  availability,
  stats,
  onCancelAppointment,
  onUpdateAttendance,
  onRescheduleAppointment
}: {
  mpConnected: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
  appointments: Appointment[];
  allAppointments: any[];
  availability: any[];
  stats: any;
  onCancelAppointment: (id: number) => void;
  onUpdateAttendance: (id: number, status: string) => void;
  onRescheduleAppointment: (id: number, date: string, hour: string) => void;
}) {
  const dateStr = new Date().toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' });
  const capitalizedDate = dateStr.charAt(0).toUpperCase() + dateStr.slice(1);
  const [calendarView, setCalendarView] = useState<'weekly' | 'today'>('weekly');
  const [showInactiveSlots, setShowInactiveSlots] = useState(false);
  const [selectedAppt, setSelectedAppt] = useState<any | null>(null);

  const [currentTime, setCurrentTime] = useState(new Date());

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentTime(new Date());
    }, 10000); // update every 10s
    return () => clearInterval(interval);
  }, []);

  // Find the next active/confirmed appointment closest to now
  const nextAppt = useMemo(() => {
    if (!allAppointments || allAppointments.length === 0) return null;
    const now = currentTime;
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const todayStr = `${year}-${month}-${day}`;
    const currentHour = now.getHours();
    const currentMin = now.getMinutes();

    const upcoming = allAppointments
      .filter(a => a.status !== 'completed' && a.status !== 'cancelled' && a.attendanceStatus !== 'AUSENTE' && a.attendanceStatus !== 'COMPLETADA')
      .sort((a, b) => {
        const dateDiff = a.fecha.localeCompare(b.fecha);
        if (dateDiff !== 0) return dateDiff;
        return a.hour.localeCompare(b.hour);
      });

    return upcoming.find(a => {
      if (a.fecha === todayStr) {
        const parts = a.hour.split(':');
        const apptHour = parseInt(parts[0]);
        const apptMin = parseInt(parts[1] || '0');
        if (apptHour > currentHour) return true;
        if (apptHour === currentHour) return apptMin >= currentMin;
        return false;
      }
      return a.fecha > todayStr;
    }) || upcoming[0];
  }, [allAppointments, currentTime]);

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
  };

  const getCountdownString = (appt: any) => {
    if (!appt || !appt.fecha || !appt.hour) return '';
    const parts = appt.fecha.split('-');
    const timeParts = String(appt.hour).split(':');
    const year = parseInt(parts[0] || '0', 10);
    const month = parseInt(parts[1] || '1', 10) - 1;
    const day = parseInt(parts[2] || '1', 10);
    const hour = parseInt(timeParts[0] || '0', 10);
    const min = parseInt(timeParts[1] || '0', 10);
    const apptDate = new Date(year, month, day, hour, min, 0);

    const diffMs = apptDate.getTime() - currentTime.getTime();
    if (diffMs <= 0) {
      if (diffMs > -45 * 60 * 1000) {
        return "¡En curso!";
      }
      return "Finalizado";
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const totalMinutes = Math.floor(totalSeconds / 60);
    const totalHours = Math.floor(totalMinutes / 60);
    const days = Math.floor(totalHours / 24);

    const hours = totalHours % 24;
    const minutes = totalMinutes % 60;

    return `Falta: ${days}d ${hours}h ${minutes}m`;
  };

  // Rescheduling states
  const [rescheduleDate, setRescheduleDate] = useState('');
  const [rescheduleHour, setRescheduleHour] = useState('');
  const [isRescheduling, setIsRescheduling] = useState(false);

  const weekdays = [
    { name: 'Lunes', abbr: 'Lun', num: 1 },
    { name: 'Martes', abbr: 'Mar', num: 2 },
    { name: 'Miércoles', abbr: 'Mié', num: 3 },
    { name: 'Jueves', abbr: 'Jue', num: 4 },
    { name: 'Viernes', abbr: 'Vie', num: 5 },
  ]

  // Determine dynamic slots based on doctor's actual availability range
  const getDynamicSlots = () => {
    if (!availability || availability.length === 0) {
      return ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00'];
    }
    let minHour = 24;
    let maxHour = 0;
    availability.forEach((disp) => {
      const start = parseInt(disp.horaInicio.split(':')[0]);
      const end = parseInt(disp.horaFin.split(':')[0]);
      if (start < minHour) minHour = start;
      if (end > maxHour) maxHour = end;
    });
    // Fallback if numbers are strange
    if (minHour >= maxHour) {
      return ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00'];
    }
    const slots = [];
    for (let h = minHour; h < maxHour; h++) {
      slots.push(`${String(h).padStart(2, '0')}:00`);
    }
    return slots;
  };

  const baseSlots = getDynamicSlots();

  // Parse date YYYY-MM-DD to get local day of week (1 = Monday, 5 = Friday)
  const getDayOfWeek = (dateStr: string) => {
    if (!dateStr) return -1
    const parts = dateStr.split('-')
    if (parts.length !== 3) return -1
    const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
    const day = d.getDay()
    return day === 0 ? 7 : day // Map Sunday to 7, Mon-Sat to 1-6
  }

  // Check if a slot is active in availability for a specific day
  const isSlotAvailable = (dayNum: number, slot: string) => {
    const hour = parseInt(slot.split(':')[0])
    return availability.some((disp) => {
      if (disp.diaSemana !== dayNum) return false
      const start = parseInt(disp.horaInicio.split(':')[0])
      const end = parseInt(disp.horaFin.split(':')[0])
      return hour >= start && hour < end
    })
  }

  return (
    <>
      <MPConnectBanner connected={mpConnected} onConnect={onConnect} onDisconnect={onDisconnect} />

      {nextAppt && (
        <div className="card" style={{ width: '100%', maxWidth: 'none', marginBottom: 'var(--space-4)', padding: 'var(--space-5)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 'var(--space-3)', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--space-4)' }}>
            <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 16, height: 16, color: 'var(--color-primary)' }}><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              Próximo Turno Programado
            </h2>
            <div style={{
              backgroundColor: 'var(--green-50)',
              color: 'var(--color-primary)',
              padding: 'var(--space-1) var(--space-3)',
              borderRadius: 'var(--radius-full)',
              fontSize: 'var(--text-xs)',
              fontWeight: 'bold',
              border: '1px solid var(--green-200)',
              display: 'flex',
              alignItems: 'center',
              gap: '6px'
            }}>
              <span style={{ display: 'inline-block', width: '6px', height: '6px', backgroundColor: 'var(--color-primary)', borderRadius: '50%' }}></span>
              {getCountdownString(nextAppt)}
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
            <div style={{ textAlign: 'left' }}>
              <p style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)' }}>
                {nextAppt.patientName}
              </p>
              <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-2)', flexWrap: 'wrap' }}>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  📅 <strong>Fecha:</strong> {formatDate(nextAppt.fecha)}
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  ⏰ <strong>Horario:</strong> {nextAppt.hour} hs
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  🩺 <strong>Modalidad:</strong> {nextAppt.type}
                </span>
              </div>
              <div style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-xs)', display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <span style={{ color: 'var(--color-text-secondary)' }}>Asistencia:</span>
                <span className="badge" style={{ 
                  backgroundColor: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--green-100)' : nextAppt.attendanceStatus === 'AUSENTE' ? '#fdf2f2' : 'var(--neutral-100)',
                  color: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--green-700)' : nextAppt.attendanceStatus === 'AUSENTE' ? 'var(--color-danger)' : 'var(--color-text-secondary)',
                  fontWeight: 'bold',
                  fontSize: '11px',
                  padding: '2px 8px',
                  borderRadius: 'var(--radius-sm)'
                }}>
                  {nextAppt.attendanceStatus === 'LLEGO' ? '🚶‍♂️ Presente' : nextAppt.attendanceStatus === 'AUSENTE' ? '❌ Ausente' : '⏳ Esperando paciente'}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
              <button 
                onClick={() => onUpdateAttendance(nextAppt.id, 'LLEGO')}
                className="btn btn--secondary btn--sm"
                style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '4px',
                  backgroundColor: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--green-50)' : 'transparent',
                  borderColor: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--color-primary)' : 'var(--color-border)',
                  color: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--color-primary)' : 'var(--color-text-primary)'
                }}
              >
                🚶‍♂️ Llegó
              </button>
              <button 
                onClick={() => onUpdateAttendance(nextAppt.id, 'AUSENTE')}
                className="btn btn--ghost btn--sm"
                style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '4px',
                  backgroundColor: nextAppt.attendanceStatus === 'AUSENTE' ? '#fdf2f2' : 'transparent',
                  color: nextAppt.attendanceStatus === 'AUSENTE' ? 'var(--color-danger)' : 'var(--color-text-secondary)',
                  border: '1px solid ' + (nextAppt.attendanceStatus === 'AUSENTE' ? 'var(--color-danger)' : 'var(--color-border)')
                }}
              >
                ❌ Ausente
              </button>
            </div>
          </div>
        </div>
      )}

      <StatsOverview stats={stats} />

      <div className="card" style={{ width: '100%', maxWidth: 'none' }}>
        <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2 className="card__title">
              {calendarView === 'weekly' ? 'Calendario Semanal' : 'Sesiones de Hoy'}
            </h2>
            <p className="card__subtitle">
              {calendarView === 'weekly' 
                ? 'Cronograma de turnos reservados por día y horario' 
                : `${capitalizedDate} · ${appointments.length} sesiones programadas`
              }
            </p>
          </div>

          <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center' }}>
            {calendarView === 'weekly' && (
              <button 
                onClick={() => setShowInactiveSlots(!showInactiveSlots)} 
                className="btn btn--secondary btn--sm"
                style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: 'var(--space-2)',
                  borderColor: showInactiveSlots ? 'var(--color-primary)' : 'var(--color-border)',
                  backgroundColor: showInactiveSlots ? 'var(--green-50)' : 'transparent',
                  color: showInactiveSlots ? 'var(--color-primary)' : 'var(--color-text-primary)'
                }}
              >
                {showInactiveSlots ? <Icon.EyeOff /> : <Icon.Eye />}
                {showInactiveSlots ? 'Ocultar no laborables' : 'Ver inactivos'}
              </button>
            )}
            <div style={{ display: 'flex', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', overflow: 'hidden' }}>
              <button 
                onClick={() => setCalendarView('weekly')} 
                className={`btn btn--sm`} 
                style={{ 
                  borderRadius: 0, 
                  backgroundColor: calendarView === 'weekly' ? 'var(--color-primary)' : 'transparent',
                  color: calendarView === 'weekly' ? 'white' : 'var(--color-text-primary)'
                }}
              >
                Semanal
              </button>
              <button 
                onClick={() => setCalendarView('today')} 
                className={`btn btn--sm`}
                style={{ 
                  borderRadius: 0, 
                  backgroundColor: calendarView === 'today' ? 'var(--color-primary)' : 'transparent',
                  color: calendarView === 'today' ? 'white' : 'var(--color-text-primary)'
                }}
              >
                Hoy
              </button>
            </div>
            <button className="btn btn--secondary btn--sm" id="btn-add-slot">
              <Icon.Plus />
              Agregar horario
            </button>
          </div>
        </div>

        {calendarView === 'today' ? (
          appointments.length === 0 ? (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', padding: 'var(--space-6)', textAlign: 'center' }}>
              No tenés sesiones programadas para el día de hoy.
            </p>
          ) : (
            <ul className="appointment-list" role="list" aria-label="Sesiones de hoy">
              {appointments.map((appt) => (
                <AppointmentCard key={appt.id} appt={appt} />
              ))}
            </ul>
          )
        ) : (
          /* Weekly Calendar Matrix Grid */
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: '80px repeat(5, 1fr)', 
            gap: 'var(--space-2)', 
            width: '100%',
            marginTop: 'var(--space-4)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            overflow: 'hidden',
            backgroundColor: 'var(--color-surface)'
          }}>
            {/* Headers */}
            <div style={{ backgroundColor: 'var(--neutral-50)', padding: 'var(--space-3) var(--space-2)', borderBottom: '2px solid var(--color-border)', borderRight: '1px solid var(--color-border)' }}></div>
            {weekdays.map((day) => (
              <div key={day.num} style={{
                textAlign: 'center',
                padding: 'var(--space-3) var(--space-2)',
                fontSize: 'var(--text-xs)',
                fontWeight: 'bold',
                color: 'var(--color-primary)',
                backgroundColor: 'var(--green-50)',
                borderBottom: '2px solid var(--color-border)',
                borderRight: day.num < 5 ? '1px solid var(--color-border)' : 'none',
                textTransform: 'uppercase',
                letterSpacing: '0.05em'
              }}>
                {day.name}
              </div>
            ))}

            {/* Rows by hour */}
            {baseSlots.filter((slot) => {
              if (showInactiveSlots) return true;
              const slotHour = parseInt(slot.split(':')[0]);
              const hasActiveAvailability = weekdays.some((day) => isSlotAvailable(day.num, slot));
              const hasAppointment = allAppointments.some((a) => {
                const apptDay = getDayOfWeek(a.fecha);
                const apptHour = parseInt(a.hour);
                return weekdays.some(d => d.num === apptDay) && apptHour === slotHour;
              });
              return hasActiveAvailability || hasAppointment;
            }).map((slot) => {
              const slotHour = parseInt(slot.split(':')[0]);
              return (
                <React.Fragment key={slot}>
                  {/* Hour Label Column */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: 'var(--neutral-50)',
                    borderRight: '1px solid var(--color-border)',
                    borderBottom: '1px solid #f0f2f5',
                    fontSize: '11px',
                    fontWeight: 'bold',
                    color: 'var(--color-text-secondary)'
                  }}>
                    {slot} hs
                  </div>

                  {/* Day Columns for this hour */}
                  {weekdays.map((day) => {
                    const appt = allAppointments.find(a => {
                      const apptDay = getDayOfWeek(a.fecha);
                      const apptHour = parseInt(a.hour);
                      return apptDay === day.num && apptHour === slotHour;
                    });
                    const isActive = isSlotAvailable(day.num, slot);

                    // If not active and not appt, and showInactiveSlots is false, render empty/neutral cell
                    const isCellVisible = appt || isActive || showInactiveSlots;

                    return (
                      <div key={day.num} style={{
                        padding: 'var(--space-2)',
                        borderRight: day.num < 5 ? '1px solid #f0f2f5' : 'none',
                        borderBottom: '1px solid #f0f2f5',
                        minHeight: '80px',
                        backgroundColor: !isCellVisible 
                          ? '#fafafa' 
                          : appt 
                            ? '#ffffff'
                            : '#f6fbf8', // Light green for active empty slots
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center'
                      }}>
                        {appt ? (
                          <div 
                            onClick={() => setSelectedAppt(appt)}
                            style={{
                              padding: 'var(--space-3)',
                              borderRadius: 'var(--radius-lg)',
                              backgroundColor: '#ffffff',
                              border: '1px solid var(--color-border)',
                              borderLeft: appt.status === 'confirmed' 
                                ? '4px solid var(--color-primary)' 
                                : appt.status === 'completed' 
                                  ? '4px solid var(--color-text-secondary)' 
                                  : '4px solid var(--color-warning)',
                              boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.03)',
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 'var(--space-2)',
                              cursor: 'pointer',
                              transition: 'all 0.2s cubic-bezier(0.16, 1, 0.3, 1)',
                              position: 'relative'
                            }}
                            onMouseEnter={(e) => {
                              e.currentTarget.style.transform = 'translateY(-2px) scale(1.01)';
                              e.currentTarget.style.borderColor = 'var(--color-primary)';
                              e.currentTarget.style.boxShadow = '0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.03)';
                            }}
                            onMouseLeave={(e) => {
                              e.currentTarget.style.transform = 'translateY(0) scale(1)';
                              e.currentTarget.style.borderColor = 'var(--color-border)';
                              e.currentTarget.style.boxShadow = '0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.03)';
                            }}
                          >
                            {/* Time & Modality Header */}
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                              <span style={{
                                fontSize: '10px',
                                fontWeight: 'bold',
                                color: appt.status === 'confirmed' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                                textTransform: 'uppercase',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '3px'
                              }}>
                                {appt.type.includes('Meet') || appt.type.includes('OSDE') ? (
                                  <><Icon.Video size={11} /> Online</>
                                ) : (
                                  <><Icon.Building size={11} /> Presencial</>
                                )}
                              </span>
                              <span style={{
                                width: '6px',
                                height: '6px',
                                borderRadius: '50%',
                                backgroundColor: appt.status === 'confirmed' ? '#10b981' : appt.status === 'completed' ? '#64748b' : '#f59e0b',
                                display: 'inline-block'
                              }} />
                            </div>

                            {/* Patient Name */}
                            <div style={{ fontSize: '12px', fontWeight: '700', color: 'var(--color-text-primary)' }}>
                              {appt.patientName}
                            </div>

                            {/* Attendance text if not Esperando */}
                            {appt.attendanceStatus && appt.attendanceStatus !== 'ESPERANDO' && (
                              <div style={{
                                fontSize: '10px',
                                fontWeight: '600',
                                color: appt.attendanceStatus === 'LLEGO' 
                                  ? 'var(--color-primary)' 
                                  : appt.attendanceStatus === 'COMPLETADA' 
                                    ? '#10b981' 
                                    : 'var(--color-danger)',
                                marginTop: '-2px'
                              }}>
                                {appt.attendanceStatus === 'LLEGO' ? '🚶‍♂️ Presente' : appt.attendanceStatus === 'COMPLETADA' ? '✓ Completada' : '❌ Ausente'}
                              </div>
                            )}

                            {/* Alert badges or details */}
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                              <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                                {appt.firstConsultation && (
                                  <span style={{
                                    backgroundColor: '#fffbeb',
                                    color: '#b45309',
                                    border: '1px solid #fef3c7',
                                    fontSize: '9px',
                                    padding: '1px 5px',
                                    borderRadius: '4px',
                                    fontWeight: '600'
                                  }}>
                                    ⚠️ 1° vez
                                  </span>
                                )}
                                <span style={{ fontSize: '9px', color: 'var(--color-text-secondary)', fontWeight: '500' }}>
                                  {appt.type.includes('OSDE') ? 'OSDE' : 'Particular'}
                                </span>
                              </div>
                              {appt.status !== 'completed' && (
                                <button 
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    onCancelAppointment(appt.id);
                                  }}
                                  style={{
                                    border: 'none',
                                    background: 'none',
                                    padding: '2px',
                                    cursor: 'pointer',
                                    color: 'var(--color-danger)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    fontSize: '9px',
                                    fontWeight: 'bold'
                                  }}
                                  title="Cancelar Turno"
                                >
                                  ✕
                                </button>
                              )}
                            </div>
                          </div>
                        ) : isActive ? (
                          <div style={{
                            textAlign: 'center',
                            fontSize: '9px',
                            color: 'var(--color-primary)',
                            fontWeight: 'bold',
                            border: '1px dashed var(--color-primary-disabled)',
                            borderRadius: 'var(--radius-sm)',
                            padding: 'var(--space-2) 0',
                            backgroundColor: '#ffffff'
                          }}>
                            Libre
                          </div>
                        ) : (
                          <div style={{
                            textAlign: 'center',
                            fontSize: '9px',
                            color: '#94a3b8',
                            fontStyle: 'italic'
                          }}>
                            —
                          </div>
                        )}
                      </div>
                    );
                  })}
                </React.Fragment>
              );
            })}
          </div>
        )}
      </div>

      {/* Appointment Detail Modal */}
      {selectedAppt && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(15, 23, 42, 0.6)',
          backdropFilter: 'blur(6px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: 'var(--space-4)'
        }} onClick={() => setSelectedAppt(null)}>
          <div style={{
            backgroundColor: 'var(--color-surface)',
            borderRadius: 'var(--radius-xl)',
            width: '100%',
            maxWidth: '520px',
            boxShadow: 'var(--shadow-2xl)',
            overflow: 'hidden',
            border: '1px solid var(--color-border)',
            display: 'flex',
            flexDirection: 'column',
            animation: 'fadeInUp 0.25s cubic-bezier(0.16, 1, 0.3, 1)'
          }} onClick={(e) => e.stopPropagation()}>
            {/* Header */}
            <div style={{
              padding: 'var(--space-5)',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              background: 'linear-gradient(to right, var(--green-50), var(--color-surface))'
            }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-primary)' }}>Detalle del Turno</h3>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                  ID Turno: #{selectedAppt.id} · Fecha: {selectedAppt.fecha}
                </p>
              </div>
              <button 
                onClick={() => setSelectedAppt(null)}
                style={{
                  border: 'none',
                  background: 'var(--neutral-100)',
                  width: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  fontWeight: 'bold',
                  color: 'var(--color-text-primary)'
                }}
              >
                ✕
              </button>
            </div>

            {/* Content */}
            <div style={{ padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxHeight: '70vh', overflowY: 'auto' }}>
              
              {/* Warning Banner: First Consultation */}
              {selectedAppt.firstConsultation && (
                <div style={{
                  backgroundColor: '#fffbeb',
                  border: '1px solid #fef3c7',
                  borderLeft: '4px solid #d97706',
                  borderRadius: 'var(--radius-lg)',
                  padding: 'var(--space-4)',
                  color: '#b45309',
                  fontSize: 'var(--text-sm)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 'var(--space-1)'
                }}>
                  <strong style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    ⚠️ Primera Consulta Médica
                  </strong>
                  <span>Es la primera vez que este paciente agenda una cita. Debés verificar y completar su Ficha Clínica antes del inicio de la sesión.</span>
                </div>
              )}

              {/* Grid with patient information */}
              <div>
                <h4 style={{ margin: '0 0 var(--space-2) 0', fontSize: 'var(--text-xs)', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-secondary)' }}>Datos del Paciente</h4>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 'var(--space-3)',
                  backgroundColor: 'var(--neutral-50)',
                  padding: 'var(--space-4)',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)'
                }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Nombre Completo</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.apellido 
                        ? `${selectedAppt.patientInfo.nombre} ${selectedAppt.patientInfo.apellido}`
                        : selectedAppt.patientName}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Sexo</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.sexo === 'M' ? 'Masculino (M)' : selectedAppt.patientInfo?.sexo === 'F' ? 'Femenino (F)' : selectedAppt.patientInfo?.sexo || 'No especificado'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>DNI / Documento</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.tipoDocumento && selectedAppt.patientInfo?.numeroDocumento
                        ? `${selectedAppt.patientInfo.tipoDocumento} ${selectedAppt.patientInfo.numeroDocumento}`
                        : selectedAppt.patientInfo?.dni || 'No especificado'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>CUIL</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.cuil || 'No especificado'}
                    </span>
                  </div>
                  <div style={{ gridColumn: 'span 2' }}>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Contacto (Email & Teléfono)</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '500', display: 'block' }}>
                      ✉ {selectedAppt.patientInfo?.mail || selectedAppt.patientInfo?.email || '-'}
                    </span>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '500', display: 'block', marginTop: '2px' }}>
                      📞 {selectedAppt.patientInfo?.telefono || '-'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Cobertura médica (Prepaga / Obra Social) */}
              <div>
                <h4 style={{ margin: '0 0 var(--space-2) 0', fontSize: 'var(--text-xs)', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-secondary)' }}>Detalle de Cobertura (OSDE)</h4>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 'var(--space-3)',
                  backgroundColor: 'var(--neutral-50)',
                  padding: 'var(--space-4)',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)'
                }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Obra Social</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.obraSocial || 'Consulta Particular'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Plan</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.credencial?.plan || '-'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Nro. Afiliado (PAN)</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientInfo?.credencial?.pan || selectedAppt.patientInfo?.numAfiliado || '-'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Token Digital</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600', color: 'var(--color-primary)' }}>
                      {selectedAppt.patientInfo?.credencial?.token || '-'}
                    </span>
                  </div>
                  <div style={{ gridColumn: 'span 2' }}>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Código Entidad</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                      {selectedAppt.patientInfo?.credencial?.codEntidad || '-'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Consultation detail card */}
              <div style={{
                backgroundColor: 'var(--green-50)',
                padding: 'var(--space-4)',
                borderRadius: 'var(--radius-lg)',
                border: '1px solid var(--color-primary-disabled)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }}>
                <div>
                  <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-primary)' }}>Modalidad / Horario</label>
                  <span style={{ fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-primary)' }}>
                    {selectedAppt.type}
                  </span>
                  <span style={{ display: 'block', fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    🕒 {selectedAppt.hour} hs (Bloque de 45 min)
                  </span>
                </div>
                <div>
                  <span className={`badge ${selectedAppt.status === 'confirmed' ? 'badge--success' : selectedAppt.status === 'completed' ? 'badge--neutral' : 'badge--warning'}`}>
                    {selectedAppt.status === 'confirmed' ? 'Confirmado' : selectedAppt.status === 'completed' ? 'Completado' : 'Pendiente'}
                  </span>
                </div>
              </div>

              {/* Attendance Section */}
              <div style={{
                padding: 'var(--space-4) 0',
                borderTop: '1px solid var(--color-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-2)'
              }}>
                <label style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>
                  Asistencia del Paciente
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 'var(--space-2)' }}>
                  {[
                    { val: 'ESPERANDO', label: 'Esperando', emoji: '⏳', color: 'var(--neutral-600)' },
                    { val: 'LLEGO', label: 'Llegó', emoji: '🚶‍♂️', color: 'var(--color-primary)' },
                    { val: 'AUSENTE', label: 'Ausente', emoji: '❌', color: 'var(--color-danger)' },
                    { val: 'COMPLETADA', label: 'Terminado', emoji: '✓', color: '#10b981' }
                  ].map((opt) => {
                    const isSelected = selectedAppt.attendanceStatus === opt.val;
                    return (
                      <button
                        key={opt.val}
                        onClick={() => {
                          onUpdateAttendance(selectedAppt.id, opt.val);
                          setSelectedAppt((prev: any) => prev ? { ...prev, attendanceStatus: opt.val } : null);
                        }}
                        style={{
                          padding: 'var(--space-2) var(--space-1)',
                          fontSize: '11px',
                          fontWeight: 'bold',
                          borderRadius: 'var(--radius-md)',
                          border: isSelected ? `2px solid ${opt.color}` : '1px solid var(--color-border)',
                          backgroundColor: isSelected ? 'var(--neutral-50)' : '#ffffff',
                          color: isSelected ? opt.color : 'var(--color-text-secondary)',
                          cursor: 'pointer',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: '2px',
                          boxShadow: isSelected ? 'var(--shadow-sm)' : 'none'
                        }}
                      >
                        <span style={{ fontSize: '14px' }}>{opt.emoji}</span>
                        <span>{opt.label}</span>
                      </button>
                    )
                  })}
                </div>
              </div>

              {/* Reschedule Section */}
              <div style={{
                padding: 'var(--space-4) 0',
                borderTop: '1px solid var(--color-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-2)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>
                    Reprogramar Consulta
                  </label>
                  <button 
                    onClick={() => {
                      setIsRescheduling(!isRescheduling);
                      setRescheduleDate(selectedAppt.fecha || '');
                      setRescheduleHour(selectedAppt.hour + ':00');
                    }}
                    style={{
                      border: 'none',
                      background: 'none',
                      color: 'var(--color-primary)',
                      fontSize: '11px',
                      fontWeight: 'bold',
                      cursor: 'pointer',
                      textDecoration: 'underline'
                    }}
                  >
                    {isRescheduling ? 'Cancelar' : 'Modificar fecha/hora'}
                  </button>
                </div>

                {isRescheduling && (
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: '1.2fr 0.8fr 1fr',
                    gap: 'var(--space-2)',
                    alignItems: 'flex-end',
                    backgroundColor: 'var(--neutral-50)',
                    padding: 'var(--space-3)',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--color-border)',
                    marginTop: 'var(--space-2)'
                  }}>
                    <div>
                      <label style={{ fontSize: '9px', color: 'var(--color-text-secondary)', display: 'block', marginBottom: '2px' }}>Nueva Fecha</label>
                      <input 
                        type="date" 
                        value={rescheduleDate}
                        onChange={(e) => setRescheduleDate(e.target.value)}
                        style={{ width: '100%', fontSize: '11px', padding: '4px' }}
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '9px', color: 'var(--color-text-secondary)', display: 'block', marginBottom: '2px' }}>Hora</label>
                      <select
                        value={rescheduleHour}
                        onChange={(e) => setRescheduleHour(e.target.value)}
                        style={{ width: '100%', fontSize: '11px', padding: '4px' }}
                      >
                        {getDynamicSlots().map(slot => (
                          <option key={slot} value={slot}>{slot} hs</option>
                        ))}
                      </select>
                    </div>
                    <button
                      onClick={() => {
                        onRescheduleAppointment(selectedAppt.id, rescheduleDate, rescheduleHour);
                        setSelectedAppt(null);
                        setIsRescheduling(false);
                      }}
                      className="btn btn--primary btn--sm"
                      style={{ height: '26px', fontSize: '10px', padding: '0 var(--space-2)' }}
                    >
                      Guardar
                    </button>
                  </div>
                )}
              </div>

            </div>

            {/* Footer */}
            <div style={{
              padding: 'var(--space-5)',
              borderTop: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 'var(--space-3)',
              backgroundColor: 'var(--neutral-50)'
            }}>
              <button className="btn btn--secondary" onClick={() => setSelectedAppt(null)}>
                Cerrar
              </button>
              {selectedAppt.meetLink && selectedAppt.status === 'confirmed' && (
                <a 
                  href={selectedAppt.meetLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn--primary"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)' }}
                >
                  <Icon.Video /> Unirse al Meet
                </a>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

// ── Root App ───────────────────────────────────────────────────
export default function App() {
  const { showAlert } = useAlert()
  const [view, setView] = useState<AppView>('landing')
  const [activeNav, setActiveNav] = useState<NavSection>('dashboard')
  const [mpConnected, setMpConnected] = useState(false)
  const [mpEnabled, setMpEnabled] = useState(false)
  const [checkoutTarget, setCheckoutTarget] = useState<CheckoutTarget | null>(null)

  // API states
  const [currentUser, setCurrentUser] = useState<any>(null)
  const [medicoInfo, setMedicoInfo] = useState<any>(null)
  const [todayAppointments, setTodayAppointments] = useState<any[]>([])
  const [allAppointments, setAllAppointments] = useState<any[]>([])
  const [notifications, setNotifications] = useState<any[]>([])
  const [showNotifications, setShowNotifications] = useState(false)
  const [newPatientAlert, setNewPatientAlert] = useState<{ nombre: string; fecha: string; hora: string } | null>(null)
  const [availability, setAvailability] = useState<any[]>([])
  const [stats, setStats] = useState<any>(null)
  const [loadingDashboard, setLoadingDashboard] = useState(false)
  const [loadingSession, setLoadingSession] = useState(true)
  const [showUnverifiedAlert, setShowUnverifiedAlert] = useState(true)

  const handleCloseUnverifiedAlert = () => {
    setShowUnverifiedAlert(false)
    setTimeout(() => {
      setShowUnverifiedAlert(true)
    }, 120000) // 2 minutes
  }

  // Check user session on app load (Recovery from localStorage)
  useEffect(() => {
    const cachedUser = localStorage.getItem('tranqui_user')
    if (cachedUser) {
      try {
        const user = JSON.parse(cachedUser)
        setCurrentUser(user)
        if (user.rol === 'PSIQUIATRA' || user.rol === 'MEDICO' || user.rol === 'ADMIN') {
          setView('dashboard')
        } else {
          setView('landing')
        }
      } catch (e) {
        localStorage.removeItem('tranqui_user')
      }
    }

    api.getMe()
      .then((user) => {
        if (user) {
          setCurrentUser(user)
          localStorage.setItem('tranqui_user', JSON.stringify(user))
          if (user.rol === 'PSIQUIATRA' || user.rol === 'MEDICO' || user.rol === 'ADMIN') {
            setView('dashboard')
          } else {
            setView('landing')
          }
        } else {
          setCurrentUser(null)
          localStorage.removeItem('tranqui_user')
          setView('landing')
        }
      })
      .catch(() => {
        setCurrentUser(null)
        localStorage.removeItem('tranqui_user')
        setView('landing')
      })
      .finally(() => {
        setLoadingSession(false)
      })
  }, [])

  // Route protection guard
  useEffect(() => {
    if (loadingSession) return

    if (currentUser) {
      const isPro = currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO' || currentUser.rol === 'ADMIN'
      if (isPro && (view === 'landing' || view === 'login')) {
        setView('dashboard')
      } else if (!isPro && view === 'dashboard') {
        setView('landing')
      }
    }
  }, [currentUser, view, loadingSession])

  // Fetch Dashboard details
  useEffect(() => {
    if (loadingSession) return

    if (view === 'dashboard') {
      if (currentUser?.rol === 'ADMIN') {
        setLoadingDashboard(false)
        return
      }
      const isPro = currentUser && (currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO')
      if (!isPro) {
        setView('landing')
        return
      }
      setLoadingDashboard(true)
      Promise.all([
        api.getPerfil(),
        api.getTurnosHoy(),
        api.getDisponibilidad(),
        api.getStats(),
        api.getTurnos(),
        api.getNotificaciones(),
        api.getMercadoPagoStatus().catch(() => ({ connected: false }))
      ])
        .then(([perfil, turnos, disp, statsData, allTurnos, notifData, mpStatus]) => {
          setMedicoInfo(perfil)
          setTodayAppointments(turnos || [])
          setAvailability(disp || [])
          setStats(statsData)
          setAllAppointments(allTurnos || [])
          setNotifications(notifData || [])
          setMpConnected(!!mpStatus?.connected)
          setMpEnabled(!!(mpStatus as any)?.mercadopagoEnabled)
        })
        .catch((err) => {
          console.error("Error al inicializar dashboard:", err)
          setView('login')
        })
        .finally(() => {
          setLoadingDashboard(false)
        })
    }
  }, [view, currentUser, loadingSession])

  // WebSocket Live Notifications Handler
  useEffect(() => {
    if (loadingSession) return

    if (currentUser && currentUser.rol === 'PSIQUIATRA' && view === 'dashboard') {
      let client: Client | null = null;
      api.getPerfil().then((perfil) => {
        if (perfil && perfil.id) {
          const socket = new SockJS('http://localhost:8081/ws-tranqui', null, { withCredentials: true } as any)
          client = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 5000,
            onConnect: () => {
              console.log("WebSocket de Notificaciones conectado para ID:", perfil.id)
              client?.subscribe(`/topic/notificaciones/${perfil.id}`, (stompMsg) => {
                try {
                  const data = JSON.parse(stompMsg.body)
                  console.log("WebSocket notification received:", data)
                  
                  // Refresh notification list from API to get full history
                  api.getNotificaciones().then((res) => {
                    setNotifications(res || [])
                  })

                  if (data.tipo === 'TURNO_RESERVADO' || data.tipo === 'TURNO_CANCELADO') {
                    // Refresh appointments list too so calendar updates immediately
                    api.getTurnos().then((turnos) => {
                      setAllAppointments(turnos || [])
                    })
                    api.getTurnosHoy().then((turnosHoy) => {
                      setTodayAppointments(turnosHoy || [])
                    })

                    // Parse patient name from message
                    let name = "Paciente"
                    let msg = data.mensaje || ""
                    if (msg.includes("El paciente ")) {
                      const nameMatch = msg.match(/El paciente (.*?) (?:ha|reservó)/)
                      if (nameMatch) {
                        name = nameMatch[1]
                      }
                    }
                    setNewPatientAlert({
                      nombre: name,
                      fecha: new Date().toLocaleDateString('es-AR'),
                      hora: "10:00"
                    })
                  }
                } catch (e) {
                  console.error("Error parsing WebSocket notification:", e)
                }
              })
            }
          })
          client.activate()
        }
      }).catch(err => console.error("Error fetching profile for WebSocket initialization:", err))

      return () => {
        if (client) {
          client.deactivate()
        }
      }
    }
  }, [currentUser, view, loadingSession])

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

  const handleConnect = async () => {
    // In local/dev environments (no real Mercado Pago credentials configured on the
    // backend) we simulate the link instead of redirecting to the real OAuth flow,
    // which would otherwise always fail with an empty client_id.
    if (!mpEnabled) {
      try {
        await api.simularConexionMercadoPago()
        setMpConnected(true)
        showAlert('Conexión simulada (modo desarrollo): tu cuenta de Mercado Pago quedó vinculada para pruebas locales.', 'success')
      } catch (err) {
        console.error("Error al simular la conexión de Mercado Pago:", err)
        showAlert('No se pudo simular la conexión con Mercado Pago.', 'error')
      }
      return
    }

    try {
      const { url } = await api.getMercadoPagoConnectUrl()
      window.location.href = url
    } catch (err) {
      console.error("Error al obtener la URL de conexión de Mercado Pago:", err)
      showAlert('No se pudo iniciar la conexión con Mercado Pago. Intentá de nuevo.', 'error')
    }
  }

  const handleDisconnectMercadoPago = async () => {
    try {
      await api.desconectarMercadoPago()
      setMpConnected(false)
      showAlert('Desvinculaste tu cuenta de Mercado Pago.', 'success')
    } catch (err) {
      console.error("Error al desvincular Mercado Pago:", err)
      showAlert('No se pudo desvincular la cuenta. Intentá de nuevo.', 'error')
    }
  }

  // Handle the ?mp=success / ?mp=error redirect coming back from the Mercado Pago OAuth callback
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const mpResult = params.get('mp')
    if (!mpResult) return

    if (mpResult === 'success') {
      showAlert('¡Tu cuenta de Mercado Pago quedó vinculada! Ya podés recibir pagos.', 'success')
      setMpConnected(true)
    } else if (mpResult === 'error') {
      showAlert('No se pudo vincular tu cuenta de Mercado Pago. Intentá de nuevo.', 'error')
    }

    params.delete('mp')
    params.delete('reason')
    const newSearch = params.toString()
    window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''))
  }, [])

  const handleBook = (pro: CheckoutTarget) => {
    if (!currentUser) {
      showAlert("Para reservar un turno, debes iniciar sesión primero.", "warning");
      setView('login');
      return;
    }
    setCheckoutTarget(pro);
    setView('checkout');
  }

  const handleLogout = async () => {
    try {
      await api.logout()
    } catch (err) {
      console.error("Error al cerrar sesión:", err)
    }
    setCurrentUser(null)
    localStorage.removeItem('tranqui_user')
    setView('landing')
  }

  const handleMarkNotificationsRead = () => {
    api.marcarNotificacionesLeidas()
      .then(() => {
        setNotifications(notifications.map(n => ({ ...n, leido: true })))
      })
      .catch((err) => console.error("Error al marcar notificaciones como leídas:", err))
  }

  const refreshDashboardAppointments = () => {
    Promise.all([
      api.getTurnosHoy(),
      api.getTurnos(),
      api.getStats()
    ]).then(([turnos, allTurnos, statsData]) => {
      setTodayAppointments(turnos || [])
      setAllAppointments(allTurnos || [])
      setStats(statsData)
    }).catch(err => console.error("Error refreshing appointments:", err));
  }

  const handleCancelAppointment = (turnoId: number) => {
    if (window.confirm("¿Estás seguro de que deseas cancelar este turno?")) {
      api.cancelarTurno(turnoId)
        .then(() => {
          showAlert("Turno cancelado con éxito.", "success");
          refreshDashboardAppointments();
        })
        .catch(err => {
          console.error(err);
          showAlert("Error al cancelar el turno.", "error");
        });
    }
  }

  const handleUpdateAttendance = (turnoId: number, asistencia: string) => {
    api.actualizarAsistencia(turnoId, asistencia)
      .then(() => {
        refreshDashboardAppointments();
      })
      .catch(err => {
        console.error(err);
        showAlert("Error al actualizar la asistencia.", "error");
      });
  }

  const handleRescheduleAppointment = (turnoId: number, fecha: string, hora: string) => {
    api.reprogramarTurno(turnoId, fecha, hora)
      .then(() => {
        showAlert("Turno reprogramado con éxito. Se ha enviado una notificación por WhatsApp al paciente.", "success");
        refreshDashboardAppointments();
      })
      .catch(err => {
        console.error(err);
        showAlert("Error al reprogramar el turno.", "error");
      });
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
        return (
          <DashboardHome
            mpConnected={mpConnected}
            onConnect={handleConnect}
            onDisconnect={handleDisconnectMercadoPago}
            appointments={todayAppointments}
            allAppointments={allAppointments}
            availability={availability}
            stats={stats} 
            onCancelAppointment={handleCancelAppointment}
            onUpdateAttendance={handleUpdateAttendance}
            onRescheduleAppointment={handleRescheduleAppointment}
          />
        )
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
          if (user.rol === 'PSIQUIATRA' || user.rol === 'MEDICO' || user.rol === 'ADMIN') {
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

  if (view === 'dashboard' && currentUser?.rol === 'ADMIN') {
    return (
      <AdminDashboard
        currentUser={currentUser}
        onLogout={handleLogout}
      />
    )
  }

  const isPro = currentUser && (currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO')
  if (view === 'dashboard' && !isPro) {
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

  const unreadCount = notifications.filter(n => !n.leido).length

  const showBanner = view === 'dashboard' && medicoInfo && !medicoInfo.verificado && showUnverifiedAlert;

  return (
    <div className="dashboard-layout">
      {showBanner && (
        <>
          <style>{`
            @keyframes slideDownAlert {
              from {
                transform: translate(-50%, -100%);
                opacity: 0;
              }
              to {
                transform: translate(-50%, 0);
                opacity: 1;
              }
            }
          `}</style>
          <div style={{
            position: 'fixed',
            top: '16px',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 10000,
            backgroundColor: '#fffbeb',
            border: '1px solid #fef3c7',
            borderLeft: '5px solid #d97706',
            borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-4) var(--space-5)',
            color: '#b45309',
            fontSize: 'var(--text-sm)',
            boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
            maxWidth: '600px',
            width: '90%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 'var(--space-4)',
            animation: 'slideDownAlert 0.3s ease-out'
          }}>
            <div style={{ flex: 1, lineHeight: 'var(--line-height-relaxed)' }}>
              <strong>⚠️ Cuenta No Verificada:</strong> Para aparecer en la lista de profesionales disponibles de la aplicación y que los pacientes puedan agendar turnos, debés completar todos tus datos demográficos, ReFeps, matrícula y subir una foto de perfil.
            </div>
            <button 
              onClick={handleCloseUnverifiedAlert}
              style={{
                background: 'none',
                border: 'none',
                color: '#b45309',
                fontSize: '18px',
                cursor: 'pointer',
                padding: '0 var(--space-1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 'bold',
                opacity: 0.7,
                transition: 'opacity 0.2s'
              }}
              onMouseEnter={(e) => e.currentTarget.style.opacity = '1'}
              onMouseLeave={(e) => e.currentTarget.style.opacity = '0.7'}
              aria-label="Cerrar alerta"
            >
              ✕
            </button>
          </div>
        </>
      )}
      <Sidebar activeNav={activeNav} onNavChange={setActiveNav} medicoInfo={medicoInfo} />

      <header className="dashboard-header" role="banner" style={{ position: 'relative' }}>
        <h1 className="dashboard-header__title">{pageTitle[activeNav]}</h1>
        <div className="dashboard-header__actions" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          
          {/* Interactive Notifications Bell */}
          <div style={{ position: 'relative' }}>
            <button 
              className="btn btn--icon btn--ghost" 
              onClick={() => setShowNotifications(!showNotifications)}
              aria-label="Notificaciones"
              style={{ position: 'relative' }}
            >
              <Icon.Bell hasUnread={unreadCount > 0} />
              {unreadCount > 0 && (
                <span style={{
                  position: 'absolute',
                  top: -2,
                  right: -2,
                  backgroundColor: 'var(--color-error)',
                  color: 'white',
                  borderRadius: '50%',
                  width: '16px',
                  height: '16px',
                  fontSize: '9px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 'bold'
                }}>
                  {unreadCount}
                </span>
              )}
            </button>

            {showNotifications && (
              <div className="card" style={{
                position: 'absolute',
                top: '48px',
                right: '0',
                width: '320px',
                maxHeight: '400px',
                overflowY: 'auto',
                zIndex: 1000,
                boxShadow: 'var(--shadow-lg)',
                border: '1px solid var(--color-border)',
                padding: 'var(--space-3)',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-2)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-2)' }}>
                  <strong style={{ fontSize: 'var(--text-sm)' }}>Historial de Notificaciones</strong>
                  {unreadCount > 0 && (
                    <button 
                      onClick={handleMarkNotificationsRead}
                      className="btn btn--ghost" 
                      style={{ fontSize: '10px', padding: '2px 6px', height: 'auto' }}
                    >
                      Marcar leídas
                    </button>
                  )}
                </div>

                {notifications.length === 0 ? (
                  <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', padding: 'var(--space-4)' }}>
                    No tenés notificaciones.
                  </p>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                    {notifications.map((n) => (
                      <div key={n.id} style={{
                        padding: 'var(--space-2) var(--space-3)',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: n.leido ? 'transparent' : '#ecfdf5',
                        borderLeft: n.leido ? '3px solid var(--color-border)' : '3px solid var(--color-primary)',
                        fontSize: '11px',
                        transition: 'background-color 0.2s',
                        color: n.leido ? 'var(--color-text-secondary)' : 'var(--color-text-primary)'
                      }}>
                        <div style={{ fontWeight: 'bold' }}>{n.titulo}</div>
                        <div style={{ marginTop: '2px', fontSize: '10px' }}>{n.mensaje}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <div className="sidebar__avatar" aria-label="Menú de perfil" role="button" tabIndex={0} style={{ cursor: 'pointer' }}>
              {medicoInfo?.initials || 'LP'}
            </div>
            <button 
              onClick={handleLogout}
              className="btn btn--ghost btn--sm"
              style={{ fontSize: '11px', padding: 'var(--space-1) var(--space-3)' }}
            >
              Cerrar sesión
            </button>
          </div>
        </div>
      </header>

      <main 
        className="dashboard-main" 
        role="main" 
        id="main-content"
        style={activeNav === 'patients' ? { 
          overflow: 'hidden', 
          height: 'calc(100vh - var(--header-height))', 
          maxHeight: 'calc(100vh - var(--header-height))', 
          display: 'flex', 
          flexDirection: 'column', 
          padding: 'var(--space-6)' 
        } : {}}
      >
        {renderContent()}
      </main>

      {newPatientAlert && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          animation: 'fadeIn 0.2s ease-out'
        }}>
          <div className="card" style={{
            maxWidth: '400px',
            width: '90%',
            padding: 'var(--space-6)',
            textAlign: 'center',
            boxShadow: 'var(--shadow-lg)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            backgroundColor: 'var(--color-surface)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 'var(--space-4)'
          }}>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              backgroundColor: '#d1fae5',
              color: 'var(--color-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 24, height: 24 }}>
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <h3 style={{
              fontFamily: 'var(--font-heading)',
              fontSize: 'var(--text-lg)',
              fontWeight: 'var(--font-weight-bold)',
              color: 'var(--color-text-primary)',
              margin: 0
            }}>
              ¡Nuevo Paciente Registrado!
            </h3>
            <p style={{
              fontSize: 'var(--text-sm)',
              color: 'var(--color-text-secondary)',
              lineHeight: 'var(--line-height-relaxed)',
              margin: 0
            }}>
              El paciente <strong>{newPatientAlert.nombre}</strong> ha reservado un nuevo turno y el pago ha sido aprobado correctamente.
            </p>
            <button
              className="btn btn--primary"
              onClick={() => setNewPatientAlert(null)}
              style={{ width: '100%', marginTop: 'var(--space-2)' }}
            >
              Entendido
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
