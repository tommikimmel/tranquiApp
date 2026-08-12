export default function GoogleCalendarConnectBanner({ connected, onConnect, onDisconnect }: { connected: boolean; onConnect: () => void; onDisconnect: () => void }) {
  return (
    <div className={`mp-connect-banner mp-connect-banner--google ${connected ? 'mp-connect-banner--connected' : ''}`} role={connected ? 'status' : 'alert'}>
      <div className="mp-connect-banner__top-row">
        <span className="mp-connect-banner__tag">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 12, height: 12 }}>
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          {connected ? 'INTEGRACIÓN ACTIVA' : 'INTEGRACIÓN DE AGENDA'}
        </span>
        <span className="mp-connect-banner__status">
          <span className="mp-connect-banner__status-dot" />
          {connected ? 'Google Calendar Conectado' : 'Sin vincular'}
        </span>
      </div>

      <div className="mp-connect-banner__body-wrapper">
        <div className="mp-connect-banner__icon">
          <img src="/logo-google-calendar.svg" alt="Google Calendar" className="mp-connect-banner__logo" />
        </div>
        <div className="mp-connect-banner__content">
          <h2 className="mp-connect-banner__title">
            {connected ? 'Google Calendar vinculado' : 'Conectá Google Calendar & Meet'}
          </h2>
          <p className="mp-connect-banner__body">
            {connected
              ? 'Tus sesiones virtuales generan videollamadas de Google Meet automáticamente y se sincronizan en tu agenda personal.'
              : 'Sincronizá tus turnos en tu agenda personal y generá reuniones virtuales de Google Meet de forma automática para cada consulta.'}
          </p>

          {!connected && (
            <div className="mp-connect-banner__features">
              <span className="mp-connect-banner__feature-chip">
                <svg className="mp-connect-banner__feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                Links de Meet automáticos
              </span>
              <span className="mp-connect-banner__feature-chip">
                <svg className="mp-connect-banner__feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                Sincronización 2-way
              </span>
              <span className="mp-connect-banner__feature-chip">
                <svg className="mp-connect-banner__feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                Recordatorios al paciente
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="mp-connect-banner__action">
        {connected ? (
          <button className="btn btn--ghost btn--sm" onClick={onDisconnect} style={{ color: 'var(--color-error)' }}>
            Desconectar Google Calendar
          </button>
        ) : (
          <button className="btn btn--primary btn--sm google-btn--connect" onClick={onConnect} id="btn-connect-google">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 14, height: 14 }}>
              <path d="M15 3h6v6M10 14L21 3M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6" />
            </svg>
            Vincular Google Calendar
          </button>
        )}
      </div>
    </div>
  )
}
