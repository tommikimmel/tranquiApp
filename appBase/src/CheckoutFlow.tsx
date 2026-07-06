import { useState, useMemo } from 'react'
import './checkout.css'

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
function generateDays(): DayOption[] {
  const days: DayOption[] = []
  const weekdays = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
  const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
  const now = new Date()

  for (let i = 0; i < 7; i++) {
    const d = new Date(now)
    d.setDate(d.getDate() + i)

    if (d.getDay() === 0 || d.getDay() === 6) continue

    const label = i === 0 ? 'Hoy' : i === 1 ? 'Mañana' : weekdays[d.getDay()]
    const sublabel = `${d.getDate()} ${months[d.getMonth()]}`

    const baseSlots = ['09:00', '10:00', '11:00', '14:00', '15:00', '16:00', '17:00']
    const slots: TimeSlot[] = baseSlots.map((time) => ({
      time,
      available: Math.random() > 0.35,
    }))

    days.push({ date: d.toISOString().split('T')[0], label, sublabel, slots })
  }

  return days
}

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
  const days = useMemo(() => generateDays(), [])
  const [selectedDay, setSelectedDay] = useState(0)
  const currentDay = days[selectedDay]

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
          <span className="checkout-pro-card__matricula">{professional.matricula} ✓</span>
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
          {currentDay.slots.every(s => !s.available) && (
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
}: {
  professional: Professional
  selectedDay: DayOption
  selectedSlot: TimeSlot
  onPay: () => void
  onBack: () => void
  paymentStatus: PaymentStatus
}) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [acceptedTerms, setAcceptedTerms] = useState(false)

  const canPay = name.trim().length > 2 && email.includes('@') && phone.length >= 8 && acceptedTerms && paymentStatus !== 'processing'

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
          <span className="checkout-summary-card__value">{selectedSlot.time} hs — 50 min</span>
        </div>
        <div className="checkout-summary-card__divider" />
        <div className="checkout-summary-card__row checkout-summary-card__row--total">
          <span className="checkout-summary-card__label">Total a pagar</span>
          <span className="checkout-summary-card__value">${professional.price.toLocaleString('es-AR')}</span>
        </div>
        <button className="checkout-summary-card__change" onClick={onBack}>
          Cambiar horario
        </button>
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
                autoComplete="email"
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
            El pago no pudo procesarse. Revisá los datos de tu medio de pago e intentá de nuevo.
          </div>
        )}

        <button
          className={`btn btn--primary btn--lg checkout-pay-btn ${paymentStatus === 'processing' ? 'btn--loading' : ''}`}
          disabled={!canPay}
          onClick={onPay}
          id="btn-pay-mp"
        >
          {paymentStatus === 'processing' ? (
            <>
              <span className="checkout-spinner" aria-hidden="true" />
              Procesando pago…
            </>
          ) : (
            <>
              Pagar ${professional.price.toLocaleString('es-AR')} con Mercado Pago
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
    </div>
  )
}

// ── Step 3: Confirmation ───────────────────────────────────────
function StepConfirmed({
  professional,
  selectedDay,
  selectedSlot,
  onDone,
}: {
  professional: Professional
  selectedDay: DayOption
  selectedSlot: TimeSlot
  onDone: () => void
}) {
  const meetLink = `https://meet.google.com/${Math.random().toString(36).slice(2, 5)}-${Math.random().toString(36).slice(2, 6)}-${Math.random().toString(36).slice(2, 5)}`

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
            ${professional.price.toLocaleString('es-AR')} ✓
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
          href={meetLink}
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
  const [step, setStep] = useState<CheckoutStep>('select')
  const [selectedDay, setSelectedDay] = useState<DayOption | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null)
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('idle')

  const handleSelectSlot = (day: DayOption, slot: TimeSlot) => {
    setSelectedDay(day)
    setSelectedSlot(slot)
    setStep('review')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handlePay = () => {
    setPaymentStatus('processing')
    setTimeout(() => {
      setPaymentStatus('idle')
      setStep('confirmed')
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }, 2500)
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
        />
      )}

      {step === 'confirmed' && selectedDay && selectedSlot && (
        <StepConfirmed
          professional={professional}
          selectedDay={selectedDay}
          selectedSlot={selectedSlot}
          onDone={onComplete}
        />
      )}
    </div>
  )
}
