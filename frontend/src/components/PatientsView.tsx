import { useState, useEffect, useRef, useCallback } from 'react'
import { useChat } from '../hooks/useChat'
import { type Patient, usePatients } from '../hooks/usePatients'
import PatientDirectorySidebar from './PatientDirectorySidebar'

export default function PatientsView({ onUnreadChatsChange }: { onUnreadChatsChange?: () => void }) {
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
    <div className="card" style={{
      padding: 0,
      display: 'grid',
      gridTemplateColumns: '320px 1fr',
      height: '100%',
      minHeight: 0,
      overflow: 'hidden'
    }}>
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

      {/* Main Chat Workspace */}
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, overflow: 'hidden', backgroundColor: '#fcfcfc' }}>
        {selectedPatient ? (
          <>
            {/* Header: Patient Bio Details */}
            <div style={{
              padding: 'var(--space-4) var(--space-6)',
              borderBottom: '1px solid var(--color-border)',
              backgroundColor: 'var(--color-surface)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-2)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
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

                <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
                  <span className="btn btn--sm btn--primary" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', cursor: 'default' }}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
                      <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
                    </svg>
                    Chat
                  </span>
                </div>
              </div>

              {/* Patient metadata ribbon */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: 'var(--space-4)',
                backgroundColor: 'var(--neutral-50)',
                padding: 'var(--space-2) var(--space-4)',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border)',
                fontSize: 'var(--text-xs)'
              }}>
                <div><strong>DNI:</strong> {selectedPatient.dni || 'No cargado'}</div>
                <div><strong>Obra Social:</strong> {selectedPatient.obraSocial || 'Particular'}</div>
                <div><strong>N° Afiliado:</strong> {selectedPatient.numAfiliado || 'N/A'}</div>
                <div><strong>Dirección:</strong> {selectedPatient.direccion || 'No cargada'}</div>
              </div>
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
