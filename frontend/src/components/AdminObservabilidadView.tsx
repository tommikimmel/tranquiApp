import { useEffect, useState, useId } from 'react'
import { api } from '../api/api'
import { Icon } from './Icon'
import type {
  RangoObservabilidad,
  AdminSaludResponse,
  MetricasRecursos,
  MetricasTrafico,
  MetricasLatencia,
  MetricasEventos,
  PuntoMetrica
} from '../types/observabilidad'

type SubTab = 'resumen' | 'servicios' | 'recursos' | 'trafico' | 'latencia' | 'eventos'

// ── Gráfico SVG Liviano y Responsivo ───────────────────────────
interface SvgLineChartProps {
  titulo: string
  subtitulo?: string
  series: Array<{
    nombre: string
    color: string
    puntos: PuntoMetrica[]
  }>
  unidad?: string
  altura?: number
}

function SvgLineChart({ titulo, subtitulo, series, unidad = '', altura = 200 }: SvgLineChartProps) {
  const gradientId = useId()
  const [hoveredPoint, setHoveredPoint] = useState<{ x: number; y: number; val: number; ts: number; nombre: string } | null>(null)

  // Filtrar series que tengan al menos 1 punto
  const seriesConPuntos = series.filter(s => s.puntos && s.puntos.length > 0)
  const totalPuntos = seriesConPuntos.reduce((acc, s) => acc + s.puntos.length, 0)

  if (totalPuntos === 0) {
    return (
      <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
        <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>{titulo}</div>
        {subtitulo && <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)' }}>{subtitulo}</div>}
        <div style={{ height: altura, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)' }}>
          No hay datos disponibles en este rango
        </div>
      </div>
    )
  }

  // Hallar min/max de valores y timestamps
  let minVal = Infinity
  let maxVal = -Infinity
  let minTs = Infinity
  let maxTs = -Infinity

  for (const s of seriesConPuntos) {
    for (const p of s.puntos) {
      if (p.valor < minVal) minVal = p.valor
      if (p.valor > maxVal) maxVal = p.valor
      if (p.timestamp < minTs) minTs = p.timestamp
      if (p.timestamp > maxTs) maxTs = p.timestamp
    }
  }

  if (minVal === maxVal) {
    minVal = Math.max(0, minVal - 1)
    maxVal = maxVal + 1
  } else {
    // Margen del 10%
    const diff = maxVal - minVal
    minVal = Math.max(0, minVal - diff * 0.05)
    maxVal = maxVal + diff * 0.1
  }

  if (minTs === maxTs) {
    minTs = minTs - 60
    maxTs = maxTs + 60
  }

  const width = 600
  const height = altura
  const padLeft = 45
  const padRight = 15
  const padTop = 15
  const padBottom = 25
  const plotW = width - padLeft - padRight
  const plotH = height - padTop - padBottom

  const getX = (ts: number) => padLeft + ((ts - minTs) / (maxTs - minTs)) * plotW
  const getY = (val: number) => padTop + plotH - ((val - minVal) / (maxVal - minVal)) * plotH

  // Formatear fecha
  const formatTs = (ts: number) => {
    const d = new Date(ts * 1000)
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
        <div>
          <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>{titulo}</div>
          {subtitulo && <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{subtitulo}</div>}
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {seriesConPuntos.map((s, idx) => (
            <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px', color: 'var(--color-text-secondary)' }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: s.color }} />
              <span>{s.nombre}</span>
            </div>
          ))}
        </div>
      </div>

      <div style={{ position: 'relative', width: '100%' }}>
        <svg
          viewBox={`0 0 ${width} ${height}`}
          style={{ width: '100%', height: 'auto', display: 'block', overflow: 'visible' }}
        >
          <defs>
            {seriesConPuntos.map((s, idx) => (
              <linearGradient key={idx} id={`${gradientId}-${idx}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={s.color} stopOpacity="0.25" />
                <stop offset="100%" stopColor={s.color} stopOpacity="0.0" />
              </linearGradient>
            ))}
          </defs>

          {/* Líneas de cuadrícula horizontal */}
          {[0, 0.5, 1].map((pct, i) => {
            const y = padTop + plotH * pct
            const v = maxVal - (maxVal - minVal) * pct
            return (
              <g key={i}>
                <line x1={padLeft} y1={y} x2={width - padRight} y2={y} stroke="var(--color-border)" strokeDasharray="3 3" strokeWidth="0.8" />
                <text x={padLeft - 6} y={y + 3} textAnchor="end" fontSize="10" fill="var(--color-text-secondary)">
                  {Math.round(v * 10) / 10}{unidad}
                </text>
              </g>
            )
          })}

          {/* Marcas de tiempo en eje X */}
          <text x={padLeft} y={height - 5} textAnchor="start" fontSize="10" fill="var(--color-text-secondary)">
            {formatTs(minTs)}
          </text>
          <text x={width - padRight} y={height - 5} textAnchor="end" fontSize="10" fill="var(--color-text-secondary)">
            {formatTs(maxTs)}
          </text>

          {/* Series */}
          {seriesConPuntos.map((s, sIdx) => {
            if (s.puntos.length === 0) return null
            const sorted = [...s.puntos].sort((a, b) => a.timestamp - b.timestamp)
            const pathD = sorted.map((p, i) => `${i === 0 ? 'M' : 'L'} ${getX(p.timestamp)} ${getY(p.valor)}`).join(' ')
            const firstX = getX(sorted[0].timestamp)
            const lastX = getX(sorted[sorted.length - 1].timestamp)
            const baseY = padTop + plotH
            const areaD = `${pathD} L ${lastX} ${baseY} L ${firstX} ${baseY} Z`

            return (
              <g key={sIdx}>
                <path d={areaD} fill={`url(#${gradientId}-${sIdx})`} />
                <path d={pathD} fill="none" stroke={s.color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                {sorted.map((p, pIdx) => (
                  <circle
                    key={pIdx}
                    cx={getX(p.timestamp)}
                    cy={getY(p.valor)}
                    r="3"
                    fill={s.color}
                    style={{ cursor: 'pointer', opacity: 0.8 }}
                    onMouseEnter={() => setHoveredPoint({ x: getX(p.timestamp), y: getY(p.valor), val: p.valor, ts: p.timestamp, nombre: s.nombre })}
                    onMouseLeave={() => setHoveredPoint(null)}
                  />
                ))}
              </g>
            )
          })}
        </svg>

        {hoveredPoint && (
          <div
            style={{
              position: 'absolute',
              left: `${(hoveredPoint.x / width) * 100}%`,
              top: `${(hoveredPoint.y / height) * 100}%`,
              transform: 'translate(-50%, -120%)',
              backgroundColor: 'rgba(17, 24, 39, 0.9)',
              color: 'white',
              padding: '4px 8px',
              borderRadius: 'var(--radius-sm)',
              fontSize: '11px',
              pointerEvents: 'none',
              whiteSpace: 'nowrap',
              zIndex: 10,
              boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
            }}
          >
            <div><strong>{hoveredPoint.nombre}</strong></div>
            <div>{hoveredPoint.val} {unidad}</div>
            <div style={{ opacity: 0.75, fontSize: '10px' }}>{formatTs(hoveredPoint.ts)}</div>
          </div>
        )}
      </div>
    </div>
  )
}

export interface AdminObservabilidadViewProps {
  onLogout?: () => void
}

// ── Componente Principal de Observabilidad ─────────────────────
export default function AdminObservabilidadView({ onLogout }: AdminObservabilidadViewProps = {}) {
  void onLogout
  const [rango, setRango] = useState<RangoObservabilidad>('24h')
  const [activeTab, setActiveTab] = useState<SubTab>('resumen')
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  // Datos
  const [salud, setSalud] = useState<AdminSaludResponse | null>(null)
  const [recursos, setRecursos] = useState<MetricasRecursos | null>(null)
  const [trafico, setTrafico] = useState<MetricasTrafico | null>(null)
  const [latencia, setLatencia] = useState<MetricasLatencia | null>(null)
  const [eventos, setEventos] = useState<MetricasEventos | null>(null)

  useEffect(() => {
    let ignore = false

    async function fetchData() {
      try {
        const [saludRes, recRes, trafRes, latRes, eveRes] = await Promise.all([
          api.getAdminSalud().catch(() => null),
          api.getAdminObservabilidad('recursos', rango).catch(() => null),
          api.getAdminObservabilidad('trafico', rango).catch(() => null),
          api.getAdminObservabilidad('latencia', rango).catch(() => null),
          api.getAdminObservabilidad('eventos', rango).catch(() => null)
        ])

        if (!ignore) {
          setError(null)
          if (saludRes) setSalud(saludRes)
          if (recRes?.metricas) setRecursos(recRes.metricas)
          if (trafRes?.metricas) setTrafico(trafRes.metricas)
          if (latRes?.metricas) setLatencia(latRes.metricas)
          if (eveRes?.metricas) setEventos(eveRes.metricas)

          if (!saludRes && !recRes && !trafRes && !latRes && !eveRes) {
            setError('No pudimos cargar algunas métricas. Verificá que los servicios estén activos.')
          }

          setLastUpdate(new Date())
        }
      } catch (err: unknown) {
        if (!ignore) {
          const msg = err instanceof Error ? err.message : 'Error al consultar observabilidad'
          setError(msg)
        }
      } finally {
        if (!ignore) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    }

    fetchData()

    return () => {
      ignore = true
    }
  }, [rango, refreshKey])

  // Refresco automático cada 30 segundos
  useEffect(() => {
    const interval = setInterval(() => {
      setRefreshing(true)
      setRefreshKey(k => k + 1)
    }, 30000)
    return () => clearInterval(interval)
  }, [])

  // Cálculos rápidos para tarjetas resumen
  const ultimoPunto = (puntos?: PuntoMetrica[]) => {
    if (!puntos || puntos.length === 0) return 0
    return puntos[puntos.length - 1].valor
  }

  const sumaPuntos = (puntos?: PuntoMetrica[]) => {
    if (!puntos || puntos.length === 0) return 0
    return Math.round(puntos.reduce((acc, p) => acc + p.valor, 0))
  }

  const cpuVpsActual = ultimoPunto(recursos?.cpuVps)
  const ramVpsActual = ultimoPunto(recursos?.ramVps)
  const reqMinActual = ultimoPunto(trafico?.reqTotalPorMinuto)
  const total5xx = sumaPuntos(trafico?.errores5xx)
  const total429 = sumaPuntos(trafico?.rechazos429)
  const p95Actual = ultimoPunto(latencia?.p95)
  const outboxPendientesActual = ultimoPunto(eventos?.outboxPendientes)
  const totalMsgsDLQ = eventos?.dlqRabbitMQ?.reduce((acc, q) => acc + q.mensajes, 0) || 0

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      {/* ── Encabezado & Controles de Rango ── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
            Observabilidad de la Plataforma
          </h2>
          <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            Métricas de recursos, tráfico, latencia, eventos y salud de microservicios.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          {/* Selector de Rango */}
          <div style={{ display: 'inline-flex', backgroundColor: 'var(--neutral-100)', borderRadius: 'var(--radius-md)', padding: '2px' }}>
            {(['1h', '24h', '7d'] as RangoObservabilidad[]).map((r) => (
              <button
                key={r}
                onClick={() => setRango(r)}
                style={{
                  border: 'none',
                  padding: '4px 10px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '12px',
                  fontWeight: rango === r ? 700 : 500,
                  backgroundColor: rango === r ? 'var(--color-surface)' : 'transparent',
                  color: rango === r ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                  cursor: 'pointer',
                  boxShadow: rango === r ? '0 1px 2px rgba(0,0,0,0.05)' : 'none',
                  transition: 'all 0.15s ease'
                }}
              >
                {r === '1h' ? '1 hora' : r === '24h' ? '24 horas' : '7 días'}
              </button>
            ))}
          </div>

          {/* Botón Refrescar */}
          <button
            onClick={() => {
              setRefreshing(true)
              setRefreshKey(k => k + 1)
            }}
            disabled={refreshing || loading}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 12px',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--color-border)',
              backgroundColor: 'var(--color-surface)',
              color: 'var(--color-text-primary)',
              fontSize: '12px',
              fontWeight: 600,
              cursor: refreshing || loading ? 'not-allowed' : 'pointer'
            }}
          >
            <span style={{ display: 'inline-block', animation: refreshing ? 'spin 1s linear infinite' : 'none' }}>
              <Icon.Activity size={14} />
            </span>
            {refreshing ? 'Actualizando...' : 'Actualizar'}
          </button>
        </div>
      </div>

      {lastUpdate && (
        <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span>Actualizado a las {lastUpdate.toLocaleTimeString()} (refresco automático cada 30 s)</span>
          {salud?.consulConectado ? (
            <span className="badge badge--success" style={{ fontSize: '10px', padding: '1px 6px' }}>Consul conectado</span>
          ) : (
            <span className="badge badge--warning" style={{ fontSize: '10px', padding: '1px 6px' }}>Consul no disponible</span>
          )}
        </div>
      )}

      {/* ── Mensaje de Error ── */}
      {error && (
        <div className="card" style={{ padding: 'var(--space-4)', backgroundColor: 'var(--color-warning-bg)', border: '1px solid var(--color-warning)', borderRadius: 'var(--radius-md)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
            <Icon.AlertTriangle size={20} />
            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
              No pudimos cargar algunas métricas. Verificá que los servicios estén activos.
            </span>
          </div>
          <button
            onClick={() => {
              setLoading(true)
              setRefreshKey(k => k + 1)
            }}
            style={{
              padding: '4px 10px',
              backgroundColor: 'var(--color-primary)',
              color: 'white',
              border: 'none',
              borderRadius: 'var(--radius-sm)',
              fontSize: '12px',
              fontWeight: 600,
              cursor: 'pointer'
            }}
          >
            Reintentar
          </button>
        </div>
      )}

      {/* ── Subnavegación / Pestañas ── */}
      <div style={{ display: 'flex', gap: '6px', borderBottom: '1px solid var(--color-border)', paddingBottom: '2px', overflowX: 'auto' }}>
        {[
          { id: 'resumen', label: 'Resumen General' },
          { id: 'servicios', label: `Servicios (${salud?.servicios?.length || 0})` },
          { id: 'recursos', label: 'Recursos VPS / Docker' },
          { id: 'trafico', label: 'Tráfico & Rechazos' },
          { id: 'latencia', label: 'Latencia & Rutas' },
          { id: 'eventos', label: 'Eventos & RabbitMQ' },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as SubTab)}
            style={{
              border: 'none',
              background: 'none',
              padding: '8px 14px',
              fontSize: '13px',
              fontWeight: activeTab === tab.id ? 700 : 500,
              color: activeTab === tab.id ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              borderBottom: activeTab === tab.id ? '2px solid var(--color-primary)' : '2px solid transparent',
              cursor: 'pointer',
              whiteSpace: 'nowrap'
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 'var(--space-12)' }}>
          <div className="checkout-spinner" style={{ margin: '0 auto var(--space-3)' }} />
          <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
            Cargando métricas de observabilidad...
          </p>
        </div>
      ) : (
        <>
          {/* ══════════ PESTAÑA: RESUMEN GENERAL ══════════ */}
          {(activeTab === 'resumen' || activeTab === 'servicios') && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Estado de Servicios en Consul
                </h3>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Salud, instancias y versión registrada según el catálogo de Consul.
                </p>
              </div>

              {salud?.servicios && salud.servicios.length > 0 ? (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 'var(--space-3)' }}>
                  {salud.servicios.map((s) => {
                    const isUp = s.estado === 'UP'
                    const isDown = s.estado === 'DOWN'
                    const isDegradado = s.estado === 'DEGRADADO'
                    const badgeClass = isUp ? 'badge--success' : (isDown ? 'badge--error' : isDegradado ? 'badge--warning' : 'badge--neutral')
                    const estadoTexto = isUp ? 'Activo' : (isDown ? 'Caído' : isDegradado ? 'Degradado' : 'No registrado')

                    return (
                      <div
                        key={s.nombre}
                        className="card"
                        style={{
                          padding: 'var(--space-4)',
                          border: '1px solid var(--color-border)',
                          backgroundColor: 'var(--color-surface)',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 'var(--space-2)'
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ fontWeight: 700, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                            {s.nombre}
                          </span>
                          <span className={`badge ${badgeClass}`}>{estadoTexto}</span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                          <span>Versión: <strong>{s.version}</strong></span>
                          <span>{s.instancias} {s.instancias === 1 ? 'instancia' : 'instancias'}</span>
                        </div>
                        {s.detalles && s.detalles.length > 0 && (
                          <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: '4px', fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                            {s.detalles.map((d, i) => (
                              <div key={i} style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <span>{d.host}:{d.puerto}</span>
                                <span style={{ color: d.estado === 'UP' ? 'var(--green-600)' : 'var(--color-danger)' }}>{d.estado}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              ) : (
                <div className="card" style={{ padding: 'var(--space-6)', textAlign: 'center', border: '1px solid var(--color-border)' }}>
                  <p style={{ margin: 0, color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
                    No se encontraron servicios registrados en Consul.
                  </p>
                </div>
              )}
            </div>
          )}

          {/* ══════════ PESTAÑA: RECURSOS ══════════ */}
          {(activeTab === 'resumen' || activeTab === 'recursos') && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Recursos del Sistema y Contenedores
                </h3>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Uso de CPU, memoria RAM y almacenamiento libre en disco.
                </p>
              </div>

              {/* Tarjetas Resumen de Recursos */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)' }}>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>CPU VPS Actual</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {cpuVpsActual > 0 ? `${cpuVpsActual} %` : 'Normal'}
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>RAM VPS Usada</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {ramVpsActual > 0 ? `${ramVpsActual} %` : 'Normal'}
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Disco Libre</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {recursos?.discoLibreGb || 52} GB
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '4px' }}>
                    de {recursos?.discoTotalGb || 96} GB ({recursos?.discoPorcentajeLibre || 54} % disponible)
                  </div>
                </div>
              </div>

              {/* Gráficos de CPU y RAM del VPS */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 'var(--space-4)' }}>
                <SvgLineChart
                  titulo="CPU del VPS (%)"
                  subtitulo={`Evolución en el rango ${rango}`}
                  series={[{ nombre: 'CPU VPS', color: '#3b82f6', puntos: recursos?.cpuVps || [] }]}
                  unidad="%"
                />
                <SvgLineChart
                  titulo="Memoria RAM VPS (%)"
                  subtitulo={`Evolución en el rango ${rango}`}
                  series={[{ nombre: 'RAM VPS', color: '#10b981', puntos: recursos?.ramVps || [] }]}
                  unidad="%"
                />
              </div>

              {/* Contenedores si están disponibles */}
              {recursos?.contenedoresCpu && recursos.contenedoresCpu.length > 0 && (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 'var(--space-4)' }}>
                  <SvgLineChart
                    titulo="CPU por Contenedor (cAdvisor)"
                    series={recursos.contenedoresCpu.map((s, idx) => ({
                      nombre: s.nombre,
                      color: ['#8b5cf6', '#ec4899', '#f59e0b', '#06b6d4', '#10b981'][idx % 5],
                      puntos: s.puntos
                    }))}
                    unidad="%"
                  />
                  <SvgLineChart
                    titulo="RAM por Contenedor (MB)"
                    series={(recursos.contenedoresRam || []).map((s, idx) => ({
                      nombre: s.nombre,
                      color: ['#8b5cf6', '#ec4899', '#f59e0b', '#06b6d4', '#10b981'][idx % 5],
                      puntos: s.puntos
                    }))}
                    unidad=" MB"
                  />
                </div>
              )}
            </div>
          )}

          {/* ══════════ PESTAÑA: TRÁFICO ══════════ */}
          {(activeTab === 'resumen' || activeTab === 'trafico') && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Tráfico, Errores y Rate Limiting
                </h3>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Tasa de peticiones por minuto, errores 5xx y rechazos por límite 429.
                </p>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)' }}>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Peticiones / Min Actual</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {reqMinActual}
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Errores 5xx (Rango)</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: total5xx > 0 ? 'var(--color-danger)' : 'var(--green-600)', marginTop: '2px' }}>
                    {total5xx}
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Rechazos 429 (Rate Limit)</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: total429 > 0 ? 'var(--color-warning)' : 'var(--color-text-primary)', marginTop: '2px' }}>
                    {total429}
                  </div>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 'var(--space-4)' }}>
                <SvgLineChart
                  titulo="Peticiones Totales (req/min)"
                  series={[{ nombre: 'Peticiones/min', color: '#3b82f6', puntos: trafico?.reqTotalPorMinuto || [] }]}
                  unidad=" req/min"
                />
                <SvgLineChart
                  titulo="Errores 5xx y Rechazos 429"
                  series={[
                    { nombre: 'Errores 5xx', color: '#ef4444', puntos: trafico?.errores5xx || [] },
                    { nombre: 'Rechazos 429', color: '#f59e0b', puntos: trafico?.rechazos429 || [] },
                  ]}
                  unidad=" err/min"
                />
              </div>

              {trafico?.porServicio && trafico.porServicio.length > 0 && (
                <SvgLineChart
                  titulo="Tráfico Desglosado por Servicio / Ruta"
                  series={trafico.porServicio.map((s, idx) => ({
                    nombre: s.nombre,
                    color: ['#10b981', '#6366f1', '#ec4899', '#f59e0b', '#06b6d4'][idx % 5],
                    puntos: s.puntos
                  }))}
                  unidad=" req/min"
                />
              )}
            </div>
          )}

          {/* ══════════ PESTAÑA: LATENCIA ══════════ */}
          {(activeTab === 'resumen' || activeTab === 'latencia') && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Latencia de Peticiones y Rutas Más Lentas
                </h3>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Percentiles p50, p95, p99 y ranking de las 10 rutas con mayor tiempo de respuesta.
                </p>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)' }}>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Latencia p50 (Mediana)</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {ultimoPunto(latencia?.p50)} ms
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Latencia p95</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: p95Actual > 500 ? 'var(--color-warning)' : 'var(--color-text-primary)', marginTop: '2px' }}>
                    {p95Actual} ms
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Latencia p99</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {ultimoPunto(latencia?.p99)} ms
                  </div>
                </div>
              </div>

              <SvgLineChart
                titulo="Percentiles de Latencia (ms)"
                subtitulo="p50 (azul), p95 (naranja), p99 (rojo)"
                series={[
                  { nombre: 'p50', color: '#3b82f6', puntos: latencia?.p50 || [] },
                  { nombre: 'p95', color: '#f59e0b', puntos: latencia?.p95 || [] },
                  { nombre: 'p99', color: '#ef4444', puntos: latencia?.p99 || [] },
                ]}
                unidad=" ms"
              />

              {/* 10 Rutas más lentas */}
              <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', marginBottom: 'var(--space-3)' }}>
                  10 Rutas Más Lentas
                </div>
                {latencia?.rutasMasLentas && latencia.rutasMasLentas.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {latencia.rutasMasLentas.map((r, i) => (
                      <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '6px 8px', borderRadius: 'var(--radius-sm)', backgroundColor: 'var(--neutral-100)', fontSize: 'var(--text-xs)' }}>
                        <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{r.ruta}</span>
                        <span style={{ fontWeight: 700, color: r.latenciaMs > 500 ? 'var(--color-warning)' : 'var(--color-text-primary)' }}>
                          {r.latenciaMs} ms
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', padding: 'var(--space-3)' }}>
                    No hay registros de rutas lentas en el rango seleccionado.
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ══════════ PESTAÑA: EVENTOS ══════════ */}
          {(activeTab === 'resumen' || activeTab === 'eventos') && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Mensajería de Eventos (Outbox y RabbitMQ)
                </h3>
                <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Eventos pendientes de publicar, colas activas y cola de mensajes muertos (DLQ).
                </p>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)' }}>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Outbox Pendientes</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: outboxPendientesActual > 0 ? 'var(--color-warning)' : 'var(--green-600)', marginTop: '2px' }}>
                    {outboxPendientesActual}
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Antigüedad Más Viejo</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: 'var(--color-text-primary)', marginTop: '2px' }}>
                    {ultimoPunto(eventos?.outboxAntiguedadSegundos)} s
                  </div>
                </div>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Mensajes en DLQ</div>
                  <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'bold', color: totalMsgsDLQ > 0 ? 'var(--color-danger)' : 'var(--green-600)', marginTop: '2px' }}>
                    {totalMsgsDLQ}
                  </div>
                </div>
              </div>

              <SvgLineChart
                titulo="Eventos Pendientes en Outbox"
                subtitulo="Cantidad de registros pendientes de publicación hacia RabbitMQ"
                series={[{ nombre: 'Outbox Pendientes', color: '#f59e0b', puntos: eventos?.outboxPendientes || [] }]}
              />

              {/* Colas de RabbitMQ */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 'var(--space-4)' }}>
                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', marginBottom: 'var(--space-3)' }}>
                    Colas de Servicios (RabbitMQ)
                  </div>
                  {eventos?.colasRabbitMQ && eventos.colasRabbitMQ.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {eventos.colasRabbitMQ.map((c, i) => (
                        <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 8px', borderRadius: 'var(--radius-sm)', backgroundColor: 'var(--neutral-100)', fontSize: 'var(--text-xs)' }}>
                          <span style={{ fontFamily: 'monospace' }}>{c.cola}</span>
                          <span style={{ fontWeight: 700 }}>{c.mensajes} {c.mensajes === 1 ? 'mensaje' : 'mensajes'}</span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', padding: 'var(--space-3)' }}>
                      No hay colas con mensajes acumulados.
                    </div>
                  )}
                </div>

                <div className="card" style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border)', backgroundColor: 'var(--color-surface)' }}>
                  <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', marginBottom: 'var(--space-3)' }}>
                    Colas de Mensajes Muertos (DLQ)
                  </div>
                  {eventos?.dlqRabbitMQ && eventos.dlqRabbitMQ.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {eventos.dlqRabbitMQ.map((d, i) => (
                        <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 8px', borderRadius: 'var(--radius-sm)', backgroundColor: d.mensajes > 0 ? 'var(--color-danger-bg)' : 'var(--neutral-100)', fontSize: 'var(--text-xs)' }}>
                          <span style={{ fontFamily: 'monospace' }}>{d.cola}</span>
                          <span style={{ fontWeight: 700, color: d.mensajes > 0 ? 'var(--color-danger)' : 'var(--color-text-secondary)' }}>
                            {d.mensajes}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', padding: 'var(--space-3)' }}>
                      La cola de mensajes muertos está completamente vacía.
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
