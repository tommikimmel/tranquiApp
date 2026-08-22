import { useState, useEffect, useRef } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

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

// "Mis Tickets" — reemplaza el viejo ComplaintModal ("Quejas y Soporte", que solo mandaba un
// mail fijo sin poder ver la respuesta). Mismo componente para paciente y profesional: ambos
// abren tickets con el mismo shape, solo cambia desde dónde se monta (LandingPage / App.tsx).
export default function MyTicketsView({ onClose }: { onClose: () => void }) {
  const { showAlert } = useAlert()
  const [view, setView] = useState<'list' | 'new' | 'detail'>('list')
  const [loading, setLoading] = useState(true)
  const [tickets, setTickets] = useState<any[]>([])
  const [selected, setSelected] = useState<any | null>(null)
  const [asunto, setAsunto] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  const cargarLista = () => {
    setLoading(true)
    api.getMisTickets()
      .then((res: any) => setTickets(Array.isArray(res) ? res : []))
      .catch((err: any) => console.error('Error al cargar tickets:', err))
      .finally(() => setLoading(false))
  }

  useEffect(() => { cargarLista() }, [])

  const abrirDetalle = (id: number) => {
    setView('detail')
    api.getTicket(id)
      .then((res: any) => setSelected(res))
      .catch((err: any) => {
        console.error('Error al cargar el ticket:', err)
        showAlert('No se pudo abrir el ticket.', 'error')
        setView('list')
      })
  }

  // Sin WebSocket dedicado para el chat de tickets (reutiliza el canal general de
  // notificaciones para avisar in-app/mail) — este poll liviano mientras el detalle está
  // abierto es lo que le da la sensación de "en vivo" a la respuesta de soporte.
  useEffect(() => {
    if (view !== 'detail' || !selected?.id) return
    const interval = setInterval(() => {
      api.getTicket(selected.id).then((res: any) => setSelected(res)).catch(() => {})
    }, 8000)
    return () => clearInterval(interval)
  }, [view, selected?.id])

  useEffect(() => {
    if (view === 'detail') {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [selected?.mensajes, view])

  const handleCrearTicket = async () => {
    if (!asunto.trim() || !mensaje.trim()) {
      showAlert('Completá el asunto y el mensaje.', 'error')
      return
    }
    setSending(true)
    try {
      const nuevo: any = await api.crearTicket({ asunto: asunto.trim(), mensaje: mensaje.trim() })
      showAlert('Ticket enviado. Te vamos a avisar por mail cuando te respondan.', 'success')
      setAsunto('')
      setMensaje('')
      cargarLista()
      abrirDetalle(nuevo.id)
    } catch (err: any) {
      showAlert(err.message || 'No se pudo crear el ticket.', 'error')
    } finally {
      setSending(false)
    }
  }

  const handleResponder = async () => {
    if (!reply.trim() || !selected) return
    setSending(true)
    try {
      const actualizado = await api.responderTicket(selected.id, reply.trim())
      setSelected(actualizado)
      setReply('')
    } catch (err: any) {
      showAlert(err.message || 'No se pudo enviar el mensaje.', 'error')
    } finally {
      setSending(false)
    }
  }

  return (
    <div style={{
      position: 'fixed',
      top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.5)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: 'var(--space-4)'
    }}>
      <div className="card" style={{
        maxWidth: '600px',
        width: '100%',
        maxHeight: '85vh',
        overflow: 'hidden',
        position: 'relative',
        padding: 'var(--space-6)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>
            {view === 'detail' && selected ? selected.asunto : view === 'new' ? 'Nuevo ticket' : 'Mis Tickets'}
          </h3>
          <button onClick={onClose} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
        </div>

        {view === 'list' && (
          <>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              style={{ alignSelf: 'flex-start' }}
              onClick={() => setView('new')}
            >
              + Nuevo ticket
            </button>
            {loading ? (
              <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}><div className="checkout-spinner" style={{ margin: 'auto' }} /></div>
            ) : tickets.length === 0 ? (
              <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-6)' }}>
                No tenés tickets abiertos. Si tenés una consulta o un problema, creá uno nuevo.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', overflowY: 'auto' }}>
                {tickets.map((t) => {
                  const e = ESTADO_LABEL[t.estado] || { label: t.estado, cls: 'badge--neutral' }
                  return (
                    <div
                      key={t.id}
                      onClick={() => abrirDetalle(t.id)}
                      style={{
                        padding: 'var(--space-3)',
                        border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)',
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '4px'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong style={{ fontSize: 'var(--text-sm)' }}>{t.asunto}</strong>
                        <span className={`badge ${e.cls}`} style={{ fontSize: '9px' }}>{e.label}</span>
                      </div>
                      {t.ultimoMensaje && (
                        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {t.ultimoMensaje}
                        </div>
                      )}
                      <div style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>{formatFecha(t.fechaActualizacion)}</div>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}

        {view === 'new' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <button className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => setView('list')}>← Volver</button>
            <div>
              <label style={{ display: 'block', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}>Asunto</label>
              <input className="form-input" value={asunto} onChange={(e) => setAsunto(e.target.value)} placeholder="Ej: Problema con un pago" style={{ width: '100%' }} />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}>Mensaje</label>
              <textarea className="form-input" value={mensaje} onChange={(e) => setMensaje(e.target.value)} rows={5} placeholder="Contanos qué necesitás..." style={{ width: '100%', resize: 'vertical' }} />
            </div>
            <button className="btn btn--primary" disabled={sending} onClick={handleCrearTicket}>
              {sending ? 'Enviando...' : 'Enviar ticket'}
            </button>
          </div>
        )}

        {view === 'detail' && selected && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', flex: 1, minHeight: 0 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button className="btn btn--ghost btn--sm" onClick={() => { setView('list'); cargarLista() }}>← Volver</button>
              {(() => { const e = ESTADO_LABEL[selected.estado] || { label: selected.estado, cls: 'badge--neutral' }; return (
                <span className={`badge ${e.cls}`} style={{ fontSize: '9px' }}>{e.label}</span>
              ) })()}
            </div>
            <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', padding: 'var(--space-2)' }}>
              {selected.mensajes.map((m: any) => (
                <div key={m.id} style={{ display: 'flex', justifyContent: m.deAdmin ? 'flex-start' : 'flex-end' }}>
                  <div style={{
                    maxWidth: '75%',
                    padding: 'var(--space-2) var(--space-3)',
                    borderRadius: 'var(--radius-md)',
                    backgroundColor: m.deAdmin ? 'var(--neutral-100)' : 'var(--color-primary)',
                    color: m.deAdmin ? 'var(--color-text-primary)' : 'white',
                  }}>
                    <div style={{ fontSize: '10px', opacity: 0.8, marginBottom: '2px' }}>
                      {m.deAdmin ? `Soporte · ${m.autorNombre}` : 'Vos'} · {formatFecha(m.fechaEnvio)}
                    </div>
                    <div style={{ fontSize: 'var(--text-sm)', whiteSpace: 'pre-wrap' }}>{m.contenido}</div>
                  </div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>
            {selected.estado === 'RESUELTO' && (
              <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', margin: 0 }}>
                Este ticket está resuelto. Si escribís un mensaje, se vuelve a abrir.
              </p>
            )}
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <input
                className="form-input"
                value={reply}
                onChange={(e) => setReply(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !sending) handleResponder() }}
                placeholder={selected.estado === 'RESUELTO' ? 'Reabrir con un nuevo mensaje...' : 'Escribí tu mensaje...'}
                style={{ flex: 1 }}
              />
              <button className="btn btn--primary btn--sm" disabled={sending || !reply.trim()} onClick={handleResponder}>
                {selected.estado === 'RESUELTO' ? 'Reabrir' : 'Enviar'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
