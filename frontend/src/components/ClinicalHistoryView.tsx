// La Historia Clínica (informes/seguimientos clínicos, con los requisitos legales de
// inalterabilidad de la Ley 26.529/27.706 — ver .agent/Etapas/09_cumplimiento_legal_historia_clinica.md)
// se va a construir más adelante. Mientras tanto, modificar los datos administrativos del
// paciente (DNI, domicilio, obra social) se sigue haciendo desde la sección Pacientes.
export default function ClinicalHistoryView() {
  return (
    <div className="card">
      <div className="card__header"><h2 className="card__title">Historia Clínica</h2></div>
      <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
        Próximamente — vamos a construir esta sección más adelante. Mientras tanto, podés modificar los datos de tus pacientes desde la sección Pacientes.
      </p>
    </div>
  )
}
