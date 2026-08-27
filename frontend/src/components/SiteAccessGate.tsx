import { useEffect, useState, type ReactNode, type FormEvent } from 'react'
import { api } from '../api/api'

// Temporary site-wide password gate ("por el momento... para que clientes externos no accedan a
// la página"). Wraps the whole app in main.tsx so nothing — landing page, login, dashboard —
// renders until the visitor's browser has the SITE-ACCESS cookie the backend's SiteAccessFilter
// checks on every request. If SITE_ACCESS_PASSWORD isn't set on the backend, /estado always
// reports autorizado:true and this component just renders children immediately.
export default function SiteAccessGate({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<'checking' | 'locked' | 'unlocked'>('checking')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    api.getSiteAccessStatus()
      .then((res: any) => setStatus(res?.autorizado ? 'unlocked' : 'locked'))
      .catch(() => setStatus('locked'))
  }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!password.trim() || submitting) return
    setSubmitting(true)
    setError('')
    try {
      await api.verificarSiteAccess(password.trim())
      setStatus('unlocked')
    } catch {
      setError('Contraseña incorrecta.')
    } finally {
      setSubmitting(false)
    }
  }

  if (status === 'unlocked') return <>{children}</>

  if (status === 'checking') {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#fafafa' }}>
        <div className="checkout-spinner" />
      </div>
    )
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: '#fafafa',
      padding: '24px',
    }}>
      <form
        onSubmit={handleSubmit}
        className="site-access-card"
        style={{
          width: '100%',
          maxWidth: '360px',
          background: '#ffffff',
          border: '1px solid #e5e7eb',
          borderRadius: '12px',
          boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
          padding: '32px 28px',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
          textAlign: 'center',
        }}
      >
        <img src="/tranqui-icon.png" alt="" aria-hidden="true" style={{ width: 40, height: 40, margin: '0 auto' }} />
        <div>
          <h1 style={{ margin: 0, fontSize: '18px', fontWeight: 700, color: '#111827' }}>Acceso restringido</h1>
          <p style={{ margin: '6px 0 0', fontSize: '13px', color: '#6b7280' }}>
            Este sitio está temporalmente disponible solo para personas autorizadas.
          </p>
        </div>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Contraseña de acceso"
          autoFocus
          style={{
            padding: '10px 14px',
            borderRadius: '8px',
            border: '1.5px solid #e5e7eb',
            fontSize: '14px',
            outline: 'none',
          }}
        />
        {error && <p style={{ margin: 0, fontSize: '13px', color: '#dc2626' }}>{error}</p>}
        <button
          type="submit"
          disabled={submitting || !password.trim()}
          className="btn btn--primary"
          style={{ width: '100%', justifyContent: 'center' }}
        >
          {submitting ? 'Verificando...' : 'Ingresar'}
        </button>
      </form>
    </div>
  )
}
