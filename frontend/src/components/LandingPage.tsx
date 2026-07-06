import { useState, useMemo, useEffect, useRef } from 'react'
import '../styles/landing.css'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'

// ── Types ──────────────────────────────────────────────────────
interface Tariff {
  label: string
  price: number
}

interface Professional {
  id: string
  name: string
  initials: string
  specialty: string
  degree: string
  matricula: string
  tags: string[]
  price: number
  tariffs: Tariff[]
  nextSlot: string
  nextSlotDay: string
  online: boolean
  color: string
}

// PROFESSIONALS mock array removed since values are loaded from API

const SPECIALTIES = ['Todos', 'Ansiedad', 'Depresión', 'Trauma', 'Pareja', 'Psiquiatría', 'Adolescentes']

// ── Crisis Modal ───────────────────────────────────────────────
function CrisisModal({ onClose }: { onClose: () => void }) {
  return (
    <div
      className="crisis-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="crisis-modal-title"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="crisis-modal">
        <h2 className="crisis-modal__title" id="crisis-modal-title">
          ¿Estás en crisis ahora mismo?
        </h2>
        <p className="crisis-modal__body">
          Si estás en peligro o atravesando una crisis aguda, no esperes un turno.
          Comunicáte de forma gratuita con estos servicios, las 24 horas:
        </p>

        <div className="crisis-modal__numbers">
          <a href="tel:135" className="crisis-number" id="crisis-call-135">
            <div className="crisis-number__num">135</div>
            <div>
              <div className="crisis-number__label">Centro de Asistencia al Suicida</div>
              <div className="crisis-number__sublabel">Gratuito · 24hs · Todo el país</div>
            </div>
          </a>
          <a href="tel:107" className="crisis-number" id="crisis-call-107">
            <div className="crisis-number__num">107</div>
            <div>
              <div className="crisis-number__label">Emergencias Médicas</div>
              <div className="crisis-number__sublabel">SAME · Atención inmediata</div>
            </div>
          </a>
        </div>

        <button className="btn btn--ghost" onClick={onClose} style={{ width: '100%' }}>
          Volver a buscar turno
        </button>
      </div>
    </div>
  )
}

// ── Skeleton Card ──────────────────────────────────────────────
function SkeletonCard() {
  return (
    <div className="pro-card pro-card--skeleton" aria-hidden="true">
      <div className="pro-card__header">
        <div className="skeleton" style={{ width: 64, height: 64, borderRadius: 'var(--radius-lg)' }} />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          <div className="skeleton" style={{ height: 20, width: '70%' }} />
          <div className="skeleton" style={{ height: 14, width: '50%' }} />
          <div className="skeleton" style={{ height: 20, width: '40%', borderRadius: '999px' }} />
        </div>
      </div>
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        {[80, 60, 70].map((w) => (
          <div key={w} className="skeleton" style={{ height: 24, width: w, borderRadius: '999px' }} />
        ))}
      </div>
      <div className="pro-card__divider" />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div className="skeleton" style={{ height: 28, width: 100 }} />
        <div className="skeleton" style={{ height: 48, width: 90, borderRadius: 'var(--radius-md)' }} />
      </div>
    </div>
  )
}

// ── Professional Card ──────────────────────────────────────────
function ProCard({ pro, onBook, onChat }: { pro: Professional; onBook: (p: Professional) => void; onChat: (p: Professional) => void }) {
  return (
    <article
      className="pro-card"
      role="article"
      aria-label={`${pro.name}, ${pro.specialty}`}
      onClick={() => onBook(pro)}
    >
      <div className="pro-card__header">
        <div
          className="pro-card__avatar"
          style={{ background: pro.color, color: 'var(--green-700)' }}
          aria-hidden="true"
        >
          {pro.initials}
          {pro.online && <span className="pro-card__online-dot" aria-label="Disponible ahora" />}
        </div>
        <div className="pro-card__info">
          <div className="pro-card__name">{pro.name}</div>
          <div className="pro-card__specialty">{pro.degree} · {pro.specialty}</div>
          <span className="pro-card__matricula">{pro.matricula} ✓</span>
        </div>
      </div>

      <div className="pro-card__tags" aria-label="Especialidades">
        {pro.tags.slice(0, 3).map((tag) => (
          <span key={tag} className="pro-card__tag">{tag}</span>
        ))}
        {pro.tags.length > 3 && (
          <span className="pro-card__tag">+{pro.tags.length - 3}</span>
        )}
      </div>

      <div className="pro-card__divider" aria-hidden="true" />

      {/* Tariff table */}
      <div className="pro-card__tariffs">
        {pro.tariffs.map((t) => (
          <div className="pro-card__tariff-row" key={t.label}>
            <span className="pro-card__tariff-label">{t.label}</span>
            <span className="pro-card__tariff-price">${t.price.toLocaleString('es-AR')}</span>
          </div>
        ))}
      </div>

      <div className="pro-card__footer" style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
        <div className="pro-card__price" style={{ marginRight: 'auto' }}>
          <span className="pro-card__price-label">50 min · Online</span>
        </div>
        <button
          className="btn btn--secondary btn--sm"
          onClick={(e) => { e.stopPropagation(); onChat(pro) }}
          style={{ whiteSpace: 'nowrap', padding: 'var(--space-2) var(--space-3)' }}
        >
          💬 Chatear
        </button>
        <button
          className="btn btn--primary btn--sm"
          id={`btn-book-${pro.id}`}
          onClick={(e) => { e.stopPropagation(); onBook(pro) }}
          aria-label={`Reservar turno con ${pro.name}`}
          style={{ whiteSpace: 'nowrap', padding: 'var(--space-2) var(--space-3)' }}
        >
          Pedir Turno
        </button>
      </div>
    </article>
  )
}

// ── Public Header ──────────────────────────────────────────────
function PublicHeader({ 
  currentUser, 
  onCrisis, 
  onProLogin,
  onLogout,
  onGoToDashboard
}: { 
  currentUser: any; 
  onCrisis: () => void; 
  onProLogin: () => void;
  onLogout: () => void;
  onGoToDashboard: () => void;
}) {
  const isDoctor = currentUser?.rol === 'PSIQUIATRA' || currentUser?.rol === 'MEDICO'

  return (
    <header className="public-header" role="banner">
      <div className="public-header__inner">
        <a href="/" className="public-header__logo" aria-label="Tranqui App - Inicio">
          tranqui
        </a>
        <span className="public-header__tagline">por Tranqui Neurociencias</span>
        <div className="public-header__spacer" />
        <div className="public-header__actions">
          <button
            className="btn-crisis"
            onClick={onCrisis}
            id="btn-crisis-trigger"
          >
            <span className="btn-crisis__dot" aria-hidden="true" />
            Ayuda urgente
          </button>

          {currentUser ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-medium)' }}>
                {currentUser.nombre}
              </span>
              {isDoctor && (
                <button 
                  className="btn btn--secondary btn--sm" 
                  onClick={onGoToDashboard}
                  style={{ padding: 'var(--space-2) var(--space-4)', fontSize: 'var(--text-xs)' }}
                >
                  Panel Profesional
                </button>
              )}
              <button 
                className="btn btn--ghost btn--sm" 
                onClick={onLogout}
                style={{ padding: 'var(--space-2) var(--space-4)', fontSize: 'var(--text-xs)' }}
              >
                Cerrar sesión
              </button>
            </div>
          ) : (
            <button
              className="btn-pro-login"
              onClick={onProLogin}
              id="btn-pro-access"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15 }}>
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
              Iniciar sesión
            </button>
          )}
        </div>
      </div>
    </header>
  )
}

// ── Trust Strip ────────────────────────────────────────────────
const TRUST_ITEMS = [
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: '16px', height: '16px' }}><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>, text: 'Profesionales con matrícula verificada' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: '16px', height: '16px' }}><rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></svg>, text: 'Transacciones 100% encriptadas' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: '16px', height: '16px' }}><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>, text: 'Turnos disponibles en menos de 24hs' },
]

function TrustStrip() {
  return (
    <div className="trust-strip" aria-label="Garantías de servicio" style={{
      borderBottom: '1px solid var(--color-border)',
      padding: 'var(--space-3) var(--space-8) var(--space-4)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 'var(--space-6)',
      flexWrap: 'wrap'
    }}>
      {TRUST_ITEMS.map((item, idx) => (
        <div className="trust-item" key={idx} role="listitem" style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          fontSize: 'var(--text-xs)',
          color: 'var(--neutral-600)'
        }}>
          <span className="trust-item__icon" aria-hidden="true" style={{
            display: 'inline-flex',
            alignItems: 'center',
            width: '16px',
            height: '16px',
            color: 'var(--color-primary)'
          }}>
            {item.icon}
          </span>
          <span className="trust-item__text">{item.text}</span>
        </div>
      ))}
    </div>
  )
}

// ── Landing Page ───────────────────────────────────────────────
interface BookTarget {
  id: string
  name: string
  degree: string
  specialty: string
  matricula: string
  price: number
  nextSlot: string
  nextSlotDay: string
}

export default function LandingPage({ 
  currentUser, 
  onNavigateToDashboard, 
  onBook,
  onLogout,
  onGoToDashboard
}: { 
  currentUser: any; 
  onNavigateToDashboard: () => void; 
  onBook?: (pro: BookTarget) => void;
  onLogout: () => void;
  onGoToDashboard: () => void;
}) {
  const [query, setQuery] = useState('')
  const [activeSpecialty, setActiveSpecialty] = useState('Todos')
  const [professionals, setProfessionals] = useState<Professional[]>([])
  const [loading, setLoading] = useState(true)
  const [showCrisis, setShowCrisis] = useState(false)

  // Patient Portal states
  const [patientTab, setPatientTab] = useState<'search' | 'portal'>('search')
  const [myAppointments, setMyAppointments] = useState<any[]>([])
  const [myTrackings, setMyTrackings] = useState<any[]>([])
  const [myReports, setMyReports] = useState<any[]>([])
  const [loadingPortal, setLoadingPortal] = useState(false)

  // Portal inner tabs
  const [portalTab, setPortalTab] = useState<'appointments' | 'tracking' | 'chat'>('appointments')
  const [activeDoctorId, setActiveDoctorId] = useState<number | null>(null)

  // Set default doctor ID (1 - Lic. Maria Paula Rossi) for patient demo chat
  useEffect(() => {
    if (currentUser && currentUser.rol === 'PACIENTE' && !activeDoctorId) {
      setActiveDoctorId(1)
    }
  }, [currentUser, activeDoctorId])

  const { messages: chatMessages, sendMessage: sendChatMessage } = useChat(activeDoctorId)
  const [chatInput, setChatInput] = useState('')
  const chatMessagesEndRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (portalTab === 'chat') {
      chatMessagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [chatMessages, portalTab])

  // Tracking form states
  const [mood, setMood] = useState('Bueno')
  const [symptoms, setSymptoms] = useState('')
  const [notes, setNotes] = useState('')
  const [savingTracking, setSavingTracking] = useState(false)

  useEffect(() => {
    // Only load professionals if authenticated
    if (!currentUser) {
      setLoading(false)
      return
    }

    setLoading(true)
    api.getMedicos()
      .then((res: any) => {
        const mapped = res.map((m: any) => ({
          ...m,
          id: String(m.id),
          nextSlot: m.nextSlot || "16:00",
          nextSlotDay: m.nextSlotDay || "Hoy",
          online: m.online !== undefined ? m.online : true,
          tariffs: m.tariffs.map((t: any) => ({
            label: t.label,
            price: t.price,
          })),
        }))
        setProfessionals(mapped)
      })
      .catch((err) => console.error("Error al cargar médicos:", err))
      .finally(() => setLoading(false))
  }, [currentUser])

  // Load Patient Portal Data
  useEffect(() => {
    if (currentUser && currentUser.rol === 'PACIENTE' && patientTab === 'portal') {
      setLoadingPortal(true)
      Promise.all([
        api.getMisTurnos(),
        api.getMisSeguimientos(),
        api.getMisInformes()
      ])
        .then(([turnos, seguimientos, informes]) => {
          setMyAppointments(turnos || [])
          setMyTrackings(seguimientos || [])
          setMyReports(informes || [])
        })
        .catch((err) => console.error("Error loading patient portal data:", err))
        .finally(() => setLoadingPortal(false))
    }
  }, [currentUser, patientTab])

  const handleAddTracking = async (e: React.FormEvent) => {
    e.preventDefault()
    setSavingTracking(true)
    try {
      const saved = await api.crearMiSeguimiento({ estadoAnimo: mood, sintomas: symptoms, notas: notes })
      setMyTrackings([saved, ...myTrackings])
      setMood('Bueno')
      setSymptoms('')
      setNotes('')
      alert("Seguimiento diario registrado correctamente ✓")
    } catch (err) {
      console.error("Error al registrar seguimiento:", err)
      alert("Error al registrar seguimiento")
    } finally {
      setSavingTracking(false)
    }
  }

  const filtered = useMemo(() => {
    return professionals.filter((pro) => {
      const matchesQuery = query === '' ||
        pro.name.toLowerCase().includes(query.toLowerCase()) ||
        (pro.degree && pro.degree.toLowerCase().includes(query.toLowerCase())) ||
        (pro.matricula && pro.matricula.toLowerCase().includes(query.toLowerCase())) ||
        pro.tags.some((t) => t.toLowerCase().includes(query.toLowerCase())) ||
        pro.specialty.toLowerCase().includes(query.toLowerCase())

      const matchesSpecialty = activeSpecialty === 'Todos' ||
        pro.tags.some((t) => t.toLowerCase().includes(activeSpecialty.toLowerCase())) ||
        pro.specialty.toLowerCase().includes(activeSpecialty.toLowerCase())

      return matchesQuery && matchesSpecialty
    })
  }, [query, activeSpecialty, professionals])

  const handleBook = (pro: Professional) => {
    if (onBook) {
      onBook({
        id: pro.id,
        name: pro.name,
        degree: pro.degree,
        specialty: pro.specialty,
        matricula: pro.matricula,
        price: pro.price,
        nextSlot: pro.nextSlot,
        nextSlotDay: pro.nextSlotDay,
      })
    }
  }

  const handleStartChat = (pro: Professional) => {
    setActiveDoctorId(Number(pro.id))
    setPatientTab('portal')
    setPortalTab('chat')
  }

  const handleSendPatientChat = (e: React.FormEvent) => {
    e.preventDefault()
    if (!chatInput.trim() || !activeDoctorId) return
    const sent = sendChatMessage(activeDoctorId, chatInput.trim())
    if (sent) {
      setChatInput('')
    }
  }

  const renderPatientPortal = () => {
    if (loadingPortal) {
      return (
        <div style={{ textAlign: 'center', padding: 'var(--space-12)', width: '100%' }}>
          <div className="checkout-spinner" style={{ margin: '0 auto var(--space-4)' }} />
          <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>Cargando portal...</p>
        </div>
      )
    }

    return (
      <div style={{
        width: '100%',
        maxWidth: '1000px',
        margin: 'var(--space-4) auto',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)',
        textAlign: 'left'
      }}>
        {/* Portal Inner Navigation */}
        <div style={{
          display: 'flex',
          borderBottom: '1px solid var(--color-border)',
          gap: 'var(--space-1)',
          paddingBottom: '2px'
        }}>
          <button
            onClick={() => setPortalTab('appointments')}
            style={{
              padding: 'var(--space-2) var(--space-4)',
              fontSize: 'var(--text-sm)',
              fontWeight: 'bold',
              border: 'none',
              borderBottom: portalTab === 'appointments' ? '3px solid var(--color-primary)' : '3px solid transparent',
              backgroundColor: 'transparent',
              color: portalTab === 'appointments' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              cursor: 'pointer'
            }}
          >
            🗓️ Turnos e Informes
          </button>
          <button
            onClick={() => setPortalTab('tracking')}
            style={{
              padding: 'var(--space-2) var(--space-4)',
              fontSize: 'var(--text-sm)',
              fontWeight: 'bold',
              border: 'none',
              borderBottom: portalTab === 'tracking' ? '3px solid var(--color-primary)' : '3px solid transparent',
              backgroundColor: 'transparent',
              color: portalTab === 'tracking' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              cursor: 'pointer'
            }}
          >
            📈 Seguimiento Diario
          </button>
          <button
            onClick={() => setPortalTab('chat')}
            style={{
              padding: 'var(--space-2) var(--space-4)',
              fontSize: 'var(--text-sm)',
              fontWeight: 'bold',
              border: 'none',
              borderBottom: portalTab === 'chat' ? '3px solid var(--color-primary)' : '3px solid transparent',
              backgroundColor: 'transparent',
              color: portalTab === 'chat' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              cursor: 'pointer'
            }}
          >
            💬 Chat con mi Profesional
          </button>
        </div>

        {/* Tab 1: Appointments & Reports */}
        {portalTab === 'appointments' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-6)' }}>
            <div className="card" style={{ padding: 'var(--space-6)' }}>
              <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-4)', marginTop: 0 }}>Mis Turnos Agendados</h3>
              {myAppointments.length === 0 ? (
                <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
                  No tenés turnos programados.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                  {myAppointments.map((appt) => {
                    const isConfirmed = appt.status === 'confirmed';
                    return (
                      <div key={appt.id} style={{
                        padding: 'var(--space-3)',
                        borderRadius: 'var(--radius-md)',
                        backgroundColor: isConfirmed ? '#ecfdf5' : '#fffbeb',
                        border: isConfirmed ? '1px solid #a7f3d0' : '1px solid #fef3c7',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 'var(--space-2)'
                      }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)' }}>
                            {appt.fecha} · {appt.hour} hs
                          </span>
                          <span className={`badge ${isConfirmed ? 'badge--success' : 'badge--warning'}`} style={{ fontSize: '9px' }}>
                            {isConfirmed ? 'Confirmado' : 'Pendiente'}
                          </span>
                        </div>
                        <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'bold' }}>
                          Profesional: {appt.patientName}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                          Modalidad: {appt.type}
                        </div>
                        {appt.meetLink && isConfirmed && (
                          <a 
                            href={appt.meetLink} 
                            target="_blank" 
                            rel="noopener noreferrer" 
                            className="btn btn--primary" 
                            style={{ fontSize: '11px', padding: 'var(--space-2) var(--space-4)', width: 'fit-content', display: 'flex', gap: '4px', alignItems: 'center', marginTop: 'var(--space-1)' }}
                          >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 14, height: 14 }}><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" ry="2" /></svg>
                            Unirse a la llamada
                          </a>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            <div className="card" style={{ padding: 'var(--space-6)' }}>
              <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-4)', marginTop: 0 }}>Mis Informes Clínicos Emitidos</h3>
              {myReports.length === 0 ? (
                <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
                  No tenés informes clínicos emitidos aún.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                  {myReports.map((r) => (
                    <div key={r.id} style={{
                      padding: 'var(--space-3)',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--color-border)',
                      backgroundColor: 'var(--neutral-50)'
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
                        <span style={{ fontSize: '11px', fontWeight: 'bold', color: 'var(--color-primary)' }}>
                          Informe {r.tipoInforme}
                        </span>
                        <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                          {r.fecha}
                        </span>
                      </div>
                      {r.planTrabajo && (
                        <div style={{ fontSize: '11px', marginBottom: 'var(--space-2)' }}>
                          <strong>Plan de Trabajo:</strong> {r.planTrabajo}
                        </div>
                      )}
                      {r.contenido && (
                        <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-2)', whiteSpace: 'pre-wrap' }}>
                          {r.contenido}
                        </div>
                      )}
                      {r.nombreArchivo && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', marginTop: 'var(--space-2)', fontSize: '11px', color: 'var(--color-primary)' }}>
                          📄 Adjunto: <em>{r.nombreArchivo}</em>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Tab 2: Daily Tracking */}
        {portalTab === 'tracking' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-6)' }}>
            <div className="card" style={{ padding: 'var(--space-6)' }}>
              <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-1)', marginTop: 0 }}>Cargar mi Estado de Hoy</h3>
              <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)' }}>
                Registrá cómo te sentís hoy para compartirlo con tu profesional.
              </p>

              <form onSubmit={handleAddTracking} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                  <div className="form-group">
                    <label className="form-label form-label--required">Estado de Ánimo</label>
                    <select 
                      value={mood} 
                      onChange={(e) => setMood(e.target.value)}
                      className="form-input"
                    >
                      <option value="Excelente">Excelente 😀</option>
                      <option value="Bueno">Bueno 🙂</option>
                      <option value="Regular">Regular 😐</option>
                      <option value="Malo">Malo 🙁</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Síntomas clave</label>
                    <input 
                      type="text" 
                      placeholder="Ej: Insomnio, palpitaciones..." 
                      value={symptoms}
                      onChange={(e) => setSymptoms(e.target.value)}
                      className="form-input"
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Notas o reflexiones de hoy</label>
                  <textarea 
                    rows={3} 
                    placeholder="Escribí notas sobre tu día..." 
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    className="form-input"
                  />
                </div>
                <button type="submit" className="btn btn--primary" disabled={savingTracking}>
                  {savingTracking ? 'Guardando...' : 'Registrar mi estado'}
                </button>
              </form>
            </div>

            <div className="card" style={{ padding: 'var(--space-6)' }}>
              <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-4)', marginTop: 0 }}>Historial de Seguimiento Diario</h3>
              {myTrackings.length === 0 ? (
                <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
                  No registraste seguimientos diarios aún.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxHeight: '300px', overflowY: 'auto' }}>
                  {myTrackings.map((t) => (
                    <div key={t.id} style={{
                      padding: 'var(--space-3)',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--color-border)',
                      backgroundColor: 'var(--neutral-50)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 'var(--space-2)'
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)', fontWeight: 'bold' }}>
                          {t.fecha}
                        </span>
                        <span className="badge badge--success" style={{ fontSize: '10px', textTransform: 'capitalize' }}>
                          Animo: {t.estadoAnimo}
                        </span>
                      </div>
                      {t.sintomas && (
                        <div style={{ fontSize: '11px' }}>
                          <strong>Síntomas:</strong> {t.sintomas}
                        </div>
                      )}
                      {t.notas && (
                        <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', fontStyle: 'italic' }}>
                          "{t.notas}"
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Tab 3: Live Chat with Professional */}
        {portalTab === 'chat' && (
          <div className="card" style={{ display: 'flex', flexDirection: 'column', height: '480px', overflow: 'hidden' }}>
            <div style={{
              padding: 'var(--space-4) var(--space-6)',
              borderBottom: '1px solid var(--color-border)',
              backgroundColor: 'var(--neutral-50)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div>
                <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', margin: 0 }}>Lic. María Paula Rossi</h3>
                <p style={{ fontSize: '11px', color: 'var(--color-text-secondary)', margin: 0 }}>Psiquiatra de Cabecera</p>
              </div>
              <span className="badge badge--success" style={{ fontSize: '9px' }}>Online</span>
            </div>

            {/* Chat Messages */}
            <div style={{
              flex: 1,
              padding: 'var(--space-6)',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-4)',
              backgroundColor: 'var(--neutral-0)'
            }}>
              {chatMessages.length === 0 ? (
                <p style={{ margin: 'auto', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', textAlign: 'center' }}>
                  No hay mensajes anteriores. ¡Escribí un mensaje para iniciar la conversación!
                </p>
              ) : (
                chatMessages.map((msg, index) => {
                  const isMe = msg.remitenteId === currentUser?.id;
                  return (
                    <div
                      key={msg.id || index}
                      style={{
                        alignSelf: isMe ? 'flex-end' : 'flex-start',
                        maxWidth: '70%',
                        backgroundColor: isMe ? 'var(--color-primary)' : 'var(--neutral-100)',
                        color: isMe ? 'white' : 'var(--color-text-primary)',
                        padding: 'var(--space-3) var(--space-4)',
                        borderRadius: isMe ? '12px 12px 0 12px' : '12px 12px 12px 0',
                        boxShadow: 'var(--shadow-sm)',
                        fontSize: 'var(--text-sm)',
                        lineHeight: 'var(--line-height-normal)',
                        wordBreak: 'break-word'
                      }}
                    >
                      {msg.contenido}
                    </div>
                  );
                })
              )}
              <div ref={chatMessagesEndRef} />
            </div>

            {/* Chat Input form */}
            <form
              onSubmit={handleSendPatientChat}
              style={{
                padding: 'var(--space-4) var(--space-6)',
                borderTop: '1px solid var(--color-border)',
                backgroundColor: 'var(--neutral-50)',
                display: 'flex',
                gap: 'var(--space-3)'
              }}
            >
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                placeholder="Escribí un mensaje..."
                style={{
                  flex: 1,
                  padding: 'var(--space-2) var(--space-4)',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--color-border)',
                  fontFamily: 'var(--font-body)',
                  fontSize: 'var(--text-sm)',
                  outline: 'none'
                }}
              />
              <button type="submit" className="btn btn--primary">Enviar</button>
            </form>
          </div>
        )}
      </div>
    )
  }

  return (
    <>
      {showCrisis && <CrisisModal onClose={() => setShowCrisis(false)} />}

      <PublicHeader
        currentUser={currentUser}
        onCrisis={() => setShowCrisis(true)}
        onProLogin={onNavigateToDashboard}
        onLogout={onLogout}
        onGoToDashboard={onGoToDashboard}
      />

      <TrustStrip />

      {/* Hero */}
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero__inner" style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <div className="hero__eyebrow">
            Psicólogos y psiquiatras en Córdoba
          </div>
          <h1 className="hero__title" id="hero-title">
            Encontrá tu espacio<br />para estar <em>tranqui</em>
          </h1>
          <p className="hero__subtitle">
            {currentUser 
              ? `Hola, ${currentUser.nombre}. Buscá y agendá tu sesión online con profesionales certificados.`
              : 'Sesiones online de 50 minutos con profesionales certificados. Agenda, pagá y empezá hoy.'
            }
          </p>

          {currentUser ? (
            <>
              {currentUser.rol === 'PACIENTE' && (
                <div style={{ display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-6)' }}>
                  <button 
                    onClick={() => setPatientTab('search')} 
                    className={`btn ${patientTab === 'search' ? 'btn--primary' : 'btn--secondary'}`}
                    style={{ fontSize: 'var(--text-sm)', padding: 'var(--space-2) var(--space-6)' }}
                  >
                    Buscar Profesionales
                  </button>
                  <button 
                    onClick={() => setPatientTab('portal')} 
                    className={`btn ${patientTab === 'portal' ? 'btn--primary' : 'btn--secondary'}`}
                    style={{ fontSize: 'var(--text-sm)', padding: 'var(--space-2) var(--space-6)' }}
                  >
                    Mi Portal Paciente (Demo)
                  </button>
                </div>
              )}

              {patientTab === 'search' ? (
                <>
                  {/* Search box */}
                  <div
                    className="search-box"
                    role="search"
                    aria-label="Buscar profesionales de salud mental"
                  >
                    <span className="search-box__icon" aria-hidden="true">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 20, height: 20 }}>
                        <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
                      </svg>
                    </span>
                    <input
                      type="search"
                      className="search-box__input"
                      placeholder="¿Qué estás buscando? (ansiedad, depresión, pareja...)"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                      aria-label="Buscar por especialidad o motivo de consulta"
                      id="search-professionals"
                      autoComplete="off"
                    />
                    <div className="search-box__divider" aria-hidden="true" />
                    <button className="search-box__filter" aria-label="Filtrar por disponibilidad">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 16, height: 16 }}>
                        <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" />
                        <line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
                      </svg>
                      Disponibilidad
                    </button>
                  </div>

                  {/* Specialty chips */}
                  <div className="specialty-chips" role="group" aria-label="Filtrar por especialidad">
                    {SPECIALTIES.map((s) => (
                      <button
                        key={s}
                        className={`specialty-chip ${activeSpecialty === s ? 'active' : ''}`}
                        onClick={() => setActiveSpecialty(s)}
                        aria-pressed={activeSpecialty === s}
                        id={`chip-${s.toLowerCase().replace(/\s/g, '-')}`}
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                </>
              ) : (
                renderPatientPortal()
              )}
            </>
          ) : (
            /* Modern, beautifully designed Landing page for unauthenticated users */
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              width: '100%',
              maxWidth: '1200px',
              margin: '0 auto',
              gap: 'var(--space-12)',
              padding: 'var(--space-6) var(--space-4)'
            }}>
              {/* Hero Section */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 'var(--space-8)',
                alignItems: 'center',
                padding: 'var(--space-10) var(--space-6)',
                background: 'linear-gradient(135deg, #f3fdf8 0%, #e8f7f0 100%)',
                borderRadius: 'var(--radius-lg)',
                border: '1px solid #d1fae5',
                width: '100%',
                textAlign: 'left'
              }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
                  <span style={{
                    fontSize: 'var(--text-xs)',
                    fontWeight: 'var(--font-weight-bold)',
                    color: 'var(--color-primary)',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em'
                  }}>
                    tranquilidad en un clic
                  </span>
                  <h2 style={{
                    fontFamily: 'var(--font-heading)',
                    fontSize: 'var(--text-4xl)',
                    fontWeight: 'var(--font-weight-bold)',
                    color: 'var(--green-900)',
                    lineHeight: '1.2',
                    margin: 0
                  }}>
                    Tu espacio digital de salud mental en Córdoba
                  </h2>
                  <p style={{
                    fontSize: 'var(--text-base)',
                    color: 'var(--green-800)',
                    lineHeight: 'var(--line-height-relaxed)',
                    margin: 0
                  }}>
                    Conectamos psicólogos, psiquiatras y pacientes. Gestioná tus turnos, accedé a recetas digitales, seguimiento diario y videoconsultas seguras de 50 minutos.
                  </p>
                  <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-2)' }}>
                    <button
                      className="btn btn--primary"
                      onClick={onNavigateToDashboard}
                      style={{ padding: 'var(--space-3) var(--space-8)', fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semi)' }}
                    >
                      Empezar ahora
                    </button>
                    <a
                      href="#features"
                      className="btn btn--secondary"
                      style={{ padding: 'var(--space-3) var(--space-8)', fontSize: 'var(--text-base)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                    >
                      Saber más
                    </a>
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'center', position: 'relative' }}>
                  <div style={{
                    width: '320px',
                    height: '320px',
                    borderRadius: 'var(--radius-lg)',
                    background: 'url("/logo-tranqui.png") no-repeat center',
                    backgroundSize: 'contain',
                    opacity: 0.85,
                    filter: 'drop-shadow(0px 10px 20px rgba(16, 185, 129, 0.15))'
                  }} />
                </div>
              </div>

              {/* Statistics strip */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 'var(--space-6)',
                width: '100%'
              }}>
                {[
                  { val: '24hs', lbl: 'Disponibilidad de turnos rápidos' },
                  { val: '100%', lbl: 'Profesionales matriculados' },
                  { val: '0%', lbl: 'Comisión en tus cobros' }
                ].map((s, idx) => (
                  <div key={idx} className="card" style={{
                    padding: 'var(--space-6)',
                    textAlign: 'center',
                    border: '1px solid var(--color-border)',
                    boxShadow: 'var(--shadow-sm)'
                  }}>
                    <div style={{ fontSize: 'var(--text-3xl)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-primary)', marginBottom: 'var(--space-1)' }}>{s.val}</div>
                    <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>{s.lbl}</div>
                  </div>
                ))}
              </div>

              {/* Features section */}
              <div id="features" style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
                <div style={{ textAlign: 'center', marginBottom: 'var(--space-4)' }}>
                  <h3 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-2xl)', fontWeight: 'var(--font-weight-bold)' }}>¿Qué ofrece Tranqui?</h3>
                  <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>Diseñado específicamente para las necesidades de salud mental de Córdoba</p>
                </div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: 'var(--space-6)'
                }}>
                  {[
                    { title: 'Agenda & Calendario', desc: 'Organizá tu disponibilidad semanal a pantalla completa y visualizá tus pacientes del día en formato de calendario interactivo.' },
                    { title: 'Seguimiento & Informes', desc: 'Llevá el seguimiento clínico diario de tus pacientes y redactá informes (Evaluativos, Evolutivos, Generales o Finales) con pre-llenado automático.' },
                    { title: 'Recetas & WhatsApp', desc: 'Generá recetas electrónicas oficiales firmadas digitalmente y envialas instantáneamente por email y WhatsApp.' }
                  ].map((f, idx) => (
                    <div key={idx} className="card" style={{
                      padding: 'var(--space-6)',
                      border: '1px solid var(--color-border)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 'var(--space-3)'
                    }}>
                      <div style={{
                        width: '40px',
                        height: '40px',
                        borderRadius: 'var(--radius-md)',
                        backgroundColor: 'var(--green-50)',
                        color: 'var(--color-primary)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 'bold'
                      }}>{idx + 1}</div>
                      <h4 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', margin: 0 }}>{f.title}</h4>
                      <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', margin: 0, lineHeight: 'var(--line-height-relaxed)' }}>{f.desc}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* Results (Only displayed if logged in and search tab is active) */}
      {currentUser && patientTab === 'search' && (
        <section className="results-section" aria-labelledby="results-heading">
          <div className="results-header">
            <h2 id="results-heading" className="results-count">
              {loading ? 'Buscando profesionales...' : (
                <><strong>{filtered.length} profesional{filtered.length !== 1 ? 'es' : ''}</strong> disponible{filtered.length !== 1 ? 's' : ''}</>
              )}
            </h2>
            <div className="results-sort">
              <span>Ordenar por:</span>
              <button className="btn btn--ghost btn--sm" id="btn-sort">Próxima disponibilidad ↓</button>
            </div>
          </div>

          <div className="results-grid" role="list" aria-label="Profesionales disponibles">
            {loading
              ? Array.from({ length: 6 }).map((_, i) => <SkeletonCard key={i} />)
              : filtered.length === 0
                ? (
                  <div className="empty-state" role="status" aria-live="polite">
                    <div className="empty-state__emoji" aria-hidden="true">🌱</div>
                    <h3 className="empty-state__title">No encontramos resultados</h3>
                    <p className="empty-state__body">
                      No hay profesionales con esa especialidad disponibles ahora.
                      Probá con otro término o explorá todas las especialidades.
                    </p>
                    <button
                      className="btn btn--secondary"
                      onClick={() => { setQuery(''); setActiveSpecialty('Todos') }}
                      id="btn-clear-filters"
                    >
                      Ver todos los profesionales
                    </button>
                  </div>
                )
                : filtered.map((pro) => (
                  <div role="listitem" key={pro.id}>
                    <ProCard pro={pro} onBook={handleBook} onChat={handleStartChat} />
                  </div>
                ))
            }
          </div>
        </section>
      )}
    </>
  )
}
