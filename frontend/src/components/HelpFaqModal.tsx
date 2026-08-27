function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function IconHelp({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <circle cx="12" cy="12" r="10" />
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

// Pure static FAQ content — split out of LandingPage.tsx and lazy-loaded (see App usage) so its
// markup never ships in the initial landing-page bundle, only fetched the first time someone
// actually opens "Ayuda".
export default function HelpFaqModal({ onClose }: { onClose: () => void }) {
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
      <div className="card mobile-modal-card" style={{
        maxWidth: '600px',
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
          <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}><IconHelp size={20} /> Ayuda y Preguntas Frecuentes (FAQ)</h3>
          <button onClick={onClose} className="btn btn--ghost btn--sm mobile-modal-close" style={{ fontSize: '16px', padding: '4px' }}><IconClose /></button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', textAlign: 'left', fontSize: 'var(--text-sm)', lineHeight: '1.5' }}>
          <div>
            <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>1. ¿Cómo me registro en la aplicación?</h4>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              El registro es sumamente sencillo. Podés iniciar sesión directamente con tu cuenta de Google haciendo clic en el botón <strong>"Iniciar sesión"</strong> en la parte superior derecha. Tu cuenta de paciente se creará automáticamente.
            </p>
          </div>
          <div>
            <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>2. ¿Cómo solicitar un turno?</h4>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Una vez que hayas iniciado sesión, navegá en la lista de profesionales en la página de inicio. Hacé clic en la tarjeta del profesional con el que quieras atenderte, seleccioná el día y horario disponible, completá los datos del formulario y hacé clic en "Confirmar reserva".
            </p>
          </div>
          <div>
            <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>3. ¿Cómo realizar el pago del turno?</h4>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Al reservar el turno, el sistema te redirigirá a Mercado Pago para abonar de forma segura. Si cerrás la pestaña sin abonar, podés ir a <strong>"Mis Turnos"</strong> desde tu menú de perfil en el Header y hacer clic en el botón azul <strong>"Pagar Turno"</strong> en cualquier momento.
            </p>
          </div>
          <div>
            <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>4. ¿Cómo cancelar un turno?</h4>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Si necesitás cancelar una reserva, abrí tu menú de perfil (haciendo clic en tu nombre en la parte superior derecha), seleccioná <strong>"Mis Turnos"</strong>, ubicá el turno correspondiente y hacé clic en el botón <strong>"Cancelar"</strong>.
            </p>
          </div>
          <div>
            <h4 style={{ fontWeight: 'bold', color: 'var(--color-primary)', margin: '0 0 var(--space-1)' }}>5. ¿Cómo unirse a la videollamada?</h4>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Una vez que el turno esté pagado y confirmado, se generará un link de Google Meet. Podés unirte directamente haciendo clic en el botón <strong>"Unirse a la llamada"</strong> en tu listado de turnos de hoy en la página de inicio, o en el modal <strong>"Mis Turnos"</strong> de tu perfil.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
