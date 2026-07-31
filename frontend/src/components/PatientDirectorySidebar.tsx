import { type Patient, getInitials } from '../hooks/usePatients'

// Patient directory sidebar: search by nombre/email/dni + selectable list.
// Shared between PatientsView (chat) and ClinicalHistoryView (ficha/seguimiento/informes)
// so both entry points list/search patients identically.
export default function PatientDirectorySidebar({
  title = 'Mis Pacientes',
  searchPlaceholder = 'Buscar por nombre, email, DNI...',
  patients,
  filteredPatients,
  loadingPatients,
  searchQuery,
  onSearchQueryChange,
  selectedPatient,
  onSelectPatient,
  unreadCounts,
}: {
  title?: string
  searchPlaceholder?: string
  patients: Patient[]
  filteredPatients: Patient[]
  loadingPatients: boolean
  searchQuery: string
  onSearchQueryChange: (q: string) => void
  selectedPatient: Patient | null
  onSelectPatient: (p: Patient) => void
  unreadCounts?: Record<number, number>
}) {
  return (
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
          {title}
        </div>
        <input
          type="text"
          placeholder={searchPlaceholder}
          value={searchQuery}
          onChange={(e) => onSearchQueryChange(e.target.value)}
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
            const unread = unreadCounts?.[patient.id] || 0

            return (
              <div
                key={patient.id}
                onClick={() => onSelectPatient(patient)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-3)',
                  padding: 'var(--space-3) var(--space-4)',
                  cursor: 'pointer',
                  borderTop: '1px solid var(--color-border)',
                  borderRight: '1px solid var(--color-border)',
                  borderBottom: '1px solid var(--color-border)',
                  borderLeft: isSelected
                    ? '4px solid var(--color-primary)'
                    : isHighPriority
                      ? '4px solid #f59e0b'
                      : '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  backgroundColor: isSelected ? 'var(--green-50)' : '#ffffff',
                  transition: 'all 0.2s',
                  position: 'relative',
                  margin: 'var(--space-2) var(--space-3)',
                  boxShadow: isSelected ? 'var(--shadow-sm)' : 'none'
                }}
              >
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '50%',
                  backgroundColor: isHighPriority ? '#fef3c7' : 'var(--neutral-100)',
                  color: isHighPriority ? 'var(--color-warning)' : 'var(--color-text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 'var(--font-weight-semi)',
                  fontSize: 'var(--text-xs)',
                  border: '1px solid var(--color-border)'
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
                    textOverflow: 'ellipsis',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                  }}>
                    <span>{patient.nombre}</span>
                    {unread > 0 && (
                      <span style={{
                        backgroundColor: 'var(--color-error)',
                        color: 'white',
                        borderRadius: '50%',
                        width: '18px',
                        height: '18px',
                        fontSize: '9px',
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 'bold',
                        marginLeft: '4px',
                        flexShrink: 0
                      }}>
                        {unread}
                      </span>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: '4px', alignItems: 'center', marginTop: '2px', flexWrap: 'wrap' }}>
                    {patient.sinTurno && (
                      <span className="badge badge--neutral" style={{ fontSize: '8.5px', padding: '1px 5px', backgroundColor: '#e2e8f0', color: '#475569', display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 9, height: 9 }}>
                          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                        </svg>
                        Sin Turno
                      </span>
                    )}
                    {isHighPriority && (
                      <span className="badge badge--warning" style={{ fontSize: '8px', padding: '0px 3px' }}>
                        Urgente
                      </span>
                    )}
                    <span style={{ fontSize: '10px', color: 'var(--color-text-secondary)' }}>
                      Última: {patient.ultimaVisita || 'Ninguna'}
                    </span>
                  </div>
                </div>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
