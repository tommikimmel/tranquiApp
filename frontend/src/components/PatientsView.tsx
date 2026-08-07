import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useChat } from '../hooks/useChat'
import { type Patient, usePatients, getMissingPatientFields, getInitials, calcAge, formatDomicilio } from '../hooks/usePatients'
import PatientDirectorySidebar from './PatientDirectorySidebar'
import EditPatientModal from './EditPatientModal'
import { useAlert } from '../context/AlertContext'

export default function PatientsView({ onUnreadChatsChange }: { onUnreadChatsChange?: () => void }) {
  const navigate = useNavigate()
  const { showAlert } = useAlert()
  const { patients, loadingPatients, searchQuery, setSearchQuery, filteredPatients, fetchPatients } = usePatients()
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [unreadCounts, setUnreadCounts] = useState<Record<number, number>>({})
  const [showEditModal, setShowEditModal] = useState(false)

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
                  <div style={{
                    width: 40, height: 40, borderRadius: '50%', flexShrink: 0,
                    backgroundColor: 'var(--green-100)', color: 'var(--green-700)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontFamily: 'var(--font-heading)', fontWeight: 'bold', fontSize: 'var(--text-sm)'
                  }}>
                    {getInitials(selectedPatient.apellido ? `${selectedPatient.nombre} ${selectedPatient.apellido}` : selectedPatient.nombre)}
                  </div>
                  <div>
                    <h3 style={{
                      fontFamily: 'var(--font-heading)',
                      fontSize: 'var(--text-lg)',
                      fontWeight: 'var(--font-weight-bold)',
                      margin: 0
                    }}>
                      {selectedPatient.apellido ? `${selectedPatient.nombre} ${selectedPatient.apellido}` : selectedPatient.nombre}
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
                    onClick={() => setShowEditModal(true)}
                    title="Completar DNI, domicilio y obra social — necesarios para emitir recetas electrónicas válidas"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                    Editar datos
                  </button>
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
                  <button
                    type="button"
                    className="btn btn--sm btn--primary"
                    style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}
                    onClick={() => navigate('/panel/prescriptions', { state: { patient: selectedPatient, patientId: selectedPatient.id } })}
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                      <line x1="12" y1="18" x2="12" y2="12" />
                      <line x1="9" y1="15" x2="15" y2="15" />
                    </svg>
                    Emitir Receta
                  </button>
                </div>
              </div>

              {/* Patient metadata panel — grouped into Identidad / Cobertura / Domicilio instead of
                  a single flat strip, and surfacing fields (sexo, edad, CUIL, tipo de documento,
                  domicilio estructurado) that were already fetched but never shown here. */}
              {(() => {
                const age = calcAge(selectedPatient.fechaNacimiento)
                const domicilio = formatDomicilio(selectedPatient)
                const tieneCobertura = !!selectedPatient.obraSocial && selectedPatient.obraSocial.toLowerCase() !== 'particular'
                const fields: { label: string; value: string }[] = [
                  { label: selectedPatient.tipoDocumento || 'DNI', value: selectedPatient.dni || 'No cargado' },
                  ...(selectedPatient.cuil ? [{ label: 'CUIL', value: String(selectedPatient.cuil) }] : []),
                  ...(selectedPatient.sexo ? [{ label: 'Sexo', value: selectedPatient.sexo }] : []),
                  ...(age != null ? [{ label: 'Edad', value: `${age} años` }] : []),
                ]
                const coberturaFields: { label: string; value: string }[] = [
                  { label: 'Obra Social', value: selectedPatient.obraSocial || 'Particular' },
                  ...(tieneCobertura && selectedPatient.numAfiliado && selectedPatient.numAfiliado !== 'N/A'
                    ? [{ label: 'N° Afiliado', value: selectedPatient.numAfiliado }] : []),
                  ...(tieneCobertura && selectedPatient.credencial?.plan
                    ? [{ label: 'Plan', value: selectedPatient.credencial.plan }] : []),
                ]
                return (
                  <div className="patients-master-detail__meta-panel">
                    <div className="patients-master-detail__meta-group">
                      {fields.map(f => (
                        <div key={f.label} className="patients-master-detail__meta-field">
                          <span className="patients-master-detail__meta-label">{f.label}</span>
                          <span className="patients-master-detail__meta-value">{f.value}</span>
                        </div>
                      ))}
                    </div>
                    <div className="patients-master-detail__meta-group">
                      {coberturaFields.map(f => (
                        <div key={f.label} className="patients-master-detail__meta-field">
                          <span className="patients-master-detail__meta-label">{f.label}</span>
                          <span className="patients-master-detail__meta-value">{f.value}</span>
                        </div>
                      ))}
                    </div>
                    <div className="patients-master-detail__meta-field patients-master-detail__meta-field--wide">
                      <span className="patients-master-detail__meta-label">Dirección</span>
                      <span className="patients-master-detail__meta-value">{domicilio || 'No cargada'}</span>
                    </div>
                  </div>
                )
              })()}

              {/* Cartel de Datos Incompletos para el Profesional / Psiquiatra */}
              {(() => {
                const missing = getMissingPatientFields(selectedPatient)
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

      {showEditModal && selectedPatient && (
        <EditPatientModal
          patient={selectedPatient}
          onClose={() => setShowEditModal(false)}
          onSaved={(updated) => {
            setSelectedPatient(updated)
            setShowEditModal(false)
            showAlert('Datos del paciente actualizados.', 'success')
            fetchPatients()
          }}
        />
      )}
    </div>
  )
}
