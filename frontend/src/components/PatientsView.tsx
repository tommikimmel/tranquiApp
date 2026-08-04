import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useChat } from '../hooks/useChat'
import { type Patient, usePatients } from '../hooks/usePatients'
import PatientDirectorySidebar from './PatientDirectorySidebar'

export default function PatientsView({ onUnreadChatsChange }: { onUnreadChatsChange?: () => void }) {
  const navigate = useNavigate()
  const { patients, loadingPatients, searchQuery, setSearchQuery, filteredPatients } = usePatients()
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [unreadCounts, setUnreadCounts] = useState<Record<number, number>>({})

  const handleMessageReceived = useCallback((msg: any) => {
    if (msg.remitenteId && msg.remitenteId !== selectedPatient?.id) {
      setUnreadCounts((prev) => ({
        ...prev,
        [msg.remitenteId]: (prev[msg.remitenteId] || 0) + 1
      }))
      onUnreadChatsChange?.()
    }
  }, [selectedPatient, onUnreadChatsChange])

  // Chat states
  const [inputText, setInputText] = useState('')
  const { messages, sendMessage } = useChat(selectedPatient ? selectedPatient.id : null, handleMessageReceived)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  // Initialize unread badge counts once the patient roster is loaded
  useEffect(() => {
    const initialCounts: Record<number, number> = {}
    patients.forEach((p) => {
      if (p.unreadMessagesCount && p.unreadMessagesCount > 0) {
        initialCounts[p.id] = p.unreadMessagesCount
      }
    })
    setUnreadCounts(initialCounts)
  }, [patients])

  // Auto-select top/most recent patient chat when opening Patients view
  useEffect(() => {
    if (!selectedPatient && filteredPatients.length > 0 && !loadingPatients) {
      setSelectedPatient(filteredPatients[0])
    }
  }, [filteredPatients, selectedPatient, loadingPatients])

  useEffect(() => {
    if (selectedPatient) {
      setTimeout(() => {
        onUnreadChatsChange?.()
      }, 300)
    }
  }, [selectedPatient, onUnreadChatsChange])

  // Scroll chat to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSendChat = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputText.trim() || !selectedPatient) return

    const sent = sendMessage(selectedPatient.id, inputText.trim())
    if (sent) {
      setInputText('')
    }
  }

  const handleSelectPatient = (patient: Patient) => {
    setSelectedPatient(patient)
    setUnreadCounts((prev) => ({ ...prev, [patient.id]: 0 }))
  }

  return (
    <div className="card patients-master-detail" data-selected={selectedPatient ? 'true' : 'false'} style={{
      padding: 0,
      height: '100%',
      minHeight: 0,
      overflow: 'hidden'
    }}>
      <div className="patients-master-detail__sidebar">
        <PatientDirectorySidebar
          title="Mis Pacientes"
          patients={patients}
          filteredPatients={filteredPatients}
          loadingPatients={loadingPatients}
          searchQuery={searchQuery}
          onSearchQueryChange={setSearchQuery}
          selectedPatient={selectedPatient}
          onSelectPatient={handleSelectPatient}
          unreadCounts={unreadCounts}
        />
      </div>

      {/* Main Chat Workspace */}
      <div className="patients-master-detail__main" style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, overflow: 'hidden', backgroundColor: '#fcfcfc' }}>
        {selectedPatient ? (
          <>
            {/* Header: Patient Bio Details */}
            <div className="patients-chat-header" style={{
              padding: 'var(--space-4) var(--space-6)',
              borderBottom: '1px solid var(--color-border)',
              backgroundColor: 'var(--color-surface)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-2)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                  <button
                    type="button"
                    className="patients-master-detail__back"
                    onClick={() => setSelectedPatient(null)}
                    aria-label="Volver al listado de pacientes"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: 18, height: 18 }}>
                      <polyline points="15 18 9 12 15 6" />
                    </svg>
                  </button>
                  <div>
                    <h3 style={{
                      fontFamily: 'var(--font-heading)',
                      fontSize: 'var(--text-lg)',
                      fontWeight: 'var(--font-weight-bold)',
                      margin: 0
                    }}>
                      {selectedPatient.nombre}
                    </h3>
                    <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                      {selectedPatient.email} · {selectedPatient.telefono || 'Sin teléfono'}
                    </span>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
                  <button
                    type="button"
                    className="btn btn--sm btn--secondary"
                    style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}
                    onClick={() => navigate('/panel/clinical-history', { state: { patientId: selectedPatient.id } })}
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
                      <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
                      <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
                      <line x1="9" y1="12" x2="15" y2="12" /><line x1="9" y1="16" x2="13" y2="16" />
                    </svg>
                    Historia Clínica
                  </button>
                </div>
              </div>

              {/* Patient metadata ribbon */}
              <div className="patients-master-detail__meta-grid" style={{
                display: 'grid',
                gap: 'var(--space-4)',
                backgroundColor: 'var(--neutral-50)',
                padding: 'var(--space-2) var(--space-4)',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border)',
                fontSize: 'var(--text-xs)'
              }}>
                <div><strong>DNI:</strong> {selectedPatient.dni || 'No cargado'}</div>
                <div><strong>Obra Social:</strong> {selectedPatient.obraSocial || 'Particular'}</div>
                {selectedPatient.obraSocial && selectedPatient.obraSocial.toLowerCase() !== 'particular' && selectedPatient.numAfiliado && selectedPatient.numAfiliado !== 'N/A' && (
                  <div><strong>N° Afiliado:</strong> {selectedPatient.numAfiliado}</div>
                )}
                <div><strong>Dirección:</strong> {selectedPatient.direccion || 'No cargada'}</div>
              </div>

              {/* Cartel de Datos Incompletos para el Profesional / Psiquiatra */}
              {(() => {
                const missing: string[] = []
                if (!selectedPatient.dni && !selectedPatient.numeroDocumento) missing.push('DNI / Documento')
                if (!selectedPatient.fechaNacimiento) missing.push('Fecha de Nacimiento')
                if (!selectedPatient.telefono || selectedPatient.telefono === 'Sin teléfono') missing.push('Teléfono')
                if (!selectedPatient.direccion || selectedPatient.direccion === 'No cargada') missing.push('Dirección')
                if (selectedPatient.obraSocial && selectedPatient.obraSocial.toLowerCase() !== 'particular' && (!selectedPatient.numAfiliado || selectedPatient.numAfiliado === 'N/A')) {
                  missing.push('N° de Afiliado')
                }
                if (missing.length === 0) return null
                return (
                  <div style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px',
                    padding: '12px 16px',
                    backgroundColor: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: 'var(--radius-lg, 10px)',
                    marginTop: 'var(--space-3)',
                    color: '#92400e',
                    fontSize: '13px',
                    lineHeight: '1.5',
                    boxShadow: '0 1px 3px rgba(217, 119, 6, 0.05)'
                  }}>
                    <div style={{
                      width: '28px',
                      height: '28px',
                      borderRadius: '50%',
                      backgroundColor: '#fef3c7',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      marginTop: '1px'
                    }}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="#d97706" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                        <line x1="12" y1="9" x2="12" y2="13" />
                        <line x1="12" y1="17" x2="12.01" y2="17" />
                      </svg>
                    </div>
                    <div>
                      <strong style={{ display: 'block', color: '#78350f', fontWeight: '600', marginBottom: '2px' }}>
                        Datos Incompletos del Paciente
                      </strong>
                      <span>El perfil del paciente requiere completar los siguientes campos: <strong style={{ color: '#b45309' }}>{missing.join(', ')}</strong>.</span>
                    </div>
                  </div>
                )
              })()}
            </div>

            {/* Chat */}
            <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, overflow: 'hidden' }}>
              {/* Chat Messages */}
              <div style={{
                flex: 1,
                padding: 'var(--space-6)',
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-4)',
                minHeight: 0
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
                    const isMe = msg.remitenteId !== selectedPatient.id
                    return (
                      <div
                        key={msg.id || index}
                        style={{
                          alignSelf: isMe ? 'flex-end' : 'flex-start',
                          maxWidth: '70%',
                          backgroundColor: isMe ? '#d9fdd3' : '#f0f2f5',
                          color: 'black',
                          padding: 'var(--space-3) var(--space-4)',
                          borderRadius: isMe ? '12px 12px 0 12px' : '12px 12px 12px 0',
                          boxShadow: 'var(--shadow-sm)',
                          border: 'none',
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
                onSubmit={handleSendChat}
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
                />
                <button type="submit" className="btn btn--primary">Enviar</button>
              </form>
            </div>
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
            </svg>
            <h3 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
              Chat con Pacientes
            </h3>
            <p style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-1)' }}>
              Seleccioná un paciente del directorio para chatear con él.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
