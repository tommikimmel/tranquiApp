import { useState, useMemo, useEffect, useRef } from 'react'
import './landing.css'
import { api } from './api'

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
          <span className="pro-card__price-amount">Desde ${pro.price.toLocaleString('es-AR')}</span>
          <span className="pro-card__price-label">50 min · Online</span>
        </div>
        <button
          className="btn btn--primary btn--sm"
          id={`btn-book-${pro.id}`}
          onClick={(e) => { e.stopPropagation(); onBook(pro) }}
          aria-label={`Reservar turno con ${pro.name}`}
        >
          {pro.nextSlotDay === 'Hoy'
            ? <><span style={{ fontSize: '0.65rem', opacity: 0.85, display: 'block', lineHeight: 1 }}>HOY</span>{pro.nextSlot} hs</>
            : <><span style={{ fontSize: '0.65rem', opacity: 0.85, display: 'block', lineHeight: 1 }}>{pro.nextSlotDay.toUpperCase()}</span>{pro.nextSlot} hs</>
          }
        </button>
      </div>
    </article>
  )
}

// ── Public Header ──────────────────────────────────────────────
function PublicHeader({ onCrisis, onProLogin }: { onCrisis: () => void; onProLogin: () => void }) {
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
            id="btn-crisis-header"
            aria-label="Ayuda en crisis - líneas de emergencia"
          >
            <span className="btn-crisis__dot" aria-hidden="true" />
            Ayuda urgente
          </button>
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
        </div>
      </div>
    </header>
  )
}

// ── Trust Strip (carousel) ─────────────────────────────────────
const TRUST_ITEMS = [
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>, text: 'Profesionales con matrícula verificada' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></svg>, text: 'Pagos seguros con Mercado Pago' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /><path d="M8 14l2 2 4-4" /></svg>, text: 'Cancelación gratuita hasta 24hs antes' },
  { icon: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /></svg>, text: 'Factura para reintegro de prepaga' },
]

function TrustStrip() {
  const [active, setActive] = useState(0)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    intervalRef.current = setInterval(() => {
      setActive(prev => (prev + 1) % TRUST_ITEMS.length)
    }, 3000)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [])

  return (
    <div className="trust-strip" aria-label="Garantías de la plataforma">
      <div className="trust-carousel" role="list">
        <div className="trust-carousel__viewport">
          <div className="trust-carousel__track" style={{ transform: `translateX(-${active * 100}%)` }}>
            {TRUST_ITEMS.map((item, i) => (
              <div className="trust-carousel__slide" key={i} role="listitem" aria-hidden={active !== i}>
                <span className="trust-carousel__icon">{item.icon}</span>
                {item.text}
              </div>
            ))}
          </div>
        </div>
        <div className="trust-carousel__dots">
          {TRUST_ITEMS.map((_, i) => (
            <button
              key={i}
              className={`trust-carousel__dot ${active === i ? 'active' : ''}`}
              onClick={() => setActive(i)}
              aria-label={`Garantía ${i + 1}`}
            />
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
}

export default function LandingPage({ onNavigateToDashboard, onBook }: { onNavigateToDashboard: () => void; onBook?: (pro: BookTarget) => void }) {
  const [query, setQuery] = useState('')
  const [activeSpecialty, setActiveSpecialty] = useState('Todos')
  const [professionals, setProfessionals] = useState<Professional[]>([])
  const [loading, setLoading] = useState(true)
  const [showCrisis, setShowCrisis] = useState(false)

  useEffect(() => {
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
  }, [])

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
        onCrisis={() => setShowCrisis(true)}
        onProLogin={onNavigateToDashboard}
      />

      <TrustStrip />

      {/* Hero */}
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero__inner">
          <div className="hero__eyebrow">
            Psicólogos y psiquiatras en Córdoba
          </div>
          <h1 className="hero__title" id="hero-title">
            Encontrá tu espacio<br />para estar <em>tranqui</em>
          </h1>
          <p className="hero__subtitle">
            Sesiones online de 50 minutos con profesionales certificados.
            Agenda, pagá y empezá hoy.
          </p>

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
        </div>
      </section>

      {/* Results */}
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
    </>
  )
}
