import React, { useState, useEffect, useMemo } from 'react'
import { useLocation } from 'react-router-dom'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { Icon } from './Icon'
import LaboratorioAutocomplete from './LaboratorioAutocomplete'
import EditPatientModal from './EditPatientModal'
import { computeLaboratorioStats } from '../utils/laboratorios'
import { openOfficialPrescriptionPdf } from '../utils/pdfGenerator'
import { getPatientInitials, calcAge, formatDateDDMMYYYY } from '../utils/dashboardHelpers'
import { getMissingPatientFields } from '../hooks/usePatients'

// ── Prescription View ─────────────────────────────────────────
// Quick-pick chips so completar Frecuencia/Duración sea de un click en vez de tipear a mano.
const FRECUENCIAS_RAPIDAS = ['Cada 8hs', 'Cada 12hs', 'Cada 24hs', 'Antes de dormir', 'A demanda']
const DURACIONES_RAPIDAS = ['7 días', '15 días', '30 días', '60 días', '90 días']

const MOCK_PATIENTS = [
  { id: '1', name: 'Mateo Benítez', email: 'mateo.b@gmail.com' },
  { id: '2', name: 'Matías Rodríguez', email: 'matias.r@gmail.com' },
  { id: '3', name: 'Lucía Fernández', email: 'lucia.f@gmail.com' },
  { id: '4', name: 'Santiago Torres', email: 'santiago.t@gmail.com' },
]

const COMMON_MEDS = [
  'Escitalopram 10mg',
  'Sertralina 50mg',
  'Clonazepam 0.5mg',
  'Alprazolam 0.25mg',
  'Quetiapina 25mg',
  'Risperidona 1mg',
  'Melatonina 3mg',
  'Pregabalina 75mg',
]

export default function PrescriptionView({ onSend, medicoInfo }: { onSend: (data: any) => Promise<void>; medicoInfo?: any }) {
  const { showAlert } = useAlert();
  const location = useLocation();
  const [activeTab, setActiveTab] = useState<'new' | 'history'>('new');

  const [patients, setPatients] = useState<any[]>([]);
  const [patientSearch, setPatientSearch] = useState('');
  const [selectedPatientObj, setSelectedPatientObj] = useState<any | null>(null);
  const [showPatientDropdown, setShowPatientDropdown] = useState(false);
  const [showEditPatientModal, setShowEditPatientModal] = useState(false);

  const [medications, setMedications] = useState<Array<{ name: string; dosage: string; frequency: string; duration: string; regNo?: string; nombreDroga?: string; noSustituible?: boolean; laboratorio?: string }>>([]);
  const [medSearchInput, setMedSearchInput] = useState('');
  const [showMedDropdown, setShowMedDropdown] = useState(false);
  const [medSearchResults, setMedSearchResults] = useState<any[]>([]);
  const [medSearching, setMedSearching] = useState(false);

  // Real QBI2 medicamento catalog search (debounced) — replaces the old hardcoded local list so
  // regNo (ANMAT registration number) comes from a real product instead of a made-up fallback.
  useEffect(() => {
    const query = medSearchInput.trim();
    if (query.length < 2) {
      setMedSearchResults([]);
      setMedSearching(false);
      return;
    }
    setMedSearching(true);
    const handle = setTimeout(() => {
      api.buscarMedicamentos(query, 1)
        .then((res: any) => setMedSearchResults(Array.isArray(res?.medicamentos) ? res.medicamentos : []))
        .catch((err: any) => {
          console.error("Error al buscar medicamentos en QBI2:", err);
          setMedSearchResults([]);
        })
        .finally(() => setMedSearching(false));
    }, 350);
    return () => clearTimeout(handle);
  }, [medSearchInput]);

  const [diagnosis, setDiagnosis] = useState('');
  const [notes, setNotes] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  const [emittedPrescriptions, setEmittedPrescriptions] = useState<any[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [historySearch, setHistorySearch] = useState('');
  const [selectedDetailModal, setSelectedDetailModal] = useState<any | null>(null);

  const loadHistory = () => {
    setLoadingHistory(true);
    api.getMisRecetas()
      .then((res: any) => {
        setEmittedPrescriptions(Array.isArray(res) ? res : []);
      })
      .catch((err) => console.error("Error al cargar historial de recetas:", err))
      .finally(() => setLoadingHistory(false));
  };

  useEffect(() => {
    api.getPacientesAtendidos()
      .then((data: any) => {
        const list = Array.isArray(data) && data.length > 0 ? data : MOCK_PATIENTS;
        setPatients(list);

        const statePatient = location.state?.patient || location.state?.patientObj;
        const statePatientId = location.state?.patientId;
        const statePatientName = location.state?.patientName;
        const stateAppt = location.state?.appt;

        let target: any = null;

        if (statePatient) {
          const found = list.find((p: any) =>
            (statePatient.id && p.id === statePatient.id) ||
            (statePatient.email && p.email && p.email.toLowerCase() === statePatient.email.toLowerCase()) ||
            (statePatient.dni && p.dni && String(p.dni) === String(statePatient.dni)) ||
            (statePatient.name && p.name && p.name.toLowerCase() === statePatient.name.toLowerCase()) ||
            (statePatient.nombre && p.nombre && `${p.nombre} ${p.apellido || ''}`.trim().toLowerCase() === `${statePatient.nombre} ${statePatient.apellido || ''}`.trim().toLowerCase())
          );
          target = found ? { ...found, ...statePatient } : statePatient;
        } else if (statePatientId) {
          target = list.find((p: any) => p.id === statePatientId);
        } else if (statePatientName) {
          target = list.find((p: any) =>
            (p.name && p.name.toLowerCase() === statePatientName.toLowerCase()) ||
            (`${p.nombre || ''} ${p.apellido || ''}`.trim().toLowerCase() === statePatientName.toLowerCase())
          );
        }

        if (!target && stateAppt) {
          target = {
            id: stateAppt.pacienteId || stateAppt.usuarioId || stateAppt.patientInfo?.id,
            name: stateAppt.patientName,
            nombre: stateAppt.patientInfo?.nombre || stateAppt.patientName,
            apellido: stateAppt.patientInfo?.apellido || '',
            email: stateAppt.patientInfo?.email || stateAppt.patientInfo?.mail,
            telefono: stateAppt.patientInfo?.telefono,
            dni: stateAppt.patientInfo?.dni || stateAppt.patientInfo?.numeroDocumento,
            tipoDocumento: stateAppt.patientInfo?.tipoDocumento || 'DNI',
            fechaNacimiento: stateAppt.patientInfo?.fechaNacimiento,
            obraSocial: stateAppt.patientInfo?.obraSocial,
            numAfiliado: stateAppt.patientInfo?.numAfiliado,
            credencial: stateAppt.patientInfo?.credencial,
            domicilio: stateAppt.patientInfo?.domicilio,
          };
        }

        if (target) {
          setSelectedPatientObj(target);
          setActiveTab('new');
        }
      })
      .catch(() => setPatients(MOCK_PATIENTS));

    loadHistory();
  }, [location.state]);

  const doctorName = medicoInfo ? `${medicoInfo.nombre || ''} ${medicoInfo.apellido || ''}`.trim() : 'Médico';
  const doctorMatricula = medicoInfo?.matricula || medicoInfo?.matriculaNumero ? `MN ${medicoInfo.matricula || medicoInfo.matriculaNumero}` : 'MN S/N';

  const selectedPatientName = selectedPatientObj
    ? (selectedPatientObj.name || `${selectedPatientObj.nombre || ''} ${selectedPatientObj.apellido || ''}`.trim())
    : '';
  const selectedPatientDni = selectedPatientObj?.dni || selectedPatientObj?.numeroDocumento || 'S/D';
  const selectedPatientTipoDoc = selectedPatientObj?.tipoDocumento || 'DNI';
  const selectedPatientAge = calcAge(selectedPatientObj?.fechaNacimiento);
  // Same check RecetaService.emitirReceta runs server-side (see getMissingPatientFields) — run
  // it here too so the médico sees it and can fix it before submitting, not after a failed send.
  const missingPatientFields = selectedPatientObj ? getMissingPatientFields(selectedPatientObj) : [];

  const filteredPatients = patientSearch.trim().length >= 3
    ? patients.filter(p => {
        const q = patientSearch.toLowerCase();
        const name = (p.name || `${p.nombre || ''} ${p.apellido || ''}`).toLowerCase();
        const dni = (p.dni || p.numeroDocumento || '').toString();
        const email = (p.email || '').toLowerCase();
        return name.includes(q) || dni.includes(q) || email.includes(q);
      })
    : [];

  const handleSelectPatient = (patient: any) => {
    setSelectedPatientObj(patient);
    setPatientSearch('');
    setShowPatientDropdown(false);
  };

  const handleClearPatient = () => {
    setSelectedPatientObj(null);
    setPatientSearch('');
  };

  const handleAddMedicationFromCatalog = (item: any) => {
    setMedications(prev => [...prev, {
      name: item.nombreProducto,
      dosage: item.presentacion || '',
      frequency: '',
      duration: '',
      regNo: item.regNo || '',
      nombreDroga: item.nombreDroga || item.nombreProducto,
      noSustituible: false,
      laboratorio: ''
    }]);
    setMedSearchInput('');
    setMedSearchResults([]);
    setShowMedDropdown(false);
  };

  // Free-text fallback for when QBI2's catalog doesn't have the médico's medication — sent
  // without a regNo (see RecetaService, which no longer invents one) rather than blocking them.
  const handleAddCustomMedication = () => {
    const medName = medSearchInput.trim();
    if (!medName) return;
    setMedications(prev => [...prev, { name: medName, dosage: '', frequency: '', duration: '', regNo: '', nombreDroga: medName, noSustituible: false, laboratorio: '' }]);
    setMedSearchInput('');
    setMedSearchResults([]);
    setShowMedDropdown(false);
  };

  const handleMedKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (medSearchResults.length > 0) {
        handleAddMedicationFromCatalog(medSearchResults[0]);
      } else {
        handleAddCustomMedication();
      }
    }
  };

  const updateMedication = (index: number, field: string, value: string | boolean) => {
    const updated = [...medications];
    updated[index] = { ...updated[index], [field]: value };
    setMedications(updated);
  };

  const removeMedication = (index: number) => {
    setMedications(prev => prev.filter((_, i) => i !== index));
  };

  const canSend = selectedPatientObj && missingPatientFields.length === 0 && medications.length > 0 && medications.some(m => m.name.trim().length > 0);

  const handleSend = () => {
    if (!selectedPatientObj) return;
    setSending(true);
    onSend({
      pacienteId: Number(selectedPatientObj.id),
      medications,
      diagnosis,
      notes
    })
    .then(() => {
      setSent(true);
      loadHistory();
    })
    .catch((err) => {
      console.error("Error al emitir receta:", err);
      showAlert(err?.message || "Error al emitir y enviar receta", "error");
    })
    .finally(() => setSending(false));
  };

  const handleReset = () => {
    setSelectedPatientObj(null);
    setPatientSearch('');
    setMedications([]);
    setMedSearchInput('');
    setDiagnosis('');
    setNotes('');
    setSent(false);
  };

  const filteredEmittedPrescriptions = historySearch.trim()
    ? emittedPrescriptions.filter(rx => {
        const q = historySearch.toLowerCase();
        const pName = rx.paciente ? `${rx.paciente.nombre || ''} ${rx.paciente.apellido || ''}`.toLowerCase() : '';
        const pDni = (rx.paciente?.dni || rx.paciente?.numeroDocumento || '').toString();
        const meds = (rx.medicamentos || '').toLowerCase();
        const diag = (rx.diagnostico || '').toLowerCase();
        return pName.includes(q) || pDni.includes(q) || meds.includes(q) || diag.includes(q);
      })
    : emittedPrescriptions;

  const laboratorioStats = useMemo(() => computeLaboratorioStats(emittedPrescriptions), [emittedPrescriptions]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* Banner "Próximamente": la integración oficial con QBI2 todavía no está en producción
          (corre en modo de prueba/homologación) — el plan Clínico ya da acceso a esta sección,
          pero conviene dejar explícito que la validación legal real todavía no está activa. */}
      <div style={{
        display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)', padding: 'var(--space-3) var(--space-4)',
        backgroundColor: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 'var(--radius-md)', color: '#92400E',
      }}>
        <span style={{
          fontSize: '10px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.03em',
          background: '#FEF3C7', border: '1px solid #FDE68A', borderRadius: '999px', padding: '3px 9px', flexShrink: 0,
        }}>
          Próximamente
        </span>
        <span style={{ fontSize: 'var(--text-xs)' }}>
          Estamos terminando la integración oficial con QBI2/Innovamed. Podés probar el flujo completo, pero por ahora corre en modo de homologación — todavía no emite recetas con validez legal.
        </span>
      </div>

      {/* Header & Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 'var(--text-2xl)', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>Recetas Electrónicas</h1>
          <p style={{ margin: '4px 0 0', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
            Emití recetas a tus pacientes y consultá el historial completo de prescripciones realizadas.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)', backgroundColor: 'var(--neutral-100)', padding: '4px', borderRadius: 'var(--radius-md)' }}>
          <button
            className={`btn btn--sm ${activeTab === 'new' ? 'btn--primary' : 'btn--ghost'}`}
            onClick={() => setActiveTab('new')}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
            </svg>
            Nueva Receta
          </button>
          <button
            className={`btn btn--sm ${activeTab === 'history' ? 'btn--primary' : 'btn--ghost'}`}
            onClick={() => { setActiveTab('history'); loadHistory(); }}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
              <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
              <path d="M12 11v4l3 2" />
            </svg>
            Historial Emitidas ({emittedPrescriptions.length})
          </button>
        </div>
      </div>

      {activeTab === 'history' ? (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
            <h2 className="card__title">Historial de Recetas Emitidas</h2>
            <input
              type="text"
              className="form-input"
              style={{ maxWidth: '320px' }}
              placeholder="Buscar por paciente, DNI o medicamento..."
              value={historySearch}
              onChange={(e) => setHistorySearch(e.target.value)}
            />
          </div>

          {!loadingHistory && laboratorioStats.length > 0 && (
            <div style={{
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              padding: 'var(--space-4)',
              backgroundColor: 'var(--neutral-50)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-3)'
            }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semi)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Icon.Newspaper size={16} /> Medicamentos por laboratorio
                </h3>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Cantidad de medicamentos recetados por laboratorio, según lo cargaste al emitir cada receta.
                  Variantes de escritura (con o sin tilde, "b"/"v") se agrupan como el mismo laboratorio.
                </p>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                {laboratorioStats.map((stat) => {
                  const maxCount = laboratorioStats[0].count;
                  const pct = maxCount > 0 ? Math.round((stat.count / maxCount) * 100) : 0;
                  return (
                    <div key={stat.key} className="rx-lab-stat-row" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                      <span className="rx-lab-stat-label" style={{ flex: '0 0 160px', fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {stat.label}
                      </span>
                      <div style={{ flex: 1, height: '8px', backgroundColor: 'var(--neutral-200)', borderRadius: '999px', overflow: 'hidden' }}>
                        <div style={{ width: `${pct}%`, height: '100%', backgroundColor: 'var(--color-primary)', borderRadius: '999px' }} />
                      </div>
                      <span style={{ flex: '0 0 auto', fontSize: 'var(--text-sm)', fontWeight: 'bold', color: 'var(--color-text-primary)', minWidth: '18px', textAlign: 'right' }}>
                        {stat.count}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {loadingHistory ? (
            <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-6)' }}>Cargando historial de recetas...</p>
          ) : filteredEmittedPrescriptions.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              {filteredEmittedPrescriptions.map((rx) => {
                const patientFullName = rx.paciente ? `${rx.paciente.nombre || ''} ${rx.paciente.apellido || ''}`.trim() : 'Paciente';
                const patientDni = rx.paciente?.dni || rx.paciente?.numeroDocumento ? `DNI: ${rx.paciente.dni || rx.paciente.numeroDocumento}` : '';
                return (
                  <div key={rx.id} style={{
                    padding: 'var(--space-4)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    backgroundColor: '#ffffff',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 'var(--space-2)'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
                      <div>
                        <strong style={{ fontSize: 'var(--text-base)', color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                            <circle cx="12" cy="7" r="4" />
                          </svg>
                          {patientFullName}
                        </strong>
                        {patientDni && (
                          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginLeft: '8px' }}>
                            ({patientDni})
                          </span>
                        )}
                      </div>
                      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', backgroundColor: 'var(--neutral-100)', padding: '2px 8px', borderRadius: 'var(--radius-sm)' }}>
                        Emisión: {formatDateDDMMYYYY(rx.fechaEmision)}
                      </span>
                    </div>

                    {rx.diagnostico && (
                      <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                        <strong>Diagnóstico (CIE-10):</strong> {rx.diagnostico}
                      </div>
                    )}

                    <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', background: 'var(--neutral-50)', padding: 'var(--space-2)', borderRadius: 'var(--radius-sm)' }}>
                      <strong>Medicación prescrita:</strong>
                      <div style={{ whiteSpace: 'pre-wrap', marginTop: '2px' }}>{rx.medicamentos}</div>
                    </div>

                    <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                      <button
                        className="btn btn--secondary btn--sm"
                        onClick={() => setSelectedDetailModal(rx)}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                      >
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                          <circle cx="12" cy="12" r="3" />
                        </svg>
                        Ver detalle
                      </button>
                      <button
                        className="btn btn--ghost btn--sm"
                        onClick={() => openOfficialPrescriptionPdf(rx, () => showAlert('Esta receta todavía no tiene el documento oficial de QBI2/Innovamed disponible. Contactá a soporte.', 'error'))}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                      >
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <line x1="12" y1="18" x2="12" y2="12" />
                          <polyline points="9 15 12 18 15 15" />
                        </svg>
                        Ver PDF oficial
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-6)' }}>
              No se encontraron recetas emitidas.
            </p>
          )}

          {/* Modal Detalle Receta */}
          {selectedDetailModal && (
            <div style={{
              position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
              backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              zIndex: 9999, padding: 'var(--space-4)'
            }}>
              <div className="card mobile-modal-card" style={{ maxWidth: '600px', width: '100%', maxHeight: '85vh', overflowY: 'auto', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
                  <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Detalle de Receta #{selectedDetailModal.id}</h3>
                  <button className="btn btn--ghost btn--sm mobile-modal-close" onClick={() => setSelectedDetailModal(null)} aria-label="Cerrar"><Icon.X /></button>
                </div>
                <div style={{ padding: 'var(--space-4)', backgroundColor: 'var(--neutral-50)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                  <div>
                    <strong style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>PACIENTE</strong>
                    <div style={{ fontSize: 'var(--text-base)', fontWeight: 'bold', color: 'var(--color-primary)' }}>
                      {selectedDetailModal.paciente ? `${selectedDetailModal.paciente.nombre || ''} ${selectedDetailModal.paciente.apellido || ''}`.trim() : 'Paciente'}
                    </div>
                    {selectedDetailModal.paciente?.dni && (
                      <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>DNI: {selectedDetailModal.paciente.dni}</div>
                    )}
                  </div>
                  <div>
                    <strong style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>MÉDICO PRESCRIPTOR</strong>
                    <div style={{ fontSize: 'var(--text-sm)' }}>
                      {selectedDetailModal.medico ? `${selectedDetailModal.medico.nombre} ${selectedDetailModal.medico.apellido || ''}` : doctorName} ({selectedDetailModal.medico?.matricula ? `MN ${selectedDetailModal.medico.matricula}` : doctorMatricula})
                    </div>
                  </div>
                  <div>
                    <strong style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>FECHA DE EMISIÓN</strong>
                    <div style={{ fontSize: 'var(--text-sm)' }}>{formatDateDDMMYYYY(selectedDetailModal.fechaEmision)}</div>
                  </div>
                  {selectedDetailModal.diagnostico && (
                    <div>
                      <strong style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>DIAGNÓSTICO (CIE-10)</strong>
                      <div style={{ fontSize: 'var(--text-sm)' }}>{selectedDetailModal.diagnostico}</div>
                    </div>
                  )}
                  <div>
                    <strong style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>MEDICACIÓN PRESCRIPTA</strong>
                    <pre style={{
                      fontFamily: 'inherit', whiteSpace: 'pre-wrap', backgroundColor: '#ffffff',
                      padding: 'var(--space-3)', borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--color-border)', marginTop: '4px', fontSize: 'var(--text-sm)'
                    }}>
                      {selectedDetailModal.medicamentos}
                    </pre>
                  </div>
                  {selectedDetailModal.indicaciones && (
                    <div>
                      <strong style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>INDICACIONES</strong>
                      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                        {selectedDetailModal.indicaciones}
                      </div>
                    </div>
                  )}
                  <button
                    className="btn btn--primary btn--sm"
                    style={{ marginTop: 'var(--space-2)', alignSelf: 'flex-start', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    onClick={() => openOfficialPrescriptionPdf(selectedDetailModal, () => showAlert('Esta receta todavía no tiene el documento oficial de QBI2/Innovamed disponible. Contactá a soporte.', 'error'))}
                  >
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                      <line x1="12" y1="18" x2="12" y2="12" />
                      <polyline points="9 15 12 18 15 15" />
                    </svg>
                    Ver PDF oficial de Receta
                  </button>
                  {selectedDetailModal.pdfUrl && (
                    <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', wordBreak: 'break-all' }}>
                      <strong>Link del documento (QBI2/Innovamed):</strong>{' '}
                      <a href={selectedDetailModal.pdfUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--color-primary)' }}>
                        {selectedDetailModal.pdfUrl}
                      </a>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      ) : (
        <>
          {sent ? (
            <div className="card" style={{ textAlign: 'center', padding: 'var(--space-10)' }}>
              <div style={{
                width: 64, height: 64, borderRadius: 'var(--radius-full)',
                background: 'var(--green-50)', border: '2px solid var(--green-300)',
                color: 'var(--green-600)', display: 'flex', alignItems: 'center',
                justifyContent: 'center', margin: '0 auto var(--space-5)',
              }}>
                <Icon.Check />
              </div>
              <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-2xl)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-3)' }}>
                Receta emitida exitosamente
              </h2>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
                Se envió la receta a <strong>{selectedPatientObj?.name || `${selectedPatientObj?.nombre || ''} ${selectedPatientObj?.apellido || ''}`.trim()}</strong>
              </p>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)', marginBottom: 'var(--space-8)' }}>
                Emisor: {doctorName} ({doctorMatricula}) · Registrada en el sistema.
              </p>
              <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center' }}>
                <button className="btn btn--primary" onClick={handleReset} id="btn-new-prescription">
                  Nueva receta
                </button>
                <button className="btn btn--secondary" onClick={() => { handleReset(); setActiveTab('history'); }}>
                  Ver Historial de Recetas
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* Patient Search Autocomplete */}
              <div className="card" style={{ overflow: 'visible' }}>
                <div className="card__header" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: '2px' }}>
                  <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span className="rx-step-badge">1</span>
                    Paciente
                  </h2>
                  <p className="card__subtitle">Buscá y seleccioná a quién le vas a recetar.</p>
                </div>

                {location.state?.fromPendingDocument && selectedPatientObj && missingPatientFields.length === 0 && (
                  <div style={{
                    margin: 'var(--space-2) 0 var(--space-4) 0',
                    padding: '10px 14px',
                    backgroundColor: '#ECFDF5',
                    border: '1.5px solid #10B981',
                    borderRadius: 'var(--radius-md)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    fontSize: '13px',
                    color: '#065F46'
                  }}>
                    <div style={{
                      width: '28px', height: '28px', borderRadius: '50%',
                      backgroundColor: '#D1FAE5', color: '#10B981',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                    }}>
                      <Icon.FileText size={14} />
                    </div>
                    <div>
                      <strong>Receta fuera de turno seleccionada automáticamente</strong>
                      <div style={{ fontSize: '12px', color: '#047857', marginTop: '2px' }}>
                        Se seleccionó a <strong>{selectedPatientName}</strong> desde la solicitud de documento pendiente.
                      </div>
                    </div>
                  </div>
                )}

                {selectedPatientObj && missingPatientFields.length > 0 && (
                  <div style={{
                    margin: 'var(--space-2) 0 var(--space-4) 0',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px',
                    padding: '12px 14px',
                    backgroundColor: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: 'var(--radius-md)',
                    color: '#92400e',
                    fontSize: '13px',
                    lineHeight: '1.5'
                  }}>
                    <div style={{
                      width: '28px', height: '28px', borderRadius: '50%',
                      backgroundColor: '#fef3c7', color: '#d97706',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                    }}>
                      <Icon.AlertTriangle size={16} />
                    </div>
                    <div style={{ flex: 1, minWidth: '200px' }}>
                      <strong style={{ display: 'block', color: '#78350f', fontWeight: 600, marginBottom: '2px' }}>
                        {selectedPatientName || 'Este paciente'} no tiene todos los datos necesarios
                      </strong>
                      <span>
                        Faltan: <strong style={{ color: '#b45309' }}>{missingPatientFields.join(', ')}</strong>.
                        {' '}No se puede emitir una receta electrónica válida sin estos datos.
                      </span>
                    </div>
                    <button
                      type="button"
                      className="btn btn--sm btn--secondary"
                      style={{ flexShrink: 0, whiteSpace: 'nowrap' }}
                      onClick={() => setShowEditPatientModal(true)}
                    >
                      Completar datos
                    </button>
                  </div>
                )}

                {selectedPatientObj ? (
                  <div className="rx-selected-patient">
                    <div className="rx-selected-patient__avatar">{getPatientInitials(selectedPatientName)}</div>
                    <div className="rx-selected-patient__info">
                      <div className="rx-selected-patient__name">{selectedPatientName || 'Paciente sin nombre'}</div>
                      <div className="rx-selected-patient__meta">
                        <span><strong>{selectedPatientTipoDoc}</strong> {selectedPatientDni}</span>
                        {selectedPatientAge != null && <span>{selectedPatientAge} años</span>}
                        {selectedPatientObj.obraSocial && (
                          <span>{selectedPatientObj.obraSocial}{selectedPatientObj.numAfiliado ? ` · Afiliado ${selectedPatientObj.numAfiliado}` : ''}</span>
                        )}
                        {selectedPatientObj.telefono && <span>{selectedPatientObj.telefono}</span>}
                        {selectedPatientObj.email && <span>{selectedPatientObj.email}</span>}
                      </div>
                    </div>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      onClick={handleClearPatient}
                      title="Elegir otro paciente"
                    >
                      Cambiar
                    </button>
                  </div>
                ) : (
                  <div className="form-group" style={{ position: 'relative', marginBottom: 0 }}>
                    <label className="form-label form-label--required">Buscador de paciente</label>
                    <input
                      id="rx-patient-search"
                      name="rx-patient-search"
                      className="form-input"
                      type="text"
                      placeholder="Ingresá al menos 3 letras para buscar (Nombre, Apellido, DNI)..."
                      value={patientSearch}
                      onChange={(e) => {
                        setPatientSearch(e.target.value);
                        setShowPatientDropdown(true);
                      }}
                      onFocus={() => setShowPatientDropdown(true)}
                      autoComplete="off"
                      autoCorrect="off"
                      autoCapitalize="off"
                      spellCheck={false}
                    />

                    {showPatientDropdown && patientSearch.trim().length >= 3 && (
                      <div className="rx-patient-dropdown">
                        {filteredPatients.length > 0 ? (
                          filteredPatients.map(p => {
                            const pName = p.name || `${p.nombre || ''} ${p.apellido || ''}`.trim();
                            const pDni = p.dni || p.numeroDocumento || 'S/D';
                            const pAge = calcAge(p.fechaNacimiento);
                            return (
                              <div
                                key={p.id}
                                onClick={() => handleSelectPatient(p)}
                                className="patient-search-item"
                              >
                                <div className="rx-selected-patient__avatar rx-selected-patient__avatar--sm">{getPatientInitials(pName)}</div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div style={{ fontWeight: 'bold', fontSize: 'var(--text-sm)' }}>{pName}</div>
                                  <div className="rx-selected-patient__meta rx-selected-patient__meta--sm">
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
                                        <rect x="2" y="5" width="20" height="14" rx="2" /><circle cx="8.5" cy="12" r="2" /><line x1="14" y1="10" x2="19" y2="10" /><line x1="14" y1="14" x2="19" y2="14" />
                                      </svg>
                                      DNI {pDni}
                                    </span>
                                    {pAge != null && (
                                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
                                          <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
                                        </svg>
                                        {pAge} años
                                      </span>
                                    )}
                                    {p.obraSocial && (
                                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
                                          <path d="M20.42 4.58a5.4 5.4 0 0 0-7.65 0l-.77.78-.77-.78a5.4 5.4 0 0 0-7.65 0C1.46 6.7 1.33 10.28 4 13l8 8 8-8c2.67-2.72 2.54-6.3.42-8.42z" />
                                        </svg>
                                        {p.obraSocial}
                                      </span>
                                    )}
                                    {(p.email || p.telefono) && (
                                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
                                          {p.email ? (
                                            <><rect x="2" y="4" width="20" height="16" rx="2" /><polyline points="2 6 12 13 22 6" /></>
                                          ) : (
                                            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z" />
                                          )}
                                        </svg>
                                        {p.email || p.telefono}
                                      </span>
                                    )}
                                  </div>
                                </div>
                              </div>
                            );
                          })
                        ) : (
                          <div style={{ padding: '16px 12px', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px' }}>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 22, height: 22, opacity: 0.5 }}>
                              <circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
                            </svg>
                            No se encontraron pacientes que coincidan con "{patientSearch}"
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Medication Search & Addition */}
              <div className="card" style={{ overflow: 'visible' }}>
                <div className="card__header" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: '2px' }}>
                  <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span className="rx-step-badge">2</span>
                    Medicación
                  </h2>
                  <p className="card__subtitle">Buscá cada medicamento en el catálogo oficial y completá cómo debe tomarlo.</p>
                </div>

                <div className="form-group" style={{ position: 'relative', marginBottom: 'var(--space-4)' }}>
                  <label className="form-label">Buscar o agregar medicamento (Presioná Enter para añadir)</label>
                  <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                    <input
                      id="rx-med-search"
                      name="rx-med-search"
                      className="form-input"
                      type="text"
                      placeholder="Ej: Escitalopram 10mg, Sertralina 50mg..."
                      value={medSearchInput}
                      onChange={(e) => {
                        setMedSearchInput(e.target.value);
                        setShowMedDropdown(true);
                      }}
                      onFocus={() => setShowMedDropdown(true)}
                      onKeyDown={handleMedKeyDown}
                      autoComplete="off"
                      autoCorrect="off"
                      autoCapitalize="off"
                      spellCheck={false}
                    />
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => handleAddCustomMedication()}
                      disabled={!medSearchInput.trim()}
                    >
                      <Icon.Plus /> Añadir
                    </button>
                  </div>

                  {showMedDropdown && medSearchInput.trim().length >= 2 && (
                    <div style={{
                      position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 90,
                      backgroundColor: '#ffffff', border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)', boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                      maxHeight: '220px', overflowY: 'auto', marginTop: '4px'
                    }}>
                      {medSearching ? (
                        <div style={{ padding: '10px 14px', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
                          Buscando en el catálogo de QBI2/Innovamed...
                        </div>
                      ) : medSearchResults.length > 0 ? (
                        medSearchResults.map((m: any, idx: number) => (
                          <div
                            key={idx}
                            onClick={() => handleAddMedicationFromCatalog(m)}
                            style={{ padding: '10px 14px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: 'var(--text-sm)' }}
                          >
                            <div>+ {m.nombreProducto}</div>
                            <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                              {m.nombreDroga} · {m.presentacion} · Reg. {m.regNo}
                            </div>
                          </div>
                        ))
                      ) : (
                        <div
                          onClick={() => handleAddCustomMedication()}
                          style={{ padding: '10px 14px', cursor: 'pointer', color: 'var(--color-primary)', fontSize: 'var(--text-sm)' }}
                        >
                          No se encontró en el catálogo de QBI2 — añadir personalizado: "{medSearchInput}" (Presioná Enter)
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* List of Added Medications */}
                {medications.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                    {medications.map((med, i) => (
                      <div key={i} style={{
                        display: 'flex', flexDirection: 'column', gap: 'var(--space-3)',
                        padding: 'var(--space-4)', background: 'var(--neutral-50)',
                        borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
                        position: 'relative'
                      }}>
                        <button
                          type="button"
                          onClick={() => removeMedication(i)}
                          style={{
                            position: 'absolute', top: 8, right: 8,
                            background: 'none', border: 'none', color: 'var(--color-text-secondary)',
                            fontSize: 'var(--text-lg)', cursor: 'pointer', lineHeight: 1
                          }}
                          aria-label="Quitar medicación"
                        >
                          ×
                        </button>
                        <div style={{ fontWeight: 'bold', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                          {i + 1}. {med.name}
                          {!med.regNo && (
                            <span title="No se encontró en el catálogo de QBI2/Innovamed — se enviará sin número de registro" style={{ fontSize: '10px', fontWeight: 'normal', color: 'var(--color-warning, #b45309)', background: 'var(--neutral-100)', padding: '2px 6px', borderRadius: '4px' }}>
                              Sin registro QBI2
                            </span>
                          )}
                        </div>
                        <label style={{ display: 'flex', alignItems: 'flex-start', gap: '6px', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', cursor: 'pointer', width: 'fit-content' }}>
                          <input
                            type="checkbox"
                            checked={!!med.noSustituible}
                            onChange={(e) => updateMedication(i, 'noSustituible', e.target.checked)}
                            style={{ marginTop: '2px' }}
                          />
                          <span>
                            No sustituible
                            <span style={{ display: 'block', fontSize: '11px' }}>La farmacia no podrá cambiarlo por otra marca (Decreto 987/03 Art.2°)</span>
                          </span>
                        </label>
                        <div className="rx-med-fields-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 'var(--space-3)' }}>
                          <div className="form-group">
                            <label className="form-label">Dosis</label>
                            <input
                              className="form-input"
                              type="text"
                              placeholder="Ej: 1 comp"
                              value={med.dosage}
                              onChange={(e) => updateMedication(i, 'dosage', e.target.value)}
                            />
                          </div>
                          <div className="form-group">
                            <label className="form-label">Frecuencia</label>
                            <input
                              className="form-input"
                              type="text"
                              placeholder="Ej: cada 24hs"
                              value={med.frequency}
                              onChange={(e) => updateMedication(i, 'frequency', e.target.value)}
                            />
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '6px' }}>
                              {FRECUENCIAS_RAPIDAS.map(f => (
                                <button
                                  key={f}
                                  type="button"
                                  className="rx-quick-chip"
                                  onClick={() => updateMedication(i, 'frequency', f)}
                                >
                                  {f}
                                </button>
                              ))}
                            </div>
                          </div>
                          <div className="form-group">
                            <label className="form-label">Duración</label>
                            <input
                              className="form-input"
                              type="text"
                              placeholder="Ej: 30 días"
                              value={med.duration}
                              onChange={(e) => updateMedication(i, 'duration', e.target.value)}
                            />
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '6px' }}>
                              {DURACIONES_RAPIDAS.map(d => (
                                <button
                                  key={d}
                                  type="button"
                                  className="rx-quick-chip"
                                  onClick={() => updateMedication(i, 'duration', d)}
                                >
                                  {d}
                                </button>
                              ))}
                            </div>
                          </div>
                          <div className="form-group">
                            <label className="form-label">Laboratorio</label>
                            <LaboratorioAutocomplete
                              value={med.laboratorio || ''}
                              onChange={(v) => updateMedication(i, 'laboratorio', v)}
                            />
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', margin: 0 }}>
                    No hay medicamentos añadidos aún. Buscá un medicamento arriba y presioná Enter para añadirlo.
                  </p>
                )}
              </div>

              {/* Diagnosis + Notes */}
              <div className="card">
                <div className="card__header" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: '2px' }}>
                  <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span className="rx-step-badge">3</span>
                    Diagnóstico e indicaciones
                  </h2>
                  <p className="card__subtitle">Opcional, pero le ayuda al paciente a entender para qué es el tratamiento.</p>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="rx-diagnosis">Diagnóstico (CIE-10)</label>
                    <input
                      id="rx-diagnosis"
                      name="rx-diagnosis"
                      className="form-input"
                      type="text"
                      placeholder="Ej: F41.1 — Trastorno de ansiedad generalizada"
                      value={diagnosis}
                      onChange={(e) => setDiagnosis(e.target.value)}
                      autoComplete="off"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="rx-notes">Indicaciones para el paciente</label>
                    <textarea
                      id="rx-notes"
                      name="rx-notes"
                      className="form-input"
                      rows={3}
                      placeholder="Instrucciones adicionales, controles, próximo turno..."
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      style={{ resize: 'vertical', fontFamily: 'var(--font-body)' }}
                      autoComplete="off"
                    />
                  </div>
                </div>
              </div>

              {/* Send */}
              <div className="card">
                <div className="card__header" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: '2px' }}>
                  <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span className="rx-step-badge">4</span>
                    Confirmar y enviar
                  </h2>
                  <p className="card__subtitle">Revisá que esté todo bien antes de emitirla — así es como va a llegar el paciente.</p>
                </div>

                {(selectedPatientObj || medications.length > 0) && (
                  <div style={{
                    backgroundColor: 'var(--neutral-50)', border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', marginBottom: 'var(--space-4)',
                    display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', fontSize: 'var(--text-sm)'
                  }}>
                    <div>
                      <strong style={{ color: 'var(--color-text-secondary)', fontSize: '11px', textTransform: 'uppercase' }}>Paciente</strong>
                      <div>{selectedPatientObj ? (selectedPatientObj.name || `${selectedPatientObj.nombre || ''} ${selectedPatientObj.apellido || ''}`.trim()) : <em style={{ color: 'var(--color-text-secondary)' }}>Sin seleccionar todavía</em>}</div>
                    </div>
                    {diagnosis && (
                      <div>
                        <strong style={{ color: 'var(--color-text-secondary)', fontSize: '11px', textTransform: 'uppercase' }}>Diagnóstico</strong>
                        <div>{diagnosis}</div>
                      </div>
                    )}
                    <div>
                      <strong style={{ color: 'var(--color-text-secondary)', fontSize: '11px', textTransform: 'uppercase' }}>Medicación ({medications.length})</strong>
                      {medications.length > 0 ? (
                        <ul style={{ margin: '2px 0 0', paddingLeft: '18px' }}>
                          {medications.map((med, i) => (
                            <li key={i}>
                              {med.name}
                              {(med.dosage || med.frequency || med.duration) && (
                                <span style={{ color: 'var(--color-text-secondary)' }}>
                                  {' — '}
                                  {[med.dosage, med.frequency, med.duration].filter(Boolean).join(', ')}
                                </span>
                              )}
                              {med.noSustituible && <span style={{ color: 'var(--color-warning, #b45309)' }}> · No sustituible</span>}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <div><em style={{ color: 'var(--color-text-secondary)' }}>Todavía no agregaste ningún medicamento</em></div>
                      )}
                    </div>
                    {notes && (
                      <div>
                        <strong style={{ color: 'var(--color-text-secondary)', fontSize: '11px', textTransform: 'uppercase' }}>Indicaciones para el paciente</strong>
                        <div style={{ whiteSpace: 'pre-wrap' }}>{notes}</div>
                      </div>
                    )}
                  </div>
                )}

                <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)' }}>
                  Se emite como receta electrónica oficial (QBI2/Innovamed), firmada con tus datos profesionales (<strong>{doctorName} · {doctorMatricula}</strong>), y queda disponible para que el paciente la descargue desde la app.
                </p>
                <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
                  <button
                    className={`btn btn--primary ${sending ? 'btn--loading' : ''}`}
                    disabled={!canSend || sending}
                    onClick={handleSend}
                    id="btn-send-prescription"
                  >
                    {sending ? 'Enviando...' : 'Emitir receta y enviar al paciente'}
                  </button>
                  <button className="btn btn--ghost" disabled={sending} onClick={handleReset}>
                    Limpiar todo
                  </button>
                </div>
                {!canSend && selectedPatientObj && missingPatientFields.length > 0 && (
                  <p style={{ fontSize: 'var(--text-xs)', color: '#b45309', marginTop: 'var(--space-2)' }}>
                    Completá los datos del paciente para poder emitir la receta.
                  </p>
                )}
              </div>
            </>
          )}
        </>
      )}

      {showEditPatientModal && selectedPatientObj && (
        <EditPatientModal
          patient={selectedPatientObj}
          onClose={() => setShowEditPatientModal(false)}
          onSaved={(updated) => {
            setSelectedPatientObj((prev: any) => ({ ...prev, ...updated }));
            setShowEditPatientModal(false);
            showAlert('Datos del paciente actualizados.', 'success');
          }}
        />
      )}
    </div>
  );
}
