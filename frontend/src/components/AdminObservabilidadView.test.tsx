import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import AdminObservabilidadView from './AdminObservabilidadView'
import { api } from '../api/api'
import type {
  AdminSaludResponse,
  AdminObservabilidadResponse,
  MetricasRecursos,
  MetricasTrafico,
  MetricasLatencia,
  MetricasEventos
} from '../types/observabilidad'

vi.mock('../api/api', () => ({
  api: {
    getAdminSalud: vi.fn(),
    getAdminObservabilidad: vi.fn()
  }
}))

describe('AdminObservabilidadView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const mockSalud: AdminSaludResponse = {
    consulConectado: true,
    servicios: [
      {
        nombre: 'gateway',
        estado: 'UP',
        version: '0.0.1-SNAPSHOT',
        instancias: 1,
        detalles: [{ id: 'gateway:8080', host: 'gateway', puerto: 8080, estado: 'UP' }]
      },
      {
        nombre: 'backend',
        estado: 'UP',
        version: '0.0.1-SNAPSHOT',
        instancias: 1,
        detalles: [{ id: 'backend:8080', host: 'backend', puerto: 8080, estado: 'UP' }]
      }
    ],
    timestamp: '2026-10-14T12:00:00Z'
  }

  const mockRecursos: AdminObservabilidadResponse<MetricasRecursos> = {
    categoria: 'recursos',
    rango: '24h',
    timestamp: 1774390000,
    metricas: {
      cpuVps: [{ timestamp: 1774380000, valor: 14.5 }, { timestamp: 1774383600, valor: 18.2 }],
      ramVps: [{ timestamp: 1774380000, valor: 42.0 }, { timestamp: 1774383600, valor: 45.5 }],
      discoLibreGb: 52.4,
      discoTotalGb: 96.0,
      discoPorcentajeLibre: 54.6,
      contenedoresCpu: [],
      contenedoresRam: []
    }
  }

  const mockTrafico: AdminObservabilidadResponse<MetricasTrafico> = {
    categoria: 'trafico',
    rango: '24h',
    timestamp: 1774390000,
    metricas: {
      reqTotalPorMinuto: [{ timestamp: 1774380000, valor: 120 }, { timestamp: 1774383600, valor: 150 }],
      errores5xx: [{ timestamp: 1774380000, valor: 0 }],
      rechazos429: [{ timestamp: 1774380000, valor: 2 }],
      porServicio: []
    }
  }

  const mockLatencia: AdminObservabilidadResponse<MetricasLatencia> = {
    categoria: 'latencia',
    rango: '24h',
    timestamp: 1774390000,
    metricas: {
      p50: [{ timestamp: 1774380000, valor: 15 }],
      p95: [{ timestamp: 1774380000, valor: 85 }],
      p99: [{ timestamp: 1774380000, valor: 140 }],
      rutasMasLentas: [
        { ruta: '/api/turnos/reservar', latenciaMs: 125.4 },
        { ruta: '/api/payments/checkout', latenciaMs: 98.2 }
      ]
    }
  }

  const mockEventos: AdminObservabilidadResponse<MetricasEventos> = {
    categoria: 'eventos',
    rango: '24h',
    timestamp: 1774390000,
    metricas: {
      outboxPendientes: [{ timestamp: 1774380000, valor: 0 }],
      outboxAntiguedadSegundos: [{ timestamp: 1774380000, valor: 0 }],
      colasRabbitMQ: [
        { cola: 'notificaciones.eventos', mensajes: 0 },
        { cola: 'turnos.eventos', mensajes: 0 }
      ],
      dlqRabbitMQ: [
        { cola: 'notificaciones.dlq', mensajes: 0 }
      ]
    }
  }

  it('muestra estado de carga inicialmente', () => {
    vi.mocked(api.getAdminSalud).mockReturnValue(new Promise(() => {}))
    vi.mocked(api.getAdminObservabilidad).mockReturnValue(new Promise(() => {}))

    render(<AdminObservabilidadView />)
    expect(screen.getByText(/Cargando métricas de observabilidad/i)).toBeInTheDocument()
  })

  it('renderiza con datos completos de servicios, recursos, tráfico y latencia', async () => {
    vi.mocked(api.getAdminSalud).mockResolvedValue(mockSalud)
    vi.mocked(api.getAdminObservabilidad).mockImplementation((cat) => {
      if (cat === 'recursos') return Promise.resolve(mockRecursos as unknown as AdminObservabilidadResponse<MetricasRecursos>)
      if (cat === 'trafico') return Promise.resolve(mockTrafico as unknown as AdminObservabilidadResponse<MetricasTrafico>)
      if (cat === 'latencia') return Promise.resolve(mockLatencia as unknown as AdminObservabilidadResponse<MetricasLatencia>)
      return Promise.resolve(mockEventos as unknown as AdminObservabilidadResponse<MetricasEventos>)
    })

    render(<AdminObservabilidadView />)

    // Esperar a que desaparezca el loading
    await waitFor(() => {
      expect(screen.queryByText(/Cargando métricas de observabilidad/i)).not.toBeInTheDocument()
    })

    // Título y estado
    expect(screen.getByText(/Observabilidad de la Plataforma/i)).toBeInTheDocument()
    expect(screen.getByText(/Consul conectado/i)).toBeInTheDocument()

    // Servicios
    expect(screen.getByText('gateway')).toBeInTheDocument()
    expect(screen.getByText('backend')).toBeInTheDocument()
    expect(screen.getAllByText(/Activo/i).length).toBeGreaterThan(0)

    // Recursos
    expect(screen.getByText(/52.4 GB/i)).toBeInTheDocument()
    expect(screen.getByText(/18.2 %/i)).toBeInTheDocument()

    // Latencia
    expect(screen.getByText(/85 ms/i)).toBeInTheDocument()
    expect(screen.getByText('/api/turnos/reservar')).toBeInTheDocument()
  })

  it('renderiza estado vacío correctamente cuando no hay métricas ni servicios', async () => {
    vi.mocked(api.getAdminSalud).mockResolvedValue({
      consulConectado: false,
      servicios: [],
      timestamp: '2026-10-14T12:00:00Z'
    })
    const mockVacio: AdminObservabilidadResponse<MetricasRecursos> = {
      categoria: 'recursos',
      rango: '24h',
      timestamp: 1774390000,
      metricas: {
        cpuVps: [],
        ramVps: [],
        discoLibreGb: 0,
        discoTotalGb: 0,
        discoPorcentajeLibre: 0,
        contenedoresCpu: [],
        contenedoresRam: []
      }
    }
    vi.mocked(api.getAdminObservabilidad).mockResolvedValue(mockVacio as unknown as AdminObservabilidadResponse<MetricasRecursos>)

    render(<AdminObservabilidadView />)

    await waitFor(() => {
      expect(screen.queryByText(/Cargando métricas de observabilidad/i)).not.toBeInTheDocument()
    })

    expect(screen.getByText(/No se encontraron servicios registrados en Consul/i)).toBeInTheDocument()
    expect(screen.getAllByText(/No hay datos disponibles en este rango/i).length).toBeGreaterThan(0)
  })

  it('muestra mensaje de error y permite reintentar cuando falla la API', async () => {
    vi.mocked(api.getAdminSalud).mockRejectedValue(new Error('Network error'))
    vi.mocked(api.getAdminObservabilidad).mockRejectedValue(new Error('Network error'))

    render(<AdminObservabilidadView />)

    await waitFor(() => {
      expect(screen.getByText(/No pudimos cargar algunas métricas/i)).toBeInTheDocument()
    })

    const retryBtn = screen.getByRole('button', { name: /Reintentar/i })
    expect(retryBtn).toBeInTheDocument()

    // Simular que en el reintento responde ok
    vi.mocked(api.getAdminSalud).mockResolvedValue(mockSalud)
    vi.mocked(api.getAdminObservabilidad).mockResolvedValue(mockRecursos as unknown as AdminObservabilidadResponse<MetricasRecursos>)

    await userEvent.click(retryBtn)

    await waitFor(() => {
      expect(screen.getByText('gateway')).toBeInTheDocument()
    })
  })

  it('permite cambiar el rango de tiempo (1h, 24h, 7d)', async () => {
    vi.mocked(api.getAdminSalud).mockResolvedValue(mockSalud)
    vi.mocked(api.getAdminObservabilidad).mockResolvedValue(mockRecursos as unknown as AdminObservabilidadResponse<MetricasRecursos>)

    render(<AdminObservabilidadView />)

    await waitFor(() => {
      expect(screen.queryByText(/Cargando métricas de observabilidad/i)).not.toBeInTheDocument()
    })

    const btn1h = screen.getByRole('button', { name: '1 hora' })
    await userEvent.click(btn1h)

    expect(api.getAdminObservabilidad).toHaveBeenCalledWith('recursos', '1h')
    expect(api.getAdminObservabilidad).toHaveBeenCalledWith('trafico', '1h')

    const btn7d = screen.getByRole('button', { name: '7 días' })
    await userEvent.click(btn7d)

    expect(api.getAdminObservabilidad).toHaveBeenCalledWith('recursos', '7d')
    expect(api.getAdminObservabilidad).toHaveBeenCalledWith('trafico', '7d')
  })

  it('permite cambiar de pestañas para ver las diferentes secciones', async () => {
    vi.mocked(api.getAdminSalud).mockResolvedValue(mockSalud)
    vi.mocked(api.getAdminObservabilidad).mockImplementation((cat) => {
      if (cat === 'recursos') return Promise.resolve(mockRecursos as unknown as AdminObservabilidadResponse<MetricasRecursos>)
      if (cat === 'trafico') return Promise.resolve(mockTrafico as unknown as AdminObservabilidadResponse<MetricasTrafico>)
      if (cat === 'latencia') return Promise.resolve(mockLatencia as unknown as AdminObservabilidadResponse<MetricasLatencia>)
      return Promise.resolve(mockEventos as unknown as AdminObservabilidadResponse<MetricasEventos>)
    })

    render(<AdminObservabilidadView />)

    await waitFor(() => {
      expect(screen.queryByText(/Cargando métricas de observabilidad/i)).not.toBeInTheDocument()
    })

    // Pestaña Eventos
    const tabEventos = screen.getByRole('button', { name: /Eventos & RabbitMQ/i })
    await userEvent.click(tabEventos)

    expect(screen.getByText(/Mensajería de Eventos/i)).toBeInTheDocument()
    expect(screen.getByText('notificaciones.eventos')).toBeInTheDocument()
    expect(screen.getByText('notificaciones.dlq')).toBeInTheDocument()

    // Pestaña Latencia
    const tabLatencia = screen.getByRole('button', { name: /Latencia & Rutas/i })
    await userEvent.click(tabLatencia)

    expect(screen.getByText(/Latencia de Peticiones y Rutas Más Lentas/i)).toBeInTheDocument()
    expect(screen.getByText(/Percentiles de Latencia/i)).toBeInTheDocument()
  })
  it('muestra las series por contenedor y por servicio, el valor al pasar el mouse y actualiza a pedido', async () => {
    const serie = [{ nombre: 'tranqui-gateway', puntos: [{ timestamp: 1774380000, valor: 12 }, { timestamp: 1774383600, valor: 20 }] }]
    vi.mocked(api.getAdminSalud).mockResolvedValue(mockSalud)
    vi.mocked(api.getAdminObservabilidad).mockImplementation((cat) => {
      if (cat === 'recursos') return Promise.resolve({ ...mockRecursos, metricas: { ...mockRecursos.metricas, contenedoresCpu: serie, contenedoresRam: serie } } as unknown as AdminObservabilidadResponse<MetricasRecursos>)
      if (cat === 'trafico') return Promise.resolve({ ...mockTrafico, metricas: { ...mockTrafico.metricas, porServicio: serie } } as unknown as AdminObservabilidadResponse<MetricasTrafico>)
      if (cat === 'latencia') return Promise.resolve(mockLatencia as unknown as AdminObservabilidadResponse<MetricasLatencia>)
      return Promise.resolve(mockEventos as unknown as AdminObservabilidadResponse<MetricasEventos>)
    })

    const { container } = render(<AdminObservabilidadView />)
    await waitFor(() => {
      expect(screen.queryByText(/Cargando métricas de observabilidad/i)).not.toBeInTheDocument()
    })

    expect(screen.getByText('Tráfico Desglosado por Servicio / Ruta')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Recursos VPS / Docker' }))
    expect(screen.getByText('CPU por Contenedor (cAdvisor)')).toBeInTheDocument()
    expect(screen.getByText('RAM por Contenedor (MB)')).toBeInTheDocument()

    const punto = container.querySelector('circle')!
    await userEvent.hover(punto)
    await userEvent.unhover(punto)

    const llamadas = vi.mocked(api.getAdminSalud).mock.calls.length
    await userEvent.click(screen.getByRole('button', { name: /Actualizar/ }))
    await waitFor(() => expect(vi.mocked(api.getAdminSalud).mock.calls.length).toBe(llamadas + 1))
  })
  it('se actualiza sola cada 30 segundos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(api.getAdminSalud).mockResolvedValue(mockSalud)
      vi.mocked(api.getAdminObservabilidad).mockResolvedValue(mockRecursos as unknown as AdminObservabilidadResponse<MetricasRecursos>)
      render(<AdminObservabilidadView />)
      await waitFor(() => expect(api.getAdminSalud).toHaveBeenCalledTimes(1))

      await vi.advanceTimersByTimeAsync(30000)
      await waitFor(() => expect(api.getAdminSalud).toHaveBeenCalledTimes(2))
    } finally {
      vi.useRealTimers()
    }
  })
})
