import { useState } from 'react'
import { Icon } from './Icon'
import type { Appointment, NavSection } from '../types/dashboard'
import { useAlert } from '../context/AlertContext'

const MAX_DOCUMENTO_ADJUNTO_BYTES = 8 * 1024 * 1024

// Shared between AppointmentCard (the "Documentos solicitados" card + "Diario" list) and
// DashboardHome's "Detalle del Turno" modal — both need the exact same actions for a
// document-only turno, so the upload-and-send modal only lives in one place.
export default function DocumentActions({ appt, onMarcarDocumentoEnviado, onNavigate }: {
  appt: Appointment
  onMarcarDocumentoEnviado?: (id: string, archivo: { data: string; nombre: string }) => void
  onNavigate?: (section: NavSection, state?: any) => void
}) {
  const { showAlert } = useAlert()
  const [showSendModal, setShowSendModal] = useState(false)
  const [pendingFile, setPendingFile] = useState<{ data: string; nombre: string } | null>(null)

  const isReceta = !!appt.esReceta
  const isDocumentoEnviado = !!appt.documentoEnviado
  const patientPhoneDigits = (appt.patientInfo?.telefono || '').replace(/[^\d]/g, '')
  const whatsappHref = patientPhoneDigits
    ? `https://wa.me/${patientPhoneDigits}?text=${encodeURIComponent(`Hola ${appt.patientName}, te enviamos tu ${appt.type.toLowerCase()} adjunto en este mensaje.`)}`
    : undefined

  const handleGoToPrescription = () => {
    const patientObj = appt.patientInfo ? {
      ...appt.patientInfo,
      id: appt.patientInfo.id || (appt as any).pacienteId || (appt as any).usuarioId,
      name: appt.patientInfo.name || appt.patientName || `${appt.patientInfo.nombre || ''} ${appt.patientInfo.apellido || ''}`.trim(),
      nombre: appt.patientInfo.nombre || appt.patientName,
      apellido: appt.patientInfo.apellido || '',
      email: appt.patientInfo.email || appt.patientInfo.mail,
      telefono: appt.patientInfo.telefono,
      dni: appt.patientInfo.dni || appt.patientInfo.numeroDocumento,
      tipoDocumento: appt.patientInfo.tipoDocumento || 'DNI',
      fechaNacimiento: appt.patientInfo.fechaNacimiento,
      obraSocial: appt.patientInfo.obraSocial,
      numAfiliado: appt.patientInfo.numAfiliado,
      credencial: appt.patientInfo.credencial,
      domicilio: appt.patientInfo.domicilio,
    } : {
      id: (appt as any).pacienteId || (appt as any).usuarioId,
      name: appt.patientName,
      nombre: appt.patientName,
      apellido: '',
    }

    onNavigate?.('prescriptions', {
      patient: patientObj,
      patientObj: patientObj,
      patientId: patientObj.id,
      patientName: appt.patientName,
      appt: appt,
      fromPendingDocument: true,
    })
  }

  return (
    <>
      <div className="document-actions">
        {isReceta ? (
          // Recetas are fully handled inside Recetas Electrónicas: generating one there sends
          // the patient their WhatsApp notification (RecetaService#emitirReceta) and
          // auto-clears this card (TurnoService#marcarRecetasEnviadasParaPaciente) — no
          // separate email/WhatsApp/mark-as-sent action belongs here. Once that already
          // happened (isDocumentoEnviado), there's nothing left to do — showing "Generar
          // receta →" again reads as if the first one never went through.
          !isDocumentoEnviado && onNavigate && (
            <button
              type="button"
              onClick={handleGoToPrescription}
              className="btn btn--primary btn--sm document-actions__btn"
              title={`Generar receta oficial para ${appt.patientName}`}
            >
              <Icon.FileText size={14} /> Generar receta →
            </button>
          )
        ) : (
          <>
            {whatsappHref && (
              <a href={whatsappHref} target="_blank" rel="noopener noreferrer" className="btn btn--ghost btn--sm document-actions__btn" title="Enviar por WhatsApp">
                <Icon.MessageCircle size={14} /> WhatsApp
              </a>
            )}
            {!isDocumentoEnviado && onMarcarDocumentoEnviado && (
              <button
                type="button"
                onClick={() => setShowSendModal(true)}
                className="btn btn--primary btn--sm document-actions__btn"
              >
                <Icon.Mail size={14} /> Enviar por mail
              </button>
            )}
          </>
        )}
      </div>
      {showSendModal && (
        <div
          style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 9999, padding: 'var(--space-4)'
          }}
          onClick={(e) => e.target === e.currentTarget && setShowSendModal(false)}
        >
          <div className="card" style={{ maxWidth: '420px', width: '100%', padding: 'var(--space-5)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--text-md)', fontWeight: 'bold' }}>Enviar documento por mail</h3>
            <p style={{ margin: 0, fontSize: '12px', color: 'var(--color-text-secondary)' }}>
              Adjuntá el archivo — se lo mandamos por mail a {appt.patientName} y queda marcado como enviado. El archivo no se guarda dentro de la app.
            </p>
            <label style={{
              cursor: 'pointer', backgroundColor: 'var(--color-primary)', color: 'white',
              padding: 'var(--space-2) var(--space-4)', borderRadius: 'var(--radius-md)',
              fontSize: 'var(--text-xs)', fontWeight: 'bold', textAlign: 'center', width: 'fit-content'
            }}>
              {pendingFile ? 'Cambiar archivo' : 'Elegir archivo'}
              <input
                type="file"
                accept="application/pdf,image/*"
                style={{ display: 'none' }}
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (!file) return
                  if (file.size > MAX_DOCUMENTO_ADJUNTO_BYTES) {
                    showAlert(`El archivo pesa ${(file.size / (1024 * 1024)).toFixed(1)}MB — el máximo permitido es 8MB.`, 'error')
                    e.target.value = ''
                    return
                  }
                  const reader = new FileReader()
                  reader.onloadend = () => {
                    setPendingFile({ data: reader.result as string, nombre: file.name })
                  }
                  reader.readAsDataURL(file)
                }}
              />
            </label>
            {pendingFile && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', fontSize: '12px' }}>
                <Icon.FileText size={14} /> {pendingFile.nombre}
                <button type="button" onClick={() => setPendingFile(null)} className="btn btn--ghost btn--sm" style={{ color: 'var(--color-danger)', padding: '2px 6px' }}>
                  Quitar
                </button>
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => { setShowSendModal(false); setPendingFile(null) }}>
                Cancelar
              </button>
              <button
                type="button"
                className="btn btn--primary btn--sm"
                disabled={!pendingFile}
                style={!pendingFile ? { opacity: 0.5, cursor: 'not-allowed' } : undefined}
                onClick={() => {
                  if (!pendingFile) return
                  onMarcarDocumentoEnviado?.(appt.id, pendingFile)
                  setShowSendModal(false)
                  setPendingFile(null)
                }}
              >
                Enviar mail
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
