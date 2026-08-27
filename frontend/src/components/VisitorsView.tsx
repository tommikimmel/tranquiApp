import { useState, useEffect, useRef } from 'react'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'

export default function VisitorsView() {
  const [canales, setCanales] = useState<any[]>([])
  const [selectedCanal, setSelectedCanal] = useState<any | null>(null)
  const [inputText, setInputText] = useState('')
  const [loadingCanales, setLoadingCanales] = useState(true)

  // Real-time WebSocket hook for visitors
  const { messages, sendMessage } = useChat(selectedCanal ? selectedCanal.id : null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  // Fetch active visitador channels
  useEffect(() => {
    setLoadingCanales(true)
    api.getChatCanalesVisitadores()
      .then((res: any) => {
        setCanales(res || [])
      })
      .catch((err) => {
        console.error("Error al cargar canales de visitadores:", err)
      })
      .finally(() => {
        setLoadingCanales(false)
      })
  }, [])

  // Scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputText.trim() || !selectedCanal) return

    const sent = sendMessage(selectedCanal.id, inputText.trim())
    if (sent) {
      setInputText('')
    }
  }

  // Get laboratory color tag or badge helper
  const getLabBadgeColor = (lab: string) => {
    const l = (lab || '').toLowerCase()
    if (l.includes('gador')) return { bg: '#e0f2fe', text: '#0369a1', border: '#bae6fd' } // Blue
    if (l.includes('roemmers')) return { bg: '#f3e8ff', text: '#6b21a8', border: '#e9d5ff' } // Purple
    if (l.includes('bagó') || l.includes('bago')) return { bg: '#ecfdf5', text: '#047857', border: '#a7f3d0' } // Green
    if (l.includes('raffo')) return { bg: '#fff1f2', text: '#be123c', border: '#fecdd3' } // Rose
    return { bg: '#f1f5f9', text: '#475569', border: '#e2e8f0' } // Slate
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* Information Banner */}
      <div className="alert-banner alert-banner--success" role="status">
        <span className="alert-banner__icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 20, height: 20 }}>
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
            <path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        </span>
        <div className="alert-banner__content">
          <div className="alert-banner__title">Canal Exclusivo para Visitadores Médicos</div>
          <div className="alert-banner__body">
            Este canal está completamente separado de tus pacientes.
            Los laboratorios asociados pueden responderte y enviarte estudios y muestras directamente aquí.
          </div>
        </div>
      </div>

      <div className="card visitors-grid" style={{
        padding: 0,
        display: 'grid',
        gridTemplateColumns: '320px 1fr',
        height: 'calc(100vh - var(--header-height) - var(--space-24))',
        overflow: 'hidden'
      }}>
        {/* Left Side: Reps List */}
        <div className="visitors-grid__sidebar" style={{
          borderRight: '1px solid var(--color-border)',
          display: 'flex',
          flexDirection: 'column',
          height: '100%'
        }}>
          <div style={{ 
            padding: 'var(--space-4) var(--space-5)', 
            borderBottom: '1px solid var(--color-border)',
            fontWeight: 'var(--font-weight-bold)',
            fontSize: 'var(--text-base)',
            color: 'var(--color-text-primary)'
          }}>
            Representantes de Laboratorio
          </div>

          <div style={{ flex: 1, overflowY: 'auto' }}>
            {loadingCanales ? (
              <div style={{ padding: 'var(--space-8)', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                <div className="checkout-spinner" style={{ margin: '0 auto var(--space-2)' }} />
                Cargando representantes...
              </div>
            ) : canales.length === 0 ? (
              <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
                No tenés conversaciones con laboratorios.
              </div>
            ) : (
              canales.map((canal) => {
                const isSelected = selectedCanal?.id === canal.id
                // For visitadores, specialty stores the laboratory name
                const labName = canal.specialty || 'Laboratorio'
                const styleColors = getLabBadgeColor(labName)

                return (
                  <div
                    key={canal.id}
                    onClick={() => setSelectedCanal(canal)}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 'var(--space-1)',
                      padding: 'var(--space-4) var(--space-5)',
                      cursor: 'pointer',
                      borderBottom: '1px solid var(--color-border)',
                      backgroundColor: isSelected ? 'var(--green-50)' : 'transparent',
                      transition: 'background-color 0.2s',
                    }}
                  >
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'space-between',
                      width: '100%'
                    }}>
                      <span style={{ 
                        fontSize: 'var(--text-sm)', 
                        fontWeight: 'var(--font-weight-medium)',
                        color: 'var(--color-text-primary)' 
                      }}>
                        {canal.nombre}
                      </span>
                      <span style={{
                        fontSize: '10px',
                        padding: '2px 8px',
                        borderRadius: '12px',
                        backgroundColor: styleColors.bg,
                        color: styleColors.text,
                        border: `1px solid ${styleColors.border}`,
                        fontWeight: 'var(--font-weight-semi)'
                      }}>
                        {labName}
                      </span>
                    </div>
                    <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                      {canal.email}
                    </span>
                  </div>
                )
              })
            )}
          </div>
        </div>

        {/* Right Side: Chat Box */}
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: '#fcfcfc' }}>
          {selectedCanal ? (
            <>
              {/* Header */}
              <div style={{
                padding: 'var(--space-4) var(--space-6)',
                borderBottom: '1px solid var(--color-border)',
                backgroundColor: 'var(--color-surface)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <div>
                  <h3 style={{ 
                    fontFamily: 'var(--font-heading)', 
                    fontSize: 'var(--text-base)', 
                    fontWeight: 'var(--font-weight-bold)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--space-2)'
                  }}>
                    {selectedCanal.nombre}
                    <span style={{
                      fontSize: '11px',
                      padding: '2px 8px',
                      borderRadius: '12px',
                      ...getLabBadgeColor(selectedCanal.specialty)
                    }}>
                      {selectedCanal.specialty || 'Laboratorio'}
                    </span>
                  </h3>
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                    Contacto verificado · {selectedCanal.email}
                  </span>
                </div>
              </div>

              {/* Message History */}
              <div style={{ 
                flex: 1, 
                padding: 'var(--space-6)', 
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-4)'
              }}>
                {messages.length === 0 ? (
                  <div style={{ 
                    margin: 'auto', 
                    color: 'var(--color-text-secondary)', 
                    fontSize: 'var(--text-sm)',
                    textAlign: 'center'
                  }}>
                    No hay mensajes anteriores.
                  </div>
                ) : (
                  messages.map((msg, index) => {
                    const isMe = msg.remitenteId !== selectedCanal.id
                    return (
                      <div
                        key={msg.id || index}
                        style={{
                          alignSelf: isMe ? 'flex-end' : 'flex-start',
                          maxWidth: '70%',
                          backgroundColor: isMe ? 'var(--color-primary)' : 'var(--color-surface)',
                          color: isMe ? 'var(--color-text-on-primary)' : 'var(--color-text-primary)',
                          padding: 'var(--space-3) var(--space-4)',
                          borderRadius: isMe ? '12px 12px 0 12px' : '12px 12px 12px 0',
                          boxShadow: 'var(--shadow-sm)',
                          border: isMe ? 'none' : '1px solid var(--color-border)',
                          fontSize: 'var(--text-sm)',
                          lineHeight: 'var(--line-height-normal)',
                          wordBreak: 'break-word'
                        }}
                      >
                        {msg.contenido}
                      </div>
                    )
                  })
                )}
                <div ref={messagesEndRef} />
              </div>

              {/* Chat Input */}
              <form 
                onSubmit={handleSend}
                style={{
                  padding: 'var(--space-4) var(--space-6)',
                  borderTop: '1px solid var(--color-border)',
                  backgroundColor: 'var(--color-surface)',
                  display: 'flex',
                  gap: 'var(--space-3)'
                }}
              >
                <input
                  type="text"
                  className="chat-reply-input"
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  placeholder={`Responder a ${selectedCanal.nombre}...`}
                  style={{
                    flex: 1,
                    padding: 'var(--space-2) var(--space-4)',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--color-border)',
                    fontFamily: 'var(--font-body)',
                    fontSize: 'var(--text-sm)',
                    outline: 'none'
                  }}
                  onFocus={(e) => e.target.style.borderColor = 'var(--color-primary)'}
                  onBlur={(e) => e.target.style.borderColor = 'var(--color-border)'}
                />
                <button 
                  type="submit" 
                  className="btn btn--primary"
                  style={{ padding: 'var(--space-2) var(--space-5)' }}
                >
                  Enviar
                </button>
              </form>
            </>
          ) : (
            <div style={{ 
              margin: 'auto', 
              textAlign: 'center', 
              color: 'var(--color-text-secondary)',
              padding: 'var(--space-8)'
            }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 48, height: 48, margin: '0 auto var(--space-4)', opacity: 0.4 }}>
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
              <h3 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
                Canal de Laboratorios
              </h3>
              <p style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-1)' }}>
                Seleccioná un visitador médico para responder sus consultas y ver novedades.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
