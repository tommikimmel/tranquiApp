import { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { downloadReportPDF } from '../utils/pdfGenerator'
import { type Patient, usePatients } from '../hooks/usePatients'
import PatientSearchBar from './PatientSearchBar'

interface TrackingEntry {
  id: number
  fecha: string
  estadoAnimo: string
  sintomas: string
  notas: string
  nombreMedico?: string
}

interface ClinicalReport {
  id: number
  fecha: string
  tipoInforme: string
  planTrabajo: string
  contenido: string
  nombreArchivo: string
  nombreMedico?: string
}

// A custom select component for Mood with SVGs
function MoodSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [isOpen, setIsOpen] = useState(false);
  const options = [
    { value: 'Excelente', label: 'Excelente', emoji: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 18, height: 18, color: '#10b981' }}>
        <circle cx="12" cy="12" r="10" />
        <path d="M8 14s1.5 2 4 2 4-2 4-2" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </svg>
    )},
    { value: 'Bueno', label: 'Bueno', emoji: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 18, height: 18, color: '#3b82f6' }}>
        <circle cx="12" cy="12" r="10" />
        <path d="M8 15s1.5 1 4 1 4-1 4-1" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </svg>
    )},
    { value: 'Regular', label: 'Regular', emoji: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 18, height: 18, color: '#ca8a04' }}>
        <circle cx="12" cy="12" r="10" />
        <line x1="8" y1="15" x2="16" y2="15" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </svg>
    )},
    { value: 'Malo', label: 'Malo', emoji: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 18, height: 18, color: '#ef4444' }}>
        <circle cx="12" cy="12" r="10" />
        <path d="M16 16s-1.5-2-4-2-4 2-4 2" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </svg>
    )}
  ];
  const selected = options.find(o => o.value === value) || options[1];

  return (
    <div style={{ position: 'relative', width: '100%' }}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="form-input"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          justifyContent: 'space-between',
          textAlign: 'left',
          cursor: 'pointer',
          backgroundColor: '#ffffff',
          width: '100%',
          height: '38px',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)'
        }}
      >
        <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {selected.emoji}
          <span>{selected.label}</span>
        </span>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 14, height: 14 }}>
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      {isOpen && (
        <div style={{
          position: 'absolute',
          top: '42px',
          left: 0,
          right: 0,
          backgroundColor: '#ffffff',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)',
          boxShadow: 'var(--shadow-md)',
          zIndex: 100,
          overflow: 'hidden'
        }}>
          {options.map(o => (
            <div
              key={o.value}
              onClick={() => {
                onChange(o.value);
                setIsOpen(false);
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: 'var(--space-2) var(--space-3)',
                cursor: 'pointer',
                backgroundColor: value === o.value ? 'var(--neutral-100)' : 'transparent',
                transition: 'background-color 0.2s'
              }}
              onMouseEnter={(e) => {
                if (value !== o.value) e.currentTarget.style.backgroundColor = 'var(--neutral-50)';
              }}
              onMouseLeave={(e) => {
                if (value !== o.value) e.currentTarget.style.backgroundColor = 'transparent';
              }}
            >
              {o.emoji}
              <span style={{ fontSize: 'var(--text-sm)' }}>{o.label}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function ClinicalHistoryView() {
  const { showAlert } = useAlert()
  const location = useLocation()
  const navigate = useNavigate()
  const { patients, loadingPatients, searchQuery, setSearchQuery, filteredPatients, setPatients } = usePatients()
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [activeTab, setActiveTab] = useState<'ficha' | 'seguimiento' | 'informes'>('ficha')

  // Deep-link from PatientsView's "Historia Clínica" button: navigate(..., { state: { patientId } })
  // pre-selects that patient once the roster has loaded, then clears the nav state so it doesn't
  // re-trigger on later visits (e.g. via the sidebar) or override a manual search.
  useEffect(() => {
    const targetId = (location.state as { patientId?: number } | null)?.patientId
    if (targetId == null || loadingPatients) return
    const found = patients.find((p) => p.id === targetId)
    if (found) setSelectedPatient(found)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, patients, loadingPatients, navigate])

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

  // Editing & Deleting states
  const [editingReportId, setEditingReportId] = useState<number | null>(null)
  const [editReportType, setEditReportType] = useState('')
  const [editReportPlan, setEditReportPlan] = useState('')
  const [editReportContent, setEditReportContent] = useState('')
  const [confirmModalConfig, setConfirmModalConfig] = useState<{
    isOpen: boolean
    title: string
    message: string
    onConfirm: () => void
  }>({
    isOpen: false,
    title: '',
    message: '',
    onConfirm: () => {}
  })

  // Edit Patient Details states
  const [isEditingPatient, setIsEditingPatient] = useState(false)
  const [editDni, setEditDni] = useState('')
  const [editObraSocial, setEditObraSocial] = useState('')
  const [editNumAfiliado, setEditNumAfiliado] = useState('')
  const [editDireccion, setEditDireccion] = useState('')
  const [editTelefono, setEditTelefono] = useState('')

  const [editNombre, setEditNombre] = useState('')
  const [editApellido, setEditApellido] = useState('')
  const [editSexo, setEditSexo] = useState('M')
  const [editFechaNacimiento, setEditFechaNacimiento] = useState('')
  const [editCuil, setEditCuil] = useState('')
  const [editTipoDocumento, setEditTipoDocumento] = useState('DN')
  const [editNumeroDocumento, setEditNumeroDocumento] = useState('')
  const [editDatosOfuscado, setEditDatosOfuscado] = useState('N')
  const [editCredCodEntidad, setEditCredCodEntidad] = useState('')
  const [editCredPan, setEditCredPan] = useState('')
  const [editCredPlan, setEditCredPlan] = useState('')
  const [editCredToken, setEditCredToken] = useState('')
  const [hasObraSocial, setHasObraSocial] = useState(false)

  const [savingPatientDetails, setSavingPatientDetails] = useState(false)

  useEffect(() => {
    if (selectedPatient) {
      setEditDni(selectedPatient.dni || '')
      setEditObraSocial(selectedPatient.obraSocial || '')
      setEditNumAfiliado(selectedPatient.numAfiliado || '')
      setEditDireccion(selectedPatient.direccion || '')
      setEditTelefono(selectedPatient.telefono || '')

      setEditNombre(selectedPatient.nombre || '')
      setEditApellido(selectedPatient.apellido || '')
      setEditSexo(selectedPatient.sexo || 'M')
      setEditFechaNacimiento(selectedPatient.fechaNacimiento || '')
      setEditCuil(selectedPatient.cuil ? String(selectedPatient.cuil) : '')
      setEditTipoDocumento(selectedPatient.tipoDocumento || 'DN')
      setEditNumeroDocumento(selectedPatient.numeroDocumento ? String(selectedPatient.numeroDocumento) : '')
      setEditDatosOfuscado(selectedPatient.datosOfuscado || 'N')
      setEditCredCodEntidad(selectedPatient.credencial?.codEntidad ? String(selectedPatient.credencial.codEntidad) : '')
      setEditCredPan(selectedPatient.credencial?.pan || '')
      setEditCredPlan(selectedPatient.credencial?.plan || '')
      setEditCredToken(selectedPatient.credencial?.token || '')

      setHasObraSocial(!!(selectedPatient.obraSocial || selectedPatient.credencial?.pan || selectedPatient.credencial?.plan || selectedPatient.credencial?.codEntidad))

      setIsEditingPatient(false)
    }
  }, [selectedPatient])

  const handleSavePatientDetails = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedPatient) return
    setSavingPatientDetails(true)
    try {
      const updatedData = {
        nombre: editNombre,
        apellido: editApellido,
        dni: editDni,
        obraSocial: hasObraSocial ? editObraSocial : '',
        numAfiliado: hasObraSocial ? editNumAfiliado : '',
        direccion: editDireccion,
        telefono: editTelefono,
        sexo: editSexo,
        fechaNacimiento: editFechaNacimiento,
        cuil: editCuil ? Number(editCuil) : null,
        tipoDocumento: editTipoDocumento,
        numeroDocumento: editNumeroDocumento ? Number(editNumeroDocumento) : null,
        datosOfuscado: editDatosOfuscado,
        credencial: hasObraSocial ? {
          codEntidad: editCredCodEntidad ? Number(editCredCodEntidad) : null,
          pan: editCredPan,
          plan: editCredPlan,
          token: editCredToken
        } : undefined
      }

      await api.actualizarPaciente(selectedPatient.id, updatedData)

      // Update selectedPatient local state
      setSelectedPatient((prev: any) => {
        if (!prev) return null;
        return {
          ...prev,
          ...updatedData
        }
      })
      // Update in patients list as well
      setPatients(prevList => prevList.map(p => {
        if (p.id === selectedPatient.id) {
          return {
            ...p,
            ...updatedData
          }
        }
        return p
      }))
      setIsEditingPatient(false)
      showAlert("Datos del paciente actualizados correctamente ✓", "success")
    } catch (err) {
      console.error("Error al actualizar datos del paciente:", err)
      showAlert("Error al actualizar datos del paciente", "error")
    } finally {
      setSavingPatientDetails(false)
    }
  }

  // Load patient clinical details when selected
  useEffect(() => {
    if (selectedPatient) {
      loadClinicalDetails(selectedPatient.id)
      setActiveTab('ficha')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPatient?.id])

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
        showAlert("Seguimiento diario registrado con éxito ✓", "success")
      })
      .catch((err) => {
        console.error("Error al guardar seguimiento:", err)
        showAlert("No se pudo registrar el seguimiento", "error")
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
        showAlert("Informe clínico creado con éxito ✓", "success")
      })
      .catch((err) => {
        console.error("Error al crear informe:", err)
        showAlert("No se pudo guardar el informe", "error")
      })
  }

  const handleStartEditReport = (r: any) => {
    setEditingReportId(r.id)
    setEditReportType(r.tipoInforme)
    setEditReportPlan(r.planTrabajo || '')
    setEditReportContent(r.contenido || '')
  }

  const handleSaveEditReport = (e: React.FormEvent, reportId: number) => {
    e.preventDefault()
    if (!selectedPatient) return

    const data = {
      tipoInforme: editReportType,
      planTrabajo: editReportPlan,
      contenido: editReportContent
    }

    api.editarInforme(selectedPatient.id, reportId, data)
      .then((res: any) => {
        setReports(reports.map(rep => rep.id === reportId ? res : rep))
        setEditingReportId(null)
        showAlert("Informe editado con éxito ✓", "success")
      })
      .catch((err) => {
        console.error("Error al editar informe:", err)
        showAlert("No se pudo guardar los cambios del informe", "error")
      })
  }

  const handleDeleteReport = (reportId: number) => {
    if (!selectedPatient) return
    setConfirmModalConfig({
      isOpen: true,
      title: '¿Eliminar Informe Clínico?',
      message: 'Esta acción no se puede deshacer y el informe clínico se eliminará de forma permanente.',
      onConfirm: () => {
        api.eliminarInforme(selectedPatient.id, reportId)
          .then(() => {
            setReports(reports.filter(rep => rep.id !== reportId))
            showAlert("Informe clínico eliminado ✓", "success")
          })
          .catch((err) => {
            console.error("Error al eliminar informe:", err)
            showAlert("No se pudo eliminar el informe", "error")
          })
          .finally(() => {
            setConfirmModalConfig(prev => ({ ...prev, isOpen: false }))
          })
      }
    })
  }

  const handleDeleteTracking = (trackingId: number) => {
    if (!selectedPatient) return
    setConfirmModalConfig({
      isOpen: true,
      title: '¿Eliminar Entrada de Seguimiento?',
      message: 'Esta acción no se puede deshacer y el registro de seguimiento se eliminará de forma permanente.',
      onConfirm: () => {
        api.eliminarSeguimiento(selectedPatient.id, trackingId)
          .then(() => {
            setTrackings(trackings.filter(t => t.id !== trackingId))
            showAlert("Seguimiento diario eliminado ✓", "success")
          })
          .catch((err) => {
            console.error("Error al eliminar seguimiento:", err)
            showAlert("No se pudo eliminar el seguimiento", "error")
          })
          .finally(() => {
            setConfirmModalConfig(prev => ({ ...prev, isOpen: false }))
          })
      }
    })
  }

  const tabs: { key: 'ficha' | 'seguimiento' | 'informes'; label: string }[] = [
    { key: 'ficha', label: 'Ficha de Datos del Paciente' },
    { key: 'seguimiento', label: 'Seguimiento Diario' },
    { key: 'informes', label: 'Informes Clínicos' },
  ]

  return (
    <div className="card" style={{
      padding: 0,
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      minHeight: 0,
      overflow: 'hidden'
    }}>
      {selectedPatient ? (
        <>
          {/* Header: Patient Bio Details + persistent compact search */}
          <div style={{
            padding: 'var(--space-4) var(--space-6)',
            borderBottom: '1px solid var(--color-border)',
            backgroundColor: 'var(--color-surface)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 'var(--space-4)',
            flexWrap: 'wrap'
          }}>
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
            <PatientSearchBar
              compact
              placeholder="Buscar otro paciente..."
              patients={patients}
              filteredPatients={filteredPatients}
              loadingPatients={loadingPatients}
              searchQuery={searchQuery}
              onSearchQueryChange={setSearchQuery}
              selectedPatient={selectedPatient}
              onSelectPatient={setSelectedPatient}
            />
          </div>

          {/* Tab switcher */}
          <div style={{
            display: 'flex',
            gap: 'var(--space-1)',
            padding: '0 var(--space-6)',
            borderBottom: '1px solid var(--color-border)',
            backgroundColor: 'var(--color-surface)'
          }}>
            {tabs.map(tab => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                style={{
                  padding: 'var(--space-3) var(--space-4)',
                  border: 'none',
                  borderBottom: activeTab === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
                  backgroundColor: 'transparent',
                  color: activeTab === tab.key ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                  fontWeight: activeTab === tab.key ? 'var(--font-weight-bold)' : 'var(--font-weight-medium)',
                  fontSize: 'var(--text-sm)',
                  cursor: 'pointer',
                  transition: 'color 0.15s, border-color 0.15s'
                }}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Clinical History Content: one tab at a time */}
          <div style={{ flex: 1, padding: 'var(--space-6)', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-6)', backgroundColor: '#fcfcfc' }}>

            {activeTab === 'ficha' && (
              /* Section: Ficha de Datos del Paciente (Editable) */
              <div className="card" style={{ padding: 'var(--space-5)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: isEditingPatient ? 'var(--space-4)' : 'var(--space-2)' }}>
                  <div>
                    <h4 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-bold)', margin: 0 }}>Ficha de Datos del Paciente</h4>
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>Consultá y editá la información administrativa y de cobertura</p>
                  </div>
                  {!isEditingPatient && (
                    <button
                      onClick={() => setIsEditingPatient(true)}
                      className="btn btn--secondary btn--sm"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 12, height: 12, marginRight: 4, display: 'inline-block', verticalAlign: 'middle' }}><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>
                      Editar Datos
                    </button>
                  )}
                </div>

                {isEditingPatient ? (
                  <form onSubmit={handleSavePatientDetails} style={{
                    backgroundColor: 'var(--neutral-50)',
                    padding: 'var(--space-5)',
                    borderRadius: 'var(--radius-lg)',
                    border: '1px solid var(--color-border)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 'var(--space-4)'
                  }}>
                    {/* Section: Demográficos */}
                    <div>
                      <div style={{ fontWeight: 'bold', fontSize: 'var(--text-xs)', color: 'var(--color-primary)', textTransform: 'uppercase', marginBottom: 'var(--space-2)' }}>
                        1. Datos Demográficos Básicos
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label">Nombre</label>
                          <input
                            type="text"
                            value={editNombre}
                            onChange={(e) => setEditNombre(e.target.value)}
                            className="form-input"
                            required
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">Apellido</label>
                          <input
                            type="text"
                            value={editApellido}
                            onChange={(e) => setEditApellido(e.target.value)}
                            className="form-input"
                            required
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">Sexo</label>
                          <select value={editSexo} onChange={(e) => setEditSexo(e.target.value)} className="form-input">
                            <option value="M">Masculino (M)</option>
                            <option value="F">Femenino (F)</option>
                            <option value="Otro">Otro</option>
                          </select>
                        </div>
                        <div className="form-group">
                          <label className="form-label">Fecha de Nacimiento</label>
                          <input
                            type="date"
                            value={editFechaNacimiento}
                            onChange={(e) => setEditFechaNacimiento(e.target.value)}
                            className="form-input"
                          />
                        </div>
                      </div>
                    </div>

                    {/* Section: Documentación */}
                    <div>
                      <div style={{ fontWeight: 'bold', fontSize: 'var(--text-xs)', color: 'var(--color-primary)', textTransform: 'uppercase', marginBottom: 'var(--space-2)' }}>
                        2. Documentación e Identificación
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label">Tipo Documento</label>
                          <select value={editTipoDocumento} onChange={(e) => setEditTipoDocumento(e.target.value)} className="form-input">
                            <option value="DN">DNI (DN)</option>
                            <option value="LE">LE</option>
                            <option value="LC">LC</option>
                          </select>
                        </div>
                        <div className="form-group">
                          <label className="form-label">Nro. Documento</label>
                          <input
                            type="number"
                            value={editNumeroDocumento}
                            onChange={(e) => setEditNumeroDocumento(e.target.value)}
                            className="form-input"
                            placeholder="Ej. 12345678"
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">CUIL</label>
                          <input
                            type="number"
                            value={editCuil}
                            onChange={(e) => setEditCuil(e.target.value)}
                            className="form-input"
                            placeholder="Ej. 27123456780"
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">Ofuscar Datos Sensibles</label>
                          <select value={editDatosOfuscado} onChange={(e) => setEditDatosOfuscado(e.target.value)} className="form-input">
                            <option value="N">No (Ofrecido públicamente)</option>
                            <option value="S">Sí (Ocultar datos de salud)</option>
                          </select>
                        </div>
                      </div>
                    </div>

                    {/* Section: Contacto */}
                    <div>
                      <div style={{ fontWeight: 'bold', fontSize: 'var(--text-xs)', color: 'var(--color-primary)', textTransform: 'uppercase', marginBottom: 'var(--space-2)' }}>
                        3. Ubicación y Contacto
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-3)' }}>
                        <div className="form-group">
                          <label className="form-label">Dirección</label>
                          <input
                            type="text"
                            value={editDireccion}
                            onChange={(e) => setEditDireccion(e.target.value)}
                            className="form-input"
                            placeholder="Calle, Número, Localidad"
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">Teléfono</label>
                          <input
                            type="text"
                            value={editTelefono}
                            onChange={(e) => setEditTelefono(e.target.value)}
                            className="form-input"
                            placeholder="Contacto del paciente"
                          />
                        </div>
                      </div>
                    </div>

                    {/* Section: Credencial */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                      <div className="form-group" style={{ marginBottom: 'var(--space-1)' }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', cursor: 'pointer', fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                          <input
                            type="checkbox"
                            checked={hasObraSocial}
                            onChange={(e) => setHasObraSocial(e.target.checked)}
                            style={{ width: '15px', height: '15px', accentColor: 'var(--color-primary)' }}
                          />
                          <span>EL PACIENTE POSEE OBRA SOCIAL / COBERTURA</span>
                        </label>
                      </div>

                      {hasObraSocial && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                          <div style={{ fontWeight: 'bold', fontSize: 'var(--text-xs)', color: 'var(--color-primary)', textTransform: 'uppercase', marginBottom: 'var(--space-1)' }}>
                            4. Cobertura y Obra Social
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 'var(--space-3)' }}>
                            <div className="form-group">
                              <label className="form-label">Obra Social</label>
                              <input
                                type="text"
                                value={editObraSocial}
                                onChange={(e) => setEditObraSocial(e.target.value)}
                                className="form-input"
                                placeholder="Ej. OSDE"
                              />
                            </div>
                            <div className="form-group">
                              <label className="form-label">Plan</label>
                              <input
                                type="text"
                                value={editCredPlan}
                                onChange={(e) => setEditCredPlan(e.target.value)}
                                className="form-input"
                                placeholder="Ej. 410"
                              />
                            </div>
                            <div className="form-group">
                              <label className="form-label">Cod. Entidad</label>
                              <input
                                type="number"
                                value={editCredCodEntidad}
                                onChange={(e) => setEditCredCodEntidad(e.target.value)}
                                className="form-input"
                                placeholder="Ej. 7100"
                              />
                            </div>
                          </div>

                          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
                            <div className="form-group">
                              <label className="form-label">Nro. Credencial / Afiliado</label>
                              <input
                                type="text"
                                value={editCredPan}
                                onChange={(e) => setEditCredPan(e.target.value)}
                                className="form-input"
                                placeholder="Número impreso en la credencial"
                              />
                            </div>
                            <div className="form-group">
                              <label className="form-label">Token de Seguridad</label>
                              <input
                                type="text"
                                value={editCredToken}
                                onChange={(e) => setEditCredToken(e.target.value)}
                                className="form-input"
                                placeholder="Código temporal"
                              />
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    <div style={{ display: 'flex', gap: 'var(--space-2)', alignSelf: 'flex-end', marginTop: 'var(--space-2)' }}>
                      <button
                        type="button"
                        onClick={() => {
                          setIsEditingPatient(false);
                          setEditDni(selectedPatient.dni || '');
                          setEditObraSocial(selectedPatient.obraSocial || '');
                          setEditNumAfiliado(selectedPatient.numAfiliado || '');
                          setEditDireccion(selectedPatient.direccion || '');
                          setEditTelefono(selectedPatient.telefono || '');
                          setEditNombre(selectedPatient.nombre || '');
                          setEditApellido(selectedPatient.apellido || '');
                          setEditSexo(selectedPatient.sexo || 'M');
                          setEditFechaNacimiento(selectedPatient.fechaNacimiento || '');
                          setEditCuil(selectedPatient.cuil ? String(selectedPatient.cuil) : '');
                          setEditTipoDocumento(selectedPatient.tipoDocumento || 'DN');
                          setEditNumeroDocumento(selectedPatient.numeroDocumento ? String(selectedPatient.numeroDocumento) : '');
                          setEditDatosOfuscado(selectedPatient.datosOfuscado || 'N');
                          setEditCredCodEntidad(selectedPatient.credencial?.codEntidad ? String(selectedPatient.credencial.codEntidad) : '');
                          setEditCredPan(selectedPatient.credencial?.pan || '');
                          setEditCredPlan(selectedPatient.credencial?.plan || '');
                          setEditCredToken(selectedPatient.credencial?.token || '');
                        }}
                        className="btn btn--secondary btn--sm"
                      >
                        Cancelar
                      </button>
                      <button
                        type="submit"
                        disabled={savingPatientDetails}
                        className="btn btn--primary btn--sm"
                      >
                        {savingPatientDetails ? 'Guardando...' : '✓ Guardar Cambios'}
                      </button>
                    </div>
                  </form>
                ) : (
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(4, 1fr)',
                    gap: 'var(--space-4)',
                    backgroundColor: 'var(--neutral-50)',
                    padding: 'var(--space-5)',
                    borderRadius: 'var(--radius-lg)',
                    border: '1px solid var(--color-border)',
                    fontSize: 'var(--text-xs)',
                    textAlign: 'left'
                  }}>
                    <div style={{ gridColumn: 'span 4', fontWeight: 'bold', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px', marginBottom: '2px' }}>
                      Ficha Técnica del Afiliado
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Nombre Completo</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                        {selectedPatient.nombre} {selectedPatient.apellido || ''}
                      </div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Sexo</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                        {selectedPatient.sexo === 'M' ? 'Masculino (M)' : selectedPatient.sexo === 'F' ? 'Femenino (F)' : selectedPatient.sexo || 'No especificado'}
                      </div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Fecha de Nacimiento</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>{selectedPatient.fechaNacimiento || 'No cargada'}</div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>CUIL</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>{selectedPatient.cuil || 'No cargado'}</div>
                    </div>

                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Documentación</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                        {selectedPatient.tipoDocumento || 'DNI'} {selectedPatient.numeroDocumento || selectedPatient.dni || 'No cargado'}
                      </div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Obra Social & Plan</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                        {selectedPatient.obraSocial || 'Particular'} {selectedPatient.credencial?.plan ? `Plan ${selectedPatient.credencial.plan}` : ''}
                      </div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>PAN (N° Credencial)</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>{selectedPatient.credencial?.pan || selectedPatient.numAfiliado || '-'}</div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Token Digital / Entidad</div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '600', color: 'var(--color-primary)' }}>
                        {selectedPatient.credencial?.token || '-'} {selectedPatient.credencial?.codEntidad ? `(Cod: ${selectedPatient.credencial.codEntidad})` : ''}
                      </div>
                    </div>

                    <div style={{ gridColumn: 'span 2' }}>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Dirección</div>
                      <div style={{ fontSize: 'var(--text-sm)' }}>{selectedPatient.direccion || 'No cargada'}</div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Teléfono</div>
                      <div style={{ fontSize: 'var(--text-sm)' }}>{selectedPatient.telefono || 'Sin teléfono'}</div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--color-text-secondary)', fontWeight: 'bold', marginBottom: '2px' }}>Ofuscar Datos</div>
                      <div style={{ fontSize: 'var(--text-sm)' }}>{selectedPatient.datosOfuscado === 'S' ? 'Sí (Sensible)' : 'No (Público)'}</div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {activeTab === 'seguimiento' && (
              /* Section: Seguimiento Diario */
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
                        <MoodSelect
                          value={newMood}
                          onChange={setNewMood}
                        />
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
                          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
                            <span style={{ color: 'var(--color-text-secondary)' }}>{t.fecha}</span>
                            <button
                              onClick={() => handleDeleteTracking(t.id)}
                              style={{ border: 'none', background: 'none', color: 'var(--color-danger)', cursor: 'pointer', fontWeight: 'bold', fontSize: '11px', padding: '0 2px' }}
                              title="Eliminar Seguimiento"
                            >
                              ✕
                            </button>
                          </div>
                        </div>
                        {t.sintomas && <div style={{ color: 'var(--color-primary)', marginBottom: 'var(--space-1)' }}><strong>Síntomas:</strong> {t.sintomas}</div>}
                        {t.notas && <div style={{ color: 'var(--color-text-secondary)' }}><strong>Notas:</strong> {t.notas}</div>}
                        <div style={{ fontSize: '10px', color: 'var(--color-text-secondary)', marginTop: '4px', borderTop: '1px solid #f1f5f9', paddingTop: '2px' }}>
                          Registrado por: <strong>{t.nombreMedico || 'No especificado'}</strong>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'informes' && (
              /* Section: Informes Clínicos */
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
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 12, height: 12, marginRight: 4, display: 'inline-block', verticalAlign: 'middle', color: '#1e40af' }}><circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" /></svg>
                      Se autocompletarán los datos cargados del paciente:
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
                    {reports.map((r) => {
                      const isEditing = editingReportId === r.id;
                      return (
                        <div key={r.id} style={{
                          padding: 'var(--space-4)',
                          backgroundColor: 'var(--neutral-0)',
                          borderRadius: 'var(--radius-md)',
                          boxShadow: 'var(--shadow-sm)',
                          fontSize: 'var(--text-xs)',
                          border: '1px solid var(--color-border)'
                        }}>
                          {isEditing ? (
                            <form onSubmit={(e) => handleSaveEditReport(e, r.id)} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                              <div style={{ fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', borderBottom: '1px solid var(--color-border)', paddingBottom: '4px' }}>Editar Informe</div>
                              <div className="form-group">
                                <label className="form-label" style={{ fontWeight: 'bold' }}>Tipo de Informe</label>
                                <select value={editReportType} onChange={(e) => setEditReportType(e.target.value)} className="form-input" style={{ width: '100%' }}>
                                  <option value="Evaluativo">Evaluativo</option>
                                  <option value="Evolutivo">Evolutivo</option>
                                  <option value="General">General</option>
                                  <option value="Final">Final</option>
                                </select>
                              </div>
                              <div className="form-group">
                                <label className="form-label" style={{ fontWeight: 'bold' }}>Plan de Trabajo</label>
                                <textarea
                                  value={editReportPlan}
                                  onChange={(e) => setEditReportPlan(e.target.value)}
                                  className="form-input"
                                  rows={3}
                                  style={{ width: '100%', resize: 'vertical' }}
                                  required
                                />
                              </div>
                              <div className="form-group">
                                <label className="form-label" style={{ fontWeight: 'bold' }}>Detalle Clínico</label>
                                <textarea
                                  value={editReportContent}
                                  onChange={(e) => setEditReportContent(e.target.value)}
                                  className="form-input"
                                  rows={5}
                                  style={{ width: '100%', resize: 'vertical' }}
                                  required
                                />
                              </div>
                              <div style={{ display: 'flex', gap: 'var(--space-2)', justifyContent: 'flex-end', marginTop: 'var(--space-1)' }}>
                                <button type="button" onClick={() => setEditingReportId(null)} className="btn btn--secondary btn--sm">
                                  Cancelar
                                </button>
                                <button type="submit" className="btn btn--primary btn--sm">
                                  Guardar Cambios
                                </button>
                              </div>
                            </form>
                          ) : (
                            <>
                              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-2)', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-1)' }}>
                                <div>
                                  <span style={{ fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--green-700)' }}>
                                    Informe {r.tipoInforme}
                                  </span>
                                  <div style={{ fontSize: '10px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                                    Emitido por: <strong>{r.nombreMedico || 'No especificado'}</strong>
                                  </div>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                                  <span style={{ color: 'var(--color-text-secondary)' }}>{r.fecha}</span>
                                  <button
                                    onClick={() => downloadReportPDF(r, selectedPatient)}
                                    className="btn btn--secondary btn--sm"
                                    style={{ padding: '2px 8px', fontSize: '10px', display: 'flex', alignItems: 'center', gap: '4px' }}
                                  >
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 10, height: 10 }}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" /></svg>
                                    PDF
                                  </button>
                                  {!r.nombreArchivo && (
                                    <button
                                      onClick={() => handleStartEditReport(r)}
                                      className="btn btn--secondary btn--sm"
                                      style={{ padding: '2px 8px', fontSize: '10px', display: 'flex', alignItems: 'center', gap: '4px' }}
                                    >
                                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 10, height: 10 }}><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>
                                      Editar
                                    </button>
                                  )}
                                  <button
                                    onClick={() => handleDeleteReport(r.id)}
                                    className="btn btn--ghost btn--sm"
                                    style={{ padding: '2px 8px', fontSize: '10px', color: 'var(--color-danger)', borderColor: 'var(--color-danger)', display: 'flex', alignItems: 'center', gap: '4px' }}
                                  >
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 10, height: 10, color: 'var(--color-danger)' }}><polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /><line x1="10" y1="11" x2="10" y2="17" /><line x1="14" y1="11" x2="14" y2="17" /></svg>
                                    Eliminar
                                  </button>
                                </div>
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
                            </>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

          </div>
        </>
      ) : (
        <div style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          textAlign: 'center',
          color: 'var(--color-text-secondary)',
          padding: 'var(--space-8)',
          backgroundColor: '#fcfcfc'
        }}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 48, height: 48, margin: '0 auto var(--space-4)', opacity: 0.4 }}>
            <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
            <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
          </svg>
          <h3 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
            Historia Clínica de Pacientes
          </h3>
          <p style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-1)', maxWidth: '440px' }}>
            Buscá un paciente por nombre o DNI para ver su ficha médica, seguimiento diario e informes clínicos.
          </p>
          <div style={{ marginTop: 'var(--space-6)', width: '100%', display: 'flex', justifyContent: 'center' }}>
            <PatientSearchBar
              placeholder="Buscar por nombre o DNI..."
              patients={patients}
              filteredPatients={filteredPatients}
              loadingPatients={loadingPatients}
              searchQuery={searchQuery}
              onSearchQueryChange={setSearchQuery}
              selectedPatient={selectedPatient}
              onSelectPatient={setSelectedPatient}
            />
          </div>
        </div>
      )}
      <ConfirmModal
        isOpen={confirmModalConfig.isOpen}
        title={confirmModalConfig.title}
        message={confirmModalConfig.message}
        onConfirm={confirmModalConfig.onConfirm}
        onCancel={() => setConfirmModalConfig(prev => ({ ...prev, isOpen: false }))}
      />
    </div>
  )
}

interface ConfirmModalProps {
  isOpen: boolean
  title: string
  message: string
  onConfirm: () => void
  onCancel: () => void
}

function ConfirmModal({ isOpen, title, message, onConfirm, onCancel }: ConfirmModalProps) {
  if (!isOpen) return null
  return (
    <div style={{
      position: 'fixed',
      top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.5)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 99999,
      animation: 'fadeIn 0.2s ease-out'
    }}>
      <div className="card" style={{
        maxWidth: '400px',
        width: '90%',
        padding: 'var(--space-6)',
        textAlign: 'center',
        boxShadow: 'var(--shadow-lg)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        backgroundColor: 'var(--color-surface)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 'var(--space-4)'
      }}>
        <div style={{
          width: '48px',
          height: '48px',
          borderRadius: '50%',
          backgroundColor: '#fee2e2',
          color: 'var(--color-danger)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 24, height: 24 }}>
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
        </div>
        <h3 style={{
          fontFamily: 'var(--font-heading)',
          fontSize: 'var(--text-lg)',
          fontWeight: 'var(--font-weight-bold)',
          color: 'var(--color-text-primary)',
          margin: 0
        }}>
          {title}
        </h3>
        <p style={{
          fontSize: 'var(--text-sm)',
          color: 'var(--color-text-secondary)',
          lineHeight: 'var(--line-height-relaxed)',
          margin: 0
        }}>
          {message}
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-3)', width: '100%', marginTop: 'var(--space-2)' }}>
          <button
            className="btn btn--secondary"
            onClick={onCancel}
            style={{ flex: 1 }}
          >
            Cancelar
          </button>
          <button
            className="btn btn--primary"
            onClick={onConfirm}
            style={{ flex: 1, backgroundColor: 'var(--color-danger)', borderColor: 'var(--color-danger)' }}
          >
            Eliminar
          </button>
        </div>
      </div>
    </div>
  )
}
