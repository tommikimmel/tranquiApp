import { useState, useEffect } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { Icon } from './Icon'

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
}: {
  variant: 'post-register' | 'blocked'
  onContinue?: () => void
  onLogout?: () => void
  subscription?: { status?: string; currentPeriodEnd?: string | null; plan?: { name?: string; code?: string } | null } | null
}) {
  const { showAlert } = useAlert()
  const [plans, setPlans] = useState<PlanLite[]>([])
  const [loading, setLoading] = useState(true)
  const [payingPlanId, setPayingPlanId] = useState<number | null>(null)
  const [ciclo, setCiclo] = useState<'monthly' | 'annual'>('monthly')
  const [honorarioInput, setHonorarioInput] = useState('40.000')

  useEffect(() => {
    api.getSubscriptionPlans()
      .then((res: any) => setPlans(Array.isArray(res) ? res : []))
      .catch(() => setPlans([]))
      .finally(() => setLoading(false))
  }, [])

  const expired = subscription?.currentPeriodEnd && new Date(subscription.currentPeriodEnd) < new Date()
  const hayCicloAnual = plans.some((p) => !!p.priceArsAnual)
  const honorario = parsePlata(honorarioInput)

  // Precio a cobrar en este ciclo (lo que efectivamente le cobra Mercado Pago).
  const precioCiclo = (p: PlanLite) => (ciclo === 'annual' && p.priceArsAnual ? p.priceArsAnual : p.priceArs)
  // Equivalente mensual, para comparar "peras con peras" contra el plan mensual y contra el
  // honorario que el profesional tipeó.
  const precioMensualEquiv = (p: PlanLite) => (ciclo === 'annual' && p.priceArsAnual ? p.priceArsAnual / 12 : p.priceArs)

  const handlePay = (planId: number) => {
    setPayingPlanId(planId)
    api.iniciarCheckoutSuscripcion(planId, ciclo)
      .then((res: any) => {
        if (res?.checkoutUrl) {
          window.location.href = res.checkoutUrl
        } else {
          showAlert('No se pudo iniciar el pago. Intentá de nuevo.', 'error')
          setPayingPlanId(null)
        }
      })
      .catch((err: any) => {
        showAlert(err?.message || 'No se pudo iniciar el pago con Mercado Pago.', 'error')
        setPayingPlanId(null)
      })
  }

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
            {variant === 'post-register' ? 'Elegí tu plan profesional' : 'Volvé al buscador de Tranqui'}
          </h1>
          <p style={{ color: 'rgba(255,255,255,0.75)', fontSize: 'var(--text-sm)', maxWidth: '520px', margin: '0 auto' }}>
            {variant === 'post-register'
              ? 'Este es el catálogo de planes disponibles. Verificá tu email para activar tu cuenta — vas a poder pagar con Mercado Pago apenas inicies sesión por primera vez.'
              : 'Mientras tu suscripción no esté activa, tu perfil no aparece en el buscador público de Tranqui: ningún paciente puede encontrarte ni reservar un turno. Reactivá para volver a estar visible.'}
          </p>
        </div>

        {variant === 'blocked' && (
          <div style={{
            display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
            padding: 'var(--space-4)', backgroundColor: 'var(--color-warning-bg)', border: '1px solid #fde68a',
            borderRadius: 'var(--radius-md)', color: '#78350f'
          }}>
            <div style={{
              width: '36px', height: '36px', borderRadius: '50%', backgroundColor: '#fef3c7',
              color: 'var(--color-warning)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
            }}>
              <Icon.Lock size={18} />
            </div>
            <div style={{ fontSize: 'var(--text-sm)' }}>
              {!subscription ? (
                <span>Todavía no registramos ninguna suscripción para tu cuenta.</span>
              ) : expired ? (
                <span>
                  Tu suscripción {subscription.plan?.name ? <strong>{subscription.plan.name}</strong> : ''} venció el{' '}
                  <strong>{new Date(subscription.currentPeriodEnd as string).toLocaleDateString('es-AR')}</strong>.
                </span>
              ) : (
                <span>Estado actual de tu suscripción: <strong>{subscription.status}</strong>.</span>
              )}
              {' '}Elegí un plan abajo para pagarlo con Mercado Pago y recuperar el acceso.
            </div>
          </div>
        )}

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
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.min(plans.length || 1, 2)}, 1fr)`, gap: 'var(--space-5)' }}>
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

                  {variant === 'blocked' ? (
                    <button
                      type="button"
                      className="btn btn--primary"
                      disabled={payingPlanId !== null}
                      onClick={() => handlePay(plan.id)}
                      style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                    >
                      {payingPlanId === plan.id ? 'Redirigiendo a Mercado Pago...' : (
                        <>
                          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
                            <rect x="1" y="4" width="22" height="16" rx="2" /><line x1="1" y1="10" x2="23" y2="10" />
                          </svg>
                          Reactivar por {money(precioCiclo(plan))}
                        </>
                      )}
                    </button>
                  ) : (
                    // Todavía no hay sesión iniciada en este punto del registro (recién se loguea
                    // después de verificar el email), y /subscriptions/checkout exige estar
                    // autenticado — así que acá el plan es solo de referencia, no se puede pagar
                    // hasta después de iniciar sesión por primera vez (variant "blocked").
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', margin: 0 }}>
                      Vas a poder pagarlo apenas verifiques tu email e inicies sesión.
                    </p>
                  )}
                  {variant === 'blocked' && (
                    <p style={{ textAlign: 'center', fontSize: '11.5px', color: 'var(--color-text-secondary)', margin: 0 }}>
                      Tu perfil vuelve al buscador apenas Mercado Pago confirma.
                    </p>
                  )}
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
                style={{ width: '100px', fontWeight: 700, textAlign: 'right' }}
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
            <strong style={{ fontSize: 'var(--text-sm)' }}>¿Necesitás dar de baja?</strong>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Escribinos a soporte y te la cancelamos sin vueltas.</span>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', gap: 'var(--space-3)' }}>
          {variant === 'post-register' && onContinue && (
            <button type="button" className="btn btn--primary" onClick={onContinue} style={{ minWidth: '220px' }}>
              Continuar y verificar mi email
            </button>
          )}
          {variant === 'blocked' && onLogout && (
            <button type="button" className="btn btn--ghost" onClick={onLogout}>
              Cerrar sesión
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
