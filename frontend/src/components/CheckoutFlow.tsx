import { useState, useMemo, useEffect } from 'react'
import '../styles/checkout.css'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

function IconCheck({ size = 12 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}

// ── Types ──────────────────────────────────────────────────────
interface Professional {
  id: string
  name: string
  degree: string
  specialty: string
  matricula: string
  price: number
  nextSlot: string
  nextSlotDay: string
}

interface TimeSlot {
  time: string
  available: boolean
}

interface DayOption {
  date: string
  label: string
  sublabel: string
  slots: TimeSlot[]
}

type CheckoutStep = 'select' | 'review' | 'confirmed'
type PaymentStatus = 'idle' | 'processing' | 'error'

// ── Mock Data ──────────────────────────────────────────────────
// generateDays mock function removed since availability is loaded from API

// ── Header ─────────────────────────────────────────────────────
function CheckoutHeader({ step, onBack }: { step: CheckoutStep; onBack: () => void }) {
  const stepLabels: Record<CheckoutStep, string> = {
    select: 'Elegí tu horario',
    review: 'Confirmá tu turno',
    confirmed: 'Turno confirmado',
  }

  return (
    <header className="checkout-header">
      <div className="checkout-header__inner">
        {step !== 'confirmed' && (
          <button className="checkout-header__back" onClick={onBack} aria-label="Volver">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: 20, height: 20 }}>
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
        )}
        <a href="/" className="checkout-header__logo" onClick={(e) => { e.preventDefault(); onBack() }}>
          tranqui
        </a>
        <div className="checkout-header__step-label">{stepLabels[step]}</div>
        <div style={{ flex: 1 }} />
        <div className="checkout-header__secure">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 14, height: 14 }}>
            <rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
          Pago seguro
        </div>
      </div>

      {step !== 'confirmed' && (
        <div className="checkout-progress">
          <div className={`checkout-progress__step ${step === 'select' || step === 'review' ? 'active' : ''}`}>
            <span className="checkout-progress__dot">1</span>
            Horario
          </div>
          <div className="checkout-progress__line" />
          <div className={`checkout-progress__step ${step === 'review' ? 'active' : ''}`}>
            <span className="checkout-progress__dot">2</span>
            Pago
          </div>
        </div>
      )}
    </header>
  )
}

// ── Step 1: Select Slot ────────────────────────────────────────
function StepSelect({
  professional,
  onSelect,
}: {
  professional: Professional
  onSelect: (day: DayOption, slot: TimeSlot) => void
}) {
  const [days, setDays] = useState<DayOption[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedDay, setSelectedDay] = useState(0)
  const currentDay = days[selectedDay]

  useEffect(() => {
    const fetchAvailability = async () => {
      setLoading(true)
      const weekdays = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
      const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
      const now = new Date()
      
      const datesToFetch: { dateStr: string; label: string; sublabel: string }[] = []
      for (let i = 0; i < 7; i++) {
        const d = new Date(now)
        d.setDate(d.getDate() + i)
        if (d.getDay() === 0 || d.getDay() === 6) continue

        const label = i === 0 ? 'Hoy' : i === 1 ? 'Mañana' : weekdays[d.getDay()]
        const sublabel = `${d.getDate()} ${months[d.getMonth()]}`
        const dateStr = d.toISOString().split('T')[0]
        datesToFetch.push({ dateStr, label, sublabel })
      }

      try {
        const results = await Promise.all(
          datesToFetch.map(item => 
            api.getTurnosDisponibles(professional.id, item.dateStr)
              .then(slots => ({ ...item, slots: slots || [] }))
              .catch(err => {
                console.error("Error fetching single day:", err)
                return { ...item, slots: [] }
              })
          )
        )

        const formattedDays: DayOption[] = results.map(res => {
          let slots = res.slots.map((s: string) => ({
            time: s.slice(0, 5),
            available: true
          }));

          // If the day is today, filter out past slots based on Buenos Aires timezone context
          if (res.label === 'Hoy') {
            const targetTimeZone = 'America/Argentina/Buenos_Aires';
            const nowInBA = new Date(new Date().toLocaleString('en-US', { timeZone: targetTimeZone }));
            const currentHour = nowInBA.getHours();
            const currentMinute = nowInBA.getMinutes();

            slots = slots.filter((slot: { time: string; available: boolean }) => {
              const [shStr, smStr] = slot.time.split(':');
              const slotHour = parseInt(shStr, 10);
              const slotMin = parseInt(smStr, 10);
              if (slotHour < currentHour) return false;
              if (slotHour === currentHour && slotMin < currentMinute) return false;
              return true;
            });
          }

          return {
            date: res.dateStr,
            label: res.label,
            sublabel: res.sublabel,
            slots
          };
        })

        setDays(formattedDays)
      } catch (err) {
        console.error("Error al obtener disponibilidad:", err)
      } finally {
        setLoading(false)
      }
    }

    fetchAvailability()
  }, [professional.id])

  if (loading) {
    return (
      <div className="checkout-body" style={{ textAlign: 'center', padding: 'var(--space-10)' }}>
        <div className="checkout-spinner" style={{ margin: '0 auto var(--space-4)' }} />
        <p style={{ color: 'var(--color-text-secondary)' }}>Buscando turnos disponibles...</p>
      </div>
    )
  }

  return (
    <div className="checkout-body">
      {/* Professional summary card */}
      <div className="checkout-pro-card">
        <div className="checkout-pro-card__avatar" aria-hidden="true">
          {professional.name.split(' ').filter((_, i) => i === 0 || i === professional.name.split(' ').length - 1).map(w => w[0]).join('')}
        </div>
        <div className="checkout-pro-card__info">
          <div className="checkout-pro-card__name">{professional.name}</div>
          <div className="checkout-pro-card__specialty">{professional.degree} · {professional.specialty}</div>
          <span className="checkout-pro-card__matricula">{professional.matricula} <IconCheck /></span>
        </div>
      </div>

      {/* Day selector */}
      <div className="checkout-section">
        <h2 className="checkout-section__title">Elegí el día</h2>
        <div className="day-selector" role="radiogroup" aria-label="Seleccionar día">
          {days.map((day, i) => {
            const availableCount = day.slots.filter(s => s.available).length
            return (
              <button
                key={day.date}
                className={`day-selector__item ${selectedDay === i ? 'active' : ''} ${availableCount === 0 ? 'disabled' : ''}`}
                onClick={() => availableCount > 0 && setSelectedDay(i)}
                role="radio"
                aria-checked={selectedDay === i}
                disabled={availableCount === 0}
              >
                <span className="day-selector__label">{day.label}</span>
                <span className="day-selector__date">{day.sublabel}</span>
                <span className={`day-selector__avail ${availableCount === 0 ? 'none' : ''}`}>
                  {availableCount === 0 ? 'Sin turnos' : `${availableCount} turnos`}
                </span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Time slots */}
      {currentDay && (
        <div className="checkout-section">
          <h2 className="checkout-section__title">
            Horarios disponibles — {currentDay.label} {currentDay.sublabel}
          </h2>
          <div className="slot-grid" role="radiogroup" aria-label="Seleccionar horario">
            {currentDay.slots.map((slot) => (
              <button
                key={slot.time}
                className={`slot-btn ${slot.available ? '' : 'slot-btn--taken'}`}
                disabled={!slot.available}
                onClick={() => onSelect(currentDay, slot)}
                aria-label={`${slot.time} ${slot.available ? '— disponible' : '— no disponible'}`}
              >
                {slot.time} hs
                {!slot.available && <span className="slot-btn__tag">Ocupado</span>}
              </button>
            ))}
          </div>
          {currentDay.slots.length === 0 && (
            <div className="checkout-empty">
              No hay horarios disponibles este día. Probá otro día.
            </div>
          )}
        </div>
      )}

      {/* Info strip */}
      <div className="checkout-info-strip">
        <div className="checkout-info-strip__item">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 16, height: 16 }}>
            <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
          </svg>
          Sesión de 50 minutos por videollamada
        </div>
        <div className="checkout-info-strip__item">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 16, height: 16 }}>
            <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" />
          </svg>
          Cancelación gratuita hasta 24hs antes
        </div>
      </div>
    </div>
  )
}

// ── Step 2: Review & Pay ───────────────────────────────────────
function StepReview({
  professional,
  selectedDay,
  selectedSlot,
  onPay,
  onBack,
  paymentStatus,
  errorMessage,
}: {
  professional: Professional
  selectedDay: DayOption
  selectedSlot: TimeSlot
  onPay: (data: { name: string; email: string; phone: string; tipo: 'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'; afiliado?: string; customTime?: string }) => void
  onBack: () => void
  paymentStatus: PaymentStatus
  errorMessage?: string | null
}) {
  const getCachedUserData = () => {
    try {
      const cached = localStorage.getItem('tranqui_user')
      if (cached) {
        return JSON.parse(cached)
      }
    } catch (e) {}
    return null
  }
  const cachedUser = getCachedUserData()

  const [name, setName] = useState(cachedUser?.nombre || '')
  const [email, setEmail] = useState(cachedUser?.email || '')
  const [phone, setPhone] = useState(cachedUser?.telefono || '')
  const [tipo, setTipo] = useState<'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'>('PARTICULAR')
  const [afiliado, setAfiliado] = useState('')
  const [customTime, setCustomTime] = useState(selectedSlot.time)
  const [acceptedTerms, setAcceptedTerms] = useState(false)

  const [isFirstTime, setIsFirstTime] = useState(false)
  const [showFirstTimeAlert, setShowFirstTimeAlert] = useState(false)

  const handleEmailBlur = async () => {
    if (email.trim().includes('@')) {
      try {
        const isFirst = await api.checkFirstConsultation(email.trim())
        if (isFirst) {
          setIsFirstTime(true)
          setShowFirstTimeAlert(true)
        } else {
          setIsFirstTime(false)
        }
      } catch (err) {
        console.error("Error al verificar primera consulta:", err)
      }
    }
  }

  useEffect(() => {
    if (email) {
      handleEmailBlur()
    }
  }, [])

  const services = [
    { id: 'PARTICULAR', label: 'Consulta Particular', price: professional.price, desc: 'Consulta estándar de 50 minutos' },
    { id: 'OSDE', label: 'Copago OSDE', price: 10500, desc: 'Requiere número de afiliado' },
    { id: 'RECETA', label: 'Receta fuera de turno', price: 45000, desc: 'Solicitud de recetas o órdenes médicas' },
    { id: 'CERTIFICADO', label: 'Certificado', price: 55000, desc: 'Emisión de certificados aptos y licencias' },
    { id: 'SOBRETUNO', label: 'Sobre turno', price: 90000, desc: 'Horario personalizado fuera de agenda' }
  ] as const

  const currentPrice = useMemo(() => {
    const s = services.find(x => x.id === tipo)
    let basePrice = s ? s.price : professional.price
    if (isFirstTime) {
      basePrice = Math.round(basePrice * 1.30)
    }
    return basePrice
  }, [tipo, professional.price, isFirstTime])

  const canPay = name.trim().length > 2 && 
                  email.includes('@') && 
                  phone.length >= 8 && 
                  (tipo !== 'OSDE' || afiliado.trim().length > 4) &&
                  (tipo !== 'SOBRETUNO' || /^([01]\d|2[0-3]):[0-5]\d$/.test(customTime)) &&
                  acceptedTerms && 
                  paymentStatus !== 'processing';

  const handlePayClick = () => {
    if (canPay) {
      onPay({
        name,
        email,
        phone,
        tipo,
        afiliado: tipo === 'OSDE' ? afiliado : undefined,
        customTime: tipo === 'SOBRETUNO' ? customTime : undefined
      });
    }
  };

  return (
    <div className="checkout-body">
      {/* Appointment summary */}
      <div className="checkout-summary-card">
        <h2 className="checkout-summary-card__title">Resumen de tu turno</h2>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Profesional</span>
          <span className="checkout-summary-card__value">{professional.name}</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Especialidad</span>
          <span className="checkout-summary-card__value">{professional.specialty}</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Fecha</span>
          <span className="checkout-summary-card__value">{selectedDay.label} {selectedDay.sublabel}</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Horario</span>
          <span className="checkout-summary-card__value">
            {tipo === 'SOBRETUNO' ? customTime : selectedSlot.time} hs — 50 min
          </span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Cobertura</span>
          <span className="checkout-summary-card__value">
            {services.find(x => x.id === tipo)?.label || 'Particular'}
          </span>
        </div>
        <div className="checkout-summary-card__divider" />
        <div className="checkout-summary-card__row checkout-summary-card__row--total">
          <span className="checkout-summary-card__label">Total a pagar</span>
          <span className="checkout-summary-card__value">${currentPrice.toLocaleString('es-AR')}</span>
        </div>
        <button className="checkout-summary-card__change" onClick={onBack}>
          Cambiar horario
        </button>
      </div>

      {/* Tipo de consulta select */}
      <div className="checkout-section">
        <h2 className="checkout-section__title">Tipo de consulta</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
          {services.map((s) => {
            const isSelected = tipo === s.id
            return (
              <div
                key={s.id}
                onClick={() => setTipo(s.id)}
                style={{
                  padding: 'var(--space-4)',
                  border: isSelected ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  backgroundColor: isSelected ? 'var(--green-50)' : 'var(--color-surface)',
                  cursor: 'pointer',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  transition: 'all 0.2s',
                  boxShadow: 'var(--shadow-sm)'
                }}
              >
                <div>
                  <div style={{ fontWeight: 'var(--font-weight-bold)', fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                    {s.label}
                  </div>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    {s.desc}
                  </div>
                </div>
                <div style={{ fontWeight: 'var(--font-weight-semi)', fontSize: 'var(--text-sm)', color: 'var(--color-primary)' }}>
                  ${s.price.toLocaleString('es-AR')}
                </div>
              </div>
            )
          })}
        </div>

        {tipo === 'OSDE' && (
          <div className="form-group" style={{ animation: 'fadeIn 150ms ease-out', marginTop: 'var(--space-4)' }}>
            <label className="form-label form-label--required" htmlFor="checkout-afiliado">Número de afiliado OSDE</label>
            <input
              id="checkout-afiliado"
              className="form-input"
              type="text"
              placeholder="Ej: 1-234567-8"
              value={afiliado}
              onChange={(e) => setAfiliado(e.target.value)}
            />
          </div>
        )}

        {tipo === 'SOBRETUNO' && (
          <div className="form-group" style={{ animation: 'fadeIn 150ms ease-out', marginTop: 'var(--space-4)' }}>
            <label className="form-label form-label--required" htmlFor="checkout-customtime">Horario del sobreturno</label>
            <input
              id="checkout-customtime"
              className="form-input"
              type="time"
              value={customTime}
              onChange={(e) => setCustomTime(e.target.value)}
              style={{ maxWidth: '150px' }}
            />
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'block', marginTop: '4px' }}>
              Especificá la hora exacta en la que querés realizar la consulta.
            </span>
          </div>
        )}
      </div>

      {/* Patient info */}
      <div className="checkout-section">
        <h2 className="checkout-section__title">Tus datos</h2>
        <p className="checkout-section__subtitle">Necesitamos estos datos para enviarte la confirmación y el link de la sesión.</p>

        <div className="checkout-form">
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="checkout-name">Nombre completo</label>
            <input
              id="checkout-name"
              className="form-input"
              type="text"
              placeholder="Ej: María García"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
            />
          </div>
          <div className="checkout-form__row">
            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="checkout-email">Email</label>
              <input
                id="checkout-email"
                className="form-input"
                type="email"
                placeholder="tu@email.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={handleEmailBlur}
                autoComplete="email"
                readOnly={!!cachedUser}
                style={cachedUser ? { backgroundColor: '#f3f4f6', cursor: 'not-allowed' } : {}}
              />
            </div>
            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="checkout-phone">Teléfono</label>
              <input
                id="checkout-phone"
                className="form-input"
                type="tel"
                placeholder="351 123 4567"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                autoComplete="tel"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Terms + Pay */}
      <div className="checkout-section">
        <label className="checkout-terms">
          <input
            type="checkbox"
            checked={acceptedTerms}
            onChange={(e) => setAcceptedTerms(e.target.checked)}
            id="checkout-accept-terms"
          />
          <span>
            Acepto los <a href="#" onClick={(e) => e.preventDefault()}>términos y condiciones</a> y
            la <a href="#" onClick={(e) => e.preventDefault()}>política de cancelación</a> (reembolso
            completo hasta 24hs antes).
          </span>
        </label>
 
        {paymentStatus === 'error' && (
          <div className="checkout-error" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 18, height: 18, flexShrink: 0 }}>
              <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            {errorMessage || "El pago no pudo procesarse. Revisá los datos de tu medio de pago e intentá de nuevo."}
          </div>
        )}

        <button
          className={`btn btn--primary btn--lg checkout-pay-btn ${paymentStatus === 'processing' ? 'btn--loading' : ''}`}
          disabled={!canPay}
          onClick={handlePayClick}
          id="btn-pay-mp"
        >
          {paymentStatus === 'processing' ? (
            <>
              <span className="checkout-spinner" aria-hidden="true" />
              Procesando pago…
            </>
          ) : (
            <>
              Pagar ${currentPrice.toLocaleString('es-AR')} con Mercado Pago
            </>
          )}
        </button>

        <div className="checkout-pay-methods">
          <span>Medios de pago:</span>
          Tarjeta de crédito · Tarjeta de débito · Transferencia · Mercado Pago
        </div>
      </div>

      {/* Cancellation policy */}
      <div className="checkout-policy">
        <h3 className="checkout-policy__title">Política de cancelación</h3>
        <ul className="checkout-policy__list">
          <li>Cancelación gratuita hasta 24 horas antes del turno.</li>
          <li>Cancelaciones con menos de 24hs: sin reembolso.</li>
          <li>Si el profesional cancela, se reprograma o reembolsa el 100%.</li>
        </ul>
      </div>

      {showFirstTimeAlert && (
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
              backgroundColor: '#fef3c7',
              color: '#d97706',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 24, height: 24 }}>
                <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>
            <h3 style={{
              fontFamily: 'var(--font-heading)',
              fontSize: 'var(--text-lg)',
              fontWeight: 'var(--font-weight-bold)',
              color: 'var(--color-text-primary)',
              margin: 0
            }}>
              ¡Primera Consulta!
            </h3>
            <p style={{
              fontSize: 'var(--text-sm)',
              color: 'var(--color-text-secondary)',
              lineHeight: 'var(--line-height-relaxed)',
              margin: 0
            }}>
              Se aplicará un recargo único del 30% en tu primer turno. El monto ya fue actualizado en tu resumen.
            </p>
            <button
              className="btn btn--primary"
              onClick={() => setShowFirstTimeAlert(false)}
              style={{ width: '100%', marginTop: 'var(--space-2)' }}
            >
              Aceptar
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Step 3: Confirmation ───────────────────────────────────────
function StepConfirmed({
  professional,
  selectedDay,
  selectedSlot,
  onDone,
  meetLink,
  createdTurn,
}: {
  professional: Professional
  selectedDay: DayOption
  selectedSlot: TimeSlot
  onDone: () => void
  meetLink?: string
  createdTurn?: any
}) {
  const actualMeetLink = meetLink || `https://meet.google.com/${Math.random().toString(36).slice(2, 5)}-${Math.random().toString(36).slice(2, 6)}-${Math.random().toString(36).slice(2, 5)}`

  return (
    <div className="checkout-body checkout-body--confirmed">
      <div className="checkout-confirmed">
        <div className="checkout-confirmed__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 40, height: 40 }}>
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </div>
        <h1 className="checkout-confirmed__title">¡Turno confirmado!</h1>
        <p className="checkout-confirmed__subtitle">
          Te enviamos un email con todos los detalles y el link de la videollamada.
        </p>
      </div>

      <div className="checkout-summary-card">
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Profesional</span>
          <span className="checkout-summary-card__value">{professional.name}</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Fecha y hora</span>
          <span className="checkout-summary-card__value">{selectedDay.label} {selectedDay.sublabel} · {selectedSlot.time} hs</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Duración</span>
          <span className="checkout-summary-card__value">50 minutos</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Pagado</span>
          <span className="checkout-summary-card__value checkout-summary-card__value--paid">
            ${createdTurn?.precio ? createdTurn.precio.toLocaleString('es-AR') : professional.price.toLocaleString('es-AR')}
          </span>
        </div>
      </div>

      {/* Meet link */}
      <div className="checkout-meet-card">
        <div className="checkout-meet-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 24, height: 24 }}>
            <polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" />
          </svg>
        </div>
        <div className="checkout-meet-card__content">
          <div className="checkout-meet-card__label">Link de videollamada</div>
          <div className="checkout-meet-card__sublabel">Este link se activa 10 minutos antes de tu sesión</div>
        </div>
        <a
          href={actualMeetLink}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn--primary btn--sm"
          id="btn-meet-link"
        >
          Abrir Meet
        </a>
      </div>

      {/* Next steps */}
      <div className="checkout-next-steps">
        <h3 className="checkout-next-steps__title">Próximos pasos</h3>
        <ol className="checkout-next-steps__list">
          <li>Revisá tu email — te enviamos la confirmación con todos los datos.</li>
          <li>10 minutos antes de la sesión, abrí el link de Google Meet desde arriba.</li>
          <li>Buscá un lugar tranquilo con buena conexión a internet.</li>
        </ol>
      </div>

      <div className="checkout-actions">
        <button className="btn btn--primary btn--lg" onClick={onDone} id="btn-back-home">
          Volver al inicio
        </button>
      </div>
    </div>
  )
}

// ── Main Checkout Flow ─────────────────────────────────────────
export default function CheckoutFlow({
  professional,
  onBack,
  onComplete,
}: {
  professional: Professional
  onBack: () => void
  onComplete: () => void
}) {
  const { showAlert } = useAlert()
  const [step, setStep] = useState<CheckoutStep>('select')
  const [selectedDay, setSelectedDay] = useState<DayOption | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null)
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [createdTurn, setCreatedTurn] = useState<any>(null)
  const [showMockPaymentGateway, setShowMockPaymentGateway] = useState(false)
  const [simulatingWebhook, setSimulatingWebhook] = useState(false)

  const handleSelectSlot = (day: DayOption, slot: TimeSlot) => {
    setSelectedDay(day)
    setSelectedSlot(slot)
    setStep('review')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handlePay = (patientData: { name: string; email: string; phone: string; tipo: 'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'; afiliado?: string; customTime?: string }) => {
    setPaymentStatus('processing')
    const finalTime = patientData.tipo === 'SOBRETUNO' && patientData.customTime 
      ? patientData.customTime 
      : selectedSlot!.time;

    api.reservarTurno({
      medicoId: Number(professional.id),
      fecha: selectedDay!.date,
      hora: finalTime + ":00",
      tipo: patientData.tipo,
      metadataAfiliado: patientData.afiliado,
      nombrePaciente: patientData.name,
      emailPaciente: patientData.email,
      telefonoPaciente: patientData.phone
    })
    .then((res: any) => {
      setPaymentStatus('idle')
      setCreatedTurn(res)
      if (res.checkoutUrl) {
        // En entorno local/desarrollo, abrimos la simulación de Mercado Pago
        // para poder probar el flujo completo (incluyendo el webhook).
        setShowMockPaymentGateway(true)
      } else {
        // Copago OSDE o pre-confirmado, pasar a pantalla de confirmado
        setStep('confirmed')
        window.scrollTo({ top: 0, behavior: 'smooth' })
      }
    })
    .catch((err) => {
      console.error("Error al procesar reserva de turno:", err)
      setPaymentStatus('error')
      setErrorMessage(err.message || "El pago no pudo procesarse. Revisá los datos de tu medio de pago e intentá de nuevo.")
    })
  }

  const handleBack = () => {
    if (step === 'review') {
      setStep('select')
      setPaymentStatus('idle')
    } else {
      onBack()
    }
  }

  return (
    <div className="checkout-layout">
      <CheckoutHeader step={step} onBack={step === 'confirmed' ? onComplete : handleBack} />

      {step === 'select' && (
        <StepSelect professional={professional} onSelect={handleSelectSlot} />
      )}

      {step === 'review' && selectedDay && selectedSlot && (
        <StepReview
          professional={professional}
          selectedDay={selectedDay}
          selectedSlot={selectedSlot}
          onPay={handlePay}
          onBack={() => setStep('select')}
          paymentStatus={paymentStatus}
          errorMessage={errorMessage}
        />
      )}

      {step === 'confirmed' && selectedDay && selectedSlot && (
        <StepConfirmed
          professional={professional}
          selectedDay={selectedDay}
          selectedSlot={selectedSlot}
          onDone={onComplete}
          meetLink={createdTurn?.meetLink}
          createdTurn={createdTurn}
        />
      )}
      {showMockPaymentGateway && createdTurn && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1100,
          padding: 'var(--space-4)'
        }}>
          <div className="card" style={{
            maxWidth: '480px',
            width: '100%',
            padding: 'var(--space-6)',
            boxShadow: 'var(--shadow-xl)',
            backgroundColor: '#ffffff',
            borderRadius: 'var(--radius-lg)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-4)'
          }}>
            {/* Header style like Mercado Pago */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderBottom: '2px solid #009EE3', // Mercado Pago blue
              paddingBottom: 'var(--space-3)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <span style={{ fontWeight: 'bold', color: '#009EE3', fontSize: 'var(--text-lg)' }}>Mercado Pago</span>
                <span className="badge badge--neutral" style={{ fontSize: '9px', backgroundColor: '#e5e7eb', color: '#4b5563' }}>SANDBOX / TEST</span>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              <h4 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 'bold' }}>Simulador de Pago de Turno</h4>
              <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
                Estás en modo de desarrollo local. Para facilitar las pruebas sin configurar cuentas reales ni túneles SSL (como ngrok), podés aprobar o rechazar el pago simulando la llamada del webhook de Mercado Pago de forma directa.
              </p>
            </div>

            {/* Turn details */}
            <div style={{
              backgroundColor: 'var(--neutral-50)',
              padding: 'var(--space-4)',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--color-border)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-2)',
              fontSize: 'var(--text-xs)'
            }}>
              <div><strong>Turno ID:</strong> #{createdTurn.turnoId}</div>
              <div><strong>Profesional:</strong> {professional.name}</div>
              <div><strong>Fecha:</strong> {createdTurn.fecha} a las {createdTurn.horaInicio} hs</div>
              <div><strong>Monto a abonar:</strong> ${createdTurn.precio}</div>
            </div>

            {/* Actions */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', marginTop: 'var(--space-4)' }}>
              <button
                disabled={simulatingWebhook}
                onClick={async () => {
                  setSimulatingWebhook(true);
                  try {
                    // Send request to webhook controller directly
                    const response = await fetch('http://localhost:8081/api/payments/webhook', {
                      method: 'POST',
                      headers: {
                        'Content-Type': 'application/json',
                        'x-signature': 'test-signature'
                      },
                      body: JSON.stringify({
                        external_reference: String(createdTurn.turnoId),
                        transaction_id: "mock-tx-" + Math.floor(Math.random() * 1000000)
                      })
                    });

                    if (response.ok) {
                      // Fetch updated details so we get the meetLink if generated
                      try {
                        const updatedTurn = await api.getTurnos();
                        const matching = updatedTurn.find((t: any) => t.id === createdTurn.turnoId);
                        if (matching) {
                          setCreatedTurn((prev: any) => ({
                            ...prev,
                            meetLink: matching.meetLink || prev.meetLink
                          }));
                        }
                      } catch (e) {
                        console.error("Error fetching updated turn:", e);
                      }
                      
                      showAlert("Pago acreditado. Webhook simulado con éxito.", "success");
                      setShowMockPaymentGateway(false);
                      setStep('confirmed');
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    } else {
                      showAlert("Error al simular la aprobación en el backend.", "error");
                    }
                  } catch (err) {
                    console.error("Error sending mock webhook request:", err);
                    showAlert("Error de conexión al simular el pago.", "error");
                  } finally {
                    setSimulatingWebhook(false);
                  }
                }}
                className="btn btn--primary"
                style={{ backgroundColor: 'var(--color-primary)', border: 'none', justifyContent: 'center' }}
              >
                {simulatingWebhook ? 'Simulando acreditación...' : 'Simular Pago Exitoso (Aprobar)'}
              </button>

              <button
                disabled={simulatingWebhook}
                onClick={() => {
                  setShowMockPaymentGateway(false);
                  setPaymentStatus('error');
                  setErrorMessage("Pago rechazado por el usuario en la simulación.");
                }}
                className="btn btn--ghost"
                style={{ color: 'var(--color-danger)', border: '1px solid var(--color-danger)', justifyContent: 'center' }}
              >
                Simular Pago Rechazado (Cancelar)
              </button>

              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', margin: 'var(--space-2) 0' }}>
                <div style={{ flex: 1, height: '1px', backgroundColor: 'var(--color-border)' }}></div>
                <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>O bien</span>
                <div style={{ flex: 1, height: '1px', backgroundColor: 'var(--color-border)' }}></div>
              </div>

              <a
                href={createdTurn.checkoutUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="btn btn--secondary"
                style={{ justifyContent: 'center' }}
              >
                Abrir URL oficial de Mercado Pago
              </a>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
