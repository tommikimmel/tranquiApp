import { useState, useEffect } from 'react'
import type { CSSProperties } from 'react'
import { api } from '../api/api'
import { Icon } from './Icon'
import ReactivarCuentaView from './ReactivarCuentaView'
import '../styles/checkout.css'

// Etiquetas legibles para las feature keys que devuelve el backend (PlanService.initDefaultPlansAndFeatures) —
// el DTO solo trae las keys crudas (snake_case), no un nombre para mostrar.
const FEATURE_LABELS: Record<string, string> = {
  recetas_electronicas: 'Recetas Electrónicas QBI2',
  bot_whatsapp: 'Notificaciones por WhatsApp',
  mp_split: 'Cobro anticipado del turno',
  google_meet: 'Google Meet y Calendario',
  historia_clinica: 'Historia Clínica Digital',
  agenda_compartida: 'Agenda y turnero online',
  reportes: 'Reportes y métricas',
}

// Mental accounting (ver doc de diseño adjunto, §A): separar "lo que te trae pacientes" de "lo
// que viene de gestión" en dos cajas rotuladas cambia a qué cuenta mental se imputa el precio.
// Solo agenda_compartida es realmente adquisición (la página pública de reserva); el resto es
// operación del consultorio. recetas_electronicas se trata aparte (ver RECETAS_KEY) porque su
// estado difiere según el plan: ausente en Consultorio, "Próximamente" en Clínico.
const RECETAS_KEY = 'recetas_electronicas'
const FEATURES_PACIENTES = new Set(['agenda_compartida'])

interface PlanLite {
  id: number
  code: string
  name: string
  description: string
  priceArs: number
  priceArsAnual?: number | null
  priceUsdRef: number
  billingPeriod: string
  requiresPrescriber: boolean
  features: string[]
}

const money = (n: number) => `$ ${Math.round(n).toLocaleString('es-AR')}`
const parsePlata = (s: string) => parseInt(String(s).replace(/\D/g, ''), 10) || 0

// Pantalla de selección de plan de suscripción. Se usa en dos contextos:
// - "post-register": inmediatamente después de registrarse como profesional, antes de verificar
//   el email. Todavía no hay cobro automático (Etapa 2 — falta conectar Mercado Pago con la
//   cuenta del admin), así que el botón de pago queda deshabilitado con una aclaración.
// - "blocked": un profesional ya logueado sin suscripción activa, bloqueado por
//   SubscriptionAccessFilter en el backend. Ve el mismo catálogo, más su estado actual.
export default function ChoosePlanView({
  variant,
  onContinue,
  onLogout,
  subscription,
  profession,
}: {
  variant: 'post-register' | 'blocked'
  onContinue?: () => void
  onLogout?: () => void
  subscription?: { status?: string; currentPeriodEnd?: string | null; plan?: { name?: string; code?: string } | null } | null
  profession?: string | null
}) {
  const [plans, setPlans] = useState<PlanLite[]>([])
  const [loading, setLoading] = useState(true)
  const [ciclo, setCiclo] = useState<'monthly' | 'annual'>('monthly')
  const [honorarioInput, setHonorarioInput] = useState('40.000')

  useEffect(() => {
    api.getSubscriptionPlans()
      .then((res: any) => setPlans(Array.isArray(res) ? res : []))
      .catch(() => setPlans([]))
      .finally(() => setLoading(false))
  }, [])

  // Diseño dedicado (ver suscripcion-v11.html en la raíz del proyecto) para el caso de una cuenta
  // bloqueada por falta de pago. El variant "post-register" (elegir plan recién registrado, antes
  // de poder pagar) sigue con la pantalla genérica de más abajo — el copy de "reactivar" del nuevo
  // diseño no aplica a alguien que nunca estuvo activo.
  if (variant === 'blocked') {
    return <ReactivarCuentaView plans={plans} loading={loading} subscription={subscription} profession={profession} onLogout={onLogout} />
  }

  const hayCicloAnual = plans.some((p) => !!p.priceArsAnual)
  const honorario = parsePlata(honorarioInput)

  // Precio a cobrar en este ciclo (lo que efectivamente le cobra Mercado Pago).
  const precioCiclo = (p: PlanLite) => (ciclo === 'annual' && p.priceArsAnual ? p.priceArsAnual : p.priceArs)
  // Equivalente mensual, para comparar "peras con peras" contra el plan mensual y contra el
  // honorario que el profesional tipeó.
  const precioMensualEquiv = (p: PlanLite) => (ciclo === 'annual' && p.priceArsAnual ? p.priceArsAnual / 12 : p.priceArs)

  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
      minHeight: '100vh', backgroundColor: 'var(--color-bg)', padding: 'var(--space-6) var(--space-3)'
    }}>
      <div style={{ maxWidth: '980px', width: '100%', display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>

        {/* ── Hero ── */}
        <div style={{
          backgroundColor: 'var(--color-text-primary)', borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-8) var(--space-6)', color: 'white', textAlign: 'center',
        }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-3)' }}>
            <img src="/logoTranquiApp.webp" alt="Tranqui App" width={28} height={28} style={{ display: 'block' }} />
            <span style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>
              Tranqui App
            </span>
          </div>
          <h1 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-2xl)', margin: '0 0 8px', lineHeight: 1.25 }}>
            Elegí tu plan profesional
          </h1>
          <p style={{ color: 'rgba(255,255,255,0.75)', fontSize: 'var(--text-sm)', maxWidth: '520px', margin: '0 auto' }}>
            Este es el catálogo de planes disponibles. Verificá tu email para activar tu cuenta — vas a poder pagar con Mercado Pago apenas inicies sesión por primera vez.
          </p>
        </div>

        {/* ── Toggle de ciclo ── */}
        {hayCicloAnual && (
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <div style={{ display: 'inline-flex', backgroundColor: 'var(--neutral-100)', borderRadius: 'var(--radius-md)', padding: '3px' }}>
              {(['monthly', 'annual'] as const).map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setCiclo(c)}
                  className="btn"
                  style={{
                    border: 0, padding: '9px 16px', borderRadius: 'calc(var(--radius-md) - 2px)',
                    fontSize: 'var(--text-sm)', fontWeight: 600, cursor: 'pointer',
                    backgroundColor: ciclo === c ? 'white' : 'transparent',
                    color: ciclo === c ? 'var(--color-text-primary)' : 'var(--color-text-secondary)',
                    boxShadow: ciclo === c ? 'var(--shadow-sm)' : 'none',
                  }}
                >
                  {c === 'monthly' ? 'Mensual' : (
                    <>Anual <span style={{ color: 'var(--color-success)', fontWeight: 700 }}>· 2 meses sin cargo</span></>
                  )}
                </button>
              ))}
            </div>
          </div>
        )}

        {loading ? (
          <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)' }}>Cargando planes...</p>
        ) : (
          <div
            className="plan-grid"
            style={{ '--plan-cols': Math.min(plans.length || 1, 2), gap: 'var(--space-5)' } as CSSProperties}
          >
            {plans.map((plan) => {
              const recomendado = subscription?.plan?.code === plan.code
              const precioMensual = precioMensualEquiv(plan)
              const tieneRecetas = plan.features.includes(RECETAS_KEY)
              const featuresPacientes = plan.features.filter((f) => FEATURES_PACIENTES.has(f))
              const featuresGestion = plan.features.filter((f) => f !== RECETAS_KEY && !FEATURES_PACIENTES.has(f))
              const pacientesPorMes = honorario > 0 ? Math.max(1, Math.ceil(precioMensual / honorario)) : null

              return (
                <div
                  key={plan.id}
                  className="card"
                  style={{
                    display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', padding: 'var(--space-5)',
                    border: recomendado ? '2px solid var(--color-primary)' : undefined, position: 'relative',
                  }}
                >
                  {recomendado && (
                    <span style={{
                      position: 'absolute', top: '-1px', right: '-1px', fontSize: '10px', fontWeight: 700,
                      letterSpacing: '.04em', textTransform: 'uppercase', color: 'white', background: 'var(--color-primary)',
                      padding: '5px 12px', borderRadius: '0 var(--radius-lg) 0 var(--radius-md)',
                    }}>
                      Tu plan
                    </span>
                  )}

                  <div>
                    <span className="badge badge--neutral" style={{ fontSize: '11px' }}>
                      {plan.requiresPrescriber ? 'Psiquiatras / Médicos' : 'Psicólogos'}
                    </span>
                    <h3 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-lg)', margin: '8px 0 2px' }}>{plan.name}</h3>
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>{plan.description}</p>
                  </div>

                  <div>
                    <strong style={{ fontSize: 'var(--text-2xl)', color: 'var(--color-primary)' }}>{money(precioCiclo(plan))}</strong>
                    <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                      {' '}{ciclo === 'annual' ? 'por año' : 'por mes'} · $ {Math.round(precioMensual / 30).toLocaleString('es-AR')} por día
                    </span>
                    {ciclo === 'annual' && (
                      <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                        Equivale a {money(precioMensual)} por mes — pagando mes a mes serían {money(plan.priceArs * 12)} al año.
                      </div>
                    )}
                  </div>

                  {pacientesPorMes !== null && (
                    <div style={{ fontSize: '13px', backgroundColor: 'var(--neutral-50)', borderRadius: 'var(--radius-md)', padding: '10px 12px', lineHeight: 1.4 }}>
                      Con un honorario de {money(honorario)} por consulta, el plan se paga con{' '}
                      <strong>{pacientesPorMes} paciente{pacientesPorMes !== 1 ? 's' : ''} nuevo{pacientesPorMes !== 1 ? 's' : ''} por mes</strong>.
                    </div>
                  )}

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', flex: 1 }}>
                    <div>
                      <div style={{ fontSize: '10.5px', fontWeight: 700, letterSpacing: '.04em', textTransform: 'uppercase', color: 'var(--color-primary)', marginBottom: '6px' }}>
                        Te ayuda a conseguir pacientes
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-xs)' }}>
                          <span style={{ color: 'var(--color-primary)', display: 'flex', flexShrink: 0 }}><Icon.Check /></span>
                          Perfil público visible en el buscador de Tranqui
                        </div>
                        {featuresPacientes.map((f) => (
                          <div key={f} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-xs)' }}>
                            <span style={{ color: 'var(--color-primary)', display: 'flex', flexShrink: 0 }}><Icon.Check /></span>
                            {FEATURE_LABELS[f] || f.replace(/_/g, ' ')}
                          </div>
                        ))}
                      </div>
                    </div>

                    <div>
                      <div style={{ fontSize: '10.5px', fontWeight: 700, letterSpacing: '.04em', textTransform: 'uppercase', color: 'var(--color-text-secondary)', marginBottom: '6px' }}>
                        Gestión del consultorio
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        {featuresGestion.map((f) => (
                          <div key={f} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-xs)' }}>
                            <span style={{ color: 'var(--color-text-secondary)', display: 'flex', flexShrink: 0 }}><Icon.Check /></span>
                            {FEATURE_LABELS[f] || f.replace(/_/g, ' ')}
                          </div>
                        ))}
                        {tieneRecetas && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                            <span style={{
                              fontSize: '9px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.02em',
                              color: 'var(--color-warning)', background: 'var(--color-warning-bg)', border: '1px solid #FDE68A',
                              borderRadius: '999px', padding: '2px 6px', flexShrink: 0,
                            }}>
                              Próximamente
                            </span>
                            {FEATURE_LABELS[RECETAS_KEY]}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Todavía no hay sesión iniciada en este punto del registro (recién se loguea
                      después de verificar el email), y /subscriptions/checkout exige estar
                      autenticado — así que acá el plan es solo de referencia, no se puede pagar
                      hasta después de iniciar sesión por primera vez (variant "blocked", que ahora
                      usa ReactivarCuentaView en vez de esta pantalla). */}
                  <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', margin: 0 }}>
                    Vas a poder pagarlo apenas verifiques tu email e inicies sesión.
                  </p>
                </div>
              )
            })}
          </div>
        )}

        {/* ── Calculadora: honorario compartido por las dos tarjetas ── */}
        {!loading && plans.length > 0 && (
          <div className="card" style={{ padding: 'var(--space-5)', display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <Icon.DollarSign size={18} />
            <label htmlFor="honorario-plan" style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
              Para ver con cuántos pacientes nuevos se paga cada plan, contanos cuánto cobrás por consulta:
            </label>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>$</span>
              <input
                id="honorario-plan"
                type="text"
                inputMode="numeric"
                value={honorarioInput}
                onChange={(e) => {
                  const v = parsePlata(e.target.value)
                  setHonorarioInput(v ? v.toLocaleString('es-AR') : '')
                }}
                className="form-input"
                style={{ width: '100px', fontWeight: 700, textAlign: 'right', fontSize: '16px' }}
              />
            </span>
          </div>
        )}

        {/* ── Confianza ── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--space-3)' }}>
          <div className="card" style={{ padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <Icon.Shield size={18} />
            <strong style={{ fontSize: 'var(--text-sm)' }}>Cero comisión por turno</strong>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Vos definís tu precio y te lo quedás entero.</span>
          </div>
          <div className="card" style={{ padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <Icon.Clock size={18} />
            <strong style={{ fontSize: 'var(--text-sm)' }}>Reactivación inmediata</strong>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Volvés al buscador apenas Mercado Pago confirma el pago.</span>
          </div>
          <div className="card" style={{ padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <Icon.MercadoPago size={18} />
            <strong style={{ fontSize: 'var(--text-sm)' }}>Cancelación transparente</strong>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Podés cancelar la renovación automática en cualquier momento con 1 solo clic y mantenés el acceso hasta el fin del período.</span>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', gap: 'var(--space-3)' }}>
          {onContinue && (
            <button
              type="button"
              className="btn btn--primary"
              onClick={onContinue}
              style={{ minWidth: '220px', padding: 'var(--space-4) var(--space-6)' }}
            >
              Continuar y verificar mi email
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
