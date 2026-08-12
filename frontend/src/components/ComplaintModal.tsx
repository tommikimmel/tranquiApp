import { useState } from 'react'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'

function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function IconMailWarning({ size = 20 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <rect x="2" y="4" width="20" height="16" rx="2" />
      <path d="M22 6l-10 7L2 6" />
    </svg>
  )
}

export default function ComplaintModal({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { showAlert } = useAlert()
  const [asunto, setAsunto] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [sending, setSending] = useState(false)

  if (!isOpen) return null

  const handleClose = () => {
    if (sending) return
    setAsunto('')
    setMensaje('')
    onClose()
  }

  const handleSubmit = async () => {
    if (!mensaje.trim()) {
      showAlert('Escribí tu mensaje antes de enviarlo.', 'error')
      return
    }
    setSending(true)
    try {
      await api.enviarQueja({ asunto: asunto.trim(), mensaje: mensaje.trim() })
      showAlert('Tu mensaje fue enviado a soporte. Te responderemos a la brevedad.', 'success')
      setAsunto('')
      setMensaje('')
      onClose()
    } catch (err: any) {
      showAlert(err?.message || 'No se pudo enviar tu mensaje. Intentá nuevamente.', 'error')
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
        maxWidth: '520px',
        width: '100%',
        maxHeight: '85vh',
        overflowY: 'auto',
        padding: 'var(--space-6)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <IconMailWarning size={20} /> Quejas y Soporte
          </h3>
          <button onClick={handleClose} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
        </div>

        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
          Contanos qué pasó. Tu mensaje se envía directamente a nuestro equipo de soporte a{' '}
          <strong>soporte@tranquisalud.com</strong> y te responderemos a la brevedad a tu email registrado.
        </p>

        <div className="form-group">
          <label className="form-label" htmlFor="queja-asunto">Asunto</label>
          <input
            id="queja-asunto"
            name="queja-asunto"
            className="form-input"
            type="text"
            placeholder="Ej: Problema con un pago, error en la plataforma..."
            value={asunto}
            onChange={(e) => setAsunto(e.target.value)}
            maxLength={150}
          />
        </div>

        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label form-label--required" htmlFor="queja-mensaje">Mensaje</label>
          <textarea
            id="queja-mensaje"
            name="queja-mensaje"
            className="form-input"
            rows={5}
            placeholder="Contanos el detalle de tu consulta o queja..."
            value={mensaje}
            onChange={(e) => setMensaje(e.target.value)}
            style={{ resize: 'vertical', fontFamily: 'var(--font-body)' }}
            maxLength={4000}
          />
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)' }}>
          <button className="btn btn--secondary" onClick={handleClose} disabled={sending}>
            Cancelar
          </button>
          <button className="btn btn--primary" onClick={handleSubmit} disabled={sending || !mensaje.trim()}>
            {sending ? 'Enviando...' : 'Enviar a soporte'}
          </button>
        </div>
      </div>
    </div>
  )
}
