import { useState, useMemo, useEffect } from 'react'
import '../styles/landing.css'
import { api } from '../api/api'

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
function ProCard({ pro, onBook }: { pro: Professional; onBook: (p: Professional) => void }) {
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

      <div className="pro-card__footer">
        <div className="pro-card__price">
          <span className="pro-card__price-label">50 min · Online</span>
        </div>
        <button
          className="btn btn--primary btn--sm"
          id={`btn-book-${pro.id}`}
          onClick={(e) => { e.stopPropagation(); onBook(pro) }}
          aria-label={`Reservar turno con ${pro.name}`}
          style={{ whiteSpace: 'nowrap' }}
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

// ── Trust Strip (carousel) ─────────────────────────────────────
const TRUST_ITEMS = [
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>, text: 'Profesionales con matrícula verificada' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></svg>, text: 'Transacciones 100% encriptadas' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>, text: 'Turnos disponibles en menos de 24hs' },
]

function TrustStrip() {
  return (
    <div className="trust-strip" aria-label="Garantías de servicio">
      <div className="trust-strip__inner">
        {TRUST_ITEMS.map((item, idx) => (
          <div className="trust-item" key={idx} role="listitem">
            <span className="trust-item__icon" aria-hidden="true">{item.icon}</span>
            <span className="trust-item__text">{item.text}</span>
          </div>
        ))}
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

  const filtered = useMemo(() => {
    return professionals.filter((pro) => {
      const matchesQuery = query === '' ||
        pro.name.toLowerCase().includes(query.toLowerCase()) ||
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
            /* Locked call to action card for unauthenticated users */
            <div className="card" style={{
              maxWidth: '520px',
              width: '100%',
              padding: 'var(--space-6) var(--space-8)',
              marginTop: 'var(--space-6)',
              background: 'linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)',
              border: '1px solid #bbf7d0',
              borderRadius: 'var(--radius-lg)',
              boxShadow: 'var(--shadow-md)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 'var(--space-4)',
              textAlign: 'center'
            }}>
              <div style={{
                width: '56px',
                height: '56px',
                borderRadius: '50%',
                backgroundColor: 'var(--color-primary)',
                color: 'var(--color-text-on-primary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: 'var(--shadow-sm)'
              }}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 24, height: 24 }}>
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
              </div>
              <h3 style={{
                fontFamily: 'var(--font-heading)',
                fontSize: 'var(--text-lg)',
                fontWeight: 'var(--font-weight-bold)',
                color: 'var(--green-900)',
                margin: 0
              }}>
                Consultas y Turnos Protegidos
              </h3>
              <p style={{
                fontSize: 'var(--text-sm)',
                color: 'var(--green-800)',
                lineHeight: 'var(--line-height-relaxed)',
                margin: 0
              }}>
                Para ver el listado completo de profesionales certificados de salud mental, consultar horarios disponibles y agendar tu sesión de forma segura, primero iniciá sesión.
              </p>
              <button
                className="btn btn--primary"
                onClick={onNavigateToDashboard}
                style={{
                  padding: 'var(--space-3) var(--space-6)',
                  fontWeight: 'var(--font-weight-semi)',
                  boxShadow: 'var(--shadow-md)',
                  marginTop: 'var(--space-2)'
                }}
              >
                Iniciar sesión
              </button>
            </div>
          )}
        </div>
      </section>

      {/* Results (Only displayed if logged in) */}
      {currentUser && (
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
                    <ProCard pro={pro} onBook={handleBook} />
                  </div>
                ))
            }
          </div>
        </section>
      )}
    </>
  )
}
