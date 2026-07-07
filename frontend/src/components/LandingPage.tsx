import { useState, useMemo, useEffect, useRef } from 'react'
import '../styles/landing.css'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'
import { useAlert } from '../context/AlertContext'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

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
  fotoUrl?: string
  online: boolean
  color: string
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
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
          style={{ background: pro.fotoUrl ? 'none' : pro.color, color: 'var(--green-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}
          aria-hidden="true"
        >
          {pro.fotoUrl ? (
            <img src={pro.fotoUrl} alt={pro.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            pro.initials
          )}
          {pro.online && <span className="pro-card__online-dot" aria-label="Disponible ahora" />}
        </div>
        <div className="pro-card__info">
          <div className="pro-card__name">{pro.name}</div>
          <div className="pro-card__specialty">{pro.degree} · {pro.specialty}</div>
          <span className="pro-card__matricula">{pro.matricula} ✓</span>
          <div style={{ display: 'flex', gap: '4px', marginTop: '4px', flexWrap: 'wrap' }}>
            {pro.ofreceOnline && <span className="badge badge--success" style={{ fontSize: '9px', padding: '2px 6px', textTransform: 'none', backgroundColor: '#e0f2fe', color: '#0369a1', border: '1px solid #bae6fd' }}>💻 Online</span>}
            {pro.ofrecePresencial && <span className="badge badge--success" style={{ fontSize: '9px', padding: '2px 6px', textTransform: 'none', backgroundColor: '#fef3c7', color: '#b45309', border: '1px solid #fde68a' }}>🏢 Presencial</span>}
          </div>
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
// ── Public Header ──────────────────────────────────────────────
function PublicHeader({ 
  currentUser, 
  onCrisis, 
  onProLogin,
  onLogout,
  onGoToDashboard,
  onOpenMyAppointments,
  onOpenMyClinicalHistory,
  onOpenHelp
}: { 
  currentUser: any; 
  onCrisis: () => void; 
  onProLogin: () => void;
  onLogout: () => void;
  onGoToDashboard: () => void;
  onOpenMyAppointments?: () => void;
  onOpenMyClinicalHistory?: () => void;
  onOpenHelp?: () => void;
}) {
  const isDoctor = currentUser?.rol === 'PSIQUIATRA' || currentUser?.rol === 'MEDICO'
  const [showDropdown, setShowDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  return (
    <header className="public-header" role="banner" style={{ position: 'relative', zIndex: 1000 }}>
      <div className="public-header__inner">
        <a href="/" className="public-header__logo" aria-label="Tranqui App - Inicio">
          tranqui
        </a>
        <span className="public-header__tagline">por Tranqui Neurociencias</span>
        <div className="public-header__spacer" />
        <div className="public-header__actions" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          
          {/* Help Button */}
          <button
            className="btn btn--ghost btn--sm"
            onClick={onOpenHelp}
            style={{ fontSize: 'var(--text-xs)', padding: 'var(--space-2) var(--space-4)', display: 'flex', alignItems: 'center', gap: '4px', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}
          >
            ❓ Ayuda / FAQ
          </button>

          <button
            className="btn-crisis"
            onClick={onCrisis}
            id="btn-crisis-trigger"
          >
            <span className="btn-crisis__dot" aria-hidden="true" />
            Ayuda urgente
          </button>

          {currentUser ? (
            isDoctor ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-medium)' }}>
                  {currentUser.nombre} (Médico)
                </span>
                <button 
                  className="btn btn--secondary btn--sm" 
                  onClick={onGoToDashboard}
                  style={{ padding: 'var(--space-2) var(--space-4)', fontSize: 'var(--text-xs)' }}
                >
                  Panel Profesional
                </button>
                <button 
                  className="btn btn--ghost btn--sm" 
                  onClick={onLogout}
                  style={{ padding: 'var(--space-2) var(--space-4)', fontSize: 'var(--text-xs)' }}
                >
                  Cerrar sesión
                </button>
              </div>
            ) : (
              /* Patient Profile Dropdown / ComboBox */
              <div ref={dropdownRef} style={{ position: 'relative' }}>
                <button
                  onClick={() => setShowDropdown(!showDropdown)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--space-2)',
                    background: 'none',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-lg)',
                    padding: 'var(--space-2) var(--space-3)',
                    cursor: 'pointer',
                    fontSize: 'var(--text-sm)',
                    fontWeight: 'bold',
                    color: 'var(--color-text-primary)'
                  }}
                >
                  <div style={{
                    width: '24px',
                    height: '24px',
                    borderRadius: '50%',
                    backgroundColor: 'var(--color-primary)',
                    color: 'white',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '11px',
                    fontWeight: 'bold'
                  }}>
                    {currentUser.nombre.substring(0, 2).toUpperCase()}
                  </div>
                  <span>{currentUser.nombre}</span>
                  <span style={{ fontSize: '10px' }}>▼</span>
                </button>

                {showDropdown && (
                  <div style={{
                    position: 'absolute',
                    top: '40px',
                    right: 0,
                    backgroundColor: 'white',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    boxShadow: 'var(--shadow-lg)',
                    width: '200px',
                    display: 'flex',
                    flexDirection: 'column',
                    zIndex: 1001,
                    overflow: 'hidden'
                  }}>
                    <button
                      onClick={() => {
                        setShowDropdown(false);
                        onOpenMyAppointments?.();
                      }}
                      style={{
                        padding: 'var(--space-3) var(--space-4)',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        borderBottom: '1px solid #f0f2f5',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: 'var(--color-text-primary)'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      📅 Mis Turnos
                    </button>
                    <button
                      onClick={() => {
                        setShowDropdown(false);
                        onOpenMyClinicalHistory?.();
                      }}
                      style={{
                        padding: 'var(--space-3) var(--space-4)',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        borderBottom: '1px solid #f0f2f5',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: 'var(--color-text-primary)'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      🩺 Mi Historia Clínica
                    </button>
                    <button
                      onClick={() => {
                        setShowDropdown(false);
                        onLogout();
                      }}
                      style={{
                        padding: 'var(--space-3) var(--space-4)',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: 'var(--color-danger)'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#fdf2f2'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      🚪 Cerrar sesión
                    </button>
                  </div>
                )}
              </div>
            )
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
  fotoUrl?: string
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
  const { showAlert } = useAlert()
  const [query, setQuery] = useState('')
  const [activeSpecialty, setActiveSpecialty] = useState('Todos')
  const [activeModality, setActiveModality] = useState<'Todos' | 'Online' | 'Presencial'>('Todos')
  const [professionals, setProfessionals] = useState<Professional[]>([])
  const [loading, setLoading] = useState(true)
  const [showCrisis, setShowCrisis] = useState(false)

  // Patient Portal states
  const [myAppointments, setMyAppointments] = useState<any[]>([])
  const [myReports, setMyReports] = useState<any[]>([])
  const [loadingPortal, setLoadingPortal] = useState(false)
  const [showAppointmentsModal, setShowAppointmentsModal] = useState(false)
  const [showReportsModal, setShowReportsModal] = useState(false)
  const [showHelpModal, setShowHelpModal] = useState(false)

  // Portal inner tabs
  const [activeDoctorId, setActiveDoctorId] = useState<number | null>(null)
  
  // Custom states for availability filter and WhatsApp chat
  const [filterAvailableOnly, setFilterAvailableOnly] = useState(false)
  const [showFloatingChat, setShowFloatingChat] = useState(false)
  const [chatSubView, setChatSubView] = useState<'list' | 'chat'>('list')
  const [chatChannels, setChatChannels] = useState<any[]>([])

  useEffect(() => {
    if (currentUser && currentUser.rol === 'PACIENTE' && showFloatingChat) {
      api.getChatCanales()
        .then((res: any) => {
          setChatChannels(res || []);
        })
        .catch(err => console.error("Error loading chat channels:", err));
    }
  }, [currentUser, showFloatingChat])

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
    if (showFloatingChat && chatSubView === 'chat') {
      chatMessagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [chatMessages, showFloatingChat, chatSubView])



  // Patient WebSocket Live Notifications Handler
  useEffect(() => {
    if (loading || !currentUser || currentUser.rol !== 'PACIENTE') return

    let client: Client | null = null;
    
    if (currentUser.id) {
      const socket = new SockJS('http://localhost:8081/ws-tranqui')
      client = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        onConnect: () => {
          console.log("WebSocket de Notificaciones conectado para Paciente ID:", currentUser.id)
          client?.subscribe(`/topic/notificaciones/${currentUser.id}`, (stompMsg) => {
            try {
              const data = JSON.parse(stompMsg.body)
              console.log("Paciente WebSocket notification received:", data)
              
              // Refresh patient portal data in real time
              Promise.all([
                api.getMisTurnos(),
                api.getMisInformes()
              ]).then(([turnos, informes]) => {
                setMyAppointments(turnos || [])
                setMyReports(informes || [])
              }).catch((err) => console.error("Error refreshing patient portal data via WS:", err))

            } catch (e) {
              console.error("Error processing websocket notification for patient:", e)
            }
          })
        },
        debug: (str) => {
          console.log('Patient WS Debug:', str)
        }
      })
      client.activate()
    }

    return () => {
      client?.deactivate()
    }
  }, [currentUser, loading])

  useEffect(() => {
    setLoading(true)
    api.getMedicos()
      .then((res: any) => {
        const mapped = res.map((m: any) => ({
          ...m,
          id: String(m.id),
          nextSlot: m.nextSlot || "16:00",
          nextSlotDay: m.nextSlotDay || "Hoy",
          online: m.online !== undefined ? m.online : true,
          ofreceOnline: m.ofreceOnline !== undefined ? m.ofreceOnline : true,
          ofrecePresencial: m.ofrecePresencial !== undefined ? m.ofrecePresencial : false,
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

  const refreshPatientData = () => {
    if (currentUser && currentUser.rol === 'PACIENTE') {
      setLoadingPortal(true)
      Promise.all([
        api.getMisTurnos(),
        api.getMisInformes()
      ])
        .then(([turnos, informes]) => {
          setMyAppointments(turnos || [])
          setMyReports(informes || [])
        })
        .catch((err) => console.error("Error loading patient data:", err))
        .finally(() => setLoadingPortal(false))
    }
  }

  useEffect(() => {
    refreshPatientData()
  }, [currentUser])

  const handleCancelAppointmentByPatient = (turnoId: number) => {
    if (window.confirm("¿Estás seguro de que deseas cancelar este turno?")) {
      api.cancelarTurno(turnoId)
        .then(() => {
          showAlert("Turno cancelado con éxito.", "success")
          refreshPatientData()
        })
        .catch(err => {
          console.error(err)
          showAlert("Error al cancelar el turno.", "error")
        })
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

      const matchesAvailability = !filterAvailableOnly || pro.online === true

      const matchesModality = activeModality === 'Todos' ||
        (activeModality === 'Online' && pro.ofreceOnline) ||
        (activeModality === 'Presencial' && pro.ofrecePresencial)

      return matchesQuery && matchesSpecialty && matchesAvailability && matchesModality
    })
  }, [query, activeSpecialty, activeModality, filterAvailableOnly, professionals])

  const handleBook = (pro: Professional) => {
    if (!currentUser) {
      showAlert("Para reservar un turno, debes iniciar sesión primero.", "warning")
      onNavigateToDashboard()
      return
    }
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
        fotoUrl: pro.fotoUrl
      })
    }
  }

  const handleStartChat = (pro: Professional) => {
    if (!currentUser) {
      showAlert("Para chatear con un profesional, debes iniciar sesión primero.", "warning")
      onNavigateToDashboard()
      return
    }
    setActiveDoctorId(Number(pro.id))
    setChatSubView('chat')
    setShowFloatingChat(true)
  }

  const handleSendPatientChat = (e: React.FormEvent) => {
    e.preventDefault()
    if (!chatInput.trim() || !activeDoctorId) return
    const sent = sendChatMessage(activeDoctorId, chatInput.trim())
    if (sent) {
      setChatInput('')
    }
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
        onOpenMyAppointments={() => setShowAppointmentsModal(true)}
        onOpenMyClinicalHistory={() => setShowReportsModal(true)}
        onOpenHelp={() => setShowHelpModal(true)}
      />

      <TrustStrip />

      {/* Hero with Search box directly visible for everyone */}
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
              : 'Sesiones online de 50 minutos con profesionales certificados. Iniciá sesión para agendar tu consulta.'
            }
          </p>

          {/* Search box directly in Hero */}
          <div
            className="search-box"
            role="search"
            aria-label="Buscar profesionales de salud mental"
            style={{ width: '100%', maxWidth: '600px', margin: 'var(--space-4) auto var(--space-2)' }}
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
            <button 
              onClick={() => setFilterAvailableOnly(!filterAvailableOnly)}
              className="search-box__filter" 
              style={{
                backgroundColor: filterAvailableOnly ? 'var(--green-100)' : 'transparent',
                borderColor: filterAvailableOnly ? 'var(--color-primary)' : 'var(--color-border)',
                color: filterAvailableOnly ? 'var(--color-primary)' : 'var(--color-text-primary)',
                fontWeight: filterAvailableOnly ? 'bold' : 'normal',
              }}
              aria-label="Filtrar por disponibilidad"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 16, height: 16 }}>
                <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              {filterAvailableOnly ? 'Disponibles ahora ✓' : 'Disponibilidad'}
            </button>
          </div>

          {/* Modality Filter segmented buttons */}
          <div style={{ display: 'flex', gap: 'var(--space-2)', margin: 'var(--space-3) auto var(--space-1)' }}>
            {(['Todos', 'Online', 'Presencial'] as const).map((mode) => {
              const isActive = activeModality === mode;
              const label = mode === 'Todos' ? 'Todas las modalidades' : mode === 'Online' ? '💻 Citas Online' : '🏢 Citas Presenciales';
              return (
                <button
                  key={mode}
                  onClick={() => setActiveModality(mode)}
                  style={{
                    padding: 'var(--space-2) var(--space-4)',
                    borderRadius: '20px',
                    border: '1px solid',
                    borderColor: isActive ? 'var(--color-primary)' : 'var(--color-border)',
                    backgroundColor: isActive ? 'var(--color-primary)' : 'white',
                    color: isActive ? 'white' : 'var(--color-text-primary)',
                    fontSize: 'var(--text-xs)',
                    fontWeight: isActive ? 'bold' : 'normal',
                    cursor: 'pointer',
                    boxShadow: isActive ? 'var(--shadow-sm)' : 'none',
                    transition: 'all 0.2s ease'
                  }}
                >
                  {label}
                </button>
              );
            })}
          </div>

          {/* Specialty chips */}
          <div className="specialty-chips" role="group" aria-label="Filtrar por especialidad" style={{ margin: 'var(--space-2) auto var(--space-6)' }}>
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
        </div>
      </section>

      {/* Today's Appointments Reminder Card on Home Page (directly visible) */}
      {currentUser && currentUser.rol === 'PACIENTE' && (() => {
        const isToday = (dateStr: string) => {
          if (!dateStr) return false;
          const todayStr = new Date().toISOString().split('T')[0];
          const cleanDateStr = dateStr.trim();
          if (cleanDateStr === todayStr) return true;
          const todayLocal = new Date().toLocaleDateString('es-AR');
          if (cleanDateStr === todayLocal) return true;
          const d = new Date();
          const day = String(d.getDate()).padStart(2, '0');
          const month = String(d.getMonth() + 1).padStart(2, '0');
          const year = String(d.getFullYear());
          const f1 = `${day}/${month}/${year}`;
          const f2 = `${year}-${month}-${day}`;
          return cleanDateStr === f1 || cleanDateStr === f2 || cleanDateStr.includes(f1) || cleanDateStr.includes(f2);
        };
        const todayAppointments = myAppointments.filter(appt => isToday(appt.fecha));
        
        if (todayAppointments.length === 0) return null;

        return (
          <div style={{ width: '100%', maxWidth: '1200px', margin: 'var(--space-4) auto', padding: '0 var(--space-6)' }}>
            <div className="card" style={{
              padding: 'var(--space-5) var(--space-6)',
              backgroundColor: '#ecfdf5',
              border: '1px solid var(--color-primary)',
              borderLeft: '5px solid var(--color-primary)',
              borderRadius: 'var(--radius-lg)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 'var(--space-4)',
              boxShadow: 'var(--shadow-md)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
                <span style={{ fontSize: '24px' }}>⏰</span>
                <div style={{ textAlign: 'left' }}>
                  <h4 style={{ margin: 0, fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-primary)' }}>
                    TURNOS DE HOY
                  </h4>
                  <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    Recordatorio: Tenés turno programado para hoy. Podés unirte directamente a la llamada usando el botón.
                  </p>
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', minWidth: '220px' }}>
                {todayAppointments.map(appt => (
                  <div key={appt.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-3)', backgroundColor: '#ffffff', padding: 'var(--space-2) var(--space-3)', borderRadius: 'var(--radius-md)', border: '1px solid #a7f3d0' }}>
                    <span style={{ fontSize: '11px', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                      {appt.hour} hs
                    </span>
                    {appt.meetLink && appt.status === 'confirmed' ? (
                      <a 
                        href={appt.meetLink} 
                        target="_blank" 
                        rel="noopener noreferrer" 
                        className="btn btn--primary btn--sm" 
                        style={{ fontSize: '9px', padding: '2px 8px', display: 'flex', alignItems: 'center', gap: '3px' }}
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 10, height: 10 }}><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" ry="2" /></svg>
                        Unirse
                      </a>
                    ) : appt.checkoutUrl ? (
                      <a 
                        href={appt.checkoutUrl} 
                        target="_blank" 
                        rel="noopener noreferrer" 
                        className="btn" 
                        style={{ 
                          fontSize: '9px', 
                          padding: '4px 10px', 
                          display: 'flex', 
                          alignItems: 'center', 
                          gap: '3px', 
                          backgroundColor: '#009fe3', 
                          color: 'white', 
                          borderColor: '#009fe3', 
                          fontWeight: 'bold',
                          textDecoration: 'none',
                          borderRadius: 'var(--radius-md)',
                          boxShadow: '0 2px 4px rgba(0, 158, 227, 0.15)'
                        }}
                      >
                        💳 Pagar
                      </a>
                    ) : (
                      <span style={{ fontSize: '9px', color: 'var(--color-warning)', fontWeight: 'bold' }}>Esperando pago</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        );
      })()}

      {/* Results (Displayed to everyone) */}
      {(!currentUser || currentUser.rol === 'PACIENTE') && (
        <section className="results-section" aria-labelledby="results-heading" style={{ maxWidth: '1200px', margin: '0 auto var(--space-8)', padding: '0 var(--space-6)' }}>
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

      {/* Mis Turnos Modal */}
      {showAppointmentsModal && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: 'var(--space-4)'
        }}>
          <div className="card" style={{
            maxWidth: '600px',
            width: '100%',
            maxHeight: '85vh',
            overflowY: 'auto',
            position: 'relative',
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-4)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>📅 Mis Turnos Reservados</h3>
              <button onClick={() => setShowAppointmentsModal(false)} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}>✕</button>
            </div>
            {loadingPortal ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}><div className="checkout-spinner" style={{ margin: 'auto' }} /></div>
            ) : myAppointments.length === 0 ? (
              <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-4)' }}>No tenés turnos programados.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {myAppointments.map(appt => {
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
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                          <span className={`badge ${isConfirmed ? 'badge--success' : 'badge--warning'}`} style={{ fontSize: '9px' }}>
                            {isConfirmed ? 'Confirmado' : 'Pendiente'}
                          </span>
                          {appt.status !== 'completed' && (
                            <button 
                              onClick={() => handleCancelAppointmentByPatient(appt.id)}
                              style={{
                                border: 'none',
                                background: 'none',
                                cursor: 'pointer',
                                color: 'var(--color-danger)',
                                fontSize: '12px',
                                fontWeight: 'bold'
                              }}
                              title="Cancelar Turno"
                            >
                              ✕ Cancelar
                            </button>
                          )}
                        </div>
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
                          Unirse a la videollamada
                        </a>
                      )}
                      {!isConfirmed && appt.checkoutUrl && (
                        <a 
                          href={appt.checkoutUrl} 
                          target="_blank" 
                          rel="noopener noreferrer" 
                          className="btn" 
                          style={{ 
                            fontSize: '11px', 
                            padding: 'var(--space-2) var(--space-5)', 
                            width: 'fit-content', 
                            display: 'flex', 
                            gap: '6px', 
                            alignItems: 'center', 
                            marginTop: 'var(--space-1)', 
                            textDecoration: 'none', 
                            backgroundColor: '#009fe3', 
                            color: 'white', 
                            borderColor: '#009fe3', 
                            fontWeight: 'bold',
                            borderRadius: 'var(--radius-md)',
                            boxShadow: '0 2px 4px rgba(0,158,227,0.15)'
                          }}
                        >
                          💳 Pagar Turno
                        </a>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Mi Historia Clinica Modal */}
      {showReportsModal && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: 'var(--space-4)'
        }}>
          <div className="card" style={{
            maxWidth: '600px',
            width: '100%',
            maxHeight: '85vh',
            overflowY: 'auto',
            position: 'relative',
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-4)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>🩺 Mi Historia Clínica / Informes</h3>
              <button onClick={() => setShowReportsModal(false)} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}>✕</button>
            </div>
            {loadingPortal ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}><div className="checkout-spinner" style={{ margin: 'auto' }} /></div>
            ) : myReports.length === 0 ? (
              <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-4)' }}>No tenés informes emitidos.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {myReports.map(r => (
                  <div key={r.id} style={{
                    padding: 'var(--space-4)',
                    borderRadius: 'var(--radius-md)',
                    backgroundColor: 'var(--color-surface)',
                    border: '1px solid var(--color-border)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 'var(--space-2)'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span className="badge badge--success" style={{ textTransform: 'uppercase', fontSize: '9px' }}>
                        {r.tipoInforme.replace('_', ' ')}
                      </span>
                      <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                        {r.fecha}
                      </span>
                    </div>
                    {r.planTrabajo && (
                      <div style={{ fontSize: '11px' }}>
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

      {/* Ayuda / FAQ Modal */}
      {showHelpModal && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: 'var(--space-4)'
        }}>
          <div className="card" style={{
            maxWidth: '600px',
            width: '100%',
            maxHeight: '85vh',
            overflowY: 'auto',
            position: 'relative',
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-4)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>❓ Ayuda y Preguntas Frecuentes (FAQ)</h3>
              <button onClick={() => setShowHelpModal(false)} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}>✕</button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', textAlign: 'left', fontSize: 'var(--text-sm)', lineHeight: '1.5' }}>
              <div>
                <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>1. ¿Cómo me registro en la aplicación?</h4>
                <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
                  El registro es sumamente sencillo. Podés iniciar sesión directamente con tu cuenta de Google haciendo clic en el botón <strong>"Iniciar sesión"</strong> en la parte superior derecha. Tu cuenta de paciente se creará automáticamente.
                </p>
              </div>
              <div>
                <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>2. ¿Cómo solicitar un turno?</h4>
                <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
                  Una vez que hayas iniciado sesión, navegá en la lista de profesionales en la página de inicio. Hacé clic en la tarjeta del profesional con el que quieras atenderte, seleccioná el día y horario disponible, completá los datos del formulario y hacé clic en "Confirmar reserva".
                </p>
              </div>
              <div>
                <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>3. ¿Cómo realizar el pago del turno?</h4>
                <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
                  Al reservar el turno, el sistema te redirigirá a Mercado Pago para abonar de forma segura. Si cerrás la pestaña sin abonar, podés ir a <strong>"Mis Turnos"</strong> desde tu menú de perfil en el Header y hacer clic en el botón azul <strong>"Pagar Turno"</strong> en cualquier momento.
                </p>
              </div>
              <div>
                <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>4. ¿Cómo cancelar un turno?</h4>
                <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
                  Si necesitás cancelar una reserva, abrí tu menú de perfil (haciendo clic en tu nombre en la parte superior derecha), seleccioná <strong>"Mis Turnos"</strong>, ubicá el turno correspondiente y hacé clic en el botón <strong>"✕ Cancelar"</strong>.
                </p>
              </div>
              <div>
                <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>5. ¿Cómo unirse a la videollamada?</h4>
                <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
                  Una vez que el turno esté pagado y confirmado, se generará un link de Google Meet. Podés unirte directamente haciendo clic en el botón <strong>"Unirse a la llamada"</strong> en tu listado de turnos de hoy en la página de inicio, o en el modal <strong>"Mis Turnos"</strong> de tu perfil.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
      {/* WhatsApp Floating Chat Widget */}
      {currentUser && currentUser.rol === 'PACIENTE' && (
        <>
          {/* Floating Action Button (FAB) */}
          <button
            onClick={() => {
              setShowFloatingChat(!showFloatingChat);
              if (!showFloatingChat && !activeDoctorId && professionals.length > 0) {
                // Default to list view
                setChatSubView('list');
              }
            }}
            className="whatsapp-fab"
            style={{
              position: 'fixed',
              bottom: '24px',
              right: '24px',
              width: '60px',
              height: '60px',
              borderRadius: '50%',
              backgroundColor: '#25D366',
              border: 'none',
              boxShadow: '0 4px 16px rgba(0, 0, 0, 0.25)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              zIndex: 1000,
              transition: 'transform 0.2s ease, background-color 0.2s ease',
              color: 'white'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'scale(1.08)';
              e.currentTarget.style.backgroundColor = '#20ba5a';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'scale(1)';
              e.currentTarget.style.backgroundColor = '#25D366';
            }}
            aria-label="Abrir chat con profesionales"
          >
            {/* WhatsApp Icon */}
            <svg viewBox="0 0 24 24" fill="currentColor" style={{ width: 32, height: 32 }}>
              <path d="M12.012 2c-5.506 0-9.988 4.482-9.988 9.988 0 1.76.459 3.414 1.261 4.86L2 22l5.304-1.392a9.92 9.92 0 0 0 4.708 1.18c5.507 0 9.988-4.482 9.988-9.988C22 6.482 17.519 2 12.012 2zm0 17.15c-1.572 0-3.111-.422-4.46-1.222l-.32-.19-3.32.871.887-3.238-.208-.332A8.106 8.106 0 0 1 3.82 11.99c0-4.483 3.65-8.132 8.192-8.132 4.542 0 8.192 3.65 8.192 8.133 0 4.483-3.65 8.132-8.192 8.132z" />
              <path d="M15.932 13.918c-.216-.108-1.282-.633-1.48-.705-.198-.072-.342-.108-.487.108-.144.216-.558.704-.683.848-.126.144-.252.162-.468.054a5.9 5.9 0 0 1-1.737-1.071c-.559-.499-.937-1.115-1.047-1.303-.109-.188-.012-.29.078-.396.082-.095.18-.216.27-.324.09-.108.12-.18.18-.306.06-.126.03-.234-.015-.342-.045-.108-.432-1.04-.594-1.429-.158-.383-.33-.33-.487-.33-.126 0-.27 0-.414.018a1.69 1.69 0 0 0-1.127.534c-.382.396-.983.968-.983 2.361 0 1.393 1.013 2.738 1.155 2.928.143.189 1.994 3.045 4.831 4.269.675.291 1.202.465 1.613.596.677.216 1.293.185 1.78.112.544-.081 1.66-.679 1.895-1.336.236-.657.236-1.221.166-1.336-.072-.115-.252-.18-.468-.288z" />
            </svg>
          </button>

          {/* Chat Window */}
          {showFloatingChat && (
            <div
              className="whatsapp-window"
              style={{
                position: 'fixed',
                bottom: '96px',
                right: '24px',
                width: '380px',
                height: '520px',
                borderRadius: '16px',
                boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
                backgroundColor: '#efeae2', // WhatsApp beige background
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
                zIndex: 1000,
                fontFamily: 'var(--font-body)'
              }}
            >
              {/* Header */}
              <div
                style={{
                  backgroundColor: '#008069', // WhatsApp green
                  color: 'white',
                  padding: 'var(--space-3) var(--space-4)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)'
                }}
              >
                {chatSubView === 'chat' && (
                  <button
                    onClick={() => setChatSubView('list')}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'white',
                      fontSize: '18px',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      padding: '4px'
                    }}
                    aria-label="Volver a la lista de chats"
                  >
                    ←
                  </button>
                )}

                {chatSubView === 'chat' && activeDoctorId ? (
                  // Chat view header
                  (() => {
                    const activeDoc = professionals.find(p => Number(p.id) === activeDoctorId) || {
                      name: "María Paula Rossi",
                      specialty: "Psiquiatra de Cabecera"
                    };
                    const initials = activeDoc.name.split(' ').map(n => n[0]).join('').slice(0, 2);
                    return (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flex: 1 }}>
                        <div style={{
                          width: '36px',
                          height: '36px',
                          borderRadius: '50%',
                          backgroundColor: '#128c7e',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 'bold',
                          fontSize: '13px'
                        }}>
                          {initials}
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span style={{ fontSize: '13px', fontWeight: 'bold' }}>{activeDoc.name}</span>
                          <span style={{ fontSize: '10px', opacity: 0.85 }}>Disponible</span>
                        </div>
                      </div>
                    )
                  })()
                ) : (
                  // List view header
                  <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontSize: '14px', fontWeight: 'bold' }}>Mis Chats</span>
                    <span style={{ fontSize: '10px', opacity: 0.85 }}>Tranqui App · WhatsApp Style</span>
                  </div>
                )}

                <button
                  onClick={() => setShowFloatingChat(false)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'white',
                    fontSize: '18px',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    padding: '4px'
                  }}
                  aria-label="Cerrar chat"
                >
                  ✕
                </button>
              </div>

              {/* Body */}
              {chatSubView === 'list' ? (
                // Chat List View
                <div style={{ flex: 1, overflowY: 'auto', backgroundColor: '#ffffff' }}>
                  {chatChannels.length === 0 ? (
                    <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-8)', fontSize: '12px' }}>
                      No tenés chats activos aún. ¡Iniciá un chat desde el perfil de un profesional!
                    </p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      {chatChannels.map((chan) => {
                        const initials = chan.nombre.split(' ').map((n: string) => n[0]).join('').slice(0, 2);
                        return (
                          <div
                            key={chan.id}
                            onClick={() => {
                              setActiveDoctorId(Number(chan.id));
                              setChatSubView('chat');
                            }}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 'var(--space-3)',
                              padding: 'var(--space-3) var(--space-4)',
                              borderBottom: '1px solid #f0f2f5',
                              cursor: 'pointer',
                              transition: 'background-color 0.15s ease'
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f5f6f6'}
                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                          >
                            <div style={{
                              width: '40px',
                              height: '40px',
                              borderRadius: '50%',
                              backgroundColor: 'var(--green-50)',
                              color: 'var(--color-primary)',
                              border: '1px solid var(--green-100)',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontWeight: 'bold',
                              fontSize: '13px'
                            }}>
                              {initials}
                            </div>
                            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', textAlign: 'left' }}>
                              <span style={{ fontSize: '13px', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>{chan.nombre}</span>
                              <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>{chan.email}</span>
                            </div>
                            <span style={{ fontSize: '10px', color: 'var(--color-primary)', fontWeight: 'bold' }}>Chat →</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              ) : (
                // Chat conversation view
                <>
                  {/* Messages Area */}
                  <div style={{
                    flex: 1,
                    padding: 'var(--space-4)',
                    overflowY: 'auto',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 'var(--space-3)'
                  }}>
                    {chatMessages.length === 0 ? (
                      <div style={{
                        margin: 'auto',
                        backgroundColor: '#ffeec1',
                        padding: '8px 12px',
                        borderRadius: '8px',
                        fontSize: '11px',
                        color: '#6b5315',
                        textAlign: 'center',
                        maxWidth: '85%',
                        boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
                      }}>
                        🔒 Las conversaciones en Tranqui están cifradas. Escribí un mensaje para iniciar la consulta.
                      </div>
                    ) : (
                      chatMessages.map((msg, index) => {
                        const isMe = msg.remitenteId === currentUser?.id;
                        return (
                          <div
                            key={msg.id || index}
                            style={{
                              alignSelf: isMe ? 'flex-end' : 'flex-start',
                              maxWidth: '75%',
                              backgroundColor: isMe ? '#d9fdd3' : '#f0f2f5', // WhatsApp bubbles
                              color: 'black',
                              padding: '6px 10px',
                              borderRadius: isMe ? '8px 8px 0 8px' : '8px 8px 8px 0',
                              boxShadow: '0 1px 1px rgba(0,0,0,0.1)',
                              fontSize: '12px',
                              lineHeight: '1.4',
                              wordBreak: 'break-word',
                              position: 'relative',
                              textAlign: 'left'
                            }}
                          >
                            {msg.contenido}
                          </div>
                        );
                      })
                    )}
                    <div ref={chatMessagesEndRef} />
                  </div>

                  {/* Input form */}
                  <form
                    onSubmit={handleSendPatientChat}
                    style={{
                      padding: '8px 12px',
                      backgroundColor: '#f0f2f5',
                      display: 'flex',
                      gap: 'var(--space-2)',
                      alignItems: 'center'
                    }}
                  >
                    <input
                      type="text"
                      value={chatInput}
                      onChange={(e) => setChatInput(e.target.value)}
                      placeholder="Escribí un mensaje..."
                      style={{
                        flex: 1,
                        padding: '8px 16px',
                        borderRadius: '20px',
                        border: 'none',
                        fontSize: '12px',
                        outline: 'none',
                        backgroundColor: '#ffffff'
                      }}
                    />
                    <button
                      type="submit"
                      style={{
                        backgroundColor: '#008069',
                        color: 'white',
                        border: 'none',
                        borderRadius: '50%',
                        width: '32px',
                        height: '32px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer'
                      }}
                      aria-label="Enviar"
                    >
                      ➤
                    </button>
                  </form>
                </>
              )}
            </div>
          )}
        </>
      )}
    </>
  )
}
