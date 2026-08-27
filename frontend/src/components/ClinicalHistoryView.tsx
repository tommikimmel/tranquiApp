// La Historia Clínica (informes/seguimientos clínicos, con los requisitos legales de
// inalterabilidad de la Ley 26.529/27.706 — ver .agent/Etapas/09_cumplimiento_legal_historia_clinica.md)
// se va a construir más adelante. Mientras tanto, modificar los datos administrativos del
// paciente (DNI, domicilio, obra social) se sigue haciendo desde la sección Pacientes.
export default function ClinicalHistoryView() {
  return (
    <div className="card">
      <div className="card__header"><h2 className="card__title">Historia Clínica</h2></div>
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
          Vamos a construir esta sección más adelante. Mientras tanto, podés modificar los datos de tus pacientes desde la sección Pacientes.
        </span>
      </div>
    </div>
  )
}
