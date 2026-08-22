import { Icon } from './Icon'
import type { NavSection } from '../types/dashboard'

export default function Sidebar({
  activeNav,
  onNavChange,
  medicoInfo,
  hasUnreadChats,
  mobileOpen,
  onCloseMobile
}: {
  activeNav: NavSection;
  onNavChange: (s: NavSection) => void;
  medicoInfo: any;
  hasUnreadChats: boolean;
  mobileOpen?: boolean;
  onCloseMobile?: () => void;
}) {
  const navItems = [
    { id: 'dashboard' as NavSection, label: 'Inicio', Icon: Icon.Dashboard },
    { id: 'agenda' as NavSection, label: 'Agenda', Icon: Icon.Calendar },
    { id: 'patients' as NavSection, label: 'Pacientes', Icon: Icon.Users },
    { id: 'clinical-history' as NavSection, label: 'Historia Clínica', Icon: Icon.ClinicalRecord },
    { id: 'prescriptions' as NavSection, label: 'Recetas', Icon: Icon.Prescription },
    { id: 'honorarios' as NavSection, label: 'Honorarios y servicios', Icon: Icon.DollarSign },
    { id: 'settings' as NavSection, label: 'Configuración', Icon: Icon.Settings },
  ]

  const handleNavClick = (id: NavSection) => {
    onNavChange(id)
    if (onCloseMobile) onCloseMobile()
  }

  const doctorName = medicoInfo ? `${medicoInfo.nombre || ''} ${medicoInfo.apellido || ''}`.trim() : 'Médico';
  const doctorInitials = doctorName.split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase() || 'DR';

  return (
    <>
      {mobileOpen && <div className="sidebar-backdrop" onClick={onCloseMobile} />}
      <aside className={`sidebar ${mobileOpen ? 'sidebar--open' : ''}`}>
        <div className="sidebar__logo">
          <div
            className="sidebar__logo-btn"
            style={{ cursor: 'pointer' }}
            onClick={() => handleNavClick('dashboard')}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleNavClick('dashboard'); } }}
            role="button"
            tabIndex={0}
            aria-label="Ir al inicio"
          >
            <img src="/tranqui-icon.png" alt="Tranqui" className="sidebar__logo-img" />
            <span className="sidebar__logo-text">tranqui</span>
          </div>
          {onCloseMobile && (
            <button
              className="sidebar__mobile-close"
              onClick={onCloseMobile}
              aria-label="Cerrar menú"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>

        <nav className="sidebar__nav" role="navigation" aria-label="Navegación principal">
          <span className="sidebar__nav-section-title">Menú Principal</span>
          {navItems.map(({ id, label, Icon: NavIcon }) => (
            <button
              key={id}
              className={`sidebar__nav-item ${activeNav === id ? 'active' : ''}`}
              onClick={() => handleNavClick(id)}
              aria-current={activeNav === id ? 'page' : undefined}
              aria-label={label}
            >
              <span className="nav-icon"><NavIcon /></span>
              <span className="sidebar__nav-label">{label}</span>
              {id === 'patients' && hasUnreadChats && (
                <span className="sidebar__badge-pulse" />
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar__footer">
          <div
            className="sidebar__user"
            role="button"
            tabIndex={0}
            onClick={() => handleNavClick('settings')}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleNavClick('settings') } }}
          >
            {medicoInfo?.fotoUrl ? (
              <img src={medicoInfo.fotoUrl} alt="" className="sidebar__avatar" />
            ) : (
              <div className="sidebar__avatar">{doctorInitials}</div>
            )}
            <div className="sidebar__user-info">
              <div className="sidebar__user-name">{doctorName}</div>
              <div className="sidebar__user-role">
                <span className="sidebar__user-dot" />
                {medicoInfo?.specialty || 'Profesional'}
              </div>
            </div>
          </div>
        </div>
      </aside>
    </>
  )
}
