import { useEffect, useState } from 'react'
import { api } from '../api/api'
import { type Patient } from '../hooks/usePatients'
import DateInputDDMMYYYY from './DateInputDDMMYYYY'

interface Financiador {
  idfinanciador: number
  nombreComercial: string
}

function FieldError({ message }: { message: string | null }) {
  if (!message) return null
  return <span style={{ color: '#ff4d4f', fontSize: '11px', marginTop: '2px', display: 'block' }}>{message}</span>
}

// Collects the data QBI2/Innovamed actually needs to emit a valid electronic prescription
// (DNI, fecha de nacimiento, domicilio estructurado, cobertura de obra social) — none of this
// had an editable form anywhere before; api.actualizarPaciente existed but nothing called it.
export default function EditPatientModal({
  patient,
  onClose,
  onSaved,
}: {
  patient: Patient
  onClose: () => void
  onSaved: (updated: Patient) => void
}) {
  const [apellido, setApellido] = useState(patient.apellido || '')
  const [sexo, setSexo] = useState(patient.sexo || '')
  const [tipoDocumento, setTipoDocumento] = useState(patient.tipoDocumento || 'DNI')
  const [numeroDocumento, setNumeroDocumento] = useState(patient.numeroDocumento ? String(patient.numeroDocumento) : (patient.dni || ''))
  const [fechaNacimiento, setFechaNacimiento] = useState(patient.fechaNacimiento || '')
  const [telefono, setTelefono] = useState((patient.telefono || '').replace(/^\+54\s*/, ''))

  const [calle, setCalle] = useState(patient.domicilio?.calle || '')
  const [numero, setNumero] = useState(patient.domicilio?.numero || '')
  const [localidad, setLocalidad] = useState(patient.domicilio?.localidad || '')
  const [provincia, setProvincia] = useState(patient.domicilio?.provincia || '')

  const [tieneObraSocial, setTieneObraSocial] = useState(!!(patient.credencial?.codEntidad || patient.credencial?.pan))
  const [idFinanciador, setIdFinanciador] = useState(patient.credencial?.codEntidad ? String(patient.credencial.codEntidad) : '')
  const [plan, setPlan] = useState(patient.credencial?.plan || '')
  const [numAfiliado, setNumAfiliado] = useState(patient.credencial?.pan || patient.numAfiliado || '')

  const [financiadores, setFinanciadores] = useState<Financiador[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.getFinanciadores()
      .then((res: any) => setFinanciadores(res?.financiadores || []))
      .catch((err: any) => console.error('Error al cargar financiadores:', err))
  }, [])

  // Field-level validation, shown inline so every value this form accepts is checked before
  // it ever reaches actualizarPaciente — mirrors the same rules the backend now enforces in
  // ClinicalService.actualizarPaciente, so a value rejected here would also be rejected there.
  const docDigits = numeroDocumento.trim()
  const docError = docDigits.length > 0 && (docDigits.length < 6 || docDigits.length > 9)
    ? 'El número de documento debe tener entre 6 y 9 dígitos.'
    : null

  const telefonoDigits = telefono.trim()
  const telefonoError = telefonoDigits.length > 0 && !/^\d{6,15}$/.test(telefonoDigits)
    ? 'El teléfono debe contener solo números, entre 6 y 15 dígitos.'
    : null

  const domicilioFields = [calle.trim(), numero.trim(), localidad.trim(), provincia.trim()]
  const domicilioStarted = domicilioFields.some(v => v.length > 0)
  const domicilioComplete = domicilioFields.every(v => v.length > 0)
  const domicilioError = domicilioStarted && !domicilioComplete
    ? 'Completá calle, número, localidad y provincia, o dejá todos los campos de domicilio vacíos.'
    : null

  // QBI2 rechaza la receta recién al emitirla con QBI124 "LA CANTIDAD DE CARACTERES NO CUMPLE EL
  // RANGO MINIMO O MAXIMO PARA EL FINANCIADOR SELECCIONADO" si el número de afiliado es demasiado
  // corto — antes este chequeo solo validaba el juego de caracteres (1-30), así que un valor de
  // 1-2 dígitos pasaba acá y recién fallaba en QBI2. Mirrors ClinicalService.validarDatosPaciente.
  const numAfiliadoError = tieneObraSocial && numAfiliado.trim().length > 0 && !/^[A-Za-z0-9\-/. ]{6,30}$/.test(numAfiliado.trim())
    ? 'El número de afiliado debe tener entre 6 y 30 caracteres.'
    : null

  const canSave = apellido.trim().length > 0 && !!sexo && docDigits.length > 0 && !docError && !!fechaNacimiento && !telefonoError && !domicilioError &&
    (!tieneObraSocial || (idFinanciador.trim().length > 0 && numAfiliado.trim().length > 0 && !numAfiliadoError))

  const handleSave = () => {
    if (!canSave || saving) return
    setSaving(true)
    setError(null)

    const financiadorElegido = financiadores.find(f => String(f.idfinanciador) === idFinanciador)
    const formattedPhone = telefono.trim() ? `+54 ${telefono.trim().replace(/^\+54\s*/, '')}` : patient.telefono

    // actualizarPaciente does a full replace of the patient's clinical fields, so every field
    // it accepts — even ones this modal doesn't edit — has to be seeded from the current patient
    // to avoid silently wiping data this form never touched.
    api.actualizarPaciente(patient.id, {
      nombre: patient.nombre,
      apellido: apellido.trim(),
      dni: tipoDocumento === 'DNI' ? numeroDocumento.trim() : patient.dni,
      tipoDocumento,
      numeroDocumento: numeroDocumento.trim() ? Number(numeroDocumento.trim()) : null,
      fechaNacimiento,
      telefono: formattedPhone,
      sexo,
      cuil: patient.cuil,
      datosOfuscado: patient.datosOfuscado,
      direccion: calle.trim() ? `${calle.trim()} ${numero.trim()}, ${localidad.trim()}` : patient.direccion,
      obraSocial: tieneObraSocial ? (financiadorElegido?.nombreComercial || patient.obraSocial) : null,
      numAfiliado: tieneObraSocial ? numAfiliado.trim() : null,
      domicilio: calle.trim() ? {
        calle: calle.trim(),
        numero: numero.trim(),
        localidad: localidad.trim(),
        provincia: provincia.trim(),
        pais: 'Argentina',
      } : null,
      credencial: tieneObraSocial ? {
        codEntidad: idFinanciador ? Number(idFinanciador) : null,
        pan: numAfiliado.trim(),
        plan: plan.trim(),
      } : null,
    })
      .then((updated: any) => onSaved(updated))
      .catch((err: any) => {
        console.error('Error al guardar datos del paciente:', err)
        setError(err?.message || 'No se pudieron guardar los datos. Intentá de nuevo.')
      })
      .finally(() => setSaving(false))
  }

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      zIndex: 9999, padding: 'var(--space-4)'
    }}>
      <div className="card" style={{ maxWidth: '560px', width: '100%', maxHeight: '85vh', overflowY: 'auto', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-3)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold' }}>Editar datos de {patient.nombre}</h3>
          <button className="btn btn--ghost btn--sm" onClick={onClose}>✕</button>
        </div>

        <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>
          Estos datos son los que exige QBI2/Innovamed para validar una receta electrónica a nombre de este paciente.
        </p>

        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-3)' }}>
          <div className="form-group">
            <label className="form-label form-label--required">Apellido</label>
            <input className="form-input" type="text" value={apellido} onChange={(e) => setApellido(e.target.value)} placeholder="Ej: Pérez" />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required">Sexo</label>
            <select className="form-input" value={sexo} onChange={(e) => setSexo(e.target.value)}>
              <option value="">Seleccionar...</option>
              <option value="M">Masculino</option>
              <option value="F">Femenino</option>
              <option value="X">Otro / No especifica</option>
            </select>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 'var(--space-3)' }}>
          <div className="form-group">
            <label className="form-label form-label--required">Tipo Doc.</label>
            <select className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
              <option value="DNI">DNI</option>
              <option value="LC">Libreta Cívica (LC)</option>
              <option value="LE">Libreta de Enrolamiento (LE)</option>
              <option value="Pasaporte">Pasaporte</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required">Número de documento</label>
            <input className="form-input" type="text" inputMode="numeric" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value.replace(/\D/g, ''))} />
            <FieldError message={docError} />
          </div>
        </div>

        <div className="form-group">
          <label className="form-label form-label--required">Fecha de nacimiento</label>
          <DateInputDDMMYYYY className="form-input" value={fechaNacimiento} onChange={setFechaNacimiento} max={new Date().toISOString().split('T')[0]} min="1900-01-01" />
        </div>

        <div className="form-group">
          <label className="form-label">Teléfono</label>
          <input className="form-input" type="tel" value={telefono} onChange={(e) => setTelefono(e.target.value.replace(/[^\d]/g, ''))} placeholder="Ej: 3515998822" />
          <FieldError message={telefonoError} />
        </div>

        <div className="settings-section-label">Domicilio</div>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-3)' }}>
          <div className="form-group">
            <label className="form-label">Calle</label>
            <input className="form-input" type="text" value={calle} onChange={(e) => setCalle(e.target.value)} placeholder="Ej: Av. Colón" />
          </div>
          <div className="form-group">
            <label className="form-label">Número</label>
            <input className="form-input" type="text" value={numero} onChange={(e) => setNumero(e.target.value)} placeholder="Ej: 1234" />
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
          <div className="form-group">
            <label className="form-label">Localidad</label>
            <input className="form-input" type="text" value={localidad} onChange={(e) => setLocalidad(e.target.value)} placeholder="Ej: Córdoba" />
          </div>
          <div className="form-group">
            <label className="form-label">Provincia</label>
            <input className="form-input" type="text" value={provincia} onChange={(e) => setProvincia(e.target.value)} placeholder="Ej: Córdoba" />
          </div>
        </div>
        <FieldError message={domicilioError} />

        <label className="check-chip" style={{ width: 'fit-content' }}>
          <input type="checkbox" checked={tieneObraSocial} onChange={(e) => setTieneObraSocial(e.target.checked)} />
          Tiene obra social
        </label>

        {tieneObraSocial && (
          <>
            <div className="form-group">
              <label className="form-label form-label--required">Obra Social</label>
              <select className="form-input" value={idFinanciador} onChange={(e) => setIdFinanciador(e.target.value)}>
                <option value="">Seleccionar obra social...</option>
                {financiadores.map(f => (
                  <option key={f.idfinanciador} value={f.idfinanciador}>{f.nombreComercial}</option>
                ))}
              </select>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
              <div className="form-group">
                <label className="form-label">Plan</label>
                <input className="form-input" type="text" value={plan} onChange={(e) => setPlan(e.target.value)} placeholder="Ej: 210" />
              </div>
              <div className="form-group">
                <label className="form-label form-label--required">N° de afiliado</label>
                <input className="form-input" type="text" value={numAfiliado} onChange={(e) => setNumAfiliado(e.target.value)} />
                <FieldError message={numAfiliadoError} />
              </div>
            </div>
          </>
        )}

        {error && (
          <div className="checkout-alert checkout-alert--danger" style={{ padding: '10px', borderRadius: 'var(--radius-md)', backgroundColor: '#fff2f0', border: '1px solid #ffccc7', color: '#ff4d4f', fontSize: '13px' }}>
            {error}
          </div>
        )}

        <div style={{ display: 'flex', gap: 'var(--space-2)', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
          <button className="btn btn--secondary" onClick={onClose} disabled={saving}>Cancelar</button>
          <button className="btn btn--primary" onClick={handleSave} disabled={!canSave || saving}>
            {saving ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </div>
    </div>
  )
}
