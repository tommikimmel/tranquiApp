import { useState, useEffect, useRef } from 'react'
import { api } from '../api/api'
import { useChat } from '../hooks/useChat'

interface Patient {
  id: number
  nombre: string
  email: string
  telefono: string
  dni: string
  direccion: string
  obraSocial: string
  numAfiliado: string
  ultimaVisita: string
  prioridadClinica: string
}

interface TrackingEntry {
  id: number
  fecha: string
  estadoAnimo: string
  sintomas: string
  notas: string
}

interface ClinicalReport {
  id: number
  fecha: string
  tipoInforme: string
  planTrabajo: string
  contenido: string
  nombreArchivo: string
}

export default function PatientsView() {
  const [patients, setPatients] = useState<Patient[]>([])
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [loadingPatients, setLoadingPatients] = useState(true)
  const [activeTab, setActiveTab] = useState<'chat' | 'clinical'>('chat')

  // Chat states
  const [inputText, setInputText] = useState('')
  const { messages, sendMessage } = useChat(selectedPatient ? selectedPatient.id : null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  // Tracking states
  const [trackings, setTrackings] = useState<TrackingEntry[]>([])
  const [loadingTrackings, setLoadingTrackings] = useState(false)
  const [showAddTracking, setShowAddTracking] = useState(false)
  const [newMood, setNewMood] = useState('Bueno')
  const [newSymptoms, setNewSymptoms] = useState('')
  const [newNotes, setNewNotes] = useState('')

  // Reports states
  const [reports, setReports] = useState<ClinicalReport[]>([])
  const [loadingReports, setLoadingReports] = useState(false)
  const [showAddReport, setShowAddReport] = useState(false)
  const [reportType, setReportType] = useState('Evaluativo')
  const [reportPlan, setReportPlan] = useState('')
  const [reportMode, setReportMode] = useState<'write' | 'upload'>('write')
  const [reportContent, setReportContent] = useState('')
  const [uploadFilename, setUploadFilename] = useState('')

  // Fetch patients on mount
  useEffect(() => {
    fetchPatients()
  }, [])

  const fetchPatients = () => {
    setLoadingPatients(true)
    api.getPacientesAtendidos()
      .then((res: any) => {
        setPatients(res || [])
      })
      .catch((err) => {
        console.error("Error al cargar pacientes:", err)
      })
      .finally(() => {
        setLoadingPatients(false)
      })
  }

  // Load patient clinical details when selected
  useEffect(() => {
    if (selectedPatient) {
      loadClinicalDetails(selectedPatient.id)
    }
  }, [selectedPatient])

  const loadClinicalDetails = (id: number) => {
    setLoadingTrackings(true)
    setLoadingReports(true)
    
    Promise.all([
      api.getSeguimientos(id),
      api.getInformes(id)
    ])
      .then(([trackingsRes, reportsRes]: any) => {
        setTrackings(trackingsRes || [])
        setReports(reportsRes || [])
      })
      .catch((err) => {
        console.error("Error al cargar datos clínicos:", err)
      })
      .finally(() => {
        setLoadingTrackings(false)
        setLoadingReports(false)
      })
  }

  // Scroll chat to bottom
  useEffect(() => {
    if (activeTab === 'chat') {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, activeTab])

  const handleSendChat = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputText.trim() || !selectedPatient) return

    const sent = sendMessage(selectedPatient.id, inputText.trim())
    if (sent) {
      setInputText('')
    }
  }

  const handleAddTracking = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedPatient) return

    const data = {
      estadoAnimo: newMood,
      sintomas: newSymptoms,
      notas: newNotes
    }

    api.crearSeguimiento(selectedPatient.id, data)
      .then((res: any) => {
        setTrackings([res, ...trackings])
        setShowAddTracking(false)
        setNewSymptoms('')
        setNewNotes('')
        setNewMood('Bueno')
        alert("Seguimiento diario registrado con éxito ✓")
      })
      .catch((err) => {
        console.error("Error al guardar seguimiento:", err)
        alert("No se pudo registrar el seguimiento")
      })
  }

  const handleAddReport = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedPatient) return

    const data = {
      tipoInforme: reportType,
      planTrabajo: reportPlan,
      contenido: reportMode === 'write' ? reportContent : '',
      nombreArchivo: reportMode === 'upload' ? uploadFilename || 'informe_clinico.pdf' : ''
    }

    api.crearInforme(selectedPatient.id, data)
      .then((res: any) => {
        setReports([res, ...reports])
        setShowAddReport(false)
        setReportPlan('')
        setReportContent('')
        setUploadFilename('')
        alert("Informe clínico creado con éxito ✓")
      })
      .catch((err) => {
        console.error("Error al crear informe:", err)
        alert("No se pudo guardar el informe")
      })
  }

  const filteredPatients = patients.filter(p => 
    p.nombre.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (p.dni && p.dni.includes(searchQuery))
  )

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
      {/* Sidebar: Patient Directory */}
      <div style={{ 
        borderRight: '1px solid var(--color-border)', 
        display: 'flex', 
        flexDirection: 'column',
        height: '100%'
      }}>
        <div style={{ 
          padding: 'var(--space-4) var(--space-5)', 
          borderBottom: '1px solid var(--color-border)',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-3)'
        }}>
          <div style={{
            fontWeight: 'var(--font-weight-bold)',
            fontSize: 'var(--text-base)',
            color: 'var(--color-text-primary)'
          }}>
            Mis Pacientes
          </div>
          <input
            type="text"
            placeholder="Buscar por nombre, email, DNI..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: 'var(--space-2) var(--space-3)',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--color-border)',
              fontSize: 'var(--text-xs)',
              outline: 'none',
              fontFamily: 'var(--font-body)'
            }}
          />
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {loadingPatients ? (
            <div style={{ padding: 'var(--space-8)', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
              <div className="checkout-spinner" style={{ margin: '0 auto var(--space-2)' }} />
              Cargando pacientes...
            </div>
          ) : filteredPatients.length === 0 ? (
            <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
              No se encontraron pacientes.
            </div>
          ) : (
            filteredPatients.map((patient) => {
              const isSelected = selectedPatient?.id === patient.id
              const isHighPriority = patient.prioridadClinica === 'PRIORIDAD_ALTA'
              
              return (
                <div
                  key={patient.id}
                  onClick={() => {
                    setSelectedPatient(patient)
                    setActiveTab('chat')
                  }}
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
                    {getInitials(patient.nombre)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ 
                      fontSize: 'var(--text-sm)', 
                      fontWeight: 'var(--font-weight-medium)',
                      color: 'var(--color-text-primary)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}>
                      {patient.nombre}
                      {isHighPriority && (
                        <span className="badge badge--warning" style={{ fontSize: '9px', padding: '1px 4px' }}>
                          Urgente
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
                      Última visita: {patient.ultimaVisita || 'Ninguna'}
                    </div>
                  </div>
                </div>
              )
            })
          )}
        </div>
      </div>

      {/* Main Clinical & Chat Workspace */}
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: '#fcfcfc' }}>
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
                
                {/* Tabs selection */}
                <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                  <button 
                    onClick={() => setActiveTab('chat')} 
                    className={`btn btn--sm ${activeTab === 'chat' ? 'btn--primary' : 'btn--ghost'}`}
                  >
                    💬 Chat
                  </button>
                  <button 
                    onClick={() => setActiveTab('clinical')} 
                    className={`btn btn--sm ${activeTab === 'clinical' ? 'btn--primary' : 'btn--ghost'}`}
                  >
                    📋 Historia Clínica
                  </button>
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

            {/* Content Switch */}
            {activeTab === 'chat' ? (
              <>
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
                      const isMe = msg.remitenteId !== selectedPatient.id
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
              </>
            ) : (
              /* Clinical History Tab */
              <div style={{ flex: 1, padding: 'var(--space-6)', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
                
                {/* Section: Seguimiento Diario */}
                <div className="card" style={{ padding: 'var(--space-5)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)' }}>
                    <div>
                      <h4 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', margin: 0 }}>Seguimiento Clínico Diario</h4>
                      <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>Historial de estados y evolución del paciente</p>
                    </div>
                    <button 
                      onClick={() => setShowAddTracking(!showAddTracking)} 
                      className="btn btn--secondary btn--sm"
                    >
                      {showAddTracking ? 'Cerrar' : '+ Cargar Seguimiento'}
                    </button>
                  </div>

                  {showAddTracking && (
                    <form onSubmit={handleAddTracking} style={{
                      backgroundColor: 'var(--neutral-50)',
                      padding: 'var(--space-4)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--color-border)',
                      marginBottom: 'var(--space-4)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 'var(--space-3)'
                    }}>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label form-label--required">Estado de Animo</label>
                          <select 
                            value={newMood} 
                            onChange={(e) => setNewMood(e.target.value)}
                            className="form-input"
                          >
                            <option value="Excelente">Excelente 😀</option>
                            <option value="Bueno">Bueno 🙂</option>
                            <option value="Regular">Regular 😐</option>
                            <option value="Malo">Malo 🙁</option>
                          </select>
                        </div>
                        <div className="form-group">
                          <label className="form-label">Síntomas presentados</label>
                          <input 
                            type="text" 
                            placeholder="Ej: Ansiedad leve, insomnio..." 
                            value={newSymptoms}
                            onChange={(e) => setNewSymptoms(e.target.value)}
                            className="form-input"
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <label className="form-label">Notas Clínicas / Evolución</label>
                        <textarea 
                          rows={3} 
                          placeholder="Observaciones de la sesión o notas del paciente..." 
                          value={newNotes}
                          onChange={(e) => setNewNotes(e.target.value)}
                          className="form-input"
                          style={{ resize: 'vertical' }}
                        />
                      </div>
                      <button type="submit" className="btn btn--primary btn--sm" style={{ alignSelf: 'flex-end' }}>
                        Guardar Entrada
                      </button>
                    </form>
                  )}

                  {loadingTrackings ? (
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Cargando seguimiento diario...</p>
                  ) : trackings.length === 0 ? (
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', padding: 'var(--space-2)' }}>No hay seguimientos registrados aún.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                      {trackings.map((t) => (
                        <div key={t.id} style={{
                          padding: 'var(--space-3) var(--space-4)',
                          borderLeft: '4px solid var(--color-primary)',
                          backgroundColor: 'var(--neutral-0)',
                          borderRadius: '0 var(--radius-md) var(--radius-md) 0',
                          boxShadow: 'var(--shadow-sm)',
                          fontSize: 'var(--text-xs)',
                          border: '1px solid var(--color-border)',
                          borderLeftWidth: '4px'
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-1)' }}>
                            <span style={{ fontWeight: 'bold' }}>Animo: {t.estadoAnimo}</span>
                            <span style={{ color: 'var(--color-text-secondary)' }}>{t.fecha}</span>
                          </div>
                          {t.sintomas && <div style={{ color: 'var(--color-primary)', marginBottom: 'var(--space-1)' }}><strong>Síntomas:</strong> {t.sintomas}</div>}
                          {t.notas && <div style={{ color: 'var(--color-text-secondary)' }}><strong>Notas:</strong> {t.notas}</div>}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Section: Informes Clínicos */}
                <div className="card" style={{ padding: 'var(--space-5)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)' }}>
                    <div>
                      <h4 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', margin: 0 }}>Informes Clínicos</h4>
                      <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>Gestión, creación y carga de informes oficiales</p>
                    </div>
                    <button 
                      onClick={() => setShowAddReport(!showAddReport)} 
                      className="btn btn--secondary btn--sm"
                    >
                      {showAddReport ? 'Cerrar' : '+ Generar Informe'}
                    </button>
                  </div>

                  {showAddReport && (
                    <form onSubmit={handleAddReport} style={{
                      backgroundColor: 'var(--neutral-50)',
                      padding: 'var(--space-4)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--color-border)',
                      marginBottom: 'var(--space-4)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 'var(--space-3)'
                    }}>
                      <div style={{
                        padding: 'var(--space-2) var(--space-3)',
                        backgroundColor: '#eff6ff',
                        color: '#1e40af',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: 'var(--text-xs)',
                        border: '1px solid #bfdbfe'
                      }}>
                        ℹ️ Se autocompletarán los datos cargados del paciente: 
                        <strong> DNI: {selectedPatient.dni || 'No especificado'}</strong>, 
                        <strong> Obra Social: {selectedPatient.obraSocial || 'Particular (Sin Obra Social)'}</strong>
                        {selectedPatient.obraSocial && <span>, <strong> Afiliado: {selectedPatient.numAfiliado || 'No especificado'}</strong></span>}.
                      </div>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label form-label--required">Tipo de Informe</label>
                          <select 
                            value={reportType} 
                            onChange={(e) => setReportType(e.target.value)}
                            className="form-input"
                          >
                            <option value="Evaluativo">Evaluativo</option>
                            <option value="Evolutivo">Evolutivo</option>
                            <option value="General">General</option>
                            <option value="Final">Final</option>
                          </select>
                        </div>
                        <div className="form-group">
                          <label className="form-label">Método de Informe</label>
                          <div style={{ display: 'flex', gap: 'var(--space-4)', height: '40px', alignItems: 'center' }}>
                            <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-xs)' }}>
                              <input type="radio" checked={reportMode === 'write'} onChange={() => setReportMode('write')} /> Escribir texto
                            </label>
                            <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-xs)' }}>
                              <input type="radio" checked={reportMode === 'upload'} onChange={() => setReportMode('upload')} /> Cargar archivo
                            </label>
                          </div>
                        </div>
                      </div>

                      <div className="form-group">
                        <label className="form-label form-label--required">Plan de Trabajo / Tratamiento</label>
                        <textarea 
                          rows={2} 
                          placeholder="Especificar el plan de trabajo terapéutico..." 
                          value={reportPlan}
                          onChange={(e) => setReportPlan(e.target.value)}
                          className="form-input"
                          required
                        />
                      </div>

                      {reportMode === 'write' ? (
                        <div className="form-group">
                          <label className="form-label">Contenido del Informe</label>
                          <textarea 
                            rows={4} 
                            placeholder="Detalle clínico y evolución del informe..." 
                            value={reportContent}
                            onChange={(e) => setReportContent(e.target.value)}
                            className="form-input"
                            style={{ resize: 'vertical' }}
                          />
                        </div>
                      ) : (
                        <div className="form-group">
                          <label className="form-label">Subir Archivo (.pdf, .doc)</label>
                          <input 
                            type="text" 
                            placeholder="Ej: informe_clinico_final.pdf (Simulado)" 
                            value={uploadFilename}
                            onChange={(e) => setUploadFilename(e.target.value)}
                            className="form-input"
                          />
                          <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                            Ingrese el nombre del archivo para simular la carga en el servidor.
                          </span>
                        </div>
                      )}

                      <button type="submit" className="btn btn--primary btn--sm" style={{ alignSelf: 'flex-end' }}>
                        Emitir Informe
                      </button>
                    </form>
                  )}

                  {loadingReports ? (
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>Cargando informes...</p>
                  ) : reports.length === 0 ? (
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', padding: 'var(--space-2)' }}>No se encontraron informes emitidos.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                      {reports.map((r) => (
                        <div key={r.id} style={{
                          padding: 'var(--space-4)',
                          backgroundColor: 'var(--neutral-0)',
                          borderRadius: 'var(--radius-md)',
                          boxShadow: 'var(--shadow-sm)',
                          fontSize: 'var(--text-xs)',
                          border: '1px solid var(--color-border)'
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-2)', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-1)' }}>
                            <span style={{ fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--green-700)' }}>
                              Informe {r.tipoInforme}
                            </span>
                            <span style={{ color: 'var(--color-text-secondary)' }}>{r.fecha}</span>
                          </div>
                          
                          <div style={{ marginBottom: 'var(--space-2)' }}>
                            <strong>Plan de Trabajo:</strong>
                            <p style={{ margin: 'var(--space-1) 0', color: 'var(--color-text-primary)' }}>{r.planTrabajo}</p>
                          </div>

                          {r.contenido && (
                            <div style={{ marginBottom: 'var(--space-2)' }}>
                              <strong>Detalle Clínico:</strong>
                              <p style={{ margin: 'var(--space-1) 0', color: 'var(--color-text-secondary)' }}>{r.contenido}</p>
                            </div>
                          )}

                          {r.nombreArchivo && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', color: 'var(--color-primary)', fontWeight: 'bold' }}>
                              <span>📎 Documento adjunto:</span>
                              <span style={{ textDecoration: 'underline', cursor: 'pointer' }}>{r.nombreArchivo}</span>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

              </div>
            )}
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
              Historia Clínica de Pacientes
            </h3>
            <p style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-1)' }}>
              Seleccioná un paciente del directorio para ver su ficha médica, chatear o generar informes clínicos.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
