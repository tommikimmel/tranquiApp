// Fase 4: con el servicio de pagos la reserva responde sin el link de Mercado Pago (checkoutPendiente) y
// el link se crea un instante después. Se pide a GET /api/payments/checkout/{turnoId} hasta que esté listo.

export type EstadoCheckout = {
  estado: 'PENDIENTE' | 'LISTO' | 'ERROR' | 'PAGADO' | 'VENCIDO'
  checkoutUrl?: string
  error?: string
}

const MENSAJE_DEMORA = 'No pudimos generar el link de pago. Probá de nuevo en unos minutos.'

/**
 * Devuelve el link del checkout, o rechaza con el error para mostrarle al paciente: el que informó el
 * servicio (por ejemplo, el profesional no vinculó Mercado Pago) o uno genérico si no llegó a tiempo.
 */
export async function esperarCheckout(
  obtener: () => Promise<EstadoCheckout>,
  { intentos = 20, esperaMs = 1000 }: { intentos?: number; esperaMs?: number } = {}
): Promise<string> {
  for (let i = 0; i < intentos; i++) {
    let respuesta: EstadoCheckout | null = null
    try {
      respuesta = await obtener()
    } catch {
      // Un error de red puntual no corta la espera: se reintenta como si siguiera pendiente.
    }
    if (respuesta?.estado === 'LISTO' && respuesta.checkoutUrl) {
      return respuesta.checkoutUrl
    }
    if (respuesta?.estado === 'ERROR') {
      throw new Error(respuesta.error || MENSAJE_DEMORA)
    }
    if (respuesta?.estado === 'VENCIDO') {
      throw new Error('La reserva venció antes de que pudiéramos generar el link de pago. Elegí el horario de nuevo.')
    }
    await new Promise((resolver) => setTimeout(resolver, esperaMs))
  }
  throw new Error(MENSAJE_DEMORA)
}
