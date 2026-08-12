import { useMemo, useRef, useState } from 'react'
import { searchLaboratorios } from '../utils/laboratorios'

// Text input + fuzzy-match dropdown for the "Laboratorio" field on a receta's medication row.
// Tolerates accents, b/v mix-ups and small typos (see utils/laboratorios) so the médico can
// find "Bagó" by typing "Vago" or "Bago" without a tilde. Free text is still allowed — if the
// médico's laboratorio isn't in the curated list, whatever they type is kept as-is.
export default function LaboratorioAutocomplete({
  value,
  onChange,
  placeholder = 'Ej: Bagó'
}: {
  value: string
  onChange: (value: string) => void
  placeholder?: string
}) {
  const [showDropdown, setShowDropdown] = useState(false)
  const blurTimeout = useRef<ReturnType<typeof setTimeout> | null>(null)

  const suggestions = useMemo(() => searchLaboratorios(value || '', 6), [value])

  const handleBlur = () => {
    // Delay closing so a click on a suggestion (which also fires blur) still registers.
    blurTimeout.current = setTimeout(() => setShowDropdown(false), 150)
  }

  const handleFocus = () => {
    if (blurTimeout.current) clearTimeout(blurTimeout.current)
    setShowDropdown(true)
  }

  const select = (lab: string) => {
    onChange(lab)
    setShowDropdown(false)
  }

  return (
    <div style={{ position: 'relative' }}>
      <input
        className="form-input"
        type="text"
        placeholder={placeholder}
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
        onFocus={handleFocus}
        onBlur={handleBlur}
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="off"
        spellCheck={false}
      />
      {showDropdown && value && value.trim() && suggestions.length > 0 && (
        <div style={{
          position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 90,
          backgroundColor: '#ffffff', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)', boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
          maxHeight: '200px', overflowY: 'auto', marginTop: '4px'
        }}>
          {suggestions.map((lab) => (
            <div
              key={lab}
              // onMouseDown fires before the input's onBlur, so the click reaches here instead
              // of being swallowed by the dropdown closing first.
              onMouseDown={(e) => { e.preventDefault(); select(lab) }}
              style={{ padding: '8px 12px', cursor: 'pointer', fontSize: 'var(--text-sm)' }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--neutral-50)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              {lab}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
