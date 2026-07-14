import { useState, useMemo, useEffect, useRef } from 'react'
import '../styles/checkout.css'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

let leafletLoadingPromise: Promise<void> | null = null
function loadLeafletScript(): Promise<void> {
  if ((window as any).L) {
    return Promise.resolve()
  }
  if (leafletLoadingPromise) return leafletLoadingPromise

  leafletLoadingPromise = new Promise((resolve, reject) => {
    // Leaflet needs its stylesheet applied *before* L.map() runs, otherwise the
    // map container has no position/overflow rules yet and tiles render
    // misplaced/blank. Wait for both the CSS and the script, not just the script.
    let cssReady = false
    let scriptReady = false
    const maybeResolve = () => { if (cssReady && scriptReady) resolve() }

    const linkId = 'leaflet-css'
    const existingLink = document.getElementById(linkId)
    if (existingLink) {
      cssReady = true
    } else {
      const link = document.createElement('link')
      link.id = linkId
      link.rel = 'stylesheet'
      link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'
      link.onload = () => { cssReady = true; maybeResolve() }
      link.onerror = () => { cssReady = true; maybeResolve() }
      document.head.appendChild(link)
    }

    const scriptId = 'leaflet-script'
    const script = document.createElement('script')
    script.id = scriptId
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    script.async = true
    script.onload = () => { scriptReady = true; maybeResolve() }
    script.onerror = () => reject(new Error('No se pudo cargar Leaflet'))
    document.body.appendChild(script)

    maybeResolve()
  })

  return leafletLoadingPromise
}

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
  fotoUrl?: string
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
  domicilioAtencion?: string
  domicilioLat?: number | null
  domicilioLng?: number | null
  descripcionPerfil?: string
  pacientesAtiende?: string[]
  institucionFormacion?: string
  aniosExperiencia?: number | null
  tags?: string[]
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
  paymentStatus,
  errorMessage,
}: {
  professional: Professional
  onSelect: (day: DayOption, slot: TimeSlot, patientData: { name: string; email: string; phone: string; tipo: 'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'; afiliado?: string; customTime?: string }) => void
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

  const [days, setDays] = useState<DayOption[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedDayIdx, setSelectedDayIdx] = useState<number | null>(null)
  const currentDay = selectedDayIdx !== null ? days[selectedDayIdx] : null
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null)
  const [viewMonth, setViewMonth] = useState(() => new Date())

  const handleSelectDayIdx = (idx: number) => {
    setSelectedDayIdx(idx)
    setSelectedSlot(null)
  }

  const initials = professional.name
    .split(' ')
    .filter((_, i, arr) => i === 0 || i === arr.length - 1)
    .map(w => w[0])
    .join('')
    .toUpperCase();

  // Form states
  const [name, setName] = useState(cachedUser?.nombre || '')
  const [email, setEmail] = useState(cachedUser?.email || '')
  const [phone, setPhone] = useState((cachedUser?.telefono || '').replace(/^\+54\s*/, ''))
  const [tipo, setTipo] = useState<'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'>('PARTICULAR')
  const [afiliado, setAfiliado] = useState('')
  const [customTime, setCustomTime] = useState('09:00')
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [isFirstTime, setIsFirstTime] = useState(false)
  const [showFirstTimeAlert, setShowFirstTimeAlert] = useState(false)
  const [hasShownAlert, setHasShownAlert] = useState(false)

  const mapContainerRef = useRef<HTMLDivElement>(null)
  const [leafletLoaded, setLeafletLoaded] = useState(!!(window as any).L)

  useEffect(() => {
    if (professional.domicilioLat || professional.domicilioLng) {
      loadLeafletScript()
        .then(() => setLeafletLoaded(true))
        .catch(err => console.error("Error loading Leaflet for step select map", err))
    }
  }, [professional])

  useEffect(() => {
    if (!leafletLoaded || !professional.domicilioLat || !professional.domicilioLng || !mapContainerRef.current) return
    const L = (window as any).L
    if (!L) return

    const lat = professional.domicilioLat
    const lng = professional.domicilioLng
    
    const map = L.map(mapContainerRef.current, {
      zoomControl: false,
      attributionControl: false,
      dragging: false,
      touchZoom: false,
      doubleClickZoom: false,
      scrollWheelZoom: false
    }).setView([lat, lng], 15)

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map)

    const customIcon = L.divIcon({
      html: `
        <svg viewBox="0 0 24 24" fill="none" stroke="#2E7D5B" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="width: 24px; height: 24px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); display: block;">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" fill="#ffffff" />
          <circle cx="12" cy="10" r="3.2" fill="#2E7D5B" />
        </svg>
      `,
      className: 'custom-leaflet-marker-mini',
      iconSize: [24, 24],
      iconAnchor: [12, 24]
    })

    L.marker([lat, lng], { icon: customIcon }).addTo(map)

    // Force a resize pass once the surrounding grid/panel layout has settled —
    // Leaflet measures its container synchronously at init, and a wrong initial
    // read (e.g. while web fonts are still swapping in) leaves the map blank.
    const resizeTimer = setTimeout(() => map.invalidateSize(), 150)
    const handleWindowResize = () => map.invalidateSize()
    window.addEventListener('resize', handleWindowResize)

    return () => {
      clearTimeout(resizeTimer)
      window.removeEventListener('resize', handleWindowResize)
      map.remove()
    }
  }, [leafletLoaded, professional.domicilioLat, professional.domicilioLng])

  const getDayOptionIndex = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const dateStr = `${year}-${month}-${day}`
    return days.findIndex(d => d.date === dateStr)
  }

  const gridDays = useMemo(() => {
    const year = viewMonth.getFullYear()
    const month = viewMonth.getMonth()
    const firstDay = new Date(year, month, 1)
    let startOffset = firstDay.getDay() - 1 // Lunes = 0
    if (startOffset === -1) startOffset = 6 // Domingo = 6

    const list: (Date | null)[] = []
    for (let i = 0; i < startOffset; i++) {
      list.push(null)
    }

    const lastDay = new Date(year, month + 1, 0).getDate()
    for (let d = 1; d <= lastDay; d++) {
      list.push(new Date(year, month, d))
    }
    return list
  }, [viewMonth])

  const handleEmailBlur = async () => {
    if (email.trim().includes('@')) {
      try {
        const isFirst = await api.checkFirstConsultation(email.trim())
        if (isFirst) {
          setIsFirstTime(true)
          setHasShownAlert(false)
        } else {
          setIsFirstTime(false)
        }
      } catch (err) {
        console.error("Error al verificar primera consulta:", err)
      }
    }
  }

  useEffect(() => {
    if (isFirstTime && tipo === 'PARTICULAR' && !hasShownAlert) {
      setShowFirstTimeAlert(true)
      setHasShownAlert(true)
    } else if (tipo !== 'PARTICULAR') {
      setShowFirstTimeAlert(false)
    }
  }, [tipo, isFirstTime, hasShownAlert])

  useEffect(() => {
    if (email) {
      handleEmailBlur()
    }
  }, [])

  useEffect(() => {
    const fetchAvailability = async () => {
      setLoading(true)
      const weekdays = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
      const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
      const targetTimeZone = 'America/Argentina/Cordoba'
      
      const getCordobaDateParts = (date: Date) => {
        const parts = new Intl.DateTimeFormat('en-US', {
          timeZone: targetTimeZone,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false
        }).formatToParts(date);
        
        const map: Record<string, string> = {};
        parts.forEach(p => {
          map[p.type] = p.value;
        });
        return {
          year: parseInt(map.year, 10),
          month: parseInt(map.month, 10) - 1, // 0-indexed for Date constructor
          day: parseInt(map.day, 10),
          hour: parseInt(map.hour, 10),
          minute: parseInt(map.minute, 10)
        };
      };

      const nowParts = getCordobaDateParts(new Date());
      const baseDate = new Date(nowParts.year, nowParts.month, nowParts.day, nowParts.hour, nowParts.minute);
      
      const datesToFetch: { dateStr: string; label: string; sublabel: string }[] = []
      for (let i = 0; i < 31; i++) {
        const d = new Date(baseDate)
        d.setDate(d.getDate() + i)
        const dayOfWeek = d.getDay()

        const label = i === 0 ? 'Hoy' : i === 1 ? 'Mañana' : weekdays[dayOfWeek]
        const sublabel = `${d.getDate()} ${months[d.getMonth()]}`
        
        const yearStr = d.getFullYear()
        const monthStr = String(d.getMonth() + 1).padStart(2, '0')
        const dayStr = String(d.getDate()).padStart(2, '0')
        const dateStr = `${yearStr}-${monthStr}-${dayStr}`
        
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

          if (res.label === 'Hoy') {
            const currentHour = nowParts.hour;
            const currentMinute = nowParts.minute;

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

  const services = [
    { id: 'PARTICULAR', label: 'Consulta Particular', price: professional.price, desc: 'Consulta estándar de 50 minutos' },
    { id: 'OSDE', label: 'Copago OSDE', price: 10500, desc: 'Requiere número de afiliado' },
    { id: 'RECETA', label: 'Receta fuera de turno', price: 45000, desc: 'Solicitud de recetas o órdenes médicas' },
    { id: 'CERTIFICADO', label: 'Certificado', price: 55000, desc: 'Emisión de certificados aptos y licencias' },
    { id: 'SOBRETUNO', label: 'Sobre turno', price: 90000, desc: 'Horario personalizado fuera de agenda' }
  ] as const;

  const currentPrice = useMemo(() => {
    const s = services.find(x => x.id === tipo)
    let basePrice = s ? s.price : professional.price
    if (isFirstTime && tipo === 'PARTICULAR') {
      basePrice = Math.round(basePrice * 1.30)
    }
    return basePrice
  }, [tipo, professional.price, isFirstTime])

  const canPay = name.trim().length > 2 && 
                  email.includes('@') && 
                  phone.length >= 8 && 
                  selectedDayIdx !== null &&
                  (selectedSlot !== null || tipo === 'SOBRETUNO') &&
                  (tipo !== 'OSDE' || afiliado.trim().length > 4) &&
                  (tipo !== 'SOBRETUNO' || /^([01]\d|2[0-3]):[0-5]\d$/.test(customTime)) &&
                  acceptedTerms && 
                  paymentStatus !== 'processing';

  const handlePayClick = () => {
    if (canPay) {
      onSelect(currentDay!, selectedSlot || { time: customTime, available: true }, {
        name,
        email,
        phone: `+54 ${phone.trim()}`,
        tipo,
        afiliado: tipo === 'OSDE' ? afiliado : undefined,
        customTime: tipo === 'SOBRETUNO' ? customTime : undefined
      });
    }
  };

  if (loading) {
    return (
      <div className="checkout-body" style={{ textAlign: 'center', padding: 'var(--space-10)' }}>
        <div className="checkout-spinner" style={{ margin: '0 auto var(--space-4)' }} />
        <p style={{ color: 'var(--color-text-secondary)' }}>Buscando turnos disponibles...</p>
      </div>
    )
  }

  const proBio = professional.descripcionPerfil?.trim() || (
    professional.specialty.includes('Psiquiatra') || professional.specialty.includes('Psiquiatría')
      ? "Más de 15 años acompañando tratamientos de ansiedad, depresión y trastornos del ánimo. Enfoque integral que combina farmacología con seguimiento cercano y comunicación clara con el paciente."
      : "Psicólogo clínico con más de 12 años de trayectoria. Especializado en terapia cognitivo-conductual, tratamiento de ansiedad, ataques de pánico y desarrollo personal."
  );

  return (
    <div className="checkout-body" style={{ maxWidth: '780px', margin: '0 auto' }}>
      
      {/* Header: médico + bio completa */}
      <div className="panel" style={{ padding: '24px' }}>
        <div className="doc-head">
          <div
            className="avatar lg"
            style={{ background: professional.fotoUrl ? 'none' : 'linear-gradient(135deg, #7CC53E, #2FA84F)', overflow: 'hidden' }}
          >
            {professional.fotoUrl ? (
              <img src={professional.fotoUrl} alt={professional.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              initials || "CP"
            )}
          </div>
          <div className="doc-info">
            <div className="doc-name-row">
              <span className="doc-name sora" style={{ fontSize: '22px' }}>{professional.name}</span>
              <span className="badge-mn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><polyline points="20 6 9 17 4 12" /></svg>
                {professional.matricula}
              </span>
            </div>
            <div className="doc-spec">{professional.degree} · {professional.specialty}</div>

            <p className="doc-bio">{proBio}</p>
          </div>
        </div>
      </div>

      <div className="grid-2">
        {/* Izquierda: tipo + día + horario + pagar */}
        <div className="panel" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div>
            <div className="book-title sora"><span className="dot"></span>Tipo de turno</div>
            <div className="types">
              {services.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className={`type ${tipo === s.id ? 'selected' : ''}`}
                  onClick={() => setTipo(s.id)}
                >
                  <span className="radio"></span>
                  <span style={{ flex: 1 }}>
                    <span className="t-name">{s.label}</span>
                    <div className="t-desc">{s.desc}</div>
                  </span>
                  <span className="t-price">${s.price.toLocaleString('es-AR')}</span>
                </button>
              ))}
            </div>
          </div>

          {tipo === 'OSDE' && (
            <div className="checkout-form__group">
              <label htmlFor="afiliado" className="checkout-form__label" style={{ fontWeight: 'bold', fontSize: '13px' }}>Número de afiliado OSDE *</label>
              <input
                id="afiliado"
                type="text"
                placeholder="Ej: 1-123456-7"
                className="checkout-form__input"
                value={afiliado}
                onChange={(e) => setAfiliado(e.target.value)}
                style={{ width: '100%', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', outline: 'none' }}
              />
            </div>
          )}

          {tipo === 'SOBRETUNO' && (
            <div className="checkout-form__group">
              <label htmlFor="customTime" className="checkout-form__label" style={{ fontWeight: 'bold', fontSize: '13px' }}>Sugerir horario personalizado (HH:MM) *</label>
              <input
                id="customTime"
                type="text"
                placeholder="Ej: 19:30"
                className="checkout-form__input"
                value={customTime}
                onChange={(e) => setCustomTime(e.target.value)}
                style={{ width: '100%', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', outline: 'none' }}
              />
            </div>
          )}

          <div>
            <div className="book-title sora"><span className="dot"></span>Elegí día y horario</div>
            <div className="cal">
              <div className="cal-head">
                <button
                  type="button"
                  className="cal-nav"
                  onClick={() => setViewMonth(prev => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                  disabled={viewMonth.getMonth() === new Date().getMonth() && viewMonth.getFullYear() === new Date().getFullYear()}
                  title="Mes anterior"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M15 19 8 12l7-7"/></svg>
                </button>
                <span className="month" style={{ textTransform: 'capitalize' }}>
                  {viewMonth.toLocaleDateString('es-AR', { month: 'long', year: 'numeric' })}
                </span>
                <button
                  type="button"
                  className="cal-nav"
                  onClick={() => setViewMonth(prev => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                  disabled={viewMonth.getMonth() === (new Date().getMonth() + 1) % 12}
                  title="Mes siguiente"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>

              <div className="cal-grid">
                {['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'].map(d => (
                  <span key={d} className="cal-dow">{d}</span>
                ))}
                {gridDays.map((date, i) => {
                  if (date === null) {
                    return <span key={`empty-${i}`} className="cal-day" />
                  }

                  const dayIdx = getDayOptionIndex(date)
                  const hasOption = dayIdx !== -1
                  const dayOpt = hasOption ? days[dayIdx] : null
                  const availableCount = dayOpt ? dayOpt.slots.filter(s => s.available).length : 0
                  const isSelected = hasOption && selectedDayIdx === dayIdx
                  const isToday = date.toDateString() === new Date().toDateString()

                  return (
                    <button
                      key={date.toDateString()}
                      type="button"
                      onClick={() => hasOption && availableCount > 0 && handleSelectDayIdx(dayIdx)}
                      disabled={!hasOption || availableCount === 0}
                      className={`cal-day ${hasOption && availableCount > 0 ? 'free' : ''} ${isSelected ? 'selected' : ''}`}
                    >
                      {date.getDate()}
                    </button>
                  )
                })}
              </div>
            </div>
          </div>

          {currentDay && tipo !== 'SOBRETUNO' && (
            <div>
              <div className="book-title sora" id="slots-title">
                <span className="dot"></span>Horarios — {currentDay.label} {currentDay.sublabel}
              </div>
              <div className="slots">
                {currentDay.slots.map((slot) => (
                  <button
                    key={slot.time}
                    type="button"
                    className={`slot ${selectedSlot?.time === slot.time ? 'selected' : ''}`}
                    onClick={() => setSelectedSlot(slot)}
                  >
                    {slot.time}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Patient Form Fields (Integrated above checkout button) */}
          {(selectedSlot || tipo === 'SOBRETUNO') && selectedDayIdx !== null && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-4)', animation: 'pop 0.25s ease' }}>
              <div className="book-title sora"><span className="dot"></span>Tus datos personales</div>
              
              <div className="checkout-form__group">
                <label className="checkout-form__label">Nombre completo *</label>
                <input
                  type="text"
                  placeholder="Ej: María Gómez"
                  className="checkout-form__input"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  style={{ width: '100%', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', outline: 'none' }}
                />
              </div>

              <div className="checkout-form__group">
                <label className="checkout-form__label">Email de contacto *</label>
                <input
                  type="email"
                  placeholder="Ej: maria.gomez@gmail.com"
                  className="checkout-form__input"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onBlur={handleEmailBlur}
                  style={{ width: '100%', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', outline: 'none' }}
                />
              </div>

              <div className="checkout-form__group">
                <label className="checkout-form__label">Teléfono celular *</label>
                <input
                  type="tel"
                  placeholder="Ej: 3515998822"
                  className="checkout-form__input"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  style={{ width: '100%', padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', outline: 'none' }}
                />
              </div>

              {/* Surcharge Alert */}
              {showFirstTimeAlert && (
                <div className="checkout-alert checkout-alert--warning" style={{ display: 'flex', gap: '8px', padding: '10px', borderRadius: 'var(--radius-md)', backgroundColor: '#fffbe6', border: '1px solid #ffe58f', fontSize: '12.5px', color: '#ad7c11' }}>
                  <span>⚠️</span>
                  <div>
                    <strong>Primer turno:</strong> Se cobra un recargo del 30% por única vez debido a la apertura de la historia clínica en tu primera consulta particular.
                  </div>
                </div>
              )}

              <div className="checkout-form__group checkout-form__group--checkbox" style={{ display: 'flex', alignItems: 'flex-start', gap: '8px', marginTop: '4px' }}>
                <input
                  id="acceptedTerms"
                  type="checkbox"
                  checked={acceptedTerms}
                  onChange={(e) => setAcceptedTerms(e.target.checked)}
                  style={{ marginTop: '3px' }}
                />
                <label htmlFor="acceptedTerms" style={{ fontSize: '12px', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
                  Acepto los términos de servicio, políticas de privacidad y cancelaciones de la plataforma Tranqui.
                </label>
              </div>

              {errorMessage && (
                <div className="checkout-alert checkout-alert--danger" style={{ padding: '10px', borderRadius: 'var(--radius-md)', backgroundColor: '#fff2f0', border: '1px solid #ffccc7', color: '#ff4d4f', fontSize: '13px' }}>
                  {errorMessage}
                </div>
              )}

              <div className="confirm show" style={{ marginTop: '8px' }}>
                <button
                  type="button"
                  className="pay"
                  disabled={!canPay}
                  onClick={handlePayClick}
                  style={{ opacity: canPay ? 1 : 0.6 }}
                >
                  {paymentStatus === 'processing' 
                    ? 'Procesando pago...' 
                    : `Confirmar y pagar — $${currentPrice.toLocaleString('es-AR')}`
                  }
                </button>
                <div className="fine">
                  <span>🕐 Sesión de 50 minutos</span>
                  <span>🗓 Cancelación gratuita hasta 24 hs antes</span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Derecha: consultorio */}
        <div className="panel" style={{ height: 'fit-content' }}>
          <div className="book-title sora"><span className="dot"></span>Consultorio</div>
          
          <div className="map-box">
            <div 
              style={{ position: 'relative', cursor: 'pointer' }}
              onClick={() => {
                if (professional.domicilioLat && professional.domicilioLng) {
                  const url = `https://www.google.com/maps/search/?api=1&query=${professional.domicilioLat},${professional.domicilioLng}`;
                  window.open(url, '_blank', 'noopener,noreferrer');
                }
              }}
            >
              <div
                ref={mapContainerRef}
                style={{
                  height: '190px',
                  width: '100%',
                  backgroundColor: '#EAF2EA',
                  zIndex: 1
                }}
              />
              <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                zIndex: 10,
                backgroundColor: 'transparent'
              }} />
            </div>
            
            <div className="map-addr">
              <div className="street">{professional.domicilioAtencion || "Av. Colón 1234, Piso 3, Of. B"}</div>
              <div className="city">Centro, Córdoba Capital</div>
              <a
                href={
                  professional.domicilioLat && professional.domicilioLng
                    ? `https://www.google.com/maps/search/?api=1&query=${professional.domicilioLat},${professional.domicilioLng}`
                    : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(professional.domicilioAtencion || "Av. Colón 1234, Córdoba")}`
                }
                target="_blank" 
                rel="noopener noreferrer"
              >
                Cómo llegar
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M7 17 17 7M9 7h8v8"/></svg>
              </a>
            </div>
          </div>
        </div>
      </div>

      {/* SEO Sections below fold */}
      <div className="panel">
        {professional.tags && professional.tags.length > 0 && (
          <>
            <div className="seo-title">Principales tratamientos</div>
            <div className="treat-chips">
              {professional.tags.map((t) => (
                <span className="t-chip" key={t}>{t}</span>
              ))}
            </div>
          </>
        )}

        <div className="seo-grid">
          <div>
            {professional.pacientesAtiende && professional.pacientesAtiende.length > 0 && (
              <>
                <div className="seo-title" style={{ fontSize: '14px' }}>Pacientes que atiende</div>
                <ul className="info-list">
                  {professional.pacientesAtiende.map((p) => (
                    <li key={p}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="#2FA84F" strokeWidth="2"><circle cx="12" cy="7" r="4"/><path d="M4 21v-1a7 7 0 0 1 14 0v1"/></svg>
                      <span>{p}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}

            <div className="seo-title" style={{ fontSize: '14px', marginTop: '18px' }}>Formatos de consulta</div>
            <ul className="info-list">
              {professional.ofreceOnline !== false && (
                <li>
                  <svg viewBox="0 0 24 24" fill="none" stroke="#2FA84F" strokeWidth="2"><rect x="2" y="5" width="14" height="12" rx="2"/><path d="m16 10 5-3v10l-5-3"/></svg>
                  <span>Videoconsulta <span className="sub">· Google Meet, link automático</span></span>
                </li>
              )}
              {professional.ofrecePresencial && (
                <li>
                  <svg viewBox="0 0 24 24" fill="none" stroke="#2FA84F" strokeWidth="2"><path d="M12 21s7-5.3 7-11a7 7 0 1 0-14 0c0 5.7 7 11 7 11Z"/><circle cx="12" cy="10" r="2.5"/></svg>
                  <span>En persona <span className="sub">· {professional.domicilioAtencion || "Av. Colón 1234, Córdoba"}</span></span>
                </li>
              )}
            </ul>
          </div>

          <div>
            <div className="seo-title" style={{ fontSize: '14px' }}>Formación y matrícula</div>
            <ul className="info-list">
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="#2FA84F" strokeWidth="2"><path d="m12 3 10 5-10 5L2 8l10-5Z"/><path d="M6 10.5V16c0 1.5 2.7 3 6 3s6-1.5 6-3v-5.5"/></svg>
                <span>{professional.degree}{professional.institucionFormacion ? <span className="sub"> · {professional.institucionFormacion}</span> : null}</span>
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="#2FA84F" strokeWidth="2"><circle cx="12" cy="9" r="6"/><path d="m9 14-2 7 5-3 5 3-2-7"/></svg>
                <span>Matrícula {professional.matricula} <span className="sub">· verificada por Tranqui</span></span>
              </li>
              {professional.aniosExperiencia != null && (
                <li>
                  <svg viewBox="0 0 24 24" fill="none" stroke="#2FA84F" strokeWidth="2"><path d="M12 8v4l3 2"/><circle cx="12" cy="12" r="9"/></svg>
                  <span>Más de {professional.aniosExperiencia} años de experiencia clínica</span>
                </li>
              )}
            </ul>
          </div>
        </div>
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
  meetLink,
  createdTurn,
  modality,
}: {
  professional: Professional
  selectedDay: DayOption
  selectedSlot: TimeSlot
  onDone: () => void
  meetLink?: string
  createdTurn?: any
  modality: 'online' | 'presencial'
}) {
  const actualMeetLink = meetLink || `https://meet.google.com/${Math.random().toString(36).slice(2, 5)}-${Math.random().toString(36).slice(2, 6)}-${Math.random().toString(36).slice(2, 5)}`
  const isPresencial = modality === 'presencial'

  const mapContainerRef = useRef<HTMLDivElement>(null)
  const [leafletLoaded, setLeafletLoaded] = useState(!!(window as any).L)

  useEffect(() => {
    if (isPresencial && (professional.domicilioLat || professional.domicilioLng)) {
      loadLeafletScript()
        .then(() => setLeafletLoaded(true))
        .catch(err => console.error("Error loading Leaflet for mini map", err))
    }
  }, [isPresencial, professional])

  useEffect(() => {
    if (!isPresencial || !leafletLoaded || !professional.domicilioLat || !professional.domicilioLng || !mapContainerRef.current) return
    const L = (window as any).L
    if (!L) return

    const lat = professional.domicilioLat
    const lng = professional.domicilioLng
    
    const map = L.map(mapContainerRef.current, {
      zoomControl: false,
      attributionControl: false,
      dragging: false,
      touchZoom: false,
      doubleClickZoom: false,
      scrollWheelZoom: false
    }).setView([lat, lng], 15)

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map)

    const customIcon = L.divIcon({
      html: `
        <svg viewBox="0 0 24 24" fill="none" stroke="#2E7D5B" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="width: 24px; height: 24px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); display: block;">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" fill="#ffffff" />
          <circle cx="12" cy="10" r="3.2" fill="#2E7D5B" />
        </svg>
      `,
      className: 'custom-leaflet-marker-mini',
      iconSize: [24, 24],
      iconAnchor: [12, 24]
    })

    L.marker([lat, lng], { icon: customIcon }).addTo(map)

    return () => {
      map.remove()
    }
  }, [isPresencial, leafletLoaded, professional.domicilioLat, professional.domicilioLng])

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
          {isPresencial 
            ? "Te enviamos un email con todos los detalles de la consulta."
            : "Te enviamos un email con todos los detalles y el link de la videollamada."
          }
        </p>
      </div>

      <div className="checkout-summary-card">
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Profesional</span>
          <span className="checkout-summary-card__value">{professional.name}</span>
        </div>
        <div className="checkout-summary-card__row">
          <span className="checkout-summary-card__label">Modalidad</span>
          <span className="checkout-summary-card__value">{isPresencial ? 'Presencial (en consultorio)' : 'Online (videollamada)'}</span>
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

      {/* Online Meet Link */}
      {!isPresencial && (
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
      )}

      {/* In-Person Office Location */}
      {isPresencial && professional.domicilioAtencion && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div className="checkout-meet-card" style={{ borderLeft: '5px solid var(--color-primary)' }}>
            <div className="checkout-meet-card__icon" aria-hidden="true" style={{ color: 'var(--color-primary)' }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 24, height: 24 }}>
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                <circle cx="12" cy="10" r="3" />
              </svg>
            </div>
            <div className="checkout-meet-card__content">
              <div className="checkout-meet-card__label">Dirección del consultorio</div>
              <div className="checkout-meet-card__sublabel" style={{ fontWeight: '600', color: 'var(--color-text-primary)' }}>
                {professional.domicilioAtencion}
              </div>
            </div>
            <a
              href={
                professional.domicilioLat && professional.domicilioLng
                  ? `https://www.google.com/maps/search/?api=1&query=${professional.domicilioLat},${professional.domicilioLng}`
                  : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(professional.domicilioAtencion)}`
              }
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn--primary btn--sm"
              style={{ backgroundColor: 'var(--color-primary)', flexShrink: 0 }}
            >
              Cómo llegar
            </a>
          </div>

          {/* Small Mini-Map */}
          {professional.domicilioLat && professional.domicilioLng && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', fontWeight: 'bold' }}>Ubicación en el mapa:</div>
              <div 
                style={{ position: 'relative', cursor: 'pointer' }}
                onClick={() => {
                  const url = `https://www.google.com/maps/search/?api=1&query=${professional.domicilioLat},${professional.domicilioLng}`;
                  window.open(url, '_blank', 'noopener,noreferrer');
                }}
              >
                <div 
                  ref={mapContainerRef} 
                  style={{ 
                    height: '160px', 
                    borderRadius: 'var(--radius-md)', 
                    border: '1px solid var(--color-border)',
                    zIndex: 1
                  }} 
                />
                {/* Overlay transparente para asegurar que el click funcione en todo el mapa y deshabilitar clicks de Leaflet */}
                <div style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  bottom: 0,
                  zIndex: 10,
                  backgroundColor: 'transparent'
                }} />
              </div>
            </div>
          )}
        </div>
      )}

      {/* Next steps */}
      <div className="checkout-next-steps">
        <h3 className="checkout-next-steps__title">Próximos pasos</h3>
        {isPresencial ? (
          <ol className="checkout-next-steps__list">
            <li>Revisá tu email — te enviamos la confirmación con los datos de contacto del médico.</li>
            <li>Acercate al consultorio 10 minutos antes del horario pactado.</li>
            <li>Podés guiarte utilizando la dirección o el mapa que figuran arriba.</li>
          </ol>
        ) : (
          <ol className="checkout-next-steps__list">
            <li>Revisá tu email — te enviamos la confirmación con todos los datos.</li>
            <li>10 minutos antes de la sesión, abrí el link de Google Meet desde arriba.</li>
            <li>Buscá un lugar tranquilo con buena conexión a internet.</li>
          </ol>
        )}
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
  useDocumentTitle(`Reservar turno con ${professional.name} — Tranqui App`)
  const { showAlert } = useAlert()
  const [step, setStep] = useState<CheckoutStep>('select')
  const [selectedDay, setSelectedDay] = useState<DayOption | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null)
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [createdTurn, setCreatedTurn] = useState<any>(null)
  const [showMockPaymentGateway, setShowMockPaymentGateway] = useState(false)
  const [simulatingWebhook, setSimulatingWebhook] = useState(false)

  const [modality, setModality] = useState<'online' | 'presencial'>(
    professional.ofrecePresencial && !professional.ofreceOnline ? 'presencial' : 'online'
  )

  const handleSelectSlot = (
    day: DayOption,
    slot: TimeSlot,
    patientData: { name: string; email: string; phone: string; tipo: 'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'; afiliado?: string; customTime?: string }
  ) => {
    setSelectedDay(day)
    setSelectedSlot(slot)
    
    // Automatically derive modality
    if (patientData.tipo === 'PARTICULAR' || patientData.tipo === 'OSDE') {
      setModality(professional.ofrecePresencial ? 'presencial' : 'online')
    } else {
      setModality('online')
    }

    handlePay(day, slot, patientData)
  }

  const handlePay = (
    day: DayOption,
    slot: TimeSlot,
    patientData: { name: string; email: string; phone: string; tipo: 'PARTICULAR' | 'OSDE' | 'RECETA' | 'CERTIFICADO' | 'SOBRETUNO'; afiliado?: string; customTime?: string }
  ) => {
    setPaymentStatus('processing')
    const finalTime = patientData.tipo === 'SOBRETUNO' && patientData.customTime 
      ? patientData.customTime 
      : slot.time;

    api.reservarTurno({
      medicoId: Number(professional.id),
      fecha: day.date,
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
        // The backend returns this fixed mock URL when Mercado Pago isn't really
        // configured (dev mode, or the professional hasn't linked a real account yet).
        // A real preference URL sends the patient straight to mercadopago.com.ar instead
        // of showing them our own sandbox-labeled simulator.
        if (res.checkoutUrl.includes('mock-preference-id')) {
          setShowMockPaymentGateway(true)
        } else {
          window.location.href = res.checkoutUrl
        }
      } else {
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
    onBack()
  }

  return (
    <div className="checkout-layout">
      <CheckoutHeader step={step} onBack={step === 'confirmed' ? onComplete : handleBack} />

      {step === 'select' && (
        <StepSelect
          professional={professional}
          onSelect={handleSelectSlot}
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
          modality={modality}
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
                    const isHttps = window.location.protocol === 'https:';
                    const baseUrl = isHttps ? `${window.location.protocol}//${window.location.host}` : `http://${window.location.hostname}:8081`;
                    const response = await fetch(`${baseUrl}/api/payments/webhook`, {
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
                        const updatedTurn = await api.getMisTurnos();
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
                onClick={async () => {
                  try {
                    // Release the PENDIENTE_PAGO turno now instead of leaving it to block
                    // this same patient email from re-booking for the next 5 minutes
                    // (until LiberarTurnosScheduler's cleanup pass runs).
                    await api.abandonarReservaPendiente(createdTurn.turnoId)
                  } catch (err) {
                    console.error("Error al liberar la reserva pendiente:", err)
                  }
                  setShowMockPaymentGateway(false);
                  setPaymentStatus('error');
                  setErrorMessage("Pago rechazado por el usuario en la simulación.");
                }}
                className="btn btn--ghost"
                style={{ color: 'var(--color-danger)', border: '1px solid var(--color-danger)', justifyContent: 'center' }}
              >
                Simular Pago Rechazado (Cancelar)
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
