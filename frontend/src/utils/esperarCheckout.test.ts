import { describe, expect, it, vi } from 'vitest'
import { esperarCheckout, type EstadoCheckout } from './esperarCheckout'

const rapido = { intentos: 5, esperaMs: 1 }

describe('esperarCheckout', () => {
  it('espera mientras está pendiente y devuelve el link cuando está listo', async () => {
    const obtener = vi.fn<() => Promise<EstadoCheckout>>()
      .mockResolvedValueOnce({ estado: 'PENDIENTE' })
      .mockRejectedValueOnce(new Error('red'))
      .mockResolvedValueOnce({ estado: 'LISTO', checkoutUrl: 'https://mp/checkout/63' })

    await expect(esperarCheckout(obtener, rapido)).resolves.toBe('https://mp/checkout/63')
    expect(obtener).toHaveBeenCalledTimes(3)
  })

  it('muestra el error que informa el servicio', async () => {
    const obtener = vi.fn<() => Promise<EstadoCheckout>>().mockResolvedValue({
      estado: 'ERROR', error: 'El profesional todavía no vinculó su cuenta de Mercado Pago.',
    })

    await expect(esperarCheckout(obtener, rapido)).rejects.toThrow('no vinculó su cuenta')
  })

  it('si no llega a tiempo, corta con un mensaje para reintentar', async () => {
    const obtener = vi.fn<() => Promise<EstadoCheckout>>().mockResolvedValue({ estado: 'PENDIENTE' })

    await expect(esperarCheckout(obtener, rapido)).rejects.toThrow('Probá de nuevo')
    expect(obtener).toHaveBeenCalledTimes(5)
  })
})
