import { LABORATORIOS_ARGENTINA } from '../utils/laboratorios'

// "Laboratorio" field on a receta's medication row — a strict dropdown over the curated
// LABORATORIOS_ARGENTINA list, same pattern as the Obra Social selects (LoginPage/CheckoutFlow).
// No free text: picking from a fixed list avoids typos/inconsistent spellings reaching a receta.
export default function LaboratorioAutocomplete({
  value,
  onChange
}: {
  value: string
  onChange: (value: string) => void
  placeholder?: string
}) {
  return (
    <select
      className="form-input"
      value={LABORATORIOS_ARGENTINA.includes(value) ? value : ''}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">Seleccionar laboratorio...</option>
      {LABORATORIOS_ARGENTINA.map((lab) => (
        <option key={lab} value={lab}>{lab}</option>
      ))}
    </select>
  )
}
