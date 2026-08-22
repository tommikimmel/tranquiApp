import { useState, useEffect } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { Icon } from './Icon'

// Etiquetas legibles para las feature keys que devuelve el backend (PlanService.initDefaultPlansAndFeatures) —
// el DTO solo trae las keys crudas (snake_case), no un nombre para mostrar.
const FEATURE_LABELS: Record<string, string> = {
  recetas_electronicas: 'Recetas Electrónicas QBI2',
  bot_whatsapp: 'Bot y Notificaciones de WhatsApp',
  mp_split: 'Cobro Anticipado y Mercado Pago Split',
  google_meet: 'Google Meet y Calendario',
  historia_clinica: 'Historia Clínica Digital',
  agenda_compartida: 'Agenda y Turnero Online',
  reportes: 'Reportes y Métricas Financieras',
}

interface PlanLite {
  id: number
  code: string
  name: string
  description: string
  priceArs: number
  priceUsdRef: number
  billingPeriod: string
  requiresPrescriber: boolean
  features: string[]
}

const money = (n: number) => `$ ${Math.round(n).toLocaleString('es-AR')}`

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
  subscription?: { status?: string; currentPeriodEnd?: string | null; plan?: { name?: string } | null } | null
}) {
  const { showAlert } = useAlert()
  const [plans, setPlans] = useState<PlanLite[]>([])
  const [loading, setLoading] = useState(true)
  const [payingPlanId, setPayingPlanId] = useState<number | null>(null)

  useEffect(() => {
    api.getSubscriptionPlans()
      .then((res: any) => setPlans(Array.isArray(res) ? res : []))
      .catch(() => setPlans([]))
      .finally(() => setLoading(false))
  }, [])

  const expired = subscription?.currentPeriodEnd && new Date(subscription.currentPeriodEnd) < new Date()

  const handlePay = (planId: number) => {
    setPayingPlanId(planId)
    api.iniciarCheckoutSuscripcion(planId)
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
      <div style={{ maxWidth: '920px', width: '100%', display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
            <img src="/logoTranquiApp.webp" alt="Tranqui App" width={30} height={30} style={{ display: 'block' }} />
            <span style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-xl)', fontWeight: 'bold', color: 'var(--color-primary)' }}>
              Tranqui App
            </span>
          </div>
          <h1 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-2xl)', margin: '0 0 4px' }}>
            {variant === 'post-register' ? 'Elegí tu plan profesional' : 'Necesitás una suscripción activa'}
          </h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', maxWidth: '520px', margin: '0 auto' }}>
            {variant === 'post-register'
              ? 'Este es el catálogo de planes disponibles. Verificá tu email para activar tu cuenta — vas a poder pagar con Mercado Pago apenas inicies sesión por primera vez.'
              : 'Tu acceso al panel profesional está pausado hasta que tengas una suscripción activa. Elegí un plan para renovarlo con Mercado Pago.'}
          </p>
        </div>

        {variant === 'blocked' && (
          <div style={{
            display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
            padding: 'var(--space-4)', backgroundColor: '#fffbeb', border: '1px solid #fde68a',
            borderRadius: 'var(--radius-md)', color: '#78350f'
          }}>
            <div style={{
              width: '36px', height: '36px', borderRadius: '50%', backgroundColor: '#fef3c7',
              color: '#d97706', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
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

        {loading ? (
          <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)' }}>Cargando planes...</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.min(plans.length || 1, 3)}, 1fr)`, gap: 'var(--space-4)' }}>
            {plans.map((plan) => (
              <div key={plan.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', padding: 'var(--space-5)' }}>
                <div>
                  <span className="badge badge--neutral" style={{ fontSize: '11px' }}>
                    {plan.requiresPrescriber ? 'Psiquiatras / Médicos' : 'Psicólogos'}
                  </span>
                  <h3 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-lg)', margin: '8px 0 2px' }}>{plan.name}</h3>
                  <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>{plan.description}</p>
                </div>

                <div>
                  <strong style={{ fontSize: 'var(--text-2xl)', color: 'var(--color-primary)' }}>{money(plan.priceArs)}</strong>
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}> / {plan.billingPeriod?.toLowerCase() === 'monthly' || !plan.billingPeriod ? 'mes' : plan.billingPeriod}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
                  {plan.features.map((f) => (
                    <div key={f} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--text-xs)', color: 'var(--color-text-primary)' }}>
                      <span style={{ color: 'var(--color-primary)', display: 'flex', flexShrink: 0 }}><Icon.Check /></span>
                      {FEATURE_LABELS[f] || f.replace(/_/g, ' ')}
                    </div>
                  ))}
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
                        Pagar con Mercado Pago
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
              </div>
            ))}
          </div>
        )}

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
