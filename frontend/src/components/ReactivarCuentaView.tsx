import { useState, useMemo } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

// Réplica del mockup de diseño suscripcion-v11.html (carpeta raíz del proyecto), adaptada para
// usar datos reales: precios/features vienen de /api/subscriptions/plans, el pago dispara el
// checkout real de Mercado Pago, y el estado de la cuenta (vencida/nunca activada) es el real.
//
// Las cifras del embudo (visitas totales, búsquedas por zona/especialidad) NO salen de analítica
// real — hoy la app no trackea eso. El propio mockup las marca como "DATOS A ENCHUFAR (analítica
// real)"; por decisión explícita se dejan tal cual las trae el diseño en vez de inventar una
// fuente de datos que no existe.
const ZONA = 'Córdoba Capital'
const VISITAS_ANIO = 160000
const BUSCAN_ZONA = 100000
const BUSQUEDAS_POR_ESPECIALIDAD: Record<'consultorio' | 'clinico', { esp: string; volumen: number }> = {
  consultorio: { esp: 'un psicólogo', volumen: 46000 },
  clinico: { esp: 'un psiquiatra', volumen: 34000 },
}

const FEATURE_LABELS: Record<string, string> = {
  mp_split: 'Cobro anticipado del turno',
  google_meet: 'Google Meet y Calendario',
  historia_clinica: 'Historia Clínica Digital',
  agenda_compartida: 'Agenda y turnero online',
  reportes: 'Reportes y métricas',
}
const RECETAS_KEY = 'recetas_electronicas'
const DIAS_HABILES_MES = 22

const ICO = {
  buscador: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><circle cx="9" cy="9" r="5.6" stroke="#2FA84F" strokeWidth="1.8" /><path d="M13.2 13.2L17 17" stroke="#2FA84F" strokeWidth="1.8" strokeLinecap="round" /></svg>,
  pin: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><path d="M10 18s6-5.3 6-9.4A6 6 0 004 8.6C4 12.7 10 18 10 18z" stroke="#2FA84F" strokeWidth="1.6" strokeLinejoin="round" /><circle cx="10" cy="8.5" r="2" fill="#7CC53E" /></svg>,
  agenda: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><rect x="2.6" y="4" width="14.8" height="13.4" rx="2.2" stroke="#5B6B60" strokeWidth="1.5" /><path d="M2.6 8h14.8M6.6 2.4v3M13.4 2.4v3" stroke="#5B6B60" strokeWidth="1.5" strokeLinecap="round" /></svg>,
  cobro: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><rect x="2.4" y="5" width="15.2" height="10.4" rx="2" stroke="#5B6B60" strokeWidth="1.5" /><path d="M2.4 8.4h15.2" stroke="#5B6B60" strokeWidth="1.5" /></svg>,
  meet: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><rect x="2.4" y="5.4" width="10.6" height="9.2" rx="2" stroke="#5B6B60" strokeWidth="1.5" /><path d="M13 9.6l4.6-2.8v6.4L13 10.4z" stroke="#5B6B60" strokeWidth="1.5" strokeLinejoin="round" /></svg>,
  rep: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><path d="M3 16.4h14M6 13V8.6M10 13V4.6M14 13v-5" stroke="#5B6B60" strokeWidth="1.7" strokeLinecap="round" /></svg>,
  clinica: <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><rect x="3" y="2.6" width="14" height="14.8" rx="2" stroke="#5B6B60" strokeWidth="1.5" /><path d="M7 7h6M7 10.4h6M7 13.8h3.5" stroke="#5B6B60" strokeWidth="1.5" strokeLinecap="round" /></svg>,
}
const ICO_PLAN = {
  consultorio: <svg width="21" height="21" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="6.4" r="3.2" stroke="#2FA84F" strokeWidth="1.6" /><path d="M3.6 17c0-3.3 2.9-5.4 6.4-5.4s6.4 2.1 6.4 5.4" stroke="#2FA84F" strokeWidth="1.6" strokeLinecap="round" /></svg>,
  clinico: <svg width="21" height="21" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="6.4" r="3.2" stroke="#2FA84F" strokeWidth="1.6" /><path d="M3.6 17c0-3.3 2.9-5.4 6.4-5.4s6.4 2.1 6.4 5.4" stroke="#2FA84F" strokeWidth="1.6" strokeLinecap="round" /><path d="M15.4 2.6v4M13.4 4.6h4" stroke="#7CC53E" strokeWidth="1.8" strokeLinecap="round" /></svg>,
}

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

const f = (n: number) => Math.round(n).toLocaleString('es-AR', { maximumFractionDigits: 0 })
const money = (n: number) => `$ ${f(n)}`
const parseNum = (s: string) => parseInt(String(s).replace(/\D/g, ''), 10) || 0

export default function ReactivarCuentaView({
  plans,
  loading,
  subscription,
  profession,
  onLogout,
}: {
  plans: PlanLite[]
  loading: boolean
  subscription?: { status?: string; currentPeriodEnd?: string | null; plan?: { name?: string; code?: string } | null } | null
  profession?: string | null
  onLogout?: () => void
}) {
  const { showAlert } = useAlert()
  const [ciclo, setCiclo] = useState<'monthly' | 'annual'>('monthly')
  const [payingPlanId, setPayingPlanId] = useState<number | null>(null)
  const [honorarioInput, setHonorarioInput] = useState('40.000')
  const [diaInput, setDiaInput] = useState('10')
  const [ltvInput, setLtvInput] = useState('8')

  const hayCicloAnual = plans.some((p) => !!p.priceArsAnual)
  const precioCiclo = (p: PlanLite) => (ciclo === 'annual' && p.priceArsAnual ? p.priceArsAnual : p.priceArs)
  const precioMensualEquiv = (p: PlanLite) => (ciclo === 'annual' && p.priceArsAnual ? p.priceArsAnual / 12 : p.priceArs)

  // "Mi" plan: el de la suscripción real si ya existe una fila (aunque esté vencida/pendiente),
  // si no, se infiere de la profesión — mismo criterio que usa el backend para elegir el plan de
  // migración/registro (SubscriptionService, "psicologo" → consultorio, cualquier otro → clinico).
  const perfilPlanCode = subscription?.plan?.code || (profession?.toLowerCase() === 'psicologo' ? 'consultorio' : 'clinico')
  const busq = BUSQUEDAS_POR_ESPECIALIDAD[perfilPlanCode as 'consultorio' | 'clinico'] || BUSQUEDAS_POR_ESPECIALIDAD.clinico

  const expired = subscription?.currentPeriodEnd && new Date(subscription.currentPeriodEnd) < new Date()
  const fechaOculto = expired
    ? new Date(subscription!.currentPeriodEnd as string).toLocaleDateString('es-AR', { day: 'numeric', month: 'long' })
    : null

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

  // Mismo modelo que el mockup (ver <script> de suscripcion-v11.html): seguimiento mensual, cada
  // paciente ocupa 1 turno/mes durante `ltv` meses → agenda llena = dia × 22 turnos/mes, reposición
  // = pacientes activos ÷ ltv. Recalculado acá con useMemo en vez de manipular el DOM a mano.
  const calc = useMemo(() => {
    const h = parseNum(honorarioInput)
    const d = parseNum(diaInput)
    const l = parseNum(ltvInput)

    let aviso = ''
    if (!h || !d || !l) aviso = 'Completá los tres campos para ver el cálculo.'
    else if (h < 8000) aviso = 'Ese honorario parece muy bajo. ¿Le faltan ceros?'
    else if (h > 600000) aviso = 'Ese honorario parece muy alto. ¿Le sobra un cero?'
    else if (d > 20) aviso = 'Más de 20 consultas por día son más de 10 horas seguidas. ¿Es correcto?'
    else if (l > 100) aviso = 'Más de 100 consultas por paciente es un tratamiento de varios años. ¿Es correcto?'

    if (!h || !d || !l) return { aviso, valid: false as const }

    const consultasMes = d * DIAS_HABILES_MES
    const activos = consultasMes
    const reposicion = activos / l
    const facturacion = consultasMes * h
    const valorPac = h * l

    const mio = plans.find((p) => p.code === perfilPlanCode) || plans[0] || null
    const m = mio ? precioMensualEquiv(mio) : 0
    const a = valorPac > 0 ? (m * 12) / valorPac : 0
    const meses = Math.max(1, Math.round(a > 0 ? 12 / a : 0))
    const sobran = Math.max(0, Math.round(reposicion) - a / 12)

    const frase = a <= 1
      ? '1 de esos pacientes por año'
      : (12 / a) >= 1.5
        ? `1 de esos pacientes cada ${meses} meses`
        : a <= 12
          ? 'menos de 1 de esos pacientes por mes'
          : `${(a / 12).toFixed(1).replace('.', ',')} de esos pacientes por mes`

    const pct = facturacion ? Math.min(100, (m / facturacion) * 100) : 0

    return {
      valid: true as const,
      aviso,
      consultasMes, activos, reposicion, facturacion, valorPac, m, a, frase,
      sobran: Math.round(sobran),
      seLlevaMasQueFactura: facturacion <= m,
      pct: Math.max(pct, 0.6),
    }
  }, [honorarioInput, diaInput, ltvInput, ciclo, plans, perfilPlanCode])

  const breakPorPlan = (p: PlanLite) => {
    const h = parseNum(honorarioInput)
    const l = parseNum(ltvInput)
    if (!h || !l) return null
    const valorPac = h * l
    const x = (precioMensualEquiv(p) * 12) / valorPac
    if (x <= 1) return <>Con <b>1 paciente nuevo por año</b> ya está pago</>
    if (12 / x >= 1.5) return <>Se paga con <b>1 paciente nuevo cada {Math.round(12 / x)} meses</b></>
    if (x <= 12) return <>Se paga con <b>menos de 1 paciente nuevo por mes</b></>
    return <>Se paga con <b>{(x / 12).toFixed(1).replace('.', ',')} pacientes nuevos por mes</b></>
  }

  return (
    <div className="rp-scope">
      <style>{`
        .rp-scope{
          --rp-crema:#F3F6F2; --rp-verde:#2FA84F; --rp-brote:#7CC53E; --rp-dark:#16311F;
          --rp-tinta:#0E1B12; --rp-gris:#5B6B60; --rp-linea:#DCE4DB; --rp-papel:#FFFFFF; --rp-apagado:#9AA69E;
          --rp-r:16px; --rp-sombra:0 1px 2px rgba(14,27,18,.05), 0 10px 30px -14px rgba(14,27,18,.22);
          background:var(--rp-crema);color:var(--rp-tinta);font-family:'Inter',var(--font-body),system-ui,sans-serif;
          font-size:15px;line-height:1.55;-webkit-font-smoothing:antialiased;min-height:100vh;
        }
        .rp-scope h1,.rp-scope h2,.rp-scope h3{font-family:'Sora',var(--font-heading),sans-serif;letter-spacing:-.025em;margin:0}
        .rp-wrap{max-width:960px;margin:0 auto;padding:36px 20px 70px}
        .rp-hero{background:var(--rp-dark);border-radius:var(--rp-r);padding:34px;box-shadow:var(--rp-sombra)}
        .rp-hero-grid{display:grid;grid-template-columns:1fr 396px;gap:34px;align-items:center}
        .rp-hero h1{font-size:31px;font-weight:800;color:#fff;line-height:1.2}
        .rp-hero h1 em{font-style:normal;color:var(--rp-brote)}
        .rp-hero .rp-sub{color:#9DB6A7;font-size:15px;margin:14px 0 0;max-width:40ch}
        .rp-hero .rp-sub b{color:#D6E6DB;font-weight:600}
        .rp-hero .rp-sub2{color:#EAF3EC;font-size:15px;margin:16px 0 0;max-width:42ch;border-left:2px solid var(--rp-brote);padding-left:14px;line-height:1.5}
        .rp-hero .rp-sub2 b{color:#fff;font-weight:600}
        .rp-franja{background:var(--rp-papel);border:1px solid var(--rp-linea);border-radius:var(--rp-r);margin-top:14px;padding:18px 24px;display:flex;align-items:center;gap:16px;box-shadow:var(--rp-sombra)}
        .rp-franja svg{flex:none}
        .rp-franja .rp-t{flex:1;min-width:200px}
        .rp-franja h3{font-size:16px;font-weight:800}
        .rp-franja p{margin:2px 0 0;font-size:13.5px;color:var(--rp-gris)}
        .rp-head-planes{display:flex;justify-content:space-between;align-items:flex-end;flex-wrap:wrap;gap:14px;margin:40px 0 16px}
        .rp-head-planes h2{font-size:22px;font-weight:800}
        .rp-head-planes p{margin:4px 0 0;font-size:14px;color:var(--rp-gris)}
        .rp-ciclo{display:inline-flex;background:#E7EDE7;border-radius:11px;padding:3px}
        .rp-ciclo button{font-family:inherit;font-size:13px;font-weight:600;color:var(--rp-gris);background:transparent;border:0;padding:9px 15px;border-radius:9px;cursor:pointer}
        .rp-ciclo button[aria-pressed="true"]{background:var(--rp-papel);color:var(--rp-tinta);box-shadow:0 1px 3px rgba(0,0,0,.09)}
        .rp-ciclo .rp-badge{font-size:11px;color:var(--rp-verde);font-weight:600;margin-left:5px}
        .rp-planes{display:grid;grid-template-columns:1fr 1fr;gap:14px;align-items:stretch}
        .rp-plan{background:var(--rp-papel);border:1px solid var(--rp-linea);border-radius:var(--rp-r);box-shadow:var(--rp-sombra);display:flex;flex-direction:column;position:relative;padding:26px 24px}
        .rp-plan.rp-rec{border:2px solid var(--rp-verde)}
        .rp-cinta{position:absolute;top:-1px;right:-1px;font-size:10.5px;font-weight:600;letter-spacing:.06em;text-transform:uppercase;color:#fff;background:var(--rp-verde);padding:5px 12px;border-radius:0 14px 0 11px}
        .rp-ico-plan{width:40px;height:40px;border-radius:12px;background:#EEF3EE;display:grid;place-items:center;margin-bottom:15px}
        .rp-plan.rp-rec .rp-ico-plan{background:#E4F5E8}
        .rp-plan h3{font-size:19px;font-weight:800}
        .rp-plan .rp-quien{font-size:13.5px;color:var(--rp-gris);margin:3px 0 0}
        .rp-precio{font-family:'Sora',sans-serif;font-size:35px;font-weight:800;line-height:1;letter-spacing:-.035em;margin-top:18px}
        .rp-precio .rp-sym{font-size:18px;font-weight:600;vertical-align:.36em;color:var(--rp-gris);margin-right:2px}
        .rp-unidad{font-size:13px;color:var(--rp-gris);margin-top:5px}
        .rp-tach{font-size:13px;color:var(--rp-apagado);text-decoration:line-through;margin-top:4px}
        .rp-break{margin-top:15px;font-size:13.5px;background:var(--rp-crema);border-radius:10px;padding:12px 14px;line-height:1.45}
        .rp-break b{font-weight:700}
        .rp-feats{list-style:none;margin:20px 0 0;padding:0;display:flex;flex-direction:column;gap:11px;flex:1}
        .rp-feats li{display:flex;gap:10px;font-size:13.5px;align-items:flex-start;line-height:1.4}
        .rp-feats li svg{flex:none;margin-top:1px}
        .rp-feats li.rp-destacado{font-weight:600}
        .rp-cta{margin-top:22px;display:block;width:100%;font-family:'Sora',sans-serif;font-size:15px;font-weight:700;border-radius:11px;padding:15px;cursor:pointer;border:0;transition:background .12s}
        .rp-cta.rp-p{color:#fff;background:var(--rp-verde)}
        .rp-cta.rp-p:hover:not(:disabled){background:#288F44}
        .rp-cta:disabled{opacity:.7;cursor:default}
        .rp-cta:focus-visible{outline:3px solid var(--rp-brote);outline-offset:2px}
        .rp-cta-sub{text-align:center;font-size:11.5px;color:var(--rp-gris);margin:9px 0 0;line-height:1.4}
        .rp-calc{margin-top:14px;background:var(--rp-papel);border:1px solid var(--rp-linea);border-radius:var(--rp-r);padding:24px;box-shadow:var(--rp-sombra)}
        .rp-calc h3{font-size:17px;font-weight:800;margin-bottom:4px}
        .rp-calc .rp-cap{font-size:13.5px;color:var(--rp-gris);margin:0 0 18px}
        .rp-calc-row{display:flex;align-items:center;gap:9px;flex-wrap:wrap;font-size:14px;color:var(--rp-gris)}
        .rp-calc input{font-family:'JetBrains Mono',monospace;font-size:14px;font-weight:700;width:100px;padding:8px 10px;border:1px solid var(--rp-linea);border-radius:9px;background:var(--rp-crema);color:var(--rp-tinta);text-align:right}
        .rp-calc input:focus{outline:2px solid var(--rp-brote);outline-offset:1px;border-color:transparent}
        .rp-pasos{margin-top:20px;display:flex;align-items:stretch;gap:10px}
        .rp-paso{flex:1;background:var(--rp-crema);border-radius:12px;padding:16px 16px 15px;display:flex;gap:12px;align-items:flex-start}
        .rp-paso-n{flex:none;width:32px;height:32px;border-radius:9px;background:var(--rp-papel);display:grid;place-items:center;border:1px solid var(--rp-linea)}
        .rp-paso-t{min-width:0}
        .rp-paso-rot{font-size:11px;font-weight:600;letter-spacing:.07em;text-transform:uppercase;color:var(--rp-gris)}
        .rp-paso-val{font-family:'Sora',sans-serif;font-size:16px;font-weight:800;letter-spacing:-.02em;margin-top:5px;line-height:1.25}
        .rp-paso-val.rp-destaca{color:var(--rp-verde)}
        .rp-paso-sub{font-size:12px;color:var(--rp-gris);margin-top:5px;line-height:1.4}
        .rp-flecha{flex:none;align-self:center}
        @media (max-width:880px){ .rp-pasos{flex-direction:column} .rp-flecha{transform:rotate(90deg);margin:0 auto} }
        .rp-veredicto{margin-top:20px;background:var(--rp-dark);border-radius:12px;padding:20px 22px;display:flex;align-items:center;gap:18px;flex-wrap:wrap}
        .rp-veredicto svg{flex:none}
        .rp-veredicto .rp-t{flex:1;min-width:230px}
        .rp-veredicto .rp-big{font-family:'Sora',sans-serif;font-size:23px;font-weight:800;color:#fff;letter-spacing:-.02em;line-height:1.2}
        .rp-veredicto .rp-big em{font-style:normal;color:var(--rp-brote)}
        .rp-veredicto .rp-s{font-size:13px;color:#9DB6A7;margin-top:7px}
        .rp-proporcion{margin-top:16px}
        .rp-prop-track{height:24px;background:#EDF2ED;border-radius:7px;overflow:hidden;position:relative}
        .rp-prop-fill{height:100%;background:var(--rp-dark);border-radius:7px;transition:width .35s cubic-bezier(.2,.7,.3,1)}
        .rp-prop-leg{display:flex;justify-content:space-between;margin-top:8px;font-size:12.5px;color:var(--rp-gris)}
        .rp-prop-leg b{color:var(--rp-tinta);font-weight:600}
        .rp-aviso{margin-top:12px;font-size:12.5px;color:#B67A0B;background:#FDF6E6;border:1px solid #F0E0BC;border-radius:9px;padding:9px 12px}
        .rp-gars{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:14px}
        .rp-gar{background:var(--rp-papel);border:1px solid var(--rp-linea);border-radius:13px;padding:18px}
        .rp-gar svg{margin-bottom:9px}
        .rp-gar h4{font-family:'Inter';font-size:13.5px;font-weight:600;margin:0 0 3px}
        .rp-gar p{margin:0;font-size:12.5px;color:var(--rp-gris);line-height:1.45}
        .rp-pie{margin-top:26px;text-align:center;font-size:13px;color:var(--rp-gris)}
        .rp-pie a,.rp-pie button{color:var(--rp-gris);text-decoration:underline;text-underline-offset:2px;background:none;border:0;font:inherit;cursor:pointer;padding:0}
        @media (max-width:880px){
          .rp-hero-grid,.rp-planes,.rp-gars{grid-template-columns:1fr}
          .rp-hero{padding:26px 22px}
          .rp-hero h1{font-size:25px}
          .rp-embudo{margin:0 auto}
          .rp-wrap{padding:22px 14px 50px}
          /* iOS Safari auto-zooms on focus when an input's font-size is under 16px — bump it
             here rather than on desktop, where the smaller monospace figures read better. */
          .rp-calc input{font-size:16px}
          /* Segmented Mensual/Anual control reads fine at 36px on desktop, but on a touch
             screen it's under the ~44px comfortable tap target — widen it on mobile/tablet. */
          .rp-ciclo button{padding:13px 16px}
          .rp-calc-row{gap:10px 9px}
        }
        @media (prefers-reduced-motion:reduce){.rp-scope *{transition:none!important}}
      `}</style>

      <div className="rp-wrap">
        {/* ══ HERO ══ */}
        <section className="rp-hero">
          <div className="rp-hero-grid">
            <div>
              <h1>{f(busq.volumen)} personas buscaron {busq.esp} en {ZONA} este año. <em>Tu perfil está en pausa.</em></h1>
              <p className="rp-sub">
                Podrían haber sido tus pacientes. <b>{fechaOculto ? <>Tu perfil está oculto desde el {fechaOculto}</> : 'Tu perfil todavía está oculto'}</b>, así que para esas {f(busq.volumen)} personas tu consultorio no existe. Volvés al buscador apenas reactivás.
              </p>
              <p className="rp-sub2">
                Mientras tu suscripción está inactiva, tu perfil no aparece en el buscador. <b>Reactivala y volvés a estar visible para quienes buscan un profesional en tu zona</b> — tu agenda, tus servicios y tus pacientes siguen tal cual los dejaste.
              </p>
            </div>
            <svg className="rp-embudo" viewBox="0 0 380 250" xmlns="http://www.w3.org/2000/svg" role="img" aria-label={`De ${f(VISITAS_ANIO)} personas que entraron a Tranqui este año, ${f(BUSCAN_ZONA)} buscaban ayuda en ${ZONA} y ${f(busq.volumen)} pedían ${busq.esp}. Tu perfil está oculto: ninguna te vio.`}>
              <defs>
                <linearGradient id="rp-g1" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stopColor="#7CC53E" /><stop offset="1" stopColor="#2FA84F" />
                </linearGradient>
              </defs>
              <path d="M12 14 H368 L332 62 H48 Z" fill="url(#rp-g1)" opacity=".95" />
              <text x="190" y="36" textAnchor="middle" fontFamily="JetBrains Mono, monospace" fontSize="21" fontWeight="700" fill="#0E1B12">{f(VISITAS_ANIO)}</text>
              <text x="190" y="52" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="10.5" fill="#16311F" opacity=".8">entraron a Tranqui este año</text>

              <path d="M52 70 H328 L300 118 H80 Z" fill="#2FA84F" opacity=".75" />
              <text x="190" y="92" textAnchor="middle" fontFamily="JetBrains Mono, monospace" fontSize="19" fontWeight="700" fill="#fff">{f(BUSCAN_ZONA)}</text>
              <text x="190" y="108" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="10.5" fill="#EAF3EC" opacity=".85">buscaban ayuda en {ZONA}</text>

              <path d="M84 126 H296 L272 174 H108 Z" fill="#2FA84F" opacity=".5" />
              <text x="190" y="148" textAnchor="middle" fontFamily="JetBrains Mono, monospace" fontSize="18" fontWeight="700" fill="#fff">{f(busq.volumen)}</text>
              <text x="190" y="164" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="10.5" fill="#EAF3EC" opacity=".85">pedían {busq.esp}</text>

              <rect x="112" y="182" width="156" height="52" rx="4" fill="none" stroke="#FFD98A" strokeWidth="1.6" strokeDasharray="5 4" />
              <text x="190" y="211" textAnchor="middle" fontFamily="Sora, sans-serif" fontSize="27" fontWeight="800" fill="#FFD98A">0</text>
              <text x="190" y="227" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="10.5" fill="#FFD98A" opacity=".9">te vieron: tu perfil está oculto</text>
            </svg>
          </div>
        </section>

        {/* ══ ZONA ══ */}
        <section className="rp-franja">
          <svg width="34" height="34" viewBox="0 0 24 24" fill="none"><path d="M12 21.5s7.5-6.4 7.5-11.5a7.5 7.5 0 10-15 0c0 5.1 7.5 11.5 7.5 11.5z" stroke="#2FA84F" strokeWidth="1.6" strokeLinejoin="round" /><circle cx="12" cy="10" r="2.8" fill="#7CC53E" /></svg>
          <div className="rp-t">
            <h3>Tu lugar en {ZONA} sigue reservado</h3>
            <p>Limitamos cuántos profesionales por zona reciben tráfico, para que esas {f(busq.volumen)} búsquedas no se repartan entre veinte perfiles.</p>
          </div>
        </section>

        {/* ══ PLANES ══ */}
        <div className="rp-head-planes">
          <div>
            <h2>Volvé al buscador</h2>
            <p>El precio sigue a lo que vale un paciente nuevo en tu especialidad. No es un gasto de software: es lo que cuesta estar visible.</p>
          </div>
          {hayCicloAnual && (
            <div className="rp-ciclo" role="group" aria-label="Ciclo de facturación">
              <button type="button" aria-pressed={ciclo === 'monthly'} onClick={() => setCiclo('monthly')}>Mensual</button>
              <button type="button" aria-pressed={ciclo === 'annual'} onClick={() => setCiclo('annual')}>Anual <span className="rp-badge">2 meses sin cargo</span></button>
            </div>
          )}
        </div>

        {loading ? (
          <p style={{ textAlign: 'center', color: 'var(--rp-gris)' }}>Cargando planes...</p>
        ) : (
          <div className="rp-planes">
            {plans.map((plan) => {
              const rec = plan.code === perfilPlanCode
              const m = precioMensualEquiv(plan)
              const tieneRecetas = plan.features.includes(RECETAS_KEY)
              const gestionFeatures = plan.features.filter((key) => key !== RECETAS_KEY)
              const brk = breakPorPlan(plan)

              return (
                <article key={plan.id} className={`rp-plan ${rec ? 'rp-rec' : ''}`}>
                  {rec && <span className="rp-cinta">Tu plan</span>}
                  <div className="rp-ico-plan">{ICO_PLAN[plan.code as 'consultorio' | 'clinico'] || ICO_PLAN.clinico}</div>
                  <h3>{plan.name.replace('Tranqui ', '')}</h3>
                  <p className="rp-quien">{plan.requiresPrescriber ? 'Para psiquiatras y médicos' : 'Para psicólogos'}</p>
                  <div className="rp-precio"><span className="rp-sym">$</span>{f(m)}</div>
                  <div className="rp-unidad">
                    por mes{ciclo === 'annual' ? ' · facturado anual' : ''} · $ {f(Math.round(m / 30))} por día
                  </div>
                  {ciclo === 'annual' && <div className="rp-tach">$ {f(plan.priceArs)} sin anual</div>}
                  {brk && <div className="rp-break">{brk}</div>}

                  <ul className="rp-feats">
                    <li className="rp-destacado">{ICO.buscador}<span>Perfil público visible en el buscador de Tranqui</span></li>
                    <li className="rp-destacado">{ICO.pin}<span>Lugar reservado en tu zona</span></li>
                    {gestionFeatures.map((key) => (
                      <li key={key}>
                        {ICO[key === 'agenda_compartida' ? 'agenda' : key === 'mp_split' ? 'cobro' : key === 'google_meet' ? 'meet' : key === 'reportes' ? 'rep' : 'clinica']}
                        <span>{FEATURE_LABELS[key] || key.replace(/_/g, ' ')}</span>
                      </li>
                    ))}
                    {tieneRecetas && (
                      <li>
                        {ICO.clinica}
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          <span style={{
                            fontSize: '9px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.02em',
                            color: '#B45309', background: '#FEF3C7', border: '1px solid #FDE68A',
                            borderRadius: '999px', padding: '2px 6px', flexShrink: 0,
                          }}>
                            Próximamente
                          </span>
                          {FEATURE_LABELS[RECETAS_KEY] || 'Recetas Electrónicas QBI2'}
                        </span>
                      </li>
                    )}
                  </ul>

                  <button
                    type="button"
                    className="rp-cta rp-p"
                    disabled={payingPlanId !== null}
                    onClick={() => handlePay(plan.id)}
                  >
                    {payingPlanId === plan.id ? 'Redirigiendo a Mercado Pago...' : `Reactivar por ${money(precioCiclo(plan))}`}
                  </button>
                  <p className="rp-cta-sub">Tu perfil vuelve al buscador apenas Mercado Pago confirma.</p>
                </article>
              )
            })}
          </div>
        )}

        {/* ══ CALCULADORA ══ */}
        {!loading && plans.length > 0 && (
          <section className="rp-calc">
            <h3>Cuántos pacientes nuevos necesita tu agenda</h3>
            <p className="rp-cap">Toda agenda pierde pacientes: los tratamientos terminan. La pregunta no es cuánto cuesta el plan, es con cuántos altas por mes te mantenés lleno.</p>

            <div className="rp-calc-row">
              <label htmlFor="rp-hon">Cobrás $</label>
              <input id="rp-hon" type="text" inputMode="numeric" value={honorarioInput}
                onChange={(e) => { const v = parseNum(e.target.value); setHonorarioInput(v ? f(v) : '') }} />
              <label htmlFor="rp-dia">por consulta, atendés</label>
              <input id="rp-dia" type="text" inputMode="numeric" value={diaInput} style={{ width: 56 }}
                onChange={(e) => { const v = parseNum(e.target.value); setDiaInput(v ? f(v) : '') }} />
              <label htmlFor="rp-ltv">consultas por día, y un paciente se queda</label>
              <input id="rp-ltv" type="text" inputMode="numeric" value={ltvInput} style={{ width: 56 }}
                onChange={(e) => { const v = parseNum(e.target.value); setLtvInput(v ? f(v) : '') }} />
              <label>consultas en promedio.</label>
            </div>

            <div className="rp-pasos">
              <div className="rp-paso">
                <div className="rp-paso-n">
                  <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><rect x="2.6" y="4" width="14.8" height="13.4" rx="2.2" stroke="#2FA84F" strokeWidth="1.6" /><path d="M2.6 8h14.8M6.6 2.4v3M13.4 2.4v3" stroke="#2FA84F" strokeWidth="1.6" strokeLinecap="round" /></svg>
                </div>
                <div className="rp-paso-t">
                  <div className="rp-paso-rot">Tu agenda llena</div>
                  <div className="rp-paso-val">{calc.valid ? `${f(calc.consultasMes)} consultas/mes` : '—'}</div>
                  <div className="rp-paso-sub">{calc.valid ? `${f(calc.activos)} pacientes activos, sobre ${DIAS_HABILES_MES} días hábiles` : 'Completá los datos de arriba'}</div>
                </div>
              </div>
              <svg className="rp-flecha" width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M4 10h11M11.5 6l4 4-4 4" stroke="#B9C4BC" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
              <div className="rp-paso">
                <div className="rp-paso-n">
                  <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="6.6" r="3" stroke="#2FA84F" strokeWidth="1.6" /><path d="M4 17c0-3.1 2.7-5 6-5s6 1.9 6 5" stroke="#2FA84F" strokeWidth="1.6" strokeLinecap="round" /></svg>
                </div>
                <div className="rp-paso-t">
                  <div className="rp-paso-rot">Para sostenerla</div>
                  <div className="rp-paso-val rp-destaca">{calc.valid ? `${f(Math.round(calc.reposicion))} pacientes nuevos/mes` : '—'}</div>
                  <div className="rp-paso-sub">{calc.valid ? `Porque cada paciente termina a los ${parseNum(ltvInput)} meses y deja el turno libre` : ''}</div>
                </div>
              </div>
              <svg className="rp-flecha" width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M4 10h11M11.5 6l4 4-4 4" stroke="#B9C4BC" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
              <div className="rp-paso">
                <div className="rp-paso-n">
                  <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="7.4" stroke="#2FA84F" strokeWidth="1.6" /><path d="M6.6 10.4l2.4 2.4 4.6-5" stroke="#2FA84F" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
                </div>
                <div className="rp-paso-t">
                  <div className="rp-paso-rot">El plan se paga con</div>
                  <div className="rp-paso-val rp-destaca">{calc.valid ? calc.frase : '—'}</div>
                  <div className="rp-paso-sub">{calc.valid ? `Los otros ${f(calc.sobran)} del mes quedan enteros para vos` : ''}</div>
                </div>
              </div>
            </div>

            <div className="rp-veredicto">
              <svg width="46" height="46" viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="21" stroke="#7CC53E" strokeWidth="1.5" opacity=".4" />
                <path d="M24 3a21 21 0 018.5 40.2" stroke="#7CC53E" strokeWidth="2.6" strokeLinecap="round" />
                <path d="M16 25.5l5.5 5.5L33 19" stroke="#7CC53E" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <div className="rp-t">
                <div className="rp-big">
                  {calc.valid
                    ? (calc.seLlevaMasQueFactura
                      ? <>Con esos números, <em>el plan se lleva más de lo que facturás</em></>
                      : <>Necesitás <em>{f(Math.round(calc.reposicion))} altas por mes</em> para no vaciar la agenda</>)
                    : 'Completá los datos para ver tu resultado'}
                </div>
                <div className="rp-s">
                  {calc.valid
                    ? (calc.seLlevaMasQueFactura
                      ? 'Revisá los valores, o escribinos: si estás arrancando, conviene esperar.'
                      : `Cada alta vale ${money(calc.valorPac)} a lo largo del tratamiento. El plan es el ${(calc.m / calc.facturacion * 100).toFixed(1).replace('.', ',')}% de lo que factura tu agenda llena, y se cubre con ${calc.frase.toLowerCase()}.`)
                    : ''}
                </div>
              </div>
            </div>

            <div className="rp-proporcion">
              <div className="rp-prop-track"><div className="rp-prop-fill" style={{ width: `${calc.valid ? calc.pct.toFixed(1) : 1.1}%` }} /></div>
              <div className="rp-prop-leg">
                <span>El plan: <b>{money(calc.valid ? calc.m : 0)}</b></span>
                <span>Tu agenda llena factura: <b>{money(calc.valid ? calc.facturacion : 0)}</b></span>
              </div>
            </div>

            {calc.aviso && <div className="rp-aviso">{calc.aviso}</div>}
          </section>
        )}

        {/* ══ GARANTÍAS ══ */}
        <div className="rp-gars">
          <div className="rp-gar">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M9 12.5l2.2 2.2L15.5 10" stroke="#2FA84F" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /><path d="M12 2.8l7.5 2.7v6c0 4.6-3.1 8.7-7.5 10-4.4-1.3-7.5-5.4-7.5-10v-6L12 2.8z" stroke="#2FA84F" strokeWidth="1.5" strokeLinejoin="round" /></svg>
            <h4>Cero comisión, siempre</h4>
            <p>No nos quedamos con nada de lo que cobrás. Ni de tus pacientes, ni de los que te trae Tranqui.</p>
          </div>
          <div className="rp-gar">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M12 6v6l4 2" stroke="#2FA84F" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" /><circle cx="12" cy="12" r="9" stroke="#2FA84F" strokeWidth="1.5" /></svg>
            <h4>¿Necesitás cancelar?</h4>
            <p>Escribinos a soporte y te la damos de baja sin vueltas — la seguís usando hasta que termine el período pago.</p>
          </div>
        </div>

        <p className="rp-pie">
          {onLogout && <button type="button" onClick={onLogout}>Cerrar sesión</button>}
          {' · '}<a href="mailto:soporte@tranquisalud.com">Hablar con soporte</a>
        </p>
      </div>
    </div>
  )
}
