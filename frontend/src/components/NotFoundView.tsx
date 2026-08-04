import { useNavigate } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export default function NotFoundView({ currentUser }: { currentUser?: any }) {
  useDocumentTitle('Página no encontrada — Tranqui')
  const navigate = useNavigate()

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      backgroundColor: 'var(--color-bg, #fafaf9)',
      color: 'var(--color-text-primary, #1c1917)',
      fontFamily: 'var(--font-body, system-ui, sans-serif)'
    }}>
      {/* Header */}
      <header style={{
        padding: 'var(--space-4) var(--space-8)',
        borderBottom: '1px solid var(--color-border, #e7e5e4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: 'var(--color-surface, #ffffff)'
      }}>
        <button
          onClick={() => navigate('/')}
          style={{
            border: 'none',
            background: 'none',
            fontFamily: 'var(--font-heading, serif)',
            fontSize: 'var(--text-xl, 20px)',
            fontWeight: 'bold',
            color: 'var(--color-primary, #00a650)',
            cursor: 'pointer',
            padding: 0
          }}
        >
          tranqui
        </button>
        <button
          className="btn btn--sm btn--secondary"
          onClick={() => navigate(currentUser ? '/panel' : '/login')}
        >
          {currentUser ? 'Ir a mi Panel' : 'Iniciar Sesión'}
        </button>
      </header>

      {/* 404 Body */}
      <main style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'var(--space-8) var(--space-4)',
        textAlign: 'center'
      }}>
        <div style={{
          maxWidth: '520px',
          width: '100%',
          backgroundColor: 'var(--color-surface, #ffffff)',
          border: '1px solid var(--color-border, #e7e5e4)',
          borderRadius: 'var(--radius-xl, 16px)',
          padding: 'var(--space-8) var(--space-6)',
          boxShadow: 'var(--shadow-md, 0 4px 6px -1px rgba(0,0,0,0.05))',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 'var(--space-4)'
        }}>
          {/* 404 Badge & Graphic */}
          <div style={{ position: 'relative' }}>
            <div style={{
              fontSize: '84px',
              fontWeight: '900',
              fontFamily: 'var(--font-heading, serif)',
              lineHeight: 1,
              letterSpacing: '-0.04em',
              background: 'linear-gradient(135deg, var(--color-primary, #00a650) 0%, #059669 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              opacity: 0.95
            }}>
              404
            </div>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              backgroundColor: 'rgba(0, 166, 80, 0.1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--color-primary, #00a650)',
              margin: '-12px auto 0'
            }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 26, height: 26 }}>
                <circle cx="12" cy="12" r="10" />
                <path d="M16 16s-1.5-2-4-2-4 2-4 2" />
                <line x1="9" y1="9" x2="9.01" y2="9" />
                <line x1="15" y1="9" x2="15.01" y2="9" />
              </svg>
            </div>
          </div>

          <h1 style={{
            fontFamily: 'var(--font-heading, serif)',
            fontSize: 'var(--text-2xl, 24px)',
            fontWeight: 'bold',
            margin: 0,
            color: 'var(--color-text-primary, #1c1917)'
          }}>
            Página no encontrada
          </h1>

          <p style={{
            fontSize: 'var(--text-sm, 14px)',
            color: 'var(--color-text-secondary, #78716c)',
            margin: 0,
            lineHeight: 1.6
          }}>
            La dirección web que ingresaste no existe, ha sido movida o la ruta especificada es incorrecta.
          </p>

          {/* Buttons */}
          <div style={{
            display: 'flex',
            gap: 'var(--space-3)',
            marginTop: 'var(--space-4)',
            width: '100%',
            justifyContent: 'center',
            flexWrap: 'wrap'
          }}>
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => navigate('/')}
              style={{ padding: '10px 20px', minWidth: '160px' }}
            >
              Volver al Inicio
            </button>

            {currentUser && (
              <button
                type="button"
                className="btn btn--secondary"
                onClick={() => navigate('/panel')}
                style={{ padding: '10px 20px', minWidth: '160px' }}
              >
                Ir a Mi Panel
              </button>
            )}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer style={{
        padding: 'var(--space-4)',
        textAlign: 'center',
        fontSize: 'var(--text-xs, 12px)',
        color: 'var(--color-text-secondary, #a8a29e)',
        borderTop: '1px solid var(--color-border, #f5f5f4)'
      }}>
        © {new Date().getFullYear()} Tranqui App. Todos los derechos reservados.
      </footer>
    </div>
  )
}
