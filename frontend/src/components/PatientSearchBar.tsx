import { useEffect, useRef, useState } from 'react'
import { type Patient, getInitials } from '../hooks/usePatients'

// Search-first patient finder used only by ClinicalHistoryView: a single input
// (name or DNI) with a live autocomplete dropdown of matches. Nothing is shown
// until the user explicitly clicks a result — no auto-navigation on typing.
// Distinct from PatientDirectorySidebar (the always-visible full list used by
// PatientsView), which stays untouched. They share only the initials helper
// from usePatients.ts.
export default function PatientSearchBar({
  compact = false,
  placeholder = 'Buscar paciente por nombre o DNI...',
  patients,
  filteredPatients,
  loadingPatients,
  searchQuery,
  onSearchQueryChange,
  selectedPatient,
  onSelectPatient,
}: {
  compact?: boolean
  placeholder?: string
  patients: Patient[]
  filteredPatients: Patient[]
  loadingPatients: boolean
  searchQuery: string
  onSearchQueryChange: (q: string) => void
  selectedPatient: Patient | null
  onSelectPatient: (p: Patient) => void
}) {
  const [isFocused, setIsFocused] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // Close the dropdown on outside click.
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsFocused(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const trimmedQuery = searchQuery.trim()
  const showDropdown = isFocused && trimmedQuery !== ''

  const handleSelect = (patient: Patient) => {
    onSelectPatient(patient)
    onSearchQueryChange('')
    setIsFocused(false)
  }

  return (
    // `width: 100%` (rather than a hard-coded compact px width) with maxWidth keeps this from
    // overflowing its container on narrow phones (~360px) where a fixed 360px box wouldn't fit
    // beside its own padding/margins.
    <div ref={containerRef} style={{ position: 'relative', width: '100%', maxWidth: compact ? '360px' : '480px' }}>
      <div style={{ position: 'relative' }}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{
          width: 16, height: 16,
          position: 'absolute',
          left: 'var(--space-3)',
          top: '50%',
          transform: 'translateY(-50%)',
          color: 'var(--color-text-secondary)',
          pointerEvents: 'none'
        }}>
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="text"
          placeholder={placeholder}
          value={searchQuery}
          onChange={(e) => onSearchQueryChange(e.target.value)}
          onFocus={() => setIsFocused(true)}
          className="form-input patient-search-input"
          style={{
            width: '100%',
            paddingLeft: 'var(--space-8)',
            height: compact ? '36px' : '44px',
            fontSize: compact ? 'var(--text-xs)' : 'var(--text-sm)'
          }}
        />
      </div>

      {showDropdown && (
        <div style={{
          position: 'absolute',
          top: compact ? '40px' : '48px',
          left: 0,
          right: 0,
          backgroundColor: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)',
          boxShadow: 'var(--shadow-md)',
          zIndex: 200,
          maxHeight: '320px',
          overflowY: 'auto'
        }}>
          {loadingPatients ? (
            <div style={{ padding: 'var(--space-4)', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)' }}>
              Cargando pacientes...
            </div>
          ) : patients.length === 0 ? (
            <div style={{ padding: 'var(--space-4)', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)' }}>
              No hay pacientes cargados.
            </div>
          ) : filteredPatients.length === 0 ? (
            <div style={{ padding: 'var(--space-4)', textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)' }}>
              No se encontraron pacientes.
            </div>
          ) : (
            filteredPatients.map((patient) => {
              const isSelected = selectedPatient?.id === patient.id
              const isHighPriority = patient.prioridadClinica === 'PRIORIDAD_ALTA'
              return (
                <div
                  key={patient.id}
                  onClick={() => handleSelect(patient)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--space-3)',
                    padding: 'var(--space-2) var(--space-4)',
                    cursor: 'pointer',
                    backgroundColor: isSelected ? 'var(--green-50)' : 'transparent',
                    borderBottom: '1px solid var(--color-border)',
                    transition: 'background-color 0.15s'
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) e.currentTarget.style.backgroundColor = 'var(--neutral-50)'
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) e.currentTarget.style.backgroundColor = 'transparent'
                  }}
                >
                  <div style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '50%',
                    backgroundColor: isHighPriority ? '#fef3c7' : 'var(--neutral-100)',
                    color: isHighPriority ? 'var(--color-warning)' : 'var(--color-text-secondary)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 'var(--font-weight-semi)',
                    fontSize: '11px',
                    border: '1px solid var(--color-border)',
                    flexShrink: 0
                  }}>
                    {getInitials(patient.nombre)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontSize: 'var(--text-sm)',
                      fontWeight: 'var(--font-weight-semi)',
                      color: 'var(--color-text-primary)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis'
                    }}>
                      {patient.nombre}
                      {isHighPriority && (
                        <span className="badge badge--warning" style={{ fontSize: '8px', padding: '0px 3px', marginLeft: 'var(--space-2)' }}>
                          Urgente
                        </span>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 'var(--space-2)', fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                      <span>DNI: {patient.dni || 'No cargado'}</span>
                      <span>·</span>
                      <span>Última visita: {patient.ultimaVisita || 'Ninguna'}</span>
                    </div>
                  </div>
                </div>
              )
            })
          )}
        </div>
      )}
    </div>
  )
}
