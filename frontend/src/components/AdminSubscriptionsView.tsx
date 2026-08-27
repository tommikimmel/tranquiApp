import React, { useEffect, useState, useMemo } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import type {
  AdminOverviewStatsDto,
  SubscriptionDto,
  InvoiceDto,
  PlanDto,
  ManualPaymentRequest,
  SubscriptionEventDto,
} from '../types/subscription'

function IconSearch({ size = 15 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}

// ── Traducciones para la auditoría: sin esto, el historial muestra códigos internos
// (ACTIVE, MP_PREAPPROVAL_STATUS_CHANGED, MP_WEBHOOK...) que no dicen nada a simple vista. ──
const STATUS_LABELS: Record<string, string> = {
  REGISTERED: 'Registrado (sin verificar)',
  PENDING_VERIFICATION: 'Pendiente de verificación',
  VERIFIED: 'Verificado',
  SUBSCRIPTION_PENDING: 'Pendiente de pago inicial',
  ACTIVE: 'Activa',
  PAST_DUE: 'Vencida (en gracia)',
  SUSPENDED: 'Suspendida',
  CANCELLED: 'Cancelada',
  REJECTED: 'Rechazado',
}

function statusLabel(status?: string | null): string {
  if (!status) return '—'
  return STATUS_LABELS[status] || status
}

type EventTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const EVENT_TYPE_META: Record<string, { label: string; description: string; tone: EventTone }> = {
  MP_CHECKOUT_CREATED: {
    label: 'Checkout de Mercado Pago iniciado',
    description: 'El profesional inició el proceso de pago en Mercado Pago (todavía no se confirmó el cobro).',
    tone: 'info',
  },
  MP_SUBSCRIPTION_PAYMENT: {
    label: 'Cobro de Mercado Pago',
    description: 'Mercado Pago notificó un cobro recurrente de la suscripción (aprobado o rechazado).',
    tone: 'success',
  },
  MP_PREAPPROVAL_STATUS_CHANGED: {
    label: 'Cambio de estado en Mercado Pago',
    description: 'Mercado Pago autorizó, pausó o canceló la suscripción recurrente del profesional.',
    tone: 'info',
  },
  MANUAL_PAYMENT_ACTIVATED: {
    label: 'Pago manual registrado por un admin',
    description: 'Un administrador registró un cobro manual (efectivo, transferencia o cortesía) y activó la suscripción.',
    tone: 'success',
  },
  ADMIN_STATUS_CHANGE: {
    label: 'Estado modificado manualmente',
    description: 'Un administrador cambió a mano el estado de la suscripción desde el panel.',
    tone: 'warning',
  },
  SUBSCRIPTION_SUSPENDED_GRACE_EXPIRED: {
    label: 'Suspendida automáticamente',
    description: 'El sistema suspendió la suscripción porque venció la ventana de gracia sin registrarse un nuevo pago.',
    tone: 'danger',
  },
  MIGRATION_GRACE_PERIOD_GRANTED: {
    label: 'Acceso inicial de gracia otorgado',
    description: 'El sistema le dio acceso temporal a un profesional que ya estaba dado de alta antes de existir el cobro de suscripciones.',
    tone: 'info',
  },
  PROFESSIONAL_VERIFIED: {
    label: 'Profesional verificado',
    description: 'Un administrador aprobó la verificación de matrícula del profesional.',
    tone: 'success',
  },
  PROFESSIONAL_REJECTED: {
    label: 'Verificación rechazada',
    description: 'Un administrador rechazó la verificación de matrícula del profesional.',
    tone: 'danger',
  },
}

function eventMeta(eventType: string): { label: string; description: string; tone: EventTone } {
  return EVENT_TYPE_META[eventType] || { label: eventType, description: '', tone: 'neutral' }
}

function describeActor(actorType?: string, actorId?: string): string {
  const t = (actorType || '').toUpperCase()
  if (t === 'ADMIN') return actorId ? `Administrador (${actorId})` : 'Un administrador'
  if (t === 'SYSTEM') return 'Sistema automático'
  if (t === 'MP_WEBHOOK') return 'Mercado Pago (notificación automática)'
  if (t === 'PROFESSIONAL') return 'El propio profesional'
  return actorId ? `${actorType}: ${actorId}` : (actorType || 'Desconocido')
}

const EVENT_TONE_COLORS: Record<EventTone, { bg: string; fg: string; border: string }> = {
  success: { bg: 'var(--green-50)', fg: 'var(--color-success)', border: 'var(--green-200, #bbf7d0)' },
  warning: { bg: 'var(--color-warning-bg)', fg: 'var(--color-warning)', border: '#fde68a' },
  danger: { bg: '#FEF2F2', fg: 'var(--color-danger)', border: '#fecaca' },
  info: { bg: '#EFF6FF', fg: 'var(--color-info)', border: '#BFDBFE' },
  neutral: { bg: 'var(--neutral-100)', fg: 'var(--color-text-secondary)', border: 'var(--color-border)' },
}

// Tarjeta de un evento de auditoría, compartida entre el historial global y el modal por
// suscripción para que el mismo evento se lea igual sin importar desde dónde se lo mire.
function SubscriptionEventCard({ ev, professionalLabel }: { ev: SubscriptionEventDto; professionalLabel?: string }) {
  const meta = eventMeta(ev.eventType)
  const colors = EVENT_TONE_COLORS[meta.tone]
  const hasTransition = !!(ev.previousStatus || ev.newStatus)

  return (
    <div style={{ padding: 'var(--space-3) var(--space-4)', backgroundColor: 'var(--neutral-50)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', borderLeft: `4px solid ${colors.fg}` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '6px', marginBottom: '4px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <span style={{ backgroundColor: colors.bg, color: colors.fg, border: `1px solid ${colors.border}`, borderRadius: '999px', padding: '2px 10px', fontSize: '12px', fontWeight: 700 }}>
            {meta.label}
          </span>
          {professionalLabel && (
            <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>{professionalLabel}</span>
          )}
        </div>
        <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)', whiteSpace: 'nowrap' }} title={new Date(ev.createdAt).toString()}>
          {new Date(ev.createdAt).toLocaleString('es-AR')}
        </span>
      </div>

      {meta.description && (
        <p style={{ margin: '0 0 6px 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>{meta.description}</p>
      )}

      {hasTransition && (
        <div style={{ fontSize: '12.5px', marginBottom: '4px' }}>
          Estado: <span style={{ color: 'var(--color-text-secondary)' }}>{statusLabel(ev.previousStatus)}</span>
          {' ➔ '}
          <strong style={{ color: colors.fg }}>{statusLabel(ev.newStatus)}</strong>
        </div>
      )}

      <div style={{ fontSize: '11.5px', color: 'var(--color-text-secondary)', display: 'flex', flexWrap: 'wrap', gap: '4px 10px' }}>
        <span>Responsable: {describeActor(ev.actorType, ev.actorId)}</span>
      </div>

      {ev.details && (
        <div style={{ fontSize: '11.5px', color: 'var(--color-text-secondary)', marginTop: '4px', fontStyle: 'italic' }}>
          {ev.details}
        </div>
      )}
    </div>
  )
}

interface UserLite {
  id: number
  nombre: string
  apellido?: string
  email: string
  rol: string
  profession?: string
  taxId?: string
  cuit?: string
  cuil?: number
  legalName?: string
  ivaConditionId?: number
  fiscalAddress?: string
  verificadoAdmin?: boolean
}

export default function AdminSubscriptionsView({ users = [] }: { users?: UserLite[] }) {
  const { showAlert } = useAlert()
  const [loading, setLoading] = useState(true)
  const [subTab, setSubTab] = useState<'overview' | 'subscriptions' | 'plans' | 'invoices' | 'events'>('overview')

  const [overview, setOverview] = useState<AdminOverviewStatsDto | null>(null)
  const [subscriptions, setSubscriptions] = useState<SubscriptionDto[]>([])
  const [invoices, setInvoices] = useState<InvoiceDto[]>([])
  const [plans, setPlans] = useState<PlanDto[]>([])
  const [allEvents, setAllEvents] = useState<SubscriptionEventDto[]>([])
  const [eventTypeFilter, setEventTypeFilter] = useState<string>('TODOS')
  const [eventSearch, setEventSearch] = useState('')

  // Modal manual payment state
  const [showManualModal, setShowManualModal] = useState(false)
  const [manualProfSearch, setManualProfSearch] = useState('')
  const [selectedProf, setSelectedProf] = useState<UserLite | null>(null)
  const [selectedPlanId, setSelectedPlanId] = useState<number | ''>('')
  const [manualAmount, setManualAmount] = useState<number>(149500)
  const [manualMethod, setManualMethod] = useState<'MANUAL_TRANSFER' | 'MANUAL_CASH' | 'COURTESY'>('MANUAL_TRANSFER')
  const [manualStartDate, setManualStartDate] = useState(new Date().toISOString().split('T')[0])
  const [manualEndDate, setManualEndDate] = useState(
    new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
  )
  const [manualReference, setManualReference] = useState('')
  const [manualNotes, setManualNotes] = useState('')
  const [manualEmitInvoice, setManualEmitInvoice] = useState(true)
  const [manualIdempotencyKey, setManualIdempotencyKey] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Modal Credit Note state
  const [selectedInvoiceForNc, setSelectedInvoiceForNc] = useState<InvoiceDto | null>(null)
  const [ncReason, setNcReason] = useState('Anulación de comprobante por devolución o ajuste')
  const [isSubmittingNc, setIsSubmittingNc] = useState(false)

  // Modal Edit Plan / Base Price state
  const [editingPlan, setEditingPlan] = useState<PlanDto | null>(null)
  const [editPlanName, setEditPlanName] = useState('')
  const [editPlanPriceArs, setEditPlanPriceArs] = useState<number>(0)
  const [editPlanPriceUsdRef, setEditPlanPriceUsdRef] = useState<number>(0)
  const [editPlanPriceArsAnual, setEditPlanPriceArsAnual] = useState<number>(0)
  const [editPlanDesc, setEditPlanDesc] = useState('')
  const [editPlanIsActive, setEditPlanIsActive] = useState(true)
  const [isSubmittingPlan, setIsSubmittingPlan] = useState(false)

  // Modal Subscription Audit Events state
  const [selectedSubForAudit, setSelectedSubForAudit] = useState<SubscriptionDto | null>(null)
  const [subEvents, setSubEvents] = useState<SubscriptionEventDto[]>([])
  const [loadingSubEvents, setLoadingSubEvents] = useState(false)

  // Modal Change Subscription Status state
  const [selectedSubForStatus, setSelectedSubForStatus] = useState<SubscriptionDto | null>(null)
  const [newSubStatus, setNewSubStatus] = useState<string>('ACTIVE')
  const [isSubmittingStatus, setIsSubmittingStatus] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    try {
      const [overviewData, subsData, invoicesData, plansData, eventsData] = await Promise.all([
        api.getAdminSubscriptionOverview(),
        api.getAdminSubscriptionsList(),
        api.getAdminInvoices(),
        // Catálogo completo (activos + ocultos) — con getSubscriptionPlans() (solo activos, el
        // mismo endpoint público que usa ChoosePlanView) un plan desactivado desaparecía de acá
        // sin forma de volver a activarlo.
        api.getAdminSubscriptionPlans(),
        api.getSubscriptionEvents().catch(() => []),
      ])
      setOverview(overviewData)
      setSubscriptions(subsData)
      setInvoices(invoicesData)
      setPlans(plansData)
      setAllEvents(Array.isArray(eventsData) ? eventsData : [])
    } catch (err: any) {
      showAlert('Error al cargar datos de suscripciones y facturación: ' + err.message, 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  const openManualModal = () => {
    const newIdemKey = 'ADM-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9)
    setManualIdempotencyKey(newIdemKey)
    setSelectedProf(null)
    setManualProfSearch('')
    setSelectedPlanId(visiblePlans.length > 0 ? visiblePlans[0].id : '')
    setManualAmount(visiblePlans.length > 0 ? visiblePlans[0].priceArs : 149500)
    setManualMethod('MANUAL_TRANSFER')
    setManualStartDate(new Date().toISOString().split('T')[0])
    setManualEndDate(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0])
    setManualReference('')
    setManualNotes('')
    setManualEmitInvoice(true)
    setShowManualModal(true)
  }

  const handlePlanChange = (planId: number) => {
    setSelectedPlanId(planId)
    const p = visiblePlans.find((x) => x.id === planId)
    if (p) {
      setManualAmount(p.priceArs)
    }
  }

  const handleSelectProf = (u: UserLite) => {
    setSelectedProf(u)
    setManualProfSearch(`${u.nombre} ${u.apellido || ''}`)
    // Auto-sugiere el plan según profession — el rol "PSIQUIATRA" lo comparten psicólogos y
    // psiquiatras en este sistema (no sirve para distinguirlos), así que no se lo usa acá.
    if (u.profession === 'psicologo') {
      const consultorio = visiblePlans.find((p) => p.code === 'consultorio')
      if (consultorio) {
        setSelectedPlanId(consultorio.id)
        setManualAmount(consultorio.priceArs)
      }
    } else {
      const clinico = visiblePlans.find((p) => p.code === 'clinico')
      if (clinico) {
        setSelectedPlanId(clinico.id)
        setManualAmount(clinico.priceArs)
      }
    }
  }

  const handleManualSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedProf) {
      showAlert('Seleccioná un profesional de la lista', 'warning')
      return
    }
    if (!selectedPlanId) {
      showAlert('Seleccioná un plan', 'warning')
      return
    }
    if (!manualAmount || manualAmount <= 0) {
      showAlert('El monto debe ser mayor a 0', 'warning')
      return
    }

    setIsSubmitting(true)
    try {
      const payload: ManualPaymentRequest = {
        professionalId: selectedProf.id,
        planId: Number(selectedPlanId),
        amountArs: manualAmount,
        method: manualMethod,
        periodStart: manualStartDate + 'T00:00:00',
        periodEnd: manualEndDate + 'T23:59:59',
        receiptReference: manualReference,
        notes: manualNotes,
        emitInvoice: manualMethod !== 'COURTESY' && manualEmitInvoice,
        idempotencyKey: manualIdempotencyKey,
      }

      await api.registerAdminManualPayment(payload)
      showAlert('Pago manual registrado y suscripción activada correctamente.', 'success')
      setShowManualModal(false)
      fetchData()
    } catch (err: any) {
      showAlert('Error al registrar pago manual: ' + err.message, 'error')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEmitNotaCredito = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedInvoiceForNc) return
    setIsSubmittingNc(true)
    try {
      await api.emitirNotaDeCredito(selectedInvoiceForNc.id, ncReason)
      showAlert('Nota de Crédito C emitida exitosamente en ARCA.', 'success')
      setSelectedInvoiceForNc(null)
      fetchData()
    } catch (err: any) {
      showAlert('Error al emitir Nota de Crédito: ' + err.message, 'error')
    } finally {
      setIsSubmittingNc(false)
    }
  }

  const handleRunReconciliation = async () => {
    try {
      await api.ejecutarReconciliacionAdmin()
      showAlert('Reconciliación y chequeo de topes ejecutados con éxito.', 'success')
      fetchData()
    } catch (err: any) {
      showAlert('Error en reconciliación: ' + err.message, 'error')
    }
  }

  // Open Edit Plan Modal
  const openEditPlanModal = (plan: PlanDto) => {
    setEditingPlan(plan)
    setEditPlanName(plan.name)
    setEditPlanPriceArs(plan.priceArs)
    setEditPlanPriceUsdRef(plan.priceUsdRef || 0)
    setEditPlanPriceArsAnual(plan.priceArsAnual || plan.priceArs * 10)
    setEditPlanDesc(plan.description || '')
    setEditPlanIsActive(plan.isActive !== false)
  }

  const handleSavePlan = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingPlan) return
    setIsSubmittingPlan(true)
    try {
      await api.updateSubscriptionPlan(editingPlan.id, {
        name: editPlanName,
        priceArs: editPlanPriceArs,
        priceUsdRef: editPlanPriceUsdRef,
        priceArsAnual: editPlanPriceArsAnual,
        description: editPlanDesc,
        isActive: editPlanIsActive,
      })
      showAlert(`Plan "${editPlanName}" actualizado exitosamente. Nuevo precio base: ${formatCurrency(editPlanPriceArs)} ARS`, 'success')
      setEditingPlan(null)
      fetchData()
    } catch (err: any) {
      showAlert('Error al actualizar plan: ' + err.message, 'error')
    } finally {
      setIsSubmittingPlan(false)
    }
  }

  // Open Subscription Audit Events Modal
  const openSubAuditModal = async (sub: SubscriptionDto) => {
    setSelectedSubForAudit(sub)
    setLoadingSubEvents(true)
    try {
      const events = await api.getSubscriptionEvents(sub.id)
      setSubEvents(Array.isArray(events) ? events : [])
    } catch (err: any) {
      showAlert('Error al cargar historial de auditoría: ' + err.message, 'error')
    } finally {
      setLoadingSubEvents(false)
    }
  }

  // Handle Subscription Status Change
  const handleChangeStatus = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedSubForStatus) return
    setIsSubmittingStatus(true)
    try {
      await api.updateSubscriptionStatus(selectedSubForStatus.id, newSubStatus)
      showAlert(`Estado de suscripción modificado a ${newSubStatus} exitosamente.`, 'success')
      setSelectedSubForStatus(null)
      fetchData()
    } catch (err: any) {
      showAlert('Error al cambiar estado: ' + err.message, 'error')
    } finally {
      setIsSubmittingStatus(false)
    }
  }

  // El alta manual solo debe ofrecer planes que un profesional puede contratar hoy — no el plan
  // "equipo" oculto (ver PlanService: no se ofrece por ahora), que sigue en `plans` para que la
  // pestaña "Planes y Precios Base" pueda mostrarlo y reactivarlo.
  const visiblePlans = useMemo(() => plans.filter((p) => p.isActive !== false), [plans])

  const eligibleProfessionals = useMemo(() => {
    const search = manualProfSearch.toLowerCase().trim()
    return users
      // Antes también dejaba pasar cualquier usuario con `profession` o `verificadoAdmin` en
      // true — ambos campos pueden estar presentes en pacientes también, así que colaban
      // pacientes en el buscador de "Profesional de la Salud". El rol es la única señal
      // confiable de que un usuario es profesional (mismo criterio que el resto del admin).
      .filter((u) => u.rol === 'PSIQUIATRA')
      .filter((u) => {
        if (!search) return true
        const full = `${u.nombre} ${u.apellido || ''} ${u.email} ${u.cuit || ''} ${u.taxId || ''}`.toLowerCase()
        return full.includes(search)
      })
  }, [users, manualProfSearch])

  // Para mostrar "Dr. Juan Pérez" en vez de "Suscripción #45" en el historial global.
  const subscriptionsById = useMemo(() => {
    const map = new Map<number, SubscriptionDto>()
    subscriptions.forEach((s) => map.set(s.id, s))
    return map
  }, [subscriptions])

  const eventTypesPresent = useMemo(() => {
    const types = new Set<string>()
    allEvents.forEach((ev) => types.add(ev.eventType))
    return Array.from(types)
  }, [allEvents])

  const filteredEvents = useMemo(() => {
    const term = eventSearch.toLowerCase().trim()
    return allEvents.filter((ev) => {
      if (eventTypeFilter !== 'TODOS' && ev.eventType !== eventTypeFilter) return false
      if (!term) return true
      const sub = subscriptionsById.get(ev.subscriptionId)
      const haystack = `${sub?.professionalName || ''} ${sub?.professionalEmail || ''} #${ev.subscriptionId} ${ev.details || ''}`.toLowerCase()
      return haystack.includes(term)
    })
  }, [allEvents, eventTypeFilter, eventSearch, subscriptionsById])

  const formatCurrency = (val?: number) => {
    if (val === undefined || val === null) return '$ 0'
    return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 }).format(val)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* ── Top Bar: Header & Actions ── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
            Suscripciones y Facturación ARCA
          </h2>
          <p style={{ margin: '4px 0 0 0', fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
            Administración completa de planes, precios base de cupo, altas manuales, auditoría de eventos y comprobantes ARCA.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <button onClick={handleRunReconciliation} className="btn btn--secondary btn--sm" title="Ejecutar cron diario de control de vencimientos y topes" style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/>
            </svg>
            Reconciliación
          </button>
          <button onClick={() => setSubTab('plans')} className="btn btn--secondary btn--sm" style={{ fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
            Precios Base de Planes
          </button>
          <button onClick={openManualModal} className="btn btn--primary btn--sm" style={{ fontWeight: 'bold', display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Registrar Cobro / Alta Manual
          </button>
        </div>
      </div>

      {/* ── Monotributo Tracker Banner & Alert (§3) ── */}
      {overview && (
        <div
          className="card"
          style={{
            padding: 'var(--space-5)',
            border: overview.alertaMonotributo80 ? '2px solid var(--color-danger)' : '1px solid var(--color-border)',
            backgroundColor: overview.alertaMonotributo80 ? '#FEF2F2' : 'var(--color-surface)',
            borderRadius: 'var(--radius-lg)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-3)'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ color: overview.alertaMonotributo80 ? 'var(--color-danger)' : 'var(--color-primary)' }}>
                <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
              <strong style={{ fontSize: '15px', color: overview.alertaMonotributo80 ? 'var(--color-danger)' : 'var(--color-text-primary)' }}>
                Control de Facturación Rodante Monotributo (Últimos 12 Meses)
              </strong>
              {overview.alertaMonotributo80 && (
                <span className="badge badge--error" style={{ fontSize: '11px', fontWeight: 'bold', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                  ALERTA: Superó el 80% del tope de Categoría A
                </span>
              )}
            </div>
            <span style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>
              Acumulado: <strong>{formatCurrency(overview.totalFacturado12Meses)}</strong> / Tope Cat A:{' '}
              <strong>{formatCurrency(overview.topeCategoriaA)}</strong> ({overview.porcentajeUsoCategoriaA?.toFixed(1)}%)
            </span>
          </div>

          {/* Progress Bar */}
          <div style={{ width: '100%', height: '10px', backgroundColor: '#E5E7EB', borderRadius: '999px', overflow: 'hidden' }}>
            <div
              style={{
                width: `${Math.min(overview.porcentajeUsoCategoriaA || 0, 100)}%`,
                height: '100%',
                backgroundColor: overview.alertaMonotributo80 ? 'var(--color-danger)' : 'var(--color-primary)',
                borderRadius: '999px',
                transition: 'width 0.5s ease-in-out'
              }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: 'var(--color-text-secondary)' }}>
            <span>Tope Cat A: $ 12.009.410</span>
            <span>Tope Cat B: $ 17.595.183</span>
            <span>Tope Cat K (Máximo de Servicios): $ 126.610.839</span>
          </div>
        </div>
      )}

      {/* ── KPI Summary Cards ── */}
      {overview && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-4)' }}>
          <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)', display: 'block' }}>Suscriptores Activos</span>
            <strong style={{ fontSize: '24px', color: 'var(--color-primary)' }}>{overview.suscriptoresActivos ?? 0}</strong>
          </div>

          <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)', display: 'block' }}>En Período de Gracia</span>
            <strong style={{ fontSize: '24px', color: 'var(--color-warning)' }}>{overview.suscriptoresEnGracia ?? 0}</strong>
          </div>

          <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)', display: 'block' }}>MRR (Ingreso Mensual Recurrente)</span>
            <strong style={{ fontSize: '24px', color: 'var(--color-success)' }}>{formatCurrency(overview.mrr ?? 0)}</strong>
          </div>

          <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)', display: 'block' }}>Facturas Emitidas</span>
            <strong style={{ fontSize: '24px', color: 'var(--color-text-primary)' }}>{invoices.length ?? 0}</strong>
          </div>
        </div>
      )}

      {/* ── Sub Navigation Tabs ── */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', borderBottom: '1px solid var(--color-border)', paddingBottom: '2px', overflowX: 'auto' }}>
        <button
          onClick={() => setSubTab('overview')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: 'none',
            borderBottom: subTab === 'overview' ? '3px solid var(--color-primary)' : '3px solid transparent',
            color: subTab === 'overview' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
            fontWeight: '700',
            cursor: 'pointer',
            fontSize: '14px',
            whiteSpace: 'nowrap'
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            Suscripciones Activas ({subscriptions.length})
          </span>
        </button>

        <button
          onClick={() => setSubTab('plans')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: 'none',
            borderBottom: subTab === 'plans' ? '3px solid var(--color-primary)' : '3px solid transparent',
            color: subTab === 'plans' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
            fontWeight: '700',
            cursor: 'pointer',
            fontSize: '14px',
            whiteSpace: 'nowrap'
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
            Planes y Precios Base ({plans.length})
          </span>
        </button>

        <button
          onClick={() => setSubTab('invoices')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: 'none',
            borderBottom: subTab === 'invoices' ? '3px solid var(--color-primary)' : '3px solid transparent',
            color: subTab === 'invoices' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
            fontWeight: '700',
            cursor: 'pointer',
            fontSize: '14px',
            whiteSpace: 'nowrap'
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>
            </svg>
            Facturas Electrónicas ARCA ({invoices.length})
          </span>
        </button>

        <button
          onClick={() => setSubTab('events')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: 'none',
            borderBottom: subTab === 'events' ? '3px solid var(--color-primary)' : '3px solid transparent',
            color: subTab === 'events' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
            fontWeight: '700',
            cursor: 'pointer',
            fontSize: '14px',
            whiteSpace: 'nowrap'
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
            </svg>
            Historial y Auditoría ({allEvents.length})
          </span>
        </button>
      </div>

      {/* ── SubTab 1: Subscriptions Table ── */}
      {subTab === 'overview' && (
        <div className="card" style={{ padding: 'var(--space-5)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
              <div className="checkout-spinner" style={{ margin: '0 auto var(--space-3)' }} />
              <p>Cargando suscripciones...</p>
            </div>
          ) : subscriptions.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-secondary)' }}>
              No hay suscripciones registradas todavía. Podés dar de alta la primera con el botón "+ Registrar Cobro / Alta Manual".
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid var(--color-border)', color: 'var(--color-text-secondary)' }}>
                    <th style={{ padding: '10px' }}>Profesional</th>
                    <th style={{ padding: '10px' }}>Plan</th>
                    <th style={{ padding: '10px' }}>Monto ARS</th>
                    <th style={{ padding: '10px' }}>Origen de Cobro</th>
                    <th style={{ padding: '10px' }}>Período Cubierto</th>
                    <th style={{ padding: '10px' }}>Estado</th>
                    <th style={{ padding: '10px', textAlign: 'right' }}>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {subscriptions.map((sub) => {
                    const statusBadge =
                      sub.status === 'ACTIVE'
                        ? 'badge--success'
                        : sub.status === 'PAST_DUE'
                        ? 'badge--warning'
                        : sub.status === 'SUSPENDED' || sub.status === 'CANCELLED'
                        ? 'badge--error'
                        : 'badge--info'

                    const billingSourceLabel =
                      sub.billingSource === 'MERCADOPAGO'
                        ? 'Mercado Pago'
                        : sub.billingSource === 'MANUAL_CASH'
                        ? 'Efectivo'
                        : sub.billingSource === 'MANUAL_TRANSFER'
                        ? 'Transferencia'
                        : 'Cortesía'

                    return (
                      <tr key={sub.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                        <td style={{ padding: '10px' }}>
                          <strong style={{ display: 'block', color: 'var(--color-text-primary)' }}>{sub.professionalName}</strong>
                          <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{sub.professionalEmail}</span>
                        </td>
                        <td style={{ padding: '10px' }}>
                          <span style={{ fontWeight: '600' }}>{sub.plan ? sub.plan.name : '—'}</span>
                          {sub.plan?.code === 'clinico' && (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px', fontSize: '10px', color: 'var(--color-primary)', marginTop: '2px' }}>
                              <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                              </svg>
                              Con Recetas QBI2
                            </span>
                          )}
                        </td>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>
                          {formatCurrency(sub.amountArs)}
                          {sub.billingCycle === 'annual' && (
                            <span style={{ display: 'block', fontSize: '10px', fontWeight: 'normal', color: 'var(--color-text-secondary)' }}>ciclo anual</span>
                          )}
                        </td>
                        <td style={{ padding: '10px' }}>
                          <span className="badge badge--neutral">{billingSourceLabel}</span>
                        </td>
                        <td style={{ padding: '10px', fontSize: '12px' }}>
                          {sub.currentPeriodStart ? (
                            <>
                              Hasta: <strong>{new Date(sub.currentPeriodEnd || '').toLocaleDateString('es-AR')}</strong>
                              {sub.graceUntil && (
                                <span style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                                  Gracia: {new Date(sub.graceUntil).toLocaleDateString('es-AR')}
                                </span>
                              )}
                            </>
                          ) : (
                            <span style={{ color: 'var(--color-text-secondary)' }}>Sin período</span>
                          )}
                        </td>
                        <td style={{ padding: '10px' }}>
                          <span className={`badge ${statusBadge}`} title={sub.status}>{statusLabel(sub.status)}</span>
                        </td>
                        <td style={{ padding: '10px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            <button
                              className="btn btn--secondary btn--sm"
                              onClick={() => openSubAuditModal(sub)}
                              title="Ver historial de eventos y transiciones"
                              style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            >
                              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                              </svg>
                              Auditoría
                            </button>
                            <button
                              className="btn btn--outline btn--sm"
                              onClick={() => {
                                setSelectedSubForStatus(sub)
                                setNewSubStatus(sub.status)
                              }}
                              title="Cambiar estado de suscripción"
                              style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            >
                              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                              </svg>
                              Estado
                            </button>
                            <button
                              className="btn btn--primary btn--sm"
                              onClick={() => {
                                // openManualModal() es lo que genera la clave de idempotencia
                                // (y resetea el resto del formulario) — llamarlo primero y recién
                                // después preseleccionar el profesional evita mandar el pago con
                                // idempotencyKey vacío (rechazado por el backend: "La clave de
                                // idempotencia es obligatoria").
                                openManualModal()
                                const profUser = users.find((u) => u.id === sub.professionalId)
                                if (profUser) handleSelectProf(profUser)
                              }}
                            >
                              Cobro / Renovar
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── SubTab 2: Plans and Base Prices Management ── */}
      {subTab === 'plans' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Catálogo de Planes y Precios Base</h3>
              <p style={{ margin: '2px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                Modificá los precios base en ARS, precios de referencia en USD y disponibilidad para nuevos cupos y profesionales.
              </p>
            </div>
          </div>

          <div className="sub-plans-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 'var(--space-4)' }}>
            {plans.map((p) => (
              <div
                key={p.id}
                className="card"
                style={{
                  padding: 'var(--space-5)',
                  border: p.isActive === false ? '1.5px dashed var(--color-border)' : '1.5px solid var(--color-border)',
                  borderRadius: 'var(--radius-lg)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 'var(--space-4)',
                  backgroundColor: 'white',
                  opacity: p.isActive === false ? 0.75 : 1,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <span style={{ fontSize: '11px', fontWeight: 'bold', color: 'var(--color-primary)', textTransform: 'uppercase' }}>
                      CÓDIGO: {p.code}
                    </span>
                    <h4 style={{ margin: '2px 0 0', fontSize: '18px', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                      {p.name}
                    </h4>
                  </div>
                  <span className={`badge ${p.isActive !== false ? 'badge--success' : 'badge--warning'}`} title={p.isActive !== false ? undefined : 'Los profesionales no lo ven ni pueden elegirlo, pero podés reactivarlo cuando quieras desde "Modificar Precio Base / Plan".'}>
                    {p.isActive !== false ? 'Visible para profesionales' : 'Oculto para profesionales'}
                  </span>
                </div>

                <div style={{ padding: 'var(--space-3)', backgroundColor: 'var(--neutral-50)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
                    <strong style={{ fontSize: '24px', color: 'var(--color-primary)' }}>{formatCurrency(p.priceArs)}</strong>
                    <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>/ mes ARS</span>
                  </div>
                  {p.priceUsdRef && (
                    <span style={{ display: 'block', fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                      Ref. Internacional: <strong>${p.priceUsdRef} USD</strong>
                    </span>
                  )}
                  {p.priceArsAnual && (
                    <span style={{ display: 'block', fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                      Ciclo anual: <strong>{formatCurrency(p.priceArsAnual)}</strong>
                    </span>
                  )}
                </div>

                <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', margin: 0, minHeight: '36px', lineHeight: 1.4 }}>
                  {p.description}
                </p>

                <div>
                  <strong style={{ fontSize: '12px', display: 'block', marginBottom: '6px', color: 'var(--color-text-primary)' }}>
                    Herramientas Incluidas:
                  </strong>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                    {p.features?.map((f) => (
                      <span key={f} className="badge badge--neutral" style={{ fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="var(--color-primary)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                          <polyline points="20 6 9 17 4 12"/>
                        </svg>
                        {f.replace(/_/g, ' ')}
                      </span>
                    ))}
                  </div>
                </div>

                <div style={{ marginTop: 'auto', paddingTop: 'var(--space-3)', borderTop: '1px solid var(--color-border)' }}>
                  <button
                    className="btn btn--primary btn--sm"
                    style={{ width: '100%', fontWeight: 'bold', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                    onClick={() => openEditPlanModal(p)}
                  >
                    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                    Modificar Precio Base / Plan
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── SubTab 3: Invoices Table ── */}
      {subTab === 'invoices' && (
        <div className="card" style={{ padding: 'var(--space-5)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
              <div className="checkout-spinner" style={{ margin: '0 auto var(--space-3)' }} />
              <p>Cargando comprobantes...</p>
            </div>
          ) : invoices.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-secondary)' }}>
              No hay comprobantes electrónicos emitidos todavía.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid var(--color-border)', color: 'var(--color-text-secondary)' }}>
                    <th style={{ padding: '10px' }}>Comprobante</th>
                    <th style={{ padding: '10px' }}>Fecha Emisión</th>
                    <th style={{ padding: '10px' }}>Receptor (Profesional)</th>
                    <th style={{ padding: '10px' }}>Importe Total</th>
                    <th style={{ padding: '10px' }}>CAE / Vencimiento</th>
                    <th style={{ padding: '10px' }}>Estado</th>
                    <th style={{ padding: '10px', textAlign: 'right' }}>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.map((inv) => {
                    const statusBadge =
                      inv.status === 'ISSUED'
                        ? 'badge--success'
                        : inv.status === 'VOIDED'
                        ? 'badge--neutral'
                        : 'badge--error'

                    const cbteTipoName = inv.cbteTipo === 13 ? 'Nota de Crédito C' : 'Factura C'

                    return (
                      <tr key={inv.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                        <td style={{ padding: '10px' }}>
                          <strong>{cbteTipoName}</strong>
                          <span style={{ display: 'block', fontSize: '12px', fontFamily: 'monospace', color: 'var(--color-text-secondary)' }}>
                            #{String(inv.puntoVenta).padStart(5, '0')}-{String(inv.cbteNumero).padStart(8, '0')}
                          </span>
                        </td>
                        <td style={{ padding: '10px', fontSize: '12px' }}>
                          {new Date(inv.fechaEmision).toLocaleDateString('es-AR')}
                        </td>
                        <td style={{ padding: '10px' }}>
                          <strong style={{ display: 'block' }}>{inv.receptorNombre}</strong>
                          <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                            CUIT/Doc: {inv.receptorDocNro}
                          </span>
                        </td>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>{formatCurrency(inv.importeTotal)}</td>
                        <td style={{ padding: '10px', fontSize: '12px' }}>
                          {inv.cae ? (
                            <>
                              <span style={{ fontFamily: 'monospace', fontWeight: 'bold' }}>{inv.cae}</span>
                              <span style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                                Vto: {inv.caeVencimiento ? new Date(inv.caeVencimiento).toLocaleDateString('es-AR') : '—'}
                              </span>
                            </>
                          ) : (
                            <span style={{ color: 'var(--color-text-secondary)' }}>Sin CAE</span>
                          )}
                        </td>
                        <td style={{ padding: '10px' }}>
                          <span className={`badge ${statusBadge}`}>
                            {inv.status === 'ISSUED' ? 'Emitida' : inv.status === 'VOIDED' ? 'Anulada' : inv.status}
                          </span>
                        </td>
                        <td style={{ padding: '10px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                            {inv.pdfUrl && (
                              <a
                                href={api.getInvoicePdfUrl(inv.id)}
                                target="_blank"
                                rel="noreferrer"
                                className="btn btn--secondary btn--sm"
                                title="Descargar comprobante PDF oficial con QR ARCA"
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                              >
                                <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
                                </svg>
                                PDF
                              </a>
                            )}
                            {inv.status === 'ISSUED' && inv.cbteTipo === 11 && (
                              <button
                                className="btn btn--danger btn--sm"
                                onClick={() => setSelectedInvoiceForNc(inv)}
                                title="Emitir Nota de Crédito C para anular esta Factura C"
                              >
                                Anular (NC)
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── SubTab 4: Global Event Audit Trail ── */}
      {subTab === 'events' && (
        <div className="card" style={{ padding: 'var(--space-5)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Historial y Auditoría</h3>
              <p style={{ margin: '2px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', maxWidth: '520px' }}>
                Cada vez que una suscripción cambia de estado —por un pago, una acción de un administrador o una
                suspensión automática— queda un registro acá que no se puede borrar ni editar. Es el "quién hizo qué y cuándo".
              </p>
            </div>
          </div>

          {allEvents.length === 0 ? (
            <p style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-secondary)' }}>
              No hay eventos de auditoría registrados todavía.
            </p>
          ) : (
            <>
              <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap', marginBottom: 'var(--space-4)' }}>
                <select
                  value={eventTypeFilter}
                  onChange={(e) => setEventTypeFilter(e.target.value)}
                  className="form-select"
                  style={{ minWidth: '220px' }}
                >
                  <option value="TODOS">Todos los tipos de evento</option>
                  {eventTypesPresent.map((t) => (
                    <option key={t} value={t}>{eventMeta(t).label}</option>
                  ))}
                </select>
                <div style={{ position: 'relative', flex: 1, minWidth: '220px' }}>
                  <span style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--color-text-secondary)' }}>
                    <IconSearch />
                  </span>
                  <input
                    type="text"
                    placeholder="Buscar por profesional, email o suscripción..."
                    value={eventSearch}
                    onChange={(e) => setEventSearch(e.target.value)}
                    className="form-input"
                    style={{ width: '100%', paddingLeft: '34px' }}
                  />
                </div>
              </div>

              {filteredEvents.length === 0 ? (
                <p style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-secondary)' }}>
                  Ningún evento coincide con el filtro aplicado.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', maxHeight: '640px', overflowY: 'auto', paddingRight: '4px' }}>
                  {filteredEvents.map((ev) => {
                    const sub = subscriptionsById.get(ev.subscriptionId)
                    const professionalLabel = sub
                      ? `${sub.professionalName} (${sub.professionalEmail})`
                      : `Suscripción #${ev.subscriptionId}`
                    return <SubscriptionEventCard key={ev.id} ev={ev} professionalLabel={professionalLabel} />
                  })}
                </div>
              )}
            </>
          )}
        </div>
      )}

      {/* ── Modal: Editar Plan / Precios Base ── */}
      {editingPlan && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setEditingPlan(null)}
        >
          <div className="card mobile-modal-card" style={{ maxWidth: '520px', width: '100%', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Modificar Precio Base / Plan</h3>
                <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>Código: {editingPlan.code}</span>
              </div>
              <button className="btn btn--ghost btn--sm mobile-modal-close" onClick={() => setEditingPlan(null)}>✕</button>
            </div>

            <form onSubmit={handleSavePlan} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Nombre del Plan *
                </label>
                <input
                  type="text"
                  value={editPlanName}
                  onChange={(e) => setEditPlanName(e.target.value)}
                  className="form-input"
                  style={{ width: '100%' }}
                  required
                />
              </div>

              <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                    Precio Base Mensual (ARS) *
                  </label>
                  <input
                    type="number"
                    value={editPlanPriceArs}
                    onChange={(e) => setEditPlanPriceArs(Number(e.target.value))}
                    className="form-input"
                    style={{ width: '100%', fontWeight: 'bold' }}
                    min="0"
                    step="100"
                    required
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                    Precio Ref. Internacional (USD)
                  </label>
                  <input
                    type="number"
                    value={editPlanPriceUsdRef}
                    onChange={(e) => setEditPlanPriceUsdRef(Number(e.target.value))}
                    className="form-input"
                    style={{ width: '100%' }}
                    min="0"
                    step="1"
                  />
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Precio Total del Ciclo Anual (ARS) *
                </label>
                <input
                  type="number"
                  value={editPlanPriceArsAnual}
                  onChange={(e) => setEditPlanPriceArsAnual(Number(e.target.value))}
                  className="form-input"
                  style={{ width: '100%', fontWeight: 'bold' }}
                  min="0"
                  step="100"
                  required
                />
                <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                  Es el monto único que se cobra al elegir el ciclo anual (Mercado Pago factura esto una sola vez, cada 12 meses).
                  Sugerido "2 meses sin cargo": {formatCurrency(editPlanPriceArs * 10)}.
                </span>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Descripción Comercial
                </label>
                <textarea
                  value={editPlanDesc}
                  onChange={(e) => setEditPlanDesc(e.target.value)}
                  className="form-input"
                  style={{ width: '100%', minHeight: '70px', resize: 'vertical' }}
                />
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 12px', backgroundColor: '#F3F4F6', borderRadius: 'var(--radius-sm)' }}>
                <input
                  type="checkbox"
                  id="activePlanToggle"
                  checked={editPlanIsActive}
                  onChange={(e) => setEditPlanIsActive(e.target.checked)}
                  style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                />
                <label htmlFor="activePlanToggle" style={{ fontSize: '13px', cursor: 'pointer', fontWeight: '600' }}>
                  Plan activo en el catálogo de suscripciones públicas
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <button type="button" onClick={() => setEditingPlan(null)} className="btn btn--secondary">
                  Cancelar
                </button>
                <button type="submit" disabled={isSubmittingPlan} className="btn btn--primary" style={{ fontWeight: 'bold' }}>
                  {isSubmittingPlan ? 'Guardando...' : 'Guardar Nuevo Precio Base'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Historial de Auditoría de Suscripción Específica ── */}
      {selectedSubForAudit && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setSelectedSubForAudit(null)}
        >
          <div className="card mobile-modal-card" style={{ maxWidth: '600px', width: '100%', maxHeight: '80vh', overflowY: 'auto', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>
                  Auditoría de Suscripción #{selectedSubForAudit.id}
                </h3>
                <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                  {selectedSubForAudit.professionalName} ({selectedSubForAudit.professionalEmail})
                </span>
              </div>
              <button className="btn btn--ghost btn--sm mobile-modal-close" onClick={() => setSelectedSubForAudit(null)}>✕</button>
            </div>

            {loadingSubEvents ? (
              <p style={{ textAlign: 'center', padding: 'var(--space-6)' }}>Cargando eventos...</p>
            ) : subEvents.length === 0 ? (
              <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-6)' }}>
                No hay eventos registrados para esta suscripción.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {subEvents.map((ev) => (
                  <SubscriptionEventCard key={ev.id} ev={ev} />
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Modal: Cambiar Estado de Suscripción ── */}
      {selectedSubForStatus && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setSelectedSubForStatus(null)}
        >
          <div className="card mobile-modal-card" style={{ maxWidth: '480px', width: '100%', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>
              Modificar Estado de Suscripción
            </h3>
            <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)' }}>
              Profesional: <strong>{selectedSubForStatus.professionalName}</strong> · Estado actual: <strong>{statusLabel(selectedSubForStatus.status)}</strong>
            </p>

            <form onSubmit={handleChangeStatus} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Nuevo Estado *
                </label>
                <select
                  value={newSubStatus}
                  onChange={(e) => setNewSubStatus(e.target.value)}
                  className="form-select"
                  style={{ width: '100%' }}
                >
                  <option value="ACTIVE">ACTIVE (Activa / Habilitada)</option>
                  <option value="PAST_DUE">PAST_DUE (Vencida / En Gracia)</option>
                  <option value="SUSPENDED">SUSPENDED (Suspendida por falta de pago)</option>
                  <option value="CANCELLED">CANCELLED (Cancelada)</option>
                  <option value="PENDING_VERIFICATION">PENDING_VERIFICATION (Pendiente de Verificación)</option>
                  <option value="SUBSCRIPTION_PENDING">SUBSCRIPTION_PENDING (Pendiente de Pago Inicial)</option>
                </select>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <button type="button" onClick={() => setSelectedSubForStatus(null)} className="btn btn--secondary">
                  Cancelar
                </button>
                <button type="submit" disabled={isSubmittingStatus} className="btn btn--primary" style={{ fontWeight: 'bold' }}>
                  {isSubmittingStatus ? 'Guardando...' : 'Confirmar Cambio de Estado'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Registrar Cobro / Alta Manual (§8) ── */}
      {showManualModal && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setShowManualModal(false)}
        >
          <div
            className="card mobile-modal-card"
            style={{
              maxWidth: '640px',
              width: '100%',
              maxHeight: '90vh',
              overflowY: 'auto',
              padding: 'var(--space-6)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-4)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Alta / Cobro de Suscripción Manual</h3>
                <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                  Registrá cobros en efectivo o transferencia con emisión correlativa de Factura C.
                </span>
              </div>
              <button className="btn btn--ghost btn--sm mobile-modal-close" onClick={() => setShowManualModal(false)}>✕</button>
            </div>

            <form onSubmit={handleManualSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              {/* 1. Selector de Profesional */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  1. Profesional de la Salud *
                </label>
                <input
                  type="text"
                  placeholder="Buscar por nombre, email o CUIT..."
                  value={manualProfSearch}
                  onChange={(e) => {
                    setManualProfSearch(e.target.value)
                    setSelectedProf(null)
                  }}
                  className="form-input"
                  style={{ width: '100%' }}
                />

                {!selectedProf && eligibleProfessionals.length > 0 && (
                  <div
                    style={{
                      maxHeight: '160px',
                      overflowY: 'auto',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-sm)',
                      marginTop: '4px',
                      backgroundColor: '#ffffff'
                    }}
                  >
                    {eligibleProfessionals.slice(0, 10).map((u) => (
                      <div
                        key={u.id}
                        onClick={() => handleSelectProf(u)}
                        style={{
                          padding: '8px 12px',
                          borderBottom: '1px solid #f0f0f0',
                          cursor: 'pointer',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          fontSize: '12px'
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f9fafb')}
                        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#ffffff')}
                      >
                        <div>
                          <strong>{u.nombre} {u.apellido}</strong> ({u.profession || u.rol})
                          <span style={{ display: 'block', color: 'var(--color-text-secondary)', fontSize: '11px' }}>{u.email}</span>
                        </div>
                        <span style={{ fontSize: '11px', color: 'var(--color-primary)', fontWeight: 'bold' }}>
                          CUIT: {u.cuit || u.taxId || 'Sin CUIT'}
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                {selectedProf && (
                  <div style={{ marginTop: '6px', padding: '8px 12px', backgroundColor: 'var(--green-50)', border: '1px solid var(--green-200)', borderRadius: 'var(--radius-sm)', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="var(--color-success)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                    <span>Profesional seleccionado: <strong>{selectedProf.nombre} {selectedProf.apellido}</strong> · CUIT: {selectedProf.cuit || selectedProf.taxId || 'Sin CUIT'}</span>
                  </div>
                )}
              </div>

              {/* 2. Selector de Plan */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  2. Plan Contratado *
                </label>
                <select
                  value={selectedPlanId}
                  onChange={(e) => handlePlanChange(Number(e.target.value))}
                  className="form-select"
                  style={{ width: '100%' }}
                >
                  {visiblePlans.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({formatCurrency(p.priceArs)}/mes) — {p.requiresPrescriber ? 'Psiquiatras / Médicos' : 'Psicólogos'}
                    </option>
                  ))}
                </select>
              </div>

              {/* 3. Monto y Método */}
              <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                    Monto Cobrado (ARS) *
                  </label>
                  <input
                    type="number"
                    value={manualAmount}
                    onChange={(e) => setManualAmount(Number(e.target.value))}
                    className="form-input"
                    style={{ width: '100%', fontWeight: 'bold' }}
                    min="0"
                    step="100"
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                    Método de Cobro *
                  </label>
                  <select
                    value={manualMethod}
                    onChange={(e) => setManualMethod(e.target.value as any)}
                    className="form-select"
                    style={{ width: '100%' }}
                  >
                    <option value="MANUAL_TRANSFER">Transferencia Bancaria</option>
                    <option value="MANUAL_CASH">Efectivo en Mano</option>
                    <option value="COURTESY">Cortesía (Sin cobro ni factura)</option>
                  </select>
                </div>
              </div>

              {/* 4. Fechas del Período */}
              <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                    Fecha Inicio *
                  </label>
                  <input
                    type="date"
                    value={manualStartDate}
                    onChange={(e) => setManualStartDate(e.target.value)}
                    className="form-input"
                    style={{ width: '100%' }}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                    Fecha Vencimiento *
                  </label>
                  <input
                    type="date"
                    value={manualEndDate}
                    onChange={(e) => setManualEndDate(e.target.value)}
                    className="form-input"
                    style={{ width: '100%' }}
                  />
                </div>
              </div>

              {/* 5. Referencia y Notas */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Comprobante / Referencia de Pago
                </label>
                <input
                  type="text"
                  placeholder="Ej: Transf #9182374 o Recibo #0012"
                  value={manualReference}
                  onChange={(e) => setManualReference(e.target.value)}
                  className="form-input"
                  style={{ width: '100%' }}
                />
              </div>

              {/* 6. Checkbox Emitir Factura ARCA */}
              {manualMethod !== 'COURTESY' && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 12px', backgroundColor: '#F3F4F6', borderRadius: 'var(--radius-sm)' }}>
                  <input
                    type="checkbox"
                    id="emitFacturaCheck"
                    checked={manualEmitInvoice}
                    onChange={(e) => setManualEmitInvoice(e.target.checked)}
                    style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                  />
                  <label htmlFor="emitFacturaCheck" style={{ fontSize: '13px', cursor: 'pointer', fontWeight: '600' }}>
                    Emitir Factura C Electrónica en ARCA automáticamente (con CAE y QR oficial)
                  </label>
                </div>
              )}

              {/* Idempotency key badge */}
              <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                Clave de idempotencia única: <code style={{ backgroundColor: '#eee', padding: '2px 4px' }}>{manualIdempotencyKey}</code>
              </div>

              {/* Submit Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <button type="button" onClick={() => setShowManualModal(false)} className="btn btn--secondary">
                  Cancelar
                </button>
                <button type="submit" disabled={isSubmitting || !selectedProf} className="btn btn--primary" style={{ fontWeight: 'bold' }}>
                  {isSubmitting ? 'Registrando y Facturando...' : 'Confirmar Alta y Facturar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Emitir Nota de Crédito C (§10.5) ── */}
      {selectedInvoiceForNc && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setSelectedInvoiceForNc(null)}
        >
          <div className="card mobile-modal-card" style={{ maxWidth: '520px', width: '100%', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-danger)' }}>
              Emitir Nota de Crédito C
            </h3>
            <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)' }}>
              Se anulará la Factura C <strong>#{String(selectedInvoiceForNc.puntoVenta).padStart(5, '0')}-{String(selectedInvoiceForNc.cbteNumero).padStart(8, '0')}</strong> emitida a nombre de <strong>{selectedInvoiceForNc.receptorNombre}</strong> por <strong>{formatCurrency(selectedInvoiceForNc.importeTotal)}</strong>.
            </p>

            <form onSubmit={handleEmitNotaCredito} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Motivo de la Nota de Crédito *
                </label>
                <input
                  type="text"
                  value={ncReason}
                  onChange={(e) => setNcReason(e.target.value)}
                  className="form-input"
                  style={{ width: '100%' }}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <button type="button" onClick={() => setSelectedInvoiceForNc(null)} className="btn btn--secondary">
                  Cancelar
                </button>
                <button type="submit" disabled={isSubmittingNc} className="btn btn--danger" style={{ fontWeight: 'bold' }}>
                  {isSubmittingNc ? 'Emitiendo NC...' : 'Confirmar Emisión en ARCA'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
