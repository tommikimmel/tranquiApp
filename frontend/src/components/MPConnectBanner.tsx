export default function MPConnectBanner({ connected, onConnect, onDisconnect }: { connected: boolean; onConnect: () => void; onDisconnect: () => void }) {
  return (
    <div className={`mp-connect-banner mp-connect-banner--mp ${connected ? 'mp-connect-banner--connected' : ''}`} role={connected ? 'status' : 'alert'}>
      <div className="mp-connect-banner__top-row">
        <span className="mp-connect-banner__tag">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 12, height: 12 }}>
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
          </svg>
          {connected ? 'INTEGRACIÓN ACTIVA' : 'INTEGRACIÓN DE PAGOS'}
        </span>
        <span className="mp-connect-banner__status">
          <span className="mp-connect-banner__status-dot" />
          {connected ? 'Mercado Pago Conectado' : 'Sin vincular'}
        </span>
      </div>

      <div className="mp-connect-banner__body-wrapper">
        <div className="mp-connect-banner__icon">
          <img src="/logo-mp-icon.png" alt="Mercado Pago" className="mp-connect-banner__logo" />
        </div>
        <div className="mp-connect-banner__content">
          <h2 className="mp-connect-banner__title">
            {connected ? 'Mercado Pago vinculado' : 'Conectá tu cuenta de Mercado Pago'}
          </h2>
          <p className="mp-connect-banner__body">
            {connected
              ? 'Los cobros de tus pacientes se acreditan de forma directa e instantánea en tu cuenta al confirmarse cada reserva.'
              : 'Recibí cobros de tus pacientes de forma automatizada y directa en tu cuenta bancaria o CVU, 100% libre de comisiones de plataforma.'}
          </p>

          {!connected && (
            <div className="mp-connect-banner__features">
              <span className="mp-connect-banner__feature-chip">
                <svg className="mp-connect-banner__feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                Cobros en tiempo real
              </span>
              <span className="mp-connect-banner__feature-chip">
                <svg className="mp-connect-banner__feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                0% comisiones Tranqui
              </span>
              <span className="mp-connect-banner__feature-chip">
                <svg className="mp-connect-banner__feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                Acreditación directa
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="mp-connect-banner__action">
        {connected ? (
          <button className="btn btn--ghost btn--sm" onClick={onDisconnect} style={{ color: 'var(--color-error)' }}>
            Desconectar Mercado Pago
          </button>
        ) : (
          <button className="btn btn--primary btn--sm mp-btn--connect" onClick={onConnect} id="btn-connect-mp">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: 14, height: 14 }}>
              <path d="M15 3h6v6M10 14L21 3M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6" />
            </svg>
            Vincular Mercado Pago
          </button>
        )}
      </div>
    </div>
  )
}
