import { useState, useEffect, useRef } from 'react'
import { Icon } from './Icon'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

const ESTADO_LABEL: Record<string, { label: string; cls: string }> = {
  PENDIENTE: { label: 'Pendiente', cls: 'badge--warning' },
  ACTIVO: { label: 'En curso', cls: 'badge--info' },
  RESUELTO: { label: 'Resuelto', cls: 'badge--success' },
}

const formatFecha = (iso?: string) => {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

// Lado admin del sistema de tickets — lista todos los tickets de todos los usuarios (con quién
// lo abrió) y permite responder / cambiar el estado. El detalle y la respuesta pegan a los
// mismos endpoints /api/tickets/* que usa MyTicketsView (autorizados también para ADMIN), solo
// el listado (todos vs. propios) y el cambio de estado son exclusivos de /api/admin/tickets.
export default function AdminTicketsView() {
  const { showAlert } = useAlert()
  const [tickets, setTickets] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [filtroEstado, setFiltroEstado] = useState<'TODOS' | 'PENDIENTE' | 'ACTIVO' | 'RESUELTO'>('TODOS')
  const [selected, setSelected] = useState<any | null>(null)
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  const cargarLista = () => {
    setLoading(true)
    api.getAdminTickets()
      .then((res: any) => setTickets(Array.isArray(res) ? res : []))
      .catch((err: any) => console.error('Error al cargar tickets:', err))
      .finally(() => setLoading(false))
  }

  useEffect(() => { cargarLista() }, [])

  const abrirDetalle = (id: number) => {
    api.getTicket(id)
      .then((res: any) => setSelected(res))
      .catch((err: any) => {
        console.error('Error al abrir el ticket:', err)
        showAlert('No se pudo abrir el ticket.', 'error')
      })
  }

  useEffect(() => {
    if (!selected?.id) return
    const interval = setInterval(() => {
      api.getTicket(selected.id).then((res: any) => setSelected(res)).catch(() => {})
    }, 8000)
    return () => clearInterval(interval)
  }, [selected?.id])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [selected?.mensajes])

  const handleResponder = async () => {
    if (!reply.trim() || !selected) return
    setSending(true)
    try {
      const actualizado = await api.responderTicket(selected.id, reply.trim())
      setSelected(actualizado)
      setReply('')
      cargarLista()
    } catch (err: any) {
      showAlert(err.message || 'No se pudo enviar el mensaje.', 'error')
    } finally {
      setSending(false)
    }
  }

  const handleCambiarEstado = async (estado: 'PENDIENTE' | 'ACTIVO' | 'RESUELTO') => {
    if (!selected) return
    try {
      await api.cambiarEstadoTicket(selected.id, estado)
      setSelected({ ...selected, estado })
      cargarLista()
    } catch (err: any) {
      showAlert(err.message || 'No se pudo cambiar el estado.', 'error')
    }
  }

  const ticketsFiltrados = filtroEstado === 'TODOS' ? tickets : tickets.filter(t => t.estado === filtroEstado)

  return (
    <div className="card" style={{
      padding: 'var(--space-6)',
      border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius-lg)',
      backgroundColor: 'var(--color-surface)',
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-4)'
    }}>
      <div>
        <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
          Tickets de Soporte
        </h2>
        <p style={{ margin: '2px 0 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
          Consultas de pacientes y profesionales. Respondé desde acá — se les avisa por mail.
        </p>
      </div>

      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        {(['TODOS', 'PENDIENTE', 'ACTIVO', 'RESUELTO'] as const).map(f => (
          <button
            key={f}
            onClick={() => setFiltroEstado(f)}
            className={`btn btn--sm ${filtroEstado === f ? 'btn--primary' : 'btn--ghost'}`}
          >
            {f === 'TODOS' ? 'Todos' : ESTADO_LABEL[f].label}
          </button>
        ))}
      </div>

      <div className="ticket-columns" style={{ display: 'flex', gap: 'var(--space-4)', minHeight: '400px', flexWrap: 'wrap' }}>
        {/* Lista */}
        <div className="ticket-list-panel" style={{ flex: '1 1 280px', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', maxHeight: '520px', overflowY: 'auto' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 'var(--space-6)' }}><div className="checkout-spinner" style={{ margin: 'auto' }} /></div>
          ) : ticketsFiltrados.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', padding: 'var(--space-6)' }}>
              No hay tickets{filtroEstado !== 'TODOS' ? ' en este estado' : ''}.
            </p>
          ) : (
            ticketsFiltrados.map((t) => {
              const e = ESTADO_LABEL[t.estado] || { label: t.estado, cls: 'badge--neutral' }
              const activo = selected?.id === t.id
              return (
                <div
                  key={t.id}
                  onClick={() => abrirDetalle(t.id)}
                  style={{
                    padding: 'var(--space-3)',
                    border: activo ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '4px',
                    backgroundColor: activo ? 'var(--green-50)' : 'transparent'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong style={{ fontSize: 'var(--text-sm)' }}>{t.asunto}</strong>
                    <span className={`badge ${e.cls}`} style={{ fontSize: '9px' }}>{e.label}</span>
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    <span style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>
                      {t.creadorNombre} <span style={{ fontWeight: 'normal', color: 'var(--color-text-secondary)' }}>({t.creadorRol === 'PSIQUIATRA' ? 'Profesional' : 'Paciente'})</span>
                    </span>
                    {t.creadorEmail && (
                      <span style={{ color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '11px' }}>
                        <Icon.Mail size={12} /> <strong>{t.creadorEmail}</strong>
                      </span>
                    )}
                  </div>
                  {t.ultimoMensaje && (
                    <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {t.ultimoMensaje}
                    </div>
                  )}
                  <div style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>{formatFecha(t.fechaActualizacion)}</div>
                </div>
              )
            })
          )}
        </div>

        {/* Detalle / chat */}
        <div className="ticket-detail-panel" style={{ flex: '2 1 340px', display: 'flex', flexDirection: 'column', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', minHeight: '400px', maxHeight: '520px' }}>
          {!selected ? (
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
              Elegí un ticket para ver la conversación.
            </div>
          ) : (
            <>
              <div style={{ padding: 'var(--space-3) var(--space-4)', borderBottom: '1px solid var(--color-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
                <div>
                  <strong style={{ fontSize: 'var(--text-sm)', display: 'block' }}>{selected.asunto}</strong>
                  <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap', marginTop: '2px' }}>
                    <span><strong>{selected.creadorNombre}</strong> ({selected.creadorRol === 'PSIQUIATRA' ? 'Profesional' : 'Paciente'})</span>
                    {selected.creadorEmail && (
                      <a href={`mailto:${selected.creadorEmail}`} style={{ color: 'var(--color-primary)', fontWeight: 'bold', textDecoration: 'underline', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <Icon.Mail size={14} /> {selected.creadorEmail}
                      </a>
                    )}
                  </div>
                </div>
                <select
                  value={selected.estado}
                  onChange={(e) => handleCambiarEstado(e.target.value as any)}
                  style={{ padding: '4px 8px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)', fontSize: '12px', fontWeight: 600, cursor: 'pointer' }}
                >
                  <option value="PENDIENTE">Pendiente</option>
                  <option value="ACTIVO">En curso</option>
                  <option value="RESUELTO">Resuelto</option>
                </select>
              </div>
              <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', padding: 'var(--space-3)' }}>
                {selected.mensajes.map((m: any) => (
                  <div key={m.id} style={{ display: 'flex', justifyContent: m.deAdmin ? 'flex-end' : 'flex-start' }}>
                    <div style={{
                      maxWidth: '75%',
                      padding: 'var(--space-2) var(--space-3)',
                      borderRadius: 'var(--radius-md)',
                      backgroundColor: m.deAdmin ? 'var(--color-primary)' : 'var(--neutral-100)',
                      color: m.deAdmin ? 'white' : 'var(--color-text-primary)',
                    }}>
                      <div style={{ fontSize: '10px', opacity: 0.8, marginBottom: '2px' }}>
                        {m.deAdmin ? `Vos (soporte) · ${m.autorNombre}` : m.autorNombre} · {formatFecha(m.fechaEnvio)}
                      </div>
                      <div style={{ fontSize: 'var(--text-sm)', whiteSpace: 'pre-wrap' }}>{m.contenido}</div>
                    </div>
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-2)', padding: 'var(--space-3)', borderTop: '1px solid var(--color-border)' }}>
                <input
                  className="form-input chat-reply-input"
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !sending) handleResponder() }}
                  placeholder="Responder..."
                  style={{ flex: 1 }}
                />
                <button className="btn btn--primary btn--sm" disabled={sending || !reply.trim()} onClick={handleResponder}>
                  Enviar
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
