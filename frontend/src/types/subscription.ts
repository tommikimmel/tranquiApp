export interface PlanDto {
  id: number
  code: 'consultorio' | 'clinico' | 'equipo' | string
  name: string
  description: string
  priceArs: number
  priceUsdRef: number
  priceArsAnual?: number | null
  billingPeriod: string
  minSeats: number
  requiresPrescriber: boolean
  features: string[]
  isActive?: boolean
}

export type SubscriptionStatus =
  | 'REGISTERED'
  | 'PENDING_VERIFICATION'
  | 'VERIFIED'
  | 'SUBSCRIPTION_PENDING'
  | 'ACTIVE'
  | 'PAST_DUE'
  | 'SUSPENDED'
  | 'CANCELLED'
  | 'REJECTED'

export type BillingSource = 'MERCADOPAGO' | 'MANUAL_CASH' | 'MANUAL_TRANSFER' | 'COURTESY'

export interface SubscriptionDto {
  id: number
  professionalId: number
  professionalName: string
  professionalEmail: string
  plan?: PlanDto
  status: SubscriptionStatus
  seats: number
  billingSource: BillingSource
  billingCycle?: string
  amountArs: number
  currentPeriodStart?: string
  currentPeriodEnd?: string
  nextBillingDate?: string
  graceUntil?: string
  cancelAtPeriodEnd?: boolean
  activeFeatures: string[]
}

export interface InvoiceDto {
  id: number
  paymentId?: number
  cbteTipo: number // 11 = Factura C, 13 = Nota de Crédito C
  cbteTipoNombre: string
  puntoVenta: number
  cbteNumero: number
  cae?: string
  caeVencimiento?: string
  receptorNombre: string
  receptorDocNro: number
  receptorCondicionIva: number
  importeTotal: number
  fechaEmision: string
  pdfUrl?: string
  status: 'PENDING' | 'ISSUED' | 'FAILED' | 'VOIDED'
  lastError?: string
  comprobanteAsociadoId?: number
  issuedAt?: string
}

export interface AdminOverviewStatsDto {
  suscriptoresActivos?: number
  suscriptoresEnGracia?: number
  mrr?: number
  totalFacturado12Meses?: number
  topeCategoriaA?: number
  topeCatB?: number
  topeCatK?: number
  porcentajeUsoCategoriaA?: number
  alertaMonotributo80?: boolean
}

// Un evento inmutable de auditoría de una suscripción (altas, cobros, cambios de estado
// manuales o automáticos). previousStatus/newStatus solo vienen completos cuando el evento
// representa una transición de estado real.
export interface SubscriptionEventDto {
  id: number
  subscriptionId: number
  eventType: string
  previousStatus?: SubscriptionStatus | string | null
  newStatus?: SubscriptionStatus | string | null
  actorType: string
  actorId?: string
  details?: string
  createdAt: string
}

export interface ManualPaymentRequest {
  professionalId: number
  planId: number
  amountArs: number
  method: 'MANUAL_CASH' | 'MANUAL_TRANSFER' | 'COURTESY'
  periodStart?: string
  periodEnd?: string
  receiptReference?: string
  notes?: string
  emitInvoice?: boolean
  idempotencyKey: string
}
