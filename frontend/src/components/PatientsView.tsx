import { useState, useEffect, useRef } from 'react'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'

export default function PatientsView() {
  const [canales, setCanales] = useState<any[]>([])
  const [selectedCanal, setSelectedCanal] = useState<any | null>(null)
  const [inputText, setInputText] = useState('')
  const [loadingCanales, setLoadingCanales] = useState(true)

  // Use our real-time WebSocket chat hook
  const { messages, sendMessage } = useChat(selectedCanal ? selectedCanal.id : null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  // Fetch active patient chat channels
  useEffect(() => {
    setLoadingCanales(true)
    api.getChatCanales()
      .then((res: any) => {
        setCanales(res || [])
      })
      .catch((err) => {
        console.error("Error al cargar canales de pacientes:", err)
      })
      .finally(() => {
        setLoadingCanales(false)
      })
  }, [])

  // Scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputText.trim() || !selectedCanal) return

    const sent = sendMessage(selectedCanal.id, inputText.trim())
    if (sent) {
      // Optimistically append the message if WebSocket is fast (the hook will also handle it)
      setInputText('')
    }
  }

  // Calculate initials for avatar
  const getInitials = (name: string) => {
    if (!name) return 'P'
    const parts = name.split(' ')
    return parts.map(p => p[0]).join('').substring(0, 2).toUpperCase()
  }

  return (
    <div className="card" style={{ 
      padding: 0, 
      display: 'grid', 
      gridTemplateColumns: '320px 1fr', 
      height: 'calc(100vh - var(--header-height) - var(--space-12))',
      overflow: 'hidden'
    }}>
      {/* Sidebar: Patient List */}
      <div style={{ 
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
          Mensajes con Pacientes
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {loadingCanales ? (
            <div style={{ padding: 'var(--space-8)', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
              <div className="checkout-spinner" style={{ margin: '0 auto var(--space-2)' }} />
              Cargando pacientes...
            </div>
          ) : canales.length === 0 ? (
            <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
              No tenés chats activos con pacientes.
            </div>
          ) : (
            canales.map((canal) => {
              const isSelected = selectedCanal?.id === canal.id
              const isHighPriority = canal.prioridadClinica === 'PRIORIDAD_ALTA'
              
              return (
                <div
                  key={canal.id}
                  onClick={() => setSelectedCanal(canal)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--space-3)',
                    padding: 'var(--space-4) var(--space-5)',
                    cursor: 'pointer',
                    borderBottom: '1px solid var(--color-border)',
                    backgroundColor: isSelected ? 'var(--green-50)' : 'transparent',
                    transition: 'background-color 0.2s',
                    position: 'relative'
                  }}
                >
                  <div style={{
                    width: '40px',
                    height: '40px',
                    borderRadius: '50%',
                    backgroundColor: isHighPriority ? '#fef3c7' : 'var(--neutral-100)',
                    color: isHighPriority ? 'var(--color-warning)' : 'var(--color-text-secondary)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 'var(--font-weight-semi)',
                    fontSize: 'var(--text-sm)',
                    border: isHighPriority ? '1px solid #fde68a' : '1px solid var(--color-border)'
                  }}>
                    {getInitials(canal.nombre)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ 
                      fontSize: 'var(--text-sm)', 
                      fontWeight: isHighPriority ? 'var(--font-weight-semi)' : 'var(--font-weight-medium)',
                      color: 'var(--color-text-primary)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}>
                      {canal.nombre}
                      {isHighPriority && (
                        <span className="badge badge--warning" style={{ fontSize: '10px', padding: '1px 6px' }}>
                          Prioridad
                        </span>
                      )}
                    </div>
                    <div style={{ 
                      fontSize: 'var(--text-xs)', 
                      color: 'var(--color-text-secondary)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      marginTop: 'var(--space-1)'
                    }}>
                      {canal.email}
                    </div>
                  </div>
                </div>
              )
            })
          )}
        </div>
      </div>

      {/* Main Chat Area */}
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: '#fcfcfc' }}>
        {selectedCanal ? (
          <>
            {/* Chat Header */}
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
                  fontWeight: 'var(--font-weight-bold)' 
                }}>
                  {selectedCanal.nombre}
                </h3>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Paciente · {selectedCanal.email}
                </span>
              </div>
              {selectedCanal.prioridadClinica === 'PRIORIDAD_ALTA' && (
                <div className="badge badge--warning">
                  Próxima cita en menos de 72 hs
                </div>
              )}
            </div>

            {/* Chat Messages */}
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
                  No hay mensajes anteriores. ¡Escribí un mensaje para iniciar la conversación!
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
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                placeholder="Escribí un mensaje..."
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
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            <h3 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
              Tus conversaciones
            </h3>
            <p style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-1)' }}>
              Seleccioná un paciente de la lista para ver su historial y chatear en tiempo real.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
