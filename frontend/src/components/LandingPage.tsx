import { useState, useMemo, useEffect, useRef, lazy, Suspense } from 'react'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import '../styles/landing.css'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'
import { useAlert } from '../context/AlertContext'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import DateInputDDMMYYYY from './DateInputDDMMYYYY'
import CancelTurnoConfirmModal from './CancelTurnoConfirmModal'

// Modals that aren't needed for first paint (only shown after a click) are lazy-loaded so their
// code doesn't ship in the initial landing-page bundle — LandingPage itself stays a static import
// (see App.tsx) since almost every first-time visitor needs it immediately.
const HelpFaqModal = lazy(() => import('./HelpFaqModal'))
const MisTurnosModal = lazy(() => import('./MisTurnosModal'))
const TurnoDetailModal = lazy(() => import('./TurnoDetailModal'))
const PrescriptionsModal = lazy(() => import('./PrescriptionsModal'))
const MyTicketsView = lazy(() => import('./MyTicketsView'))

const formatDateDDMMYYYY = (dateStr?: string) => {
  if (!dateStr) return '';
  if (dateStr.includes('/')) return dateStr;
  const parts = dateStr.trim().split('T')[0].split('-');
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
  return dateStr;
};

// A confirmed (paid) document-only turno (receta/certificado/informe, see Turno.ocupaAgenda)
// isn't waiting on anything scheduled — what matters to the patient is whether the médico
// already sent it, so it gets its own "Documento pendiente/enviado" label instead of "Confirmado".
const getTurnoBadge = (appt: { status?: string; ocupaAgenda?: boolean; documentoEnviado?: boolean }) => {
  const isConfirmed = appt.status === 'confirmed';
  if (isConfirmed && appt.ocupaAgenda === false) {
    return appt.documentoEnviado
      ? { label: 'Documento enviado', cls: 'badge--success' }
      : { label: 'Documento pendiente', cls: 'badge--warning' };
  }
  return isConfirmed
    ? { label: 'Confirmado', cls: 'badge--success' }
    : { label: 'Pendiente', cls: 'badge--warning' };
};

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

function IconLocationPin({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <path d="M12 21s-7-6.1-7-11a7 7 0 0 1 14 0c0 4.9-7 11-7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

function IconVideoCam({ size = 15 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="2" y="6" width="14" height="12" rx="2" />
      <path d="M16 10l6-3v10l-6-3" />
    </svg>
  )
}

function IconPhone({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>
  )
}

function IconMailSmall({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="2" y="4" width="20" height="16" rx="2" /><polyline points="2 6 12 13 22 6" />
    </svg>
  )
}

function IconClipboard({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <path d="M9 4h6a1 1 0 0 1 1 1v1H8V5a1 1 0 0 1 1-1Z" />
      <rect x="5" y="6" width="14" height="16" rx="2" />
      <line x1="8" y1="12" x2="16" y2="12" /><line x1="8" y1="16" x2="16" y2="16" />
    </svg>
  )
}

function IconTicket({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <path d="M21 12a2 2 0 0 0-2-2V7a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v3a2 2 0 0 1 0 4v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2z" />
      <line x1="12" y1="6" x2="12" y2="18" strokeDasharray="1.5 2" />
    </svg>
  )
}

function IconChevronRight({ size = 18 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <path d="M9 18l6-6-6-6" />
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
  id: string
  label: string
  price: number
  enabled: boolean
  requiereObraSocial?: boolean
  obraSocial?: string
  precioOnline?: number | null
  precioPresencial?: number | null
  requiereAgenda?: boolean
}

interface RedesSociales {
  instagram?: string
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
  domicilioAtencionTorre?: string
  domicilioAtencionPiso?: string
  domicilioAtencionDepto?: string
  domicilioAtencionBarrio?: string
  descripcionPerfil?: string
  pacientesAtiende?: string[]
  institucionFormacion?: string
  aniosExperiencia?: number | null
  experiencia?: string
  redesSociales?: RedesSociales
  telefono?: string
  emailContacto?: string
  publicaciones?: string
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

          {(pro.telefono || pro.emailContacto) && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
              {pro.telefono && (
                <a
                  href={`tel:${pro.telefono.replace(/\s+/g, '')}`}
                  onClick={(e) => e.stopPropagation()}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '11px', color: 'var(--color-text-secondary)', textDecoration: 'none' }}
                >
                  <IconPhone size={12} /> {pro.telefono}
                </a>
              )}
              {pro.emailContacto && (
                <a
                  href={`mailto:${pro.emailContacto}`}
                  onClick={(e) => e.stopPropagation()}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '11px', color: 'var(--color-text-secondary)', textDecoration: 'none' }}
                >
                  <IconMailSmall size={12} /> {pro.emailContacto}
                </a>
              )}
            </div>
          )}
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
  const [showTicketsView, setShowTicketsView] = useState(false);
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
            className="btn btn--ghost btn--sm btn-faq"
            onClick={onOpenHelp}
            title="Ayuda y preguntas frecuentes"
          >
            <IconHelp />
            <span className="btn-faq-text">Ayuda / FAQ</span>
          </button>

          <button
            className="btn-crisis"
            onClick={onCrisis}
            id="btn-crisis-trigger"
          >
            <span className="btn-crisis__dot" aria-hidden="true" />
            <span className="btn-crisis-text">Ayuda urgente</span>
          </button>

          {currentUser ? (
            isDoctor ? (
              <div className="header-pro-actions" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <span className="header-pro-name" style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-medium)' }}>
                  {currentUser.nombre}
                </span>
                <button 
                  className="btn btn--secondary btn--sm" 
                  onClick={onGoToDashboard}
                >
                  Panel
                </button>
                <button 
                  className="btn btn--ghost btn--sm" 
                  onClick={onLogout}
                >
                  Salir
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
                        color: 'var(--color-text-primary)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15, flexShrink: 0, color: 'var(--color-text-secondary)' }}>
                        <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
                      </svg>
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
                        color: 'var(--color-text-primary)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15, flexShrink: 0, color: 'var(--color-text-secondary)' }}>
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                        <path d="M9 15l2 2 4-4" />
                      </svg>
                      Mi Receta
                    </button>
                    <a
                      href="/mi-cuenta"
                      onClick={() => setShowDropdown(false)}
                      style={{
                        padding: 'var(--space-3) var(--space-4)',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        borderBottom: '1px solid #f0f2f5',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: 'var(--color-text-primary)',
                        textDecoration: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f8fafc')}
                      onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15, flexShrink: 0, color: 'var(--color-text-secondary)' }}>
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                      </svg>
                      Mi Cuenta
                    </a>
                    <a
                      href="/privacidad"
                      target="_blank"
                      rel="noreferrer"
                      onClick={() => setShowDropdown(false)}
                      style={{
                        padding: 'var(--space-3) var(--space-4)',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        borderBottom: '1px solid #f0f2f5',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: 'var(--color-text-primary)',
                        textDecoration: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f8fafc')}
                      onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15, flexShrink: 0, color: 'var(--color-text-secondary)' }}>
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                      </svg>
                      Privacidad y mis datos
                    </a>
                    <button
                      onClick={() => {
                        setShowDropdown(false);
                        setShowTicketsView(true);
                      }}
                      style={{
                        padding: 'var(--space-3) var(--space-4)',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        borderBottom: '1px solid #f0f2f5',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: 'var(--color-text-primary)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15, flexShrink: 0, color: 'var(--color-text-secondary)' }}>
                        <rect x="2" y="4" width="20" height="16" rx="2" /><path d="M22 6l-10 7L2 6" />
                      </svg>
                      Soporte
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
                        color: 'var(--color-danger)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#fdf2f2'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15, flexShrink: 0 }}>
                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
                      </svg>
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
      {showTicketsView && (
        <Suspense fallback={null}>
          <MyTicketsView onClose={() => setShowTicketsView(false)} />
        </Suspense>
      )}
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
  telefono?: string
  emailContacto?: string
  publicaciones?: string
  domicilioAtencion?: string
  domicilioLat?: number | null
  domicilioLng?: number | null
  domicilioAtencionTorre?: string
  domicilioAtencionPiso?: string
  domicilioAtencionDepto?: string
  domicilioAtencionBarrio?: string
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
  descripcionPerfil?: string
  pacientesAtiende?: string[]
  institucionFormacion?: string
  aniosExperiencia?: number | null
  tags?: string[]
  experiencia?: string
  redesSociales?: RedesSociales
  tariffs?: Tariff[]
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
  const [myPrescriptions, setMyPrescriptions] = useState<any[]>([])
  const [loadingPortal, setLoadingPortal] = useState(false)
  const [showAppointmentsModal, setShowAppointmentsModal] = useState(false)
  const [showPrescriptionsModal, setShowPrescriptionsModal] = useState(false)
  const [showTicketsView, setShowTicketsView] = useState(false)
  // null = auto (defaults to 'documentos' only when there are no upcoming turnos but there are
  // pending documents); once the patient clicks a tab explicitly it sticks to that choice.
  const [homeCardView, setHomeCardView] = useState<'turnos' | 'documentos' | null>(null)
  const [showHelpModal, setShowHelpModal] = useState(false)
  const [cancelTurnoId, setCancelTurnoId] = useState<number | null>(null)
  const [detailTurno, setDetailTurno] = useState<any | null>(null)

  useEffect(() => {
    if (showPrescriptionsModal) {
      api.getMisRecetas()
        .then((res: any) => {
          setMyPrescriptions(Array.isArray(res) ? res : [])
        })
        .catch((err: any) => console.error("Error al obtener recetas:", err))
    }
  }, [showPrescriptionsModal])

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
    if (!currentUser || currentUser.rol !== 'PACIENTE') return

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
                api.getMisRecetas()
              ]).then(([turnos, recetas]) => {
                setMyAppointments(turnos || [])
                setMyPrescriptions(recetas || [])
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
    // Deliberately keyed on the stable id/rol pair, not the whole `currentUser` object (a new
    // reference on every parent re-render) or the unrelated `loading` state (the médicos list
    // fetch below, which used to toggle shortly after mount and tear the socket down mid-handshake
    // — the exact "WebSocket is closed before the connection is established" error in prod).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser?.id, currentUser?.rol])

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
            id: t.id,
            label: t.label,
            price: t.price,
            enabled: t.enabled,
            requiereObraSocial: t.requiereObraSocial,
            obraSocial: t.obraSocial,
            precioOnline: t.precioOnline,
            precioPresencial: t.precioPresencial,
            requiereAgenda: t.requiereAgenda,
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
        api.getMisRecetas()
      ])
        .then(([turnos, recetas]) => {
          setMyAppointments(turnos || [])
          setMyPrescriptions(recetas || [])
          reconciliarTurnosPendientes(turnos || [])
        })
        .catch((err) => console.error("Error loading patient data:", err))
        .finally(() => setLoadingPortal(false))
    }
  }

  // Self-heals turnos stuck showing "Pendiente" despite already being paid — Mercado Pago's
  // webhook isn't 100% reliable (delayed/dropped deliveries, or the patient closing the tab
  // before the return-from-checkout redirect ever fires, so the other reconciliation path below
  // never runs either). Every time "Mis Turnos" loads, re-checks each still-pending turno with a
  // real (non-mock) payment attempt directly against Mercado Pago — see WebhookController#
  // verificarPago — and silently reloads the list again if anything got confirmed.
  const reconciliarTurnosPendientes = (turnos: any[]) => {
    const pendientes = turnos.filter(t => t.status === 'pending' && t.checkoutUrl && !t.checkoutUrl.includes('mock'))
    if (pendientes.length === 0) return
    Promise.allSettled(pendientes.map(t => api.verificarPagoTurno(t.id)))
      .then(() => api.getMisTurnos())
      .then((turnos2: any) => { if (turnos2) setMyAppointments(turnos2) })
      .catch((err) => console.error('Error al reconciliar turnos pendientes:', err))
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

    // Mercado Pago's async webhook is what normally confirms the turno server-side, but webhook
    // delivery isn't guaranteed — when it's delayed or dropped, the turno used to stay stuck
    // showing "Pendiente" forever even though the patient already paid. Since MP already told us
    // right here (via these same query params) that the payment was approved, verify it directly
    // against Mercado Pago's API as a fallback instead of just trusting the webhook to eventually
    // show up — see WebhookController#verificarPago. Safe to call even if the webhook already
    // processed it (idempotent no-op).
    const externalReference = params.get('external_reference')
    const paymentId = params.get('payment_id') || params.get('collection_id')
    if (mpStatus === 'approved' && externalReference && paymentId) {
      api.verificarPagoTurno(externalReference, paymentId)
        .catch(err => console.error('Error al verificar el pago manualmente:', err))
        .finally(refreshPatientData)
    } else {
      refreshPatientData()
    }

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
        domicilioAtencionTorre: pro.domicilioAtencionTorre,
        domicilioAtencionPiso: pro.domicilioAtencionPiso,
        domicilioAtencionDepto: pro.domicilioAtencionDepto,
        domicilioAtencionBarrio: pro.domicilioAtencionBarrio,
        ofreceOnline: pro.ofreceOnline,
        ofrecePresencial: pro.ofrecePresencial,
        descripcionPerfil: pro.descripcionPerfil,
        pacientesAtiende: pro.pacientesAtiende,
        institucionFormacion: pro.institucionFormacion,
        aniosExperiencia: pro.aniosExperiencia,
        tags: pro.tags,
        experiencia: pro.experiencia,
        redesSociales: pro.redesSociales,
        telefono: pro.telefono,
        emailContacto: pro.emailContacto,
        publicaciones: pro.publicaciones,
        tariffs: pro.tariffs
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
        onOpenMyClinicalHistory={() => setShowPrescriptionsModal(true)}
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
                  <label className="availability-popover__label" htmlFor="availability-date-input">Ver disponibilidad para el (DD/MM/AAAA):</label>
                  <DateInputDDMMYYYY
                    id="availability-date-input"
                    className="availability-popover__input"
                    min={new Date().toISOString().split('T')[0]}
                    value={availabilityDate}
                    onChange={(iso) => { setAvailabilityDate(iso); setShowDatePicker(false) }}
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

          {/* Quick access to the patient's own appointments and clinical history */}
          {currentUser && currentUser.rol === 'PACIENTE' && (
            <div className="quick-access-grid">
              <button
                type="button"
                className="quick-access-card"
                onClick={() => setShowAppointmentsModal(true)}
              >
                <span className="quick-access-card__icon"><IconCalendar size={22} /></span>
                <span className="quick-access-card__body">
                  <span className="quick-access-card__title">Mis Turnos</span>
                  <span className="quick-access-card__subtitle">Ver y gestionar tus sesiones agendadas</span>
                </span>
                <span className="quick-access-card__arrow"><IconChevronRight /></span>
              </button>
              <button
                type="button"
                className="quick-access-card"
                onClick={() => setShowPrescriptionsModal(true)}
              >
                <span className="quick-access-card__icon"><IconClipboard size={22} /></span>
                <span className="quick-access-card__body">
                  <span className="quick-access-card__title">Mi Receta</span>
                  <span className="quick-access-card__subtitle">Consultá tus recetas médicas prescritas</span>
                </span>
                <span className="quick-access-card__arrow"><IconChevronRight /></span>
              </button>
              <button
                type="button"
                className="quick-access-card"
                onClick={() => setShowTicketsView(true)}
              >
                <span className="quick-access-card__icon"><IconTicket size={22} /></span>
                <span className="quick-access-card__body">
                  <span className="quick-access-card__title">Soporte</span>
                  <span className="quick-access-card__subtitle">Consultas y problemas — te respondemos por acá</span>
                </span>
                <span className="quick-access-card__arrow"><IconChevronRight /></span>
              </button>
            </div>
          )}
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
        // Documents (ocupaAgenda === false) are purchases, not scheduled sessions — they never
        // belong in "Mis Próximos Turnos" (no modalidad, no real horario). Once documentoEnviado
        // is true there's nothing left to track either, so they drop out entirely instead of
        // lingering here forever (unlike a real turno, a document-only turno never flips to
        // "completed" on its own — see TurnoService's status computation).
        const upcomingTurnos = myAppointments.filter(appt => appt.ocupaAgenda !== false && appt.status !== 'completed');
        const pendingDocuments = myAppointments.filter(appt => appt.ocupaAgenda === false && !appt.documentoEnviado);

        if (upcomingTurnos.length === 0 && pendingDocuments.length === 0) return null;

        const activeView = homeCardView ?? (upcomingTurnos.length === 0 && pendingDocuments.length > 0 ? 'documentos' : 'turnos');

        return (
          <div style={{ width: '100%', maxWidth: '1200px', margin: 'var(--space-4) auto', padding: '0 var(--space-6)' }}>
            <div className="card" style={{
              backgroundColor: 'var(--neutral-100)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-5)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-4)',
              fontFamily: 'var(--font-body)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                <div>
                  <h4 style={{ margin: 0, fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--green-600)', textTransform: 'uppercase', letterSpacing: '0.4px' }}>
                    {activeView === 'turnos' ? 'Mis Próximos Turnos' : 'Mis Documentos Pendientes'}
                  </h4>
                  <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                    {activeView === 'turnos'
                      ? 'Tenés turnos programados en Tranqui App. Podés pagar consultas pendientes o unirte a la videollamada el día de la sesión.'
                      : 'Recetas fuera de turno, certificados e informes que solicitaste y todavía no recibiste.'}
                  </p>
                </div>
                {pendingDocuments.length > 0 && upcomingTurnos.length > 0 && (
                  <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
                    <button
                      type="button"
                      onClick={() => setHomeCardView('turnos')}
                      className={`btn btn--sm ${activeView === 'turnos' ? 'btn--primary' : 'btn--ghost'}`}
                    >
                      Turnos ({upcomingTurnos.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setHomeCardView('documentos')}
                      className={`btn btn--sm ${activeView === 'documentos' ? 'btn--primary' : 'btn--ghost'}`}
                    >
                      Documentos Pendientes ({pendingDocuments.length})
                    </button>
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {activeView === 'turnos' ? upcomingTurnos.map(appt => {
                  const isTodayAppt = isToday(appt.fecha);
                  const isConfirmed = appt.status === 'confirmed';
                  const isOnline = appt.modalidad ? appt.modalidad === 'ONLINE' : !!appt.meetLink;
                  return (
                    <div
                      key={appt.id}
                      onClick={() => setDetailTurno(appt)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setDetailTurno(appt) }}
                      style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 'var(--space-4)',
                      backgroundColor: 'var(--color-surface)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                      padding: 'var(--space-3) var(--space-4)',
                      flexWrap: 'wrap',
                      cursor: 'pointer'
                    }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1, minWidth: 220 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                          <span style={{ color: 'var(--color-text-secondary)' }}><IconCalendar size={16} /></span>
                          <span style={{ fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                            {formatDateDDMMYYYY(appt.fecha)} · {appt.hour} hs
                          </span>
                        </div>

                        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          <span style={{ fontWeight: 500, color: 'var(--color-text-primary)' }}>{appt.patientName}</span>
                          {' · '}
                          {isOnline ? 'Consulta virtual' : 'Consulta presencial'}
                        </div>

                        {!isOnline && appt.domicilioAtencion && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '2px' }}>
                            <span style={{ color: 'var(--color-text-secondary)' }}><IconLocationPin size={16} /></span>
                            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{appt.domicilioAtencion}</span>
                          </div>
                        )}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                        {(() => { const b = getTurnoBadge(appt); return (
                          <span className={`badge ${b.cls}`} style={{ fontSize: '11px' }}>
                            {b.label}
                          </span>
                        ) })()}

                        {isOnline && appt.meetLink && isConfirmed && isTodayAppt && (
                          <a
                            href={appt.meetLink}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={(e) => e.stopPropagation()}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: '6px',
                              background: 'var(--green-500)',
                              color: '#fff',
                              fontSize: 'var(--text-xs)',
                              fontWeight: 600,
                              padding: '8px 14px',
                              borderRadius: 'var(--radius-md)',
                              textDecoration: 'none',
                              whiteSpace: 'nowrap'
                            }}
                          >
                            <IconVideoCam size={15} /> Unirse
                          </a>
                        )}

                        {appt.checkoutUrl && !isConfirmed && (
                          <a
                            href={appt.checkoutUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={(e) => e.stopPropagation()}
                            style={{
                              fontSize: 'var(--text-xs)',
                              fontWeight: 'bold',
                              padding: '8px 14px',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '4px',
                              backgroundColor: '#009fe3',
                              color: 'white',
                              textDecoration: 'none',
                              borderRadius: 'var(--radius-md)',
                              boxShadow: '0 2px 4px rgba(0, 158, 227, 0.15)',
                              whiteSpace: 'nowrap'
                            }}
                          >
                            {appt.type === 'Copago OSDE' || appt.type === 'Obra Social' ? 'Pagar Copago' : 'Pagar Consulta'}
                          </a>
                        )}

                        <button
                          onClick={(e) => { e.stopPropagation(); handleCancelAppointmentByPatient(appt.id) }}
                          style={{
                            background: 'transparent',
                            border: '1px solid var(--color-border)',
                            color: 'var(--color-text-secondary)',
                            fontSize: 'var(--text-xs)',
                            fontWeight: 600,
                            padding: '8px 14px',
                            borderRadius: 'var(--radius-md)',
                            cursor: 'pointer',
                            whiteSpace: 'nowrap'
                          }}
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  );
                }) : pendingDocuments.map(appt => {
                  const isConfirmed = appt.status === 'confirmed';
                  return (
                    <div
                      key={appt.id}
                      onClick={() => setDetailTurno(appt)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setDetailTurno(appt) }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 'var(--space-4)',
                        backgroundColor: 'var(--color-surface)',
                        border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)',
                        padding: 'var(--space-3) var(--space-4)',
                        flexWrap: 'wrap',
                        cursor: 'pointer'
                      }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1, minWidth: 220 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                          <span style={{ color: 'var(--color-text-secondary)' }}><IconClipboard size={16} /></span>
                          <span style={{ fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                            {appt.type}
                          </span>
                        </div>
                        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          Profesional: <span style={{ fontWeight: 500, color: 'var(--color-text-primary)' }}>{appt.patientName}</span>
                          {' · '}Solicitado el {formatDateDDMMYYYY(appt.fecha)}
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                        {(() => { const b = getTurnoBadge(appt); return (
                          <span className={`badge ${b.cls}`} style={{ fontSize: '11px' }}>
                            {b.label}
                          </span>
                        ) })()}

                        {appt.checkoutUrl && !isConfirmed && (
                          <a
                            href={appt.checkoutUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={(e) => e.stopPropagation()}
                            style={{
                              fontSize: 'var(--text-xs)',
                              fontWeight: 'bold',
                              padding: '8px 14px',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '4px',
                              backgroundColor: '#009fe3',
                              color: 'white',
                              textDecoration: 'none',
                              borderRadius: 'var(--radius-md)',
                              boxShadow: '0 2px 4px rgba(0, 158, 227, 0.15)',
                              whiteSpace: 'nowrap'
                            }}
                          >
                            Pagar
                          </a>
                        )}

                        <button
                          onClick={(e) => { e.stopPropagation(); handleCancelAppointmentByPatient(appt.id) }}
                          style={{
                            background: 'transparent',
                            border: '1px solid var(--color-border)',
                            color: 'var(--color-text-secondary)',
                            fontSize: 'var(--text-xs)',
                            fontWeight: 600,
                            padding: '8px 14px',
                            borderRadius: 'var(--radius-md)',
                            cursor: 'pointer',
                            whiteSpace: 'nowrap'
                          }}
                        >
                          Cancelar
                        </button>
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

      <footer className="site-footer">
        <div className="site-footer__inner">
          <span>© {new Date().getFullYear()} Tranqui App — por Tranqui Neurociencias</span>
          <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
            <a href="/privacidad">Política de Privacidad</a>
            <a href="/terminos">Términos y Condiciones</a>
          </div>
        </div>
      </footer>

      {/* Mis Turnos Modal */}
      {showAppointmentsModal && (
        <Suspense fallback={null}>
          <MisTurnosModal
            appointments={myAppointments}
            loading={loadingPortal}
            onClose={() => setShowAppointmentsModal(false)}
            onCancel={handleCancelAppointmentByPatient}
          />
        </Suspense>
      )}

      {/* Detalle de un turno puntual, abierto al hacer click en una fila de "Mis Próximos Turnos" */}
      {detailTurno && (
        <Suspense fallback={null}>
          <TurnoDetailModal
            appt={detailTurno}
            onClose={() => setDetailTurno(null)}
            onCancel={handleCancelAppointmentByPatient}
          />
        </Suspense>
      )}

      {/* Mi Historia Clinica Modal */}
      {showPrescriptionsModal && (
        <Suspense fallback={null}>
          <PrescriptionsModal prescriptions={myPrescriptions} onClose={() => setShowPrescriptionsModal(false)} />
        </Suspense>
      )}

      {/* Soporte / Tickets */}
      {showTicketsView && (
        <Suspense fallback={null}>
          <MyTicketsView onClose={() => setShowTicketsView(false)} />
        </Suspense>
      )}

      {/* Ayuda / FAQ Modal */}
      {showHelpModal && (
        <Suspense fallback={null}>
          <HelpFaqModal onClose={() => setShowHelpModal(false)} />
        </Suspense>
      )}

      {/* Cancellation Confirmation Modal */}
      {cancelTurnoId !== null && (() => {
        const appt = myAppointments.find(a => a.id === cancelTurnoId)
        if (!appt) return null;
        return (
          <CancelTurnoConfirmModal
            appt={appt}
            onClose={() => setCancelTurnoId(null)}
            onConfirm={() => confirmCancelAppointmentByPatient(cancelTurnoId)}
          />
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
                setChatSubView('list');
              }
            }}
            className="chat-fab"
            aria-label="Abrir chat con profesionales"
          >
            {/* Tranqui chat icon: speech bubble with a calm pulse, not a borrowed brand mark */}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 28, height: 28 }}>
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5Z" />
              <circle cx="8.5" cy="11.5" r="1" fill="currentColor" stroke="none" />
              <circle cx="12" cy="11.5" r="1" fill="currentColor" stroke="none" />
              <circle cx="15.5" cy="11.5" r="1" fill="currentColor" stroke="none" />
            </svg>
            {chatChannels.some((c: any) => (c.mensajesSinLeer || 0) > 0) && (
              <span
                aria-label="Tenés mensajes nuevos"
                title="Tenés mensajes nuevos"
                style={{
                  position: 'absolute',
                  top: -2,
                  right: -2,
                  width: '18px',
                  height: '18px',
                  borderRadius: '50%',
                  backgroundColor: 'var(--color-danger, #D64545)',
                  border: '2px solid var(--color-surface, #fff)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" style={{ width: 9, height: 9 }}>
                  <line x1="12" y1="8" x2="12" y2="13" />
                  <circle cx="12" cy="16.5" r="0.5" fill="white" stroke="white" />
                </svg>
              </span>
            )}
          </button>

          {/* Chat Window */}
          {showFloatingChat && (
            <div
              className="chat-window"
              style={{
                position: 'fixed',
                bottom: '96px',
                right: '24px',
                width: '380px',
                height: '520px',
                borderRadius: 'var(--radius-lg)',
                boxShadow: 'var(--shadow-lg)',
                backgroundColor: 'var(--color-bg)',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
                zIndex: 1000,
                fontFamily: 'var(--font-body)',
                border: '1px solid var(--color-border)'
              }}
            >
              {/* Header */}
              <div
                style={{
                  background: 'linear-gradient(135deg, var(--green-400), var(--color-primary-hover))',
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
                          borderRadius: 'var(--radius-full)',
                          backgroundColor: 'rgba(255,255,255,0.22)',
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
                    <span style={{ fontSize: '14px', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>Mis Chats</span>
                    <span style={{ fontSize: '10px', opacity: 0.85 }}>Conversación segura y cifrada</span>
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
                <div style={{ flex: 1, overflowY: 'auto', backgroundColor: 'var(--color-surface)' }}>
                  {chatChannels.length === 0 ? (
                    <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-8)', fontSize: '12px' }}>
                      No tenés chats activos aún. ¡Iniciá un chat desde el perfil de un profesional!
                    </p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      {chatChannels.map((chan) => {
                        const initials = chan.nombre.split(' ').map((n: string) => n[0]).join('').slice(0, 2);
                        const tieneNuevos = (chan.mensajesSinLeer || 0) > 0;
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
                              borderBottom: '1px solid var(--color-border)',
                              cursor: 'pointer',
                              transition: 'background-color var(--transition-fast)'
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--green-50)'}
                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                          >
                            <div style={{ position: 'relative', flexShrink: 0 }}>
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
                              {tieneNuevos && (
                                <span
                                  aria-label="Mensaje nuevo"
                                  title="Mensaje nuevo"
                                  style={{
                                    position: 'absolute',
                                    top: -2,
                                    right: -2,
                                    width: '16px',
                                    height: '16px',
                                    borderRadius: '50%',
                                    backgroundColor: 'var(--color-danger, #D64545)',
                                    border: '2px solid var(--color-surface, #fff)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center'
                                  }}
                                >
                                  <svg viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3.5" strokeLinecap="round" style={{ width: 8, height: 8 }}>
                                    <line x1="12" y1="8" x2="12" y2="13" />
                                    <circle cx="12" cy="16.5" r="0.5" fill="white" stroke="white" />
                                  </svg>
                                </span>
                              )}
                            </div>
                            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', textAlign: 'left' }}>
                              <span style={{ fontSize: '13px', fontWeight: tieneNuevos ? 'bold' : 'normal', color: 'var(--color-text-primary)' }}>{chan.nombre}</span>
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
                        backgroundColor: 'var(--green-50)',
                        padding: '8px 12px',
                        borderRadius: 'var(--radius-md)',
                        fontSize: '11px',
                        color: 'var(--color-primary-hover)',
                        textAlign: 'center',
                        maxWidth: '85%',
                        boxShadow: 'var(--shadow-sm)'
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
                              background: isMe ? 'linear-gradient(180deg, var(--green-400), var(--color-primary))' : 'var(--neutral-100)',
                              color: isMe ? 'var(--color-text-on-primary)' : 'var(--color-text-primary)',
                              padding: '7px 12px',
                              borderRadius: 'var(--radius-md)',
                              boxShadow: 'var(--shadow-sm)',
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
                      backgroundColor: 'var(--color-bg)',
                      borderTop: '1px solid var(--color-border)',
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
                        borderRadius: 'var(--radius-full)',
                        border: '1px solid var(--color-border)',
                        fontSize: '12px',
                        outline: 'none',
                        backgroundColor: 'var(--color-surface)'
                      }}
                    />
                    <button
                      type="submit"
                      style={{
                        background: 'linear-gradient(180deg, var(--green-400), var(--color-primary))',
                        color: 'white',
                        border: 'none',
                        borderRadius: 'var(--radius-full)',
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
