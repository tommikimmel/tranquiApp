import { useState, useMemo, useEffect, useRef } from 'react'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import '../styles/landing.css'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'
import { useAlert } from '../context/AlertContext'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { downloadReportPDF } from '../utils/pdfGenerator'

// ── Icons ────────────────────────────────────────────────────────
function IconCheck({ size = 12 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}

function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function IconSend({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
    </svg>
  )
}

function IconCalendar({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  )
}

function IconHelp({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <circle cx="12" cy="12" r="10" />
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

// ── Types ──────────────────────────────────────────────────────
interface Tariff {
  label: string
  price: number
}

interface RedesSociales {
  instagram?: string
  facebook?: string
  linkedin?: string
  sitioWeb?: string
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
  domicilioAtencion?: string
  domicilioLat?: number | null
  domicilioLng?: number | null
  descripcionPerfil?: string
  pacientesAtiende?: string[]
  institucionFormacion?: string
  aniosExperiencia?: number | null
  experiencia?: string
  redesSociales?: RedesSociales
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
function ProCard({ pro, onBook, onChat, currentUser, availabilityDateLabel, availabilityCount }: { pro: Professional; onBook: (p: Professional) => void; onChat: (p: Professional) => void; currentUser: any; availabilityDateLabel?: string | null; availabilityCount?: number }) {
  const proBio = pro.descripcionPerfil?.trim() || pro.experiencia?.trim() || (
    pro.specialty.includes('Psiquiatra') || pro.specialty.includes('Psiquiatría')
      ? "Médico especialista con enfoque integral combinando psicoterapia y abordaje farmacológico de forma personalizada."
      : "Profesional con enfoque clínico integral y seguimiento cercano del paciente para tratamientos de ansiedad, depresión y regulación emocional."
  );

  return (
    <article
      className="doc-card"
      role="article"
      aria-label={`${pro.name}, ${pro.specialty}`}
      onClick={() => onBook(pro)}
    >
      <div className="doc-row">
        <div
          className="avatar"
          style={{ background: pro.fotoUrl ? 'none' : pro.color, overflow: 'hidden' }}
          aria-hidden="true"
        >
          {pro.fotoUrl ? (
            <img src={pro.fotoUrl} alt={pro.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            pro.initials
          )}
        </div>

        <div className="doc-info">
          <div className="doc-name-row">
            <span className="doc-name sora">{pro.name}</span>
            <span className="badge-mn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><polyline points="20 6 9 17 4 12" /></svg>
              {pro.matricula}
            </span>
          </div>
          <div className="doc-spec">{pro.degree} · {pro.specialty}</div>

          <p className="doc-bio">{proBio}</p>
          
          <div className="doc-tags">
            {pro.ofreceOnline && <span className="tag online">Online</span>}
            {pro.ofrecePresencial && <span className="tag presencial">Presencial</span>}
            {pro.nextSlot && (
              <span className="tag next">Próximo turno: {pro.nextSlotDay.toLowerCase()} {pro.nextSlot} hs</span>
            )}
          </div>
        </div>

        <div className="doc-side">
          <div className="price">
            <div className="label">Consulta</div>
            <div className="val">Desde ${pro.price.toLocaleString('es-AR')}</div>
            <div className="per">50 min · {pro.ofreceOnline ? 'Online' : 'Presencial'}</div>
          </div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <button
              className="btn btn--secondary btn--sm"
              onClick={(e) => { e.stopPropagation(); onChat(pro) }}
              style={{ whiteSpace: 'nowrap', padding: 'var(--space-2) var(--space-3)' }}
            >
              Chatear
            </button>
            <span className="go" id={`btn-book-${pro.id}`}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 16, height: 16 }}><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 10h18"/></svg>
              Pedir turno
            </span>
          </div>
        </div>
      </div>

      <div className="card-hint">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#4A5E51" strokeWidth="2"><path d="M9 18l6-6-6-6"/></svg>
        Tocá para elegir día y horario
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
          <img src="/tranqui-icon.png" alt="" aria-hidden="true" className="public-header__logo-icon" />
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
            <IconHelp />
            Ayuda / FAQ
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
                      Mis Turnos
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
                      Mi Historia Clínica
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
                      Cerrar sesión
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
    <div className="trust-strip" aria-label="Garantías de servicio">
      <div className="trust-carousel">
        <div className="trust-carousel__track">
          {Array.from({ length: 3 }).map((_, copyIdx) => (
            <div
              className="trust-carousel__group"
              key={copyIdx}
              role={copyIdx === 0 ? 'list' : undefined}
              aria-hidden={copyIdx > 0}
            >
              {TRUST_ITEMS.map((item, idx) => (
                <div className="trust-carousel__item" key={idx} role={copyIdx === 0 ? 'listitem' : undefined}>
                  <span className="trust-carousel__icon" aria-hidden="true">{item.icon}</span>
                  <span className="trust-carousel__text">{item.text}</span>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
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
  domicilioAtencion?: string
  domicilioLat?: number | null
  domicilioLng?: number | null
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
  descripcionPerfil?: string
  pacientesAtiende?: string[]
  institucionFormacion?: string
  aniosExperiencia?: number | null
  tags?: string[]
  experiencia?: string
  redesSociales?: RedesSociales
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
  useDocumentTitle('Tranqui App — Turnos con psicólogos y psiquiatras')
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
  const [cancelTurnoId, setCancelTurnoId] = useState<number | null>(null)

  // Portal inner tabs
  const [activeDoctorId, setActiveDoctorId] = useState<number | null>(null)
  
  // Custom states for availability filter and WhatsApp chat
  const [availabilityDate, setAvailabilityDate] = useState('')
  const [availabilityMap, setAvailabilityMap] = useState<Record<string, number>>({})
  const [checkingAvailability, setCheckingAvailability] = useState(false)
  const [showDatePicker, setShowDatePicker] = useState(false)
  const availabilityPopoverRef = useRef<HTMLDivElement | null>(null)
  const [showFloatingChat, setShowFloatingChat] = useState(false)
  const [chatSubView, setChatSubView] = useState<'list' | 'chat'>('list')
  const [chatChannels, setChatChannels] = useState<any[]>([])

  // Close the availability date-picker popover when clicking outside of it
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (availabilityPopoverRef.current && !availabilityPopoverRef.current.contains(event.target as Node)) {
        setShowDatePicker(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Check real availability for every visible professional on the selected date — one batched
  // request instead of one request per professional (used to fire N parallel HTTP calls every
  // time the date filter changed).
  useEffect(() => {
    if (!availabilityDate || professionals.length === 0) {
      setAvailabilityMap({})
      return
    }
    let cancelled = false
    setCheckingAvailability(true)
    api.getConteosDisponibilidad(professionals.map(p => p.id), availabilityDate)
      .then((counts: Record<string, number>) => {
        if (cancelled) return
        setAvailabilityMap(counts || {})
      })
      .catch(() => {
        if (!cancelled) setAvailabilityMap({})
      })
      .finally(() => {
        if (!cancelled) setCheckingAvailability(false)
      })
    return () => { cancelled = true }
  }, [availabilityDate, professionals])

  const refreshChatChannels = () => {
    if (currentUser && currentUser.rol === 'PACIENTE') {
      api.getChatCanales()
        .then((res: any) => {
          setChatChannels(res || []);
        })
        .catch(err => console.error("Error loading chat channels:", err));
    }
  }

  // Refresh the channel list whenever the widget opens and whenever the patient comes
  // back from an individual chat to the list, so it never shows stale conversations.
  useEffect(() => {
    if (showFloatingChat && chatSubView === 'list') {
      refreshChatChannels()
    }
  }, [currentUser, showFloatingChat, chatSubView])

  // Also refresh on every message this patient receives over the WebSocket — regardless of
  // which chat (if any) is currently open — so a new conversation or a new last message
  // shows up in the list without having to close and reopen the widget.
  const { messages: chatMessages, sendMessage: sendChatMessage } = useChat(activeDoctorId, () => refreshChatChannels())
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
      const socketUrl = window.location.protocol === 'https:' ? `https://${window.location.host}/ws-tranqui` : `http://${window.location.hostname}:8081/ws-tranqui`;
      const socket = new SockJS(socketUrl, null, { withCredentials: true } as any)
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

  // Handle the return redirect from Mercado Pago Checkout Pro after a patient pays for a turno.
  // MP appends collection_status/status (approved/pending/rejected) to the back_url instead of
  // calling us — without this the patient just lands back on a bare homepage with no feedback
  // at all about whether the payment went through, pending, or failed.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const mpStatus = params.get('collection_status') || params.get('status')
    if (!mpStatus) return

    if (mpStatus === 'approved') {
      showAlert('¡Pago acreditado! Tu turno quedó confirmado.', 'success')
    } else if (mpStatus === 'pending' || mpStatus === 'in_process') {
      showAlert('Tu pago está siendo procesado. Te avisaremos cuando se acredite.', 'warning')
    } else {
      showAlert('El pago no pudo completarse. Podés reintentarlo desde "Mis Turnos".', 'error')
    }

    refreshPatientData()

    ;['collection_status', 'status', 'payment_id', 'collection_id', 'external_reference', 'payment_type', 'merchant_order_id', 'preference_id', 'site_id', 'processing_mode', 'merchant_account_id']
      .forEach(key => params.delete(key))
    const newSearch = params.toString()
    window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''))
  }, [])

  const handleCancelAppointmentByPatient = (turnoId: number) => {
    setCancelTurnoId(turnoId)
  }

  const confirmCancelAppointmentByPatient = (turnoId: number) => {
    api.cancelarTurno(turnoId)
      .then(() => {
        showAlert("Turno cancelado con éxito.", "success")
        refreshPatientData()
      })
      .catch(err => {
        console.error(err)
        showAlert("Error al cancelar el turno.", "error")
      })
      .finally(() => {
        setCancelTurnoId(null)
      })
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

      const matchesAvailability = !availabilityDate || checkingAvailability || (availabilityMap[pro.id] ?? 0) > 0

      const matchesModality = activeModality === 'Todos' ||
        (activeModality === 'Online' && pro.ofreceOnline) ||
        (activeModality === 'Presencial' && pro.ofrecePresencial)

      return matchesQuery && matchesSpecialty && matchesAvailability && matchesModality
    })
  }, [query, activeSpecialty, activeModality, availabilityDate, availabilityMap, checkingAvailability, professionals])

  const availabilityDateLabel = useMemo(() => {
    if (!availabilityDate) return null
    const [y, m, d] = availabilityDate.split('-').map(Number)
    const date = new Date(y, m - 1, d)
    return date.toLocaleDateString('es-AR', { day: 'numeric', month: 'short' })
  }, [availabilityDate])

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
        fotoUrl: pro.fotoUrl,
        domicilioAtencion: pro.domicilioAtencion,
        domicilioLat: pro.domicilioLat,
        domicilioLng: pro.domicilioLng,
        ofreceOnline: pro.ofreceOnline,
        ofrecePresencial: pro.ofrecePresencial,
        descripcionPerfil: pro.descripcionPerfil,
        pacientesAtiende: pro.pacientesAtiende,
        institucionFormacion: pro.institucionFormacion,
        aniosExperiencia: pro.aniosExperiencia,
        tags: pro.tags,
        experiencia: pro.experiencia,
        redesSociales: pro.redesSociales
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
            <div className="availability-filter" ref={availabilityPopoverRef}>
              <button
                type="button"
                onClick={() => setShowDatePicker(v => !v)}
                className="search-box__filter"
                style={{
                  backgroundColor: availabilityDate ? 'var(--green-100)' : 'transparent',
                  borderColor: availabilityDate ? 'var(--color-primary)' : 'var(--color-border)',
                  color: availabilityDate ? 'var(--color-primary)' : 'var(--color-text-primary)',
                  fontWeight: availabilityDate ? 'bold' : 'normal',
                }}
                aria-label="Filtrar por disponibilidad en una fecha"
                aria-expanded={showDatePicker}
              >
                <IconCalendar />
                {checkingAvailability ? 'Buscando...' : availabilityDateLabel ? `Disponibles el ${availabilityDateLabel}` : 'Disponibilidad'}
                {availabilityDate && (
                  <span
                    role="button"
                    aria-label="Quitar filtro de fecha"
                    onClick={(e) => { e.stopPropagation(); setAvailabilityDate(''); setShowDatePicker(false) }}
                    className="availability-filter__clear"
                  >
                    <IconClose size={12} />
                  </span>
                )}
              </button>
              {showDatePicker && (
                <div className="availability-popover" role="dialog" aria-label="Elegir fecha de disponibilidad">
                  <label className="availability-popover__label" htmlFor="availability-date-input">Ver disponibilidad para el:</label>
                  <input
                    id="availability-date-input"
                    type="date"
                    className="availability-popover__input"
                    min={new Date().toISOString().split('T')[0]}
                    value={availabilityDate}
                    onChange={(e) => { setAvailabilityDate(e.target.value); setShowDatePicker(false) }}
                  />
                </div>
              )}
            </div>
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
        const upcomingAppointments = myAppointments.filter(appt => appt.status !== 'completed');
        
        if (upcomingAppointments.length === 0) return null;

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
              gap: 'var(--space-5)',
              boxShadow: 'var(--shadow-md)',
              flexWrap: 'wrap'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', flex: '1 1 300px' }}>
                <span style={{ fontSize: '24px' }}>⏰</span>
                <div style={{ textAlign: 'left' }}>
                  <h4 style={{ margin: 0, fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-primary)', textTransform: 'uppercase' }}>
                    Mis Próximos Turnos
                  </h4>
                  <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    Tenés turnos programados en Tranqui App. Podés pagar consultas pendientes o unirte a la videollamada el día de la sesión.
                  </p>
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', minWidth: '320px', flex: '2 1 400px' }}>
                {upcomingAppointments.map(appt => {
                  const isTodayAppt = isToday(appt.fecha);
                  const isConfirmed = appt.status === 'confirmed';
                  return (
                    <div key={appt.id} style={{ 
                      display: 'flex', 
                      flexDirection: 'column', 
                      gap: '6px', 
                      backgroundColor: '#ffffff', 
                      padding: 'var(--space-3)', 
                      borderRadius: 'var(--radius-md)', 
                      border: isConfirmed ? '1px solid #a7f3d0' : '1px solid #fef3c7' 
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-3)' }}>
                        <span style={{ fontSize: '11px', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                          {appt.fecha} · {appt.hour} hs
                        </span>
                        <span className={`badge ${isConfirmed ? 'badge--success' : 'badge--warning'}`} style={{ fontSize: '9px', padding: '2px 6px', textTransform: 'uppercase' }}>
                          {isConfirmed ? 'Confirmado' : 'Pendiente Pago'}
                        </span>
                      </div>
                      
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '2px', flexWrap: 'wrap', gap: '8px' }}>
                        <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                          <strong>Profesional:</strong> {appt.patientName} <span style={{ margin: '0 4px', opacity: 0.5 }}>|</span> <strong>Modalidad:</strong> {appt.type}
                        </span>
                        
                        <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
                          {appt.meetLink && isConfirmed && isTodayAppt && (
                            <a 
                              href={appt.meetLink} 
                              target="_blank" 
                              rel="noopener noreferrer" 
                              className="btn btn--primary btn--sm" 
                              style={{ fontSize: '10px', padding: '3px 8px', display: 'flex', alignItems: 'center', gap: '3px', textDecoration: 'none' }}
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 10, height: 10 }}><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" ry="2" /></svg>
                              Unirse
                            </a>
                          )}
                          {appt.checkoutUrl && !isConfirmed && (
                            <a 
                              href={appt.checkoutUrl} 
                              target="_blank" 
                              rel="noopener noreferrer" 
                              className="btn btn--sm" 
                              style={{ 
                                fontSize: '10px', 
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
                              {appt.type === 'Copago OSDE' ? 'Pagar Copago' : 'Pagar Consulta'}
                            </a>
                          )}
                          <button 
                            onClick={() => handleCancelAppointmentByPatient(appt.id)}
                            style={{
                              border: 'none',
                              background: 'none',
                              cursor: 'pointer',
                              color: 'var(--color-danger)',
                              fontSize: '11px',
                              fontWeight: 'bold',
                              padding: '2px 4px'
                            }}
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
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

          {/* Modality Filter segmented buttons */}
          <div style={{ display: 'flex', gap: 'var(--space-2)', margin: '0 0 var(--space-4)' }}>
            {(['Todos', 'Online', 'Presencial'] as const).map((mode) => {
              const isActive = activeModality === mode;
              const label = mode === 'Todos' ? 'Todas las modalidades' : mode === 'Online' ? 'Citas Online' : 'Citas Presenciales';
              return (
                <button
                  key={mode}
                  onClick={() => setActiveModality(mode)}
                  style={{
                    padding: 'var(--space-3) var(--space-6)',
                    borderRadius: '24px',
                    border: '1px solid',
                    borderColor: isActive ? 'var(--color-primary)' : 'var(--color-border)',
                    backgroundColor: isActive ? 'var(--color-primary)' : 'white',
                    color: isActive ? 'white' : 'var(--color-text-primary)',
                    fontSize: 'var(--text-sm)',
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

          <div className="results-grid" role="list" aria-label="Profesionales disponibles">
            {loading
              ? Array.from({ length: 6 }).map((_, i) => <SkeletonCard key={i} />)
              : filtered.length === 0
                ? (
                  <div className="empty-state" role="status" aria-live="polite">
                    <img src="/empty-state-sprout.png" alt="" aria-hidden="true" className="empty-state__emoji" />
                    <span className="empty-state__brand" aria-hidden="true">tranqui</span>
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
                    <ProCard
                      pro={pro}
                      onBook={handleBook}
                      onChat={handleStartChat}
                      currentUser={currentUser}
                      availabilityDateLabel={availabilityDateLabel}
                      availabilityCount={availabilityMap[pro.id]}
                    />
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
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Mis Turnos Reservados</h3>
              <button onClick={() => setShowAppointmentsModal(false)} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
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
                              Cancelar
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
                      {appt.domicilioAtencion && (
                        <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          <div>Dirección de atención: <strong>{appt.domicilioAtencion}</strong></div>
                          {true && (
                            <a 
                              href={appt.domicilioLat && appt.domicilioLng 
                                ? `https://www.google.com/maps/search/?api=1&query=${appt.domicilioLat},${appt.domicilioLng}`
                                : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(appt.domicilioAtencion)}`
                              }
                              target="_blank" 
                              rel="noopener noreferrer" 
                              className="btn btn--ghost btn--sm" 
                              style={{ 
                                fontSize: '10px', 
                                padding: 'var(--space-1) var(--space-2)', 
                                width: 'fit-content', 
                                display: 'inline-flex', 
                                gap: '4px', 
                                alignItems: 'center', 
                                border: '1px solid var(--color-border)',
                                backgroundColor: 'var(--color-surface)',
                                color: 'var(--color-primary)',
                                fontWeight: 'bold'
                              }}
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 12, height: 12 }}>
                                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                                <circle cx="12" cy="10" r="3" />
                              </svg>
                              Ver dirección en Google Maps
                            </a>
                          )}
                        </div>
                      )}
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
                          Pagar Turno
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
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Mi Historia Clínica / Informes</h3>
              <button onClick={() => setShowReportsModal(false)} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
            </div>
            <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-4)' }}>Próximamente — vas a poder ver acá tu historia clínica e informes.</p>
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
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}><IconHelp size={20} /> Ayuda y Preguntas Frecuentes (FAQ)</h3>
              <button onClick={() => setShowHelpModal(false)} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
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
                  Si necesitás cancelar una reserva, abrí tu menú de perfil (haciendo clic en tu nombre en la parte superior derecha), seleccioná <strong>"Mis Turnos"</strong>, ubicá el turno correspondiente y hacé clic en el botón <strong>"Cancelar"</strong>.
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

      {/* Cancellation Confirmation Modal */}
      {cancelTurnoId !== null && (() => {
        const appt = myAppointments.find(a => a.id === cancelTurnoId)
        if (!appt) return null;
        return (
          <div style={{
            position: 'fixed',
            top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10000,
            padding: 'var(--space-4)'
          }}>
            <div className="card" style={{
              maxWidth: '420px',
              width: '100%',
              padding: 'var(--space-6)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-4)',
              boxShadow: 'var(--shadow-xl)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--color-border)',
              backgroundColor: '#ffffff',
              textAlign: 'center'
            }}>
              <div style={{
                width: '56px',
                height: '56px',
                borderRadius: '50%',
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                color: 'var(--color-danger)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto var(--space-2)'
              }}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 28, height: 28 }}>
                  <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
              </div>

              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  ¿Cancelar este turno?
                </h3>
                <p style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
                  Esta acción no se puede deshacer. Si el turno ya fue pagado, se gestionará el reembolso según las políticas vigentes.
                </p>
              </div>

              <div style={{
                backgroundColor: 'var(--neutral-50)',
                padding: 'var(--space-3) var(--space-4)',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border)',
                textAlign: 'left',
                fontSize: 'var(--text-sm)',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px'
              }}>
                <div><strong>Profesional:</strong> {appt.patientName}</div>
                <div><strong>Fecha:</strong> {appt.fecha}</div>
                <div><strong>Horario:</strong> {appt.hour} hs</div>
                <div><strong>Modalidad:</strong> {appt.type}</div>
              </div>

              <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
                <button
                  type="button"
                  className="btn btn--secondary"
                  onClick={() => setCancelTurnoId(null)}
                  style={{ flex: 1, height: '42px', justifyContent: 'center' }}
                >
                  No, mantener
                </button>
                <button
                  type="button"
                  className="btn btn--danger"
                  onClick={() => confirmCancelAppointmentByPatient(cancelTurnoId)}
                  style={{ flex: 1, height: '42px', justifyContent: 'center' }}
                >
                  Sí, cancelar
                </button>
              </div>
            </div>
          </div>
        )
      })()}
      {/* WhatsApp Floating Chat Widget */}
      {currentUser && currentUser.rol === 'PACIENTE' && (
        <>
          {/* Floating Action Button (FAB) */}
          <button
            onClick={() => {
              const opening = !showFloatingChat;
              setShowFloatingChat(opening);
              if (opening) {
                // Always land on the conversation list when (re)opening the widget from
                // its icon — same as WhatsApp itself. Only an explicit "Chatear" click
                // (handleStartChat) or tapping a channel jumps straight into a chat.
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
                  <IconClose />
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
                        Las conversaciones en Tranqui están cifradas. Escribí un mensaje para iniciar la consulta.
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
                      <IconSend />
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
