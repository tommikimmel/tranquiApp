import { Icon } from './Icon'

export default function StatsOverview({ stats, period, onPeriodChange }: {
  stats: any;
  period: 'DIARIO' | 'SEMANAL' | 'MENSUAL';
  onPeriodChange: (periodo: 'DIARIO' | 'SEMANAL' | 'MENSUAL') => void;
}) {
  // Period-dependent wording for each card's label — the underlying numbers/badges already
  // come scoped to `period` from the backend (MedicoService#obtenerStats).
  const periodLabel = period === 'DIARIO' ? 'hoy' : period === 'SEMANAL' ? 'esta semana' : 'este mes'

  const periodToggle = (
    <div className="dashboard-home-seg" role="tablist" aria-label="Período de las métricas">
      <span onClick={() => onPeriodChange('DIARIO')} className={period === 'DIARIO' ? 'active' : ''} role="tab" aria-selected={period === 'DIARIO'}>
        Diario
      </span>
      <span onClick={() => onPeriodChange('SEMANAL')} className={period === 'SEMANAL' ? 'active' : ''} role="tab" aria-selected={period === 'SEMANAL'}>
        Semanal
      </span>
      <span onClick={() => onPeriodChange('MENSUAL')} className={period === 'MENSUAL' ? 'active' : ''} role="tab" aria-selected={period === 'MENSUAL'}>
        Mensual
      </span>
    </div>
  )

  if (!stats) {
    return (
      <div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 'var(--space-3)' }}>{periodToggle}</div>
        <div className="stats-grid">
          {[1, 2, 3, 4].map((i) => (
            <article key={i} className="stat-card" style={{ opacity: 0.6 }}>
              <div style={{ height: '24px', backgroundColor: 'var(--color-border)', width: '60%', borderRadius: '4px', marginBottom: '8px' }} />
              <div style={{ height: '32px', backgroundColor: 'var(--color-border)', width: '40%', borderRadius: '4px' }} />
            </article>
          ))}
        </div>
      </div>
    )
  }

  // "igual que..." (parity) strings come straight from the backend (MedicoService) for both
  // sessionsToday and noShows — surfaced here as a neutral "flat" badge instead of forcing an
  // up/down arrow onto a change that isn't actually up or down.
  const getChangeCls = (changeStr: string) => {
    if (!changeStr) return 'stat-card__change--up'
    if (changeStr.includes('igual')) return 'stat-card__change--flat'
    return changeStr.startsWith('-') ? 'stat-card__change--down' : 'stat-card__change--up'
  }

  const renderIcon = (changeStr: string) => {
    if (!changeStr || changeStr.includes('igual')) return null
    if (changeStr.includes('Política')) return <Icon.ArrowUp />
    return changeStr.startsWith('-') ? <Icon.ArrowDown /> : <Icon.ArrowUp />
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 'var(--space-3)' }}>{periodToggle}</div>
      <div className="stats-grid">
        <article className="stat-card stat-card--primary">
          <div className="stat-card__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
              <line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
            </svg>
          </div>
          <div className="stat-card__value">${(stats.earningsThisWeek || 0).toLocaleString('es-AR')}</div>
          <div className="stat-card__label">Liquidado {periodLabel}</div>
          <div className={`stat-card__change ${getChangeCls(stats.earningsThisWeekChange)}`}>
            {renderIcon(stats.earningsThisWeekChange)} {stats.earningsThisWeekChange}
          </div>
        </article>

        <article className="stat-card">
          <div className="stat-card__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
              <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" />
              <line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
            </svg>
          </div>
          <div className="stat-card__value">{stats.sessionsToday}</div>
          <div className="stat-card__label">Sesiones {periodLabel}</div>
          <div className={`stat-card__change ${getChangeCls(stats.sessionsTodayChange)}`}>
            {renderIcon(stats.sessionsTodayChange)} {stats.sessionsTodayChange}
          </div>
        </article>

        <article className="stat-card">
          <div className="stat-card__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
            </svg>
          </div>
          <div className="stat-card__value">{stats.activePatients}</div>
          <div className="stat-card__label">Pacientes activos</div>
          <div className="stat-card__change stat-card__change--up">
            <Icon.ArrowUp /> {stats.activePatientsChange}
          </div>
        </article>

        <article className="stat-card">
          <div className="stat-card__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
              <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
            </svg>
          </div>
          <div className="stat-card__value">{stats.noShowsThisMonth}</div>
          <div className="stat-card__label">Inasistencias a turnos {periodLabel}</div>
          <div className={`stat-card__change ${getChangeCls(stats.noShowsChange)}`}>
            {renderIcon(stats.noShowsChange)} {stats.noShowsChange}
          </div>
        </article>
      </div>
    </div>
  )
}
