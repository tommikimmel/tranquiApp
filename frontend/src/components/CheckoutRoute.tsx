import { useState, useEffect, lazy } from 'react'
import { Navigate, useNavigate, useLocation, useParams } from 'react-router-dom'
import { api } from '../api/api'
import { getDoctorSlug } from '../utils/dashboardHelpers'
import type { CheckoutTarget } from '../types/checkout'

const CheckoutFlow = lazy(() => import('./CheckoutFlow'))

// ── /reserva/:proId route — resolves the professional either from the
// navigation state (fast path, set by handleBook on click) or by fetching
// the public medicos list and matching the id or SEO slug (direct URL load / refresh).
export default function CheckoutRoute({ currentUser, loadingSession }: { currentUser: any; loadingSession: boolean }) {
  const { proId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const stateTarget = (location.state as CheckoutTarget | null) || null
  const [target, setTarget] = useState<CheckoutTarget | null>(stateTarget)
  const [loading, setLoading] = useState(!stateTarget)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    if (stateTarget) return
    let cancelled = false
    setLoading(true)
    api.getMedicos()
      .then((res: any[]) => {
        if (cancelled) return
        const param = (proId || '').trim().toLowerCase()
        const m = (res || []).find((x: any) => {
          if (String(x.id) === param) return true
          const slug = getDoctorSlug(x.name)
          if (slug === param) return true
          if (slug.replace(/^(dr|dra|lic)-/, '') === param.replace(/^(dr|dra|lic)-/, '')) return true
          return false
        })
        if (!m) {
          setNotFound(true)
          return
        }
        setTarget({
          id: String(m.id),
          name: m.name,
          degree: m.degree,
          specialty: m.specialty,
          matricula: m.matricula,
          price: m.price,
          nextSlot: m.nextSlot || '16:00',
          nextSlotDay: m.nextSlotDay || 'Hoy',
          fotoUrl: m.fotoUrl,
          telefono: m.telefono,
          emailContacto: m.emailContacto,
          publicaciones: m.publicaciones,
          domicilioAtencion: m.domicilioAtencion,
          domicilioLat: m.domicilioLat,
          domicilioLng: m.domicilioLng,
          domicilioAtencionTorre: m.domicilioAtencionTorre,
          domicilioAtencionPiso: m.domicilioAtencionPiso,
          domicilioAtencionDepto: m.domicilioAtencionDepto,
          domicilioAtencionBarrio: m.domicilioAtencionBarrio,
          ofreceOnline: m.ofreceOnline,
          ofrecePresencial: m.ofrecePresencial,
          descripcionPerfil: m.descripcionPerfil,
          pacientesAtiende: m.pacientesAtiende,
          institucionFormacion: m.institucionFormacion,
          aniosExperiencia: m.aniosExperiencia,
          tags: m.tags,
          experiencia: m.experiencia,
          redesSociales: m.redesSociales,
          tariffs: m.tariffs,
        })
      })
      .catch(() => setNotFound(true))
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [proId])

  if (loadingSession || loading) {
    return (
      <div style={{ textAlign: 'center', padding: 'var(--space-20)' }}>
        <div className="checkout-spinner" style={{ margin: '0 auto' }} />
      </div>
    )
  }
  if (!currentUser) {
    return <Navigate to="/login" replace />
  }
  if (notFound || !target) {
    return <Navigate to="/" replace />
  }

  return (
    <CheckoutFlow
      professional={target}
      onBack={() => navigate('/')}
      onComplete={() => navigate('/')}
    />
  )
}
