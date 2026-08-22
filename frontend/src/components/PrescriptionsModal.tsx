import { useState } from 'react'
import { openOfficialPrescriptionPdf } from '../utils/pdfGenerator'
import { useAlert } from '../context/AlertContext'

function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

const formatDateDDMMYYYY = (dateStr?: string) => {
  if (!dateStr) return '';
  if (dateStr.includes('/')) return dateStr;
  const parts = dateStr.trim().split('T')[0].split('-');
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
  return dateStr;
};

// "Mis Recetas Médicas" — list + detail view for the patient's own recetas electrónicas (QBI2).
// `selectedPrescriptionDetail` is local to this modal (resets naturally on unmount when the
// modal closes) rather than lifted to LandingPage, since nothing outside this view needs it.
export default function PrescriptionsModal({ prescriptions, onClose }: {
  prescriptions: any[]
  onClose: () => void
}) {
  const { showAlert } = useAlert()
  const [selectedPrescriptionDetail, setSelectedPrescriptionDetail] = useState<any | null>(null)

  const handleViewPdf = (rx: any) =>
    openOfficialPrescriptionPdf(rx, () => showAlert('Esta receta todavía no tiene el documento oficial de QBI2/Innovamed disponible. Contactá a tu médico.', 'error'))

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
        maxWidth: '650px',
        width: '100%',
        maxHeight: '85vh',
        overflowY: 'auto',
        position: 'relative',
        padding: 'var(--space-6)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-4)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Mis Recetas Médicas</h3>
          <button onClick={onClose} className="btn btn--ghost btn--sm" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
        </div>

        {selectedPrescriptionDetail ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <button
              className="btn btn--ghost btn--sm"
              style={{ alignSelf: 'flex-start' }}
              onClick={() => setSelectedPrescriptionDetail(null)}
            >
              ← Volver al listado
            </button>
            <div style={{ padding: 'var(--space-4)', backgroundColor: 'var(--neutral-50)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>
                Emisión: {formatDateDDMMYYYY(selectedPrescriptionDetail.fechaEmision || selectedPrescriptionDetail.fecha)}
              </div>
              <h4 style={{ margin: '0 0 var(--space-1)', color: 'var(--color-primary)' }}>
                Médico Prescriptor: {selectedPrescriptionDetail.medico ? `${selectedPrescriptionDetail.medico.nombre} ${selectedPrescriptionDetail.medico.apellido || ''}` : 'Médico Tratante'}
              </h4>
              {selectedPrescriptionDetail.medico?.matricula && (
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)' }}>
                  Matrícula: {selectedPrescriptionDetail.medico.matricula}
                </div>
              )}

              {selectedPrescriptionDetail.diagnostico && (
                <div style={{ marginBottom: 'var(--space-3)' }}>
                  <strong>Diagnóstico (CIE-10):</strong>
                  <div style={{ fontSize: 'var(--text-sm)' }}>{selectedPrescriptionDetail.diagnostico}</div>
                </div>
              )}

              <div style={{ marginBottom: 'var(--space-3)' }}>
                <strong>Medicación prescrita:</strong>
                <pre style={{
                  fontFamily: 'inherit',
                  whiteSpace: 'pre-wrap',
                  backgroundColor: '#ffffff',
                  padding: 'var(--space-3)',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--color-border)',
                  marginTop: '4px',
                  fontSize: 'var(--text-sm)'
                }}>
                  {selectedPrescriptionDetail.medicamentos}
                </pre>
              </div>

              {selectedPrescriptionDetail.indicaciones && (
                <div style={{ marginBottom: 'var(--space-3)' }}>
                  <strong>Indicaciones para el paciente:</strong>
                  <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    {selectedPrescriptionDetail.indicaciones}
                  </div>
                </div>
              )}

              <button
                className="btn btn--primary btn--sm"
                style={{ marginTop: 'var(--space-2)', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                onClick={() => handleViewPdf(selectedPrescriptionDetail)}
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <polyline points="14 2 14 8 20 8" />
                  <line x1="12" y1="18" x2="12" y2="12" />
                  <polyline points="9 15 12 18 15 15" />
                </svg>
                Ver PDF oficial de Receta
              </button>
              {selectedPrescriptionDetail.pdfUrl && (
                <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: 'var(--space-2)', wordBreak: 'break-all' }}>
                  <strong>Link del documento (QBI2/Innovamed):</strong>{' '}
                  <a href={selectedPrescriptionDetail.pdfUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--color-primary)' }}>
                    {selectedPrescriptionDetail.pdfUrl}
                  </a>
                </div>
              )}
            </div>
          </div>
        ) : prescriptions.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            {prescriptions.map((rx, idx) => (
              <div key={rx.id || idx} style={{
                padding: 'var(--space-4)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-2)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <strong style={{ fontSize: 'var(--text-sm)', color: 'var(--color-primary)' }}>
                    {rx.medico ? `${rx.medico.nombre} ${rx.medico.apellido || ''}` : 'Receta Médica'}
                  </strong>
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                    {formatDateDDMMYYYY(rx.fechaEmision || rx.fecha)}
                  </span>
                </div>
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
                  {rx.medicamentos}
                </div>
                <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  <button
                    className="btn btn--secondary btn--sm"
                    onClick={() => setSelectedPrescriptionDetail(rx)}
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
                    onClick={() => handleViewPdf(rx)}
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
            ))}
          </div>
        ) : (
          <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-6)' }}>
            No tenés recetas médicas registradas actualmente.
          </p>
        )}
      </div>
    </div>
  )
}
