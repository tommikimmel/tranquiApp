import React, { useState, useEffect, useMemo, Suspense, lazy } from 'react'
import { Routes, Route, Navigate, useNavigate, useLocation, useParams } from 'react-router-dom'
import './styles/index.css'
import './styles/dashboard.css'
// LandingPage stays a static import — it's what almost every first-time visitor (anonymous
// patients hitting "/") needs immediately, so there's nothing to gain from lazy-loading it.
// Everything below is only needed by a subset of visitors (professionals, admins, someone
// mid-checkout), so splitting them out of the main chunk shrinks what a random patient
// downloads before first paint.
import LandingPage from './components/LandingPage'
const CheckoutFlow = lazy(() => import('./components/CheckoutFlow'))
const LoginPage = lazy(() => import('./components/LoginPage'))
const PatientsView = lazy(() => import('./components/PatientsView'))
const ClinicalHistoryView = lazy(() => import('./components/ClinicalHistoryView'))
const VisitorsView = lazy(() => import('./components/VisitorsView'))
const AdminDashboard = lazy(() => import('./components/AdminDashboard'))
import AddressMapPicker from './components/AddressMapPicker'
import { api } from './api/api'
import { useAlert } from './context/AlertContext'
import { useDocumentTitle } from './hooks/useDocumentTitle'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

interface CheckoutTarget {
  id: string
  name: string
  degree: string
  specialty: string
  matricula: string
  price: number
  nextSlot: string
  nextSlotDay: string
  fotoUrl?: string
  domicilioAtencion?: string
  domicilioLat?: number | null
  domicilioLng?: number | null
  ofreceOnline?: boolean
  ofrecePresencial?: boolean
  descripcionPerfil?: string
  pacientesAtiende?: string[]
  institucionFormacion?: string
  aniosExperiencia?: number | null
  tags?: string[]
  experiencia?: string
  redesSociales?: {
    instagram?: string
    facebook?: string
    linkedin?: string
    sitioWeb?: string
  }
}

// ── /reserva/:proId route — resolves the professional either from the
// navigation state (fast path, set by handleBook on click) or by fetching
// the public medicos list and matching the id (direct URL load / refresh).
function CheckoutRoute({ currentUser, loadingSession }: { currentUser: any; loadingSession: boolean }) {
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
        const m = (res || []).find((x: any) => String(x.id) === proId)
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
          domicilioAtencion: m.domicilioAtencion,
          domicilioLat: m.domicilioLat,
          domicilioLng: m.domicilioLng,
          ofreceOnline: m.ofreceOnline,
          ofrecePresencial: m.ofrecePresencial,
          descripcionPerfil: m.descripcionPerfil,
          pacientesAtiende: m.pacientesAtiende,
          institucionFormacion: m.institucionFormacion,
          aniosExperiencia: m.aniosExperiencia,
          tags: m.tags,
          experiencia: m.experiencia,
          redesSociales: m.redesSociales,
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

// ── Types ──────────────────────────────────────────────────────
interface Appointment {
  id: string
  patientName: string
  hour: string
  ampm: string
  type: string
  status: 'confirmed' | 'pending' | 'completed'
  meetLink: string
}

// A médico's personal Google Calendar event (read-only, never a Turno) — see
// GoogleCalendarSyncService.obtenerEventosExternosCacheados on the backend.
interface ExternalEvent {
  id: string
  title: string
  fecha: string
  hour: string
  endHour: string
  allDay: boolean
}



type NavSection = 'dashboard' | 'agenda' | 'patients' | 'clinical-history' | 'prescriptions' | 'visitors' | 'payments' | 'settings'

// ── Mock Data (Removido ya que se usan APIs reales) ───────────────────────────────────

// ── Icons (inline SVG) ─────────────────────────────────────────
const Icon = {
  Dashboard: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" />
    </svg>
  ),
  Calendar: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  ),
  Users: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  ),
  CreditCard: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="4" width="22" height="16" rx="2" /><line x1="1" y1="10" x2="23" y2="10" />
    </svg>
  ),
  Settings: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  ),
  ClinicalRecord: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
      <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
      <line x1="9" y1="12" x2="15" y2="12" /><line x1="9" y1="16" x2="13" y2="16" />
    </svg>
  ),
  Video: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" />
    </svg>
  ),
  Building: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="4" y="2" width="16" height="20" rx="1" />
      <line x1="9" y1="7" x2="9" y2="7.01" /><line x1="15" y1="7" x2="15" y2="7.01" />
      <line x1="9" y1="11" x2="9" y2="11.01" /><line x1="15" y1="11" x2="15" y2="11.01" />
      <line x1="9" y1="15" x2="9" y2="15.01" /><line x1="15" y1="15" x2="15" y2="15.01" />
      <path d="M9 22v-4h6v4" />
    </svg>
  ),
  ArrowUp: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 12, height: 12 }}>
      <polyline points="18 15 12 9 6 15" />
    </svg>
  ),
  ArrowDown: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 12, height: 12 }}>
      <polyline points="6 9 12 15 18 9" />
    </svg>
  ),
  Check: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 18, height: 18 }}>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  ),
  AlertTriangle: ({ size = 20 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: size, height: size }}>
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  ),
  Plus: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 16, height: 16 }}>
      <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  ),
  Eye: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  ),
  EyeOff: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  ),
  Bell: ({ hasUnread }: { hasUnread?: boolean }) => (
    <div style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: 20, height: 20 }}>
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      {hasUnread && (
        <span style={{
          position: 'absolute',
          top: '0px',
          right: '0px',
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          backgroundColor: '#ef4444',
          border: '1px solid white'
        }} />
      )}
    </div>
  ),
  Prescription: () => (
    <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <path d="M9 15l2 2 4-4" />
    </svg>
  ),
  Trash: ({ size = 14 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <line x1="10" y1="11" x2="10" y2="17" /><line x1="14" y1="11" x2="14" y2="17" />
    </svg>
  ),
  Activity: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
    </svg>
  ),
  Clipboard: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
      <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
    </svg>
  ),
  FileText: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><line x1="10" y1="9" x2="8" y2="9" />
    </svg>
  ),
  CalendarCheck: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
      <path d="M9 16l2 2 4-4" />
    </svg>
  ),
  CalendarX: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
      <line x1="10" y1="14" x2="14" y2="18" /><line x1="14" y1="14" x2="10" y2="18" />
    </svg>
  ),
  MessageCircle: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
    </svg>
  ),
  BellSimple: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" style={{ width: size, height: size }}>
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  ),
  User: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
    </svg>
  ),
  Globe: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <circle cx="12" cy="12" r="10" />
      <line x1="2" y1="12" x2="22" y2="12" />
      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
    </svg>
  ),
  DollarSign: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <line x1="12" y1="1" x2="12" y2="23" />
      <path d="M17 5.5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H7" />
    </svg>
  ),
  Plug: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <path d="M9 2v5" /><path d="M15 2v5" />
      <path d="M6 7h12v5a6 6 0 0 1-6 6 6 6 0 0 1-6-6V7z" />
      <path d="M12 18v4" />
    </svg>
  ),
  MercadoPago: ({ size = 18 }: { size?: number } = {}) => (
    <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'block' }}>
      <path d="M40.9763,30.6458a5.2763,5.2763,0,0,1-2.1726-2.0337,54.6611,54.6611,0,0,1-8.7476,1.0169c-3.701,0-6.6869-.1757-5.4673-3.6243s4.4579-10.5561,5.5934-11.986,2.6859-3.239,3.4486-3.1542c.9463.1051,2.7152,1.2834,2.5166,2.0333-.1892.715-1.1287,2.2774-2.7471,1.1649"/>
      <path d="M32.8639,14.8392a8.1339,8.1339,0,0,1,1.2926-1.6406"/>
      <path d="M35.9393,13.4064c.5677-.1443,1.64.5818,1.035,1.3272a4.7779,4.7779,0,0,1-2.8178,1.3248c-.6248.0556-2.7967-.021-2.7967-.021-.9252,1.5981-.7149,4.0374-.7991,6.1822a9.3461,9.3461,0,0,1-.8831,3.6589c3.7009-1.7243,10.0093-3.028,13.8224-2.3972"/>
      <path d="M7.0237,30.6458a5.2763,5.2763,0,0,0,2.1726-2.0337,54.6611,54.6611,0,0,0,8.7476,1.0169c3.701,0,6.6869-.1757,5.4673-3.6243s-4.4579-10.5561-5.5934-11.986-2.6859-3.239-3.4486-3.1542c-.9463.1051-2.7152,1.2834-2.5166,2.0333.1892.715,1.1287,2.2774,2.7471,1.1649"/>
      <path d="M15.1361,14.8392a8.1339,8.1339,0,0,0-1.2926-1.6406"/>
      <path d="M12.0607,13.4064c-.5677-.1443-1.6405.5818-1.035,1.3272a4.7779,4.7779,0,0,0,2.8178,1.3248c.6248.0556,2.7967-.021,2.7967-.021.9252,1.5981.7149,4.0374.7991,6.1822a9.3461,9.3461,0,0,0,.8831,3.6589C14.6215,24.1542,8.3131,22.8505,4.5,23.4813"/>
      <path d="M32.1812,11.742a27.5655,27.5655,0,0,0-16.3641.0006"/>
      <path d="M10.9168,13.9894C6.9758,16.46,4.5,20.03,4.5,24c0,7.4558,8.73,13.5,19.5,13.5S43.5,31.4558,43.5,24c0-3.9705-2.4759-7.5407-6.4172-10.0109"/>
    </svg>
  ),
  Star: ({ size = 16 }: { size?: number } = {}) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size }}>
      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
    </svg>
  ),
}

// Maps a persisted Notificacion's `tipo` (backend/.../NotificacionService.crearNotificacion
// callers) to the icon + accent color shown in the notification bell dropdown.
function getNotificationVisual(tipo: string | undefined): { Icon: (props: { size?: number }) => React.JSX.Element; color: string } {
  switch (tipo) {
    case 'TURNO_RESERVADO':
    case 'TURNO_CONFIRMADO':
      return { Icon: Icon.CalendarCheck, color: 'var(--color-success)' }
    case 'TURNO_CANCELADO':
      return { Icon: Icon.CalendarX, color: 'var(--color-danger)' }
    case 'SEGUIMIENTO':
      return { Icon: Icon.Activity, color: '#3b82f6' }
    case 'INFORME':
      return { Icon: Icon.FileText, color: '#8b5cf6' }
    case 'NUEVO_MENSAJE':
      return { Icon: Icon.MessageCircle, color: 'var(--color-primary)' }
    default:
      return { Icon: Icon.BellSimple, color: 'var(--color-primary)' }
  }
}

// ── Sidebar Component ──────────────────────────────────────────
function Sidebar({
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

// ── MP Connect Banner ──────────────────────────────────────────
function MPConnectBanner({ connected, onConnect, onDisconnect }: { connected: boolean; onConnect: () => void; onDisconnect: () => void }) {
  return (
    <div className={`mp-connect-banner mp-connect-banner--mp ${connected ? 'mp-connect-banner--connected' : ''}`} role={connected ? 'status' : 'alert'}>
      <div className="mp-connect-banner__icon">
        <img src="/logo-mp-icon.png" alt="Mercado Pago" className="mp-connect-banner__logo" />
      </div>
      <div className="mp-connect-banner__content">
        <span className="mp-connect-banner__status">
          <span className="mp-connect-banner__status-dot" />
          {connected ? 'Conectado' : 'Sin vincular'}
        </span>
        <h2 className="mp-connect-banner__title">
          {connected ? 'Mercado Pago vinculado' : 'Conectá tu cuenta de Mercado Pago'}
        </h2>
        <p className="mp-connect-banner__body">
          {connected
            ? 'Los cobros se acreditan directo en tu cuenta al confirmarse cada sesión. 100% libre de comisiones.'
            : 'Permite que los pacientes abonen sus turnos de forma directa e instantánea a tu cuenta sin comisiones extra.'}
        </p>
      </div>
      <div className="mp-connect-banner__action">
        {connected ? (
          <button className="btn btn--ghost btn--sm" onClick={onDisconnect}>Desconectar</button>
        ) : (
          <button className="btn btn--primary btn--sm mp-btn--connect" onClick={onConnect} id="btn-connect-mp">Conectar Mercado Pago</button>
        )}
      </div>
    </div>
  )
}

// ── Google Calendar Connect Banner ──────────────────────────────
function GoogleCalendarConnectBanner({ connected, onConnect, onDisconnect }: { connected: boolean; onConnect: () => void; onDisconnect: () => void }) {
  return (
    <div className={`mp-connect-banner mp-connect-banner--google ${connected ? 'mp-connect-banner--connected' : ''}`} role={connected ? 'status' : 'alert'}>
      <div className="mp-connect-banner__icon">
        <img src="/logo-google-calendar.svg" alt="Google Calendar" className="mp-connect-banner__logo" />
      </div>
      <div className="mp-connect-banner__content">
        <span className="mp-connect-banner__status">
          <span className="mp-connect-banner__status-dot" />
          {connected ? 'Conectado' : 'Sin vincular'}
        </span>
        <h2 className="mp-connect-banner__title">
          {connected ? 'Google Calendar vinculado' : 'Vinculá tu Google Calendar / Google Meet'}
        </h2>
        <p className="mp-connect-banner__body">
          {connected
            ? 'Genera videollamadas de Google Meet y sincroniza automáticamente las sesiones en tu agenda personal.'
            : 'Sincroniza tus sesiones de forma automática con Google Calendar y crea links de Google Meet para videollamadas.'}
        </p>
      </div>
      <div className="mp-connect-banner__action">
        {connected ? (
          <button className="btn btn--ghost btn--sm" onClick={onDisconnect}>Desconectar</button>
        ) : (
          <button className="btn btn--primary btn--sm google-btn--connect" onClick={onConnect} id="btn-connect-google">
            Conectar Google Calendar
          </button>
        )}
      </div>
    </div>
  )
}

// ── Stats Overview ─────────────────────────────────────────────
function StatsOverview({ stats }: { stats: any }) {
  if (!stats) {
    return (
      <div className="stats-grid">
        {[1, 2, 3, 4].map((i) => (
          <article key={i} className="stat-card" style={{ opacity: 0.6 }}>
            <div style={{ height: '24px', backgroundColor: 'var(--color-border)', width: '60%', borderRadius: '4px', marginBottom: '8px' }} />
            <div style={{ height: '32px', backgroundColor: 'var(--color-border)', width: '40%', borderRadius: '4px' }} />
          </article>
        ))}
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
    <div className="stats-grid">
      <article className="stat-card stat-card--primary">
        <div className="stat-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" style={{ width: 22, height: 22 }}>
            <line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
          </svg>
        </div>
        <div className="stat-card__value">${(stats.earningsThisWeek || 0).toLocaleString('es-AR')}</div>
        <div className="stat-card__label">Liquidado esta semana</div>
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
        <div className="stat-card__label">Sesiones hoy</div>
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
        <div className="stat-card__label">No-shows este mes</div>
        <div className={`stat-card__change ${getChangeCls(stats.noShowsChange)}`}>
          {renderIcon(stats.noShowsChange)} {stats.noShowsChange}
        </div>
      </article>
    </div>
  )
}

// ── Appointment Item ───────────────────────────────────────────
// `compact` renders the slimmer "upcoming turnos" row (side panel on Inicio); the default
// (non-compact) rendering keeps the fuller boxed row used by the "Diario" calendar tab.
function AppointmentCard({ appt, compact }: { appt: Appointment; compact?: boolean }) {
  const statusMap = {
    confirmed: { label: 'Confirmado', cls: 'badge--success' },
    pending: { label: 'Pago pendiente', cls: 'badge--warning' },
    completed: { label: 'Completado', cls: 'badge--neutral' },
  }
  const st = statusMap[appt.status] || { label: appt.status, cls: 'badge--neutral' }
  // No explicit modality field on the appointment — a Meet link is only ever generated for
  // online consultations (see TurnoService#crearEventoReunion), so its presence is a reliable,
  // real signal for the online/presencial tag rather than fabricated data.
  const isOnline = !!appt.meetLink

  return (
    <li className={`appointment-item ${compact ? 'appointment-item--compact' : ''}`}>
      <div className="appointment-item__time">
        <div className="appointment-item__hour">{appt.hour}</div>
        <div className="appointment-item__ampm">{appt.ampm || 'hs'}</div>
      </div>
      <div className="appointment-item__divider" />
      <div className="appointment-item__info">
        <div className="appointment-item__name">{appt.patientName}</div>
        <div className="appointment-item__meta">
          {appt.type}
          {!compact && (
            <>
              {' · '}<span className={`badge ${st.cls}`} style={{ fontSize: '9px', padding: '1px 6px' }}>{st.label}</span>
            </>
          )}
        </div>
      </div>
      <span className={`appointment-item__tag appointment-item__tag--${isOnline ? 'online' : 'presencial'}`}>
        {isOnline ? 'Online' : 'Presencial'}
      </span>
      {!compact && (
        <div className="appointment-item__actions">
          {appt.meetLink && appt.status === 'confirmed' && (
            <a
              href={appt.meetLink}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn--primary btn--sm"
              style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-1)' }}
            >
              <Icon.Video /> Unirse
            </a>
          )}
          {appt.status === 'pending' && (
            <span style={{ fontSize: '11px', color: 'var(--color-warning)', fontWeight: 'bold' }}>
              Esperando pago
            </span>
          )}
        </div>
      )}
    </li>
  )
}

// Compact, read-only counterpart to AppointmentCard for a médico's personal Google Calendar
// events — same row layout so both list types stack cleanly in "Próximos Eventos", but no
// status badge, meet link, or click action since these events aren't editable from the app.
function ExternalEventCard({ event, compact }: { event: ExternalEvent; compact?: boolean }) {
  return (
    <li className={`appointment-item appointment-item--external ${compact ? 'appointment-item--compact' : ''}`}>
      <div className="appointment-item__time">
        <div className="appointment-item__hour">{event.allDay ? '' : event.hour}</div>
        <div className="appointment-item__ampm">{event.allDay ? 'Todo el día' : 'hs'}</div>
      </div>
      <div className="appointment-item__divider" />
      <div className="appointment-item__info">
        <div className="appointment-item__name">{event.title}</div>
        <div className="appointment-item__meta">Evento personal · Google Calendar</div>
      </div>
    </li>
  )
}

function AgendaView({ initialAvailability, onSave }: { initialAvailability: any[]; onSave: (data: any[]) => Promise<void> }) {
  const { showAlert } = useAlert();
  const weekdays = [
    { name: 'Lunes', abbr: 'Lun', num: 1 },
    { name: 'Martes', abbr: 'Mar', num: 2 },
    { name: 'Miércoles', abbr: 'Mié', num: 3 },
    { name: 'Jueves', abbr: 'Jue', num: 4 },
    { name: 'Viernes', abbr: 'Vie', num: 5 },
  ]
  // Per-day set of selected bookable-slot start times ("HH:mm"). Each slot is duracionTurno
  // minutes long; consecutive slots are spaced duracionTurno+intervaloTurno minutes apart —
  // so the grid of clickable positions (and therefore what's selectable) changes whenever
  // either setting changes. There's no separate "franja horaria" editor anymore: the grid
  // below (formerly a read-only preview) IS the editor.
  const [selectedSlots, setSelectedSlots] = useState<{ [key: number]: Set<string> }>({
    1: new Set(), 2: new Set(), 3: new Set(), 4: new Set(), 5: new Set()
  })

  // "Duración de turno" / "Intervalo entre turnos" — per-médico agenda settings. Loaded from
  // the existing profile endpoint (which now also returns these two fields) and persisted
  // through a small dedicated endpoint so we don't have to send the whole profile DTO from
  // here. Backend defaults (45 / 10) are mirrored here so the selects have a sane value
  // before the profile fetch resolves.
  const [duracionTurno, setDuracionTurno] = useState(45)
  const [intervaloTurno, setIntervaloTurno] = useState(10)
  const [perfilLoaded, setPerfilLoaded] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.getPerfil()
      .then((perfil: any) => {
        if (cancelled || !perfil) return
        if (perfil.duracionTurnoMinutos != null) setDuracionTurno(perfil.duracionTurnoMinutos)
        if (perfil.intervaloEntreTurnosMinutos != null) setIntervaloTurno(perfil.intervaloEntreTurnosMinutos)
      })
      .catch((err) => console.error('Error cargando configuración de agenda', err))
      .finally(() => { if (!cancelled) setPerfilLoaded(true) })
    return () => { cancelled = true }
  }, [])

  // To-Do List state and hooks
  const [tasks, setTasks] = useState<{ id: string; text: string; completed: boolean; category: 'clinical' | 'admin' | 'urgent' }[]>(() => {
    try {
      const saved = localStorage.getItem('tranqui_medico_tasks');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.error("Error reading tasks from localStorage", e);
    }
    return [];
  });

  const [newTaskText, setNewTaskText] = useState('');
  const [newTaskCategory, setNewTaskCategory] = useState<'clinical' | 'admin' | 'urgent'>('clinical');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  const sortedTasks = React.useMemo(() => {
    return [...tasks].sort((a, b) => {
      if (a.category === 'urgent' && b.category !== 'urgent') return -1;
      if (a.category !== 'urgent' && b.category === 'urgent') return 1;
      return 0;
    });
  }, [tasks]);

  const totalPages = Math.ceil(sortedTasks.length / itemsPerPage);
  const validCurrentPage = Math.min(currentPage, Math.max(1, totalPages));
  const startIndex = (validCurrentPage - 1) * itemsPerPage;
  const paginatedTasks = sortedTasks.slice(startIndex, startIndex + itemsPerPage);

  // Persist tasks on change
  useEffect(() => {
    try {
      localStorage.setItem('tranqui_medico_tasks', JSON.stringify(tasks));
    } catch (e) {
      console.error("Error saving tasks to localStorage", e);
    }
  }, [tasks]);

  const handleAddTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskText.trim()) return;
    const newTask = {
      id: String(Date.now()),
      text: newTaskText.trim(),
      completed: false,
      category: newTaskCategory,
    };
    setTasks([...tasks, newTask]);
    setNewTaskText('');
  };

  const handleToggleTask = (id: string) => {
    setTasks(tasks.map(t => t.id === id ? { ...t, completed: !t.completed } : t));
  };

  const handleDeleteTask = (id: string) => {
    setTasks(tasks.filter(t => t.id !== id));
  };

  const [saving, setSaving] = useState(false)

  const toMinutes = (t: string) => {
    const [h, m] = t.split(':').map(Number)
    return h * 60 + m
  }
  const toTimeStr = (mins: number) => `${String(Math.floor(mins / 60)).padStart(2, '0')}:${String(mins % 60).padStart(2, '0')}`

  // ── Bookable-slot grid geometry ──────────────────────────────────────────
  // The grid spans a fixed 07:00–21:00 window, generous enough for virtually any clinical
  // schedule. Candidate slot start times are anchored to GRID_START_HOUR and stepped by
  // duracionTurno+intervaloTurno ("paso") — this is the actual set of times a médico can
  // toggle on/off, so it reflows whenever either setting changes.
  const GRID_START_HOUR = 7
  const GRID_END_HOUR = 21
  const HOUR_PX = 46
  const pxPerMinute = HOUR_PX / 60
  const gridHeightPx = (GRID_END_HOUR - GRID_START_HOUR) * HOUR_PX
  const gridHours = Array.from({ length: GRID_END_HOUR - GRID_START_HOUR + 1 }, (_, i) => GRID_START_HOUR + i)

  const computeCandidateStarts = (dur: number, interval: number): string[] => {
    const starts: string[] = []
    const step = dur + interval
    if (step > 0) {
      let cursor = GRID_START_HOUR * 60
      const limit = GRID_END_HOUR * 60
      while (cursor + dur <= limit) {
        starts.push(toTimeStr(cursor))
        cursor += step
      }
    }
    return starts
  }

  const paso = duracionTurno + intervaloTurno
  const candidateStarts = computeCandidateStarts(duracionTurno, intervaloTurno)

  // Collapses a day's individually-toggled slots (picked from `candidates`, each `dur` minutes
  // long) back into contiguous { start, end } ranges — the inverse of slotsFromRanges below.
  const rangesFromDaySlots = (daySet: Set<string>, candidates: string[], dur: number): { start: string; end: string }[] => {
    const ranges: { start: string; end: string }[] = []
    let runStart: string | null = null
    candidates.forEach((cand, idx) => {
      const isSelected = daySet.has(cand)
      if (isSelected && runStart === null) runStart = cand
      const nextSelected = isSelected && candidates[idx + 1] !== undefined && daySet.has(candidates[idx + 1])
      if (isSelected && !nextSelected && runStart !== null) {
        ranges.push({ start: runStart, end: toTimeStr(toMinutes(cand) + dur) })
        runStart = null
      }
    })
    return ranges
  }

  // Selects every candidate grid slot that fully fits inside one of the given ranges — used
  // both to import saved availability and to re-fit existing ranges onto a new grid.
  const slotsFromRanges = (ranges: { horaInicio?: string; horaFin?: string; start?: string; end?: string }[], candidates: string[], dur: number): Set<string> => {
    const set = new Set<string>()
    candidates.forEach((cand) => {
      const candMin = toMinutes(cand)
      const fits = ranges.some((r) => {
        const startMin = toMinutes((r.horaInicio ?? r.start)!)
        const endMin = toMinutes((r.horaFin ?? r.end)!)
        return candMin >= startMin && candMin + dur <= endMin
      })
      if (fits) set.add(cand)
    })
    return set
  }

  // Tracks the duración/intervalo the grid was last built against, so a later change can diff
  // against it. Set by the import effect below as soon as real data lands — NOT lazily from
  // seeing duracionTurno/intervaloTurno "change", because when the médico's saved settings
  // equal the hook's own default state (45/10, the same values the backend defaults to), the
  // setDuracionTurno/setIntervaloTurno calls during profile load are no-ops (same value in,
  // same value out) and React never re-renders for them — so an effect keyed off those values
  // "changing" would never fire and this would stay uninitialized.
  const prevGridRef = React.useRef<{ duracion: number; intervalo: number } | null>(null)

  // One-time import: once we know the médico's real duración/intervalo, translate their
  // previously saved availability ranges into the equivalent set of selected grid slots.
  // Any saved boundary that doesn't line up with the fixed grid is naturally dropped —
  // expected, since availability is now defined purely by toggling grid slots.
  const importedRef = React.useRef(false)
  useEffect(() => {
    if (!perfilLoaded || importedRef.current) return
    importedRef.current = true
    const byDay: { [key: number]: any[] } = { 1: [], 2: [], 3: [], 4: [], 5: [] }
    initialAvailability.forEach((disp: any) => {
      const dayNum = disp.diaSemana
      if (dayNum < 1 || dayNum > 5) return
      byDay[dayNum].push(disp)
    })
    const next: { [key: number]: Set<string> } = { 1: new Set(), 2: new Set(), 3: new Set(), 4: new Set(), 5: new Set() }
    weekdays.forEach((d) => { next[d.num] = slotsFromRanges(byDay[d.num], candidateStarts, duracionTurno) })
    setSelectedSlots(next)
    prevGridRef.current = { duracion: duracionTurno, intervalo: intervaloTurno }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [perfilLoaded, duracionTurno, intervaloTurno])

  // Whenever duración/intervalo change AFTER the initial import (i.e. the médico tweaks the
  // selects mid-session), the grid's candidate positions shift. Rather than dropping every
  // selected slot that no longer lines up (wiping out the whole schedule), we translate the
  // previously selected slots into { start, end } ranges under the OLD grid, then re-fit those
  // same ranges onto the NEW grid — the médico's franjas horarias survive the change.
  useEffect(() => {
    if (!importedRef.current || prevGridRef.current === null) return
    const { duracion: prevDuracion, intervalo: prevIntervalo } = prevGridRef.current
    if (prevDuracion === duracionTurno && prevIntervalo === intervaloTurno) return
    const prevCandidates = computeCandidateStarts(prevDuracion, prevIntervalo)
    setSelectedSlots((prev) => {
      const next: { [key: number]: Set<string> } = {}
      weekdays.forEach((d) => {
        const daySet = prev[d.num] || new Set<string>()
        const ranges = rangesFromDaySlots(daySet, prevCandidates, prevDuracion)
        next[d.num] = slotsFromRanges(ranges, candidateStarts, duracionTurno)
      })
      return next
    })
    prevGridRef.current = { duracion: duracionTurno, intervalo: intervaloTurno }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [duracionTurno, intervaloTurno])

  const toggleSlot = (dayNum: number, slotStart: string) => {
    setSelectedSlots((prev) => {
      const daySet = new Set(prev[dayNum])
      if (daySet.has(slotStart)) daySet.delete(slotStart)
      else daySet.add(slotStart)
      return { ...prev, [dayNum]: daySet }
    })
  }

  // Merges a day's individually-toggled slots back into contiguous { start, end } ranges for
  // saving — e.g. three consecutive selected slots collapse into one range dto, which the
  // backend re-expands into the same slots via the same duración+intervalo stepping.
  const buildRangesForDay = (dayNum: number): { start: string; end: string }[] =>
    rangesFromDaySlots(selectedSlots[dayNum] || new Set<string>(), candidateStarts, duracionTurno)

  // "Copiar horario a todos los días": overwrites every weekday's selection with a copy of the
  // given day's selected slots — a one-click way to avoid re-toggling the same schedule 5 times.
  const copySlotsToAllDays = (dayNum: number) => {
    const source = selectedSlots[dayNum]
    if (!source || source.size === 0) return
    setSelectedSlots((prev) => {
      const next: { [key: number]: Set<string> } = { ...prev }
      weekdays.forEach((d) => { next[d.num] = new Set(source) })
      return next
    })
  }

  // The day whose slots the "copy to all days" action would use — the first weekday (in
  // Lun→Vie order) that already has a schedule defined, defaulting to Lunes.
  const copySourceDay = weekdays.find((d) => (selectedSlots[d.num]?.size || 0) > 0) || weekdays[0]

  const handleSave = async () => {
    setSaving(true)
    const dtos: any[] = []

    weekdays.forEach((day) => {
      buildRangesForDay(day.num).forEach((range) => {
        dtos.push({
          diaSemana: day.num,
          horaInicio: range.start,
          horaFin: range.end
        })
      })
    })

    try {
      await Promise.all([
        onSave(dtos),
        api.actualizarConfigAgenda({ duracionTurnoMinutos: duracionTurno, intervaloEntreTurnosMinutos: intervaloTurno })
      ])
      showAlert("Disponibilidad guardada correctamente ✓", "success")
    } catch (err) {
      console.error(err)
      showAlert("Error al guardar disponibilidad", "error")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="agenda-view">
      {/* Weekly availability panel */}
      <div className="agenda-view-panel">
        <div className="agenda-view-panel__head">
          <div>
            <div className="agenda-view-panel__title">Disponibilidad semanal</div>
            <p className="agenda-view-panel__subtitle">Definí tus franjas horarias y la duración del turno. Los horarios reservables se calculan solos.</p>
          </div>
          <button className="agenda-view-btn-primary" onClick={handleSave} disabled={saving} id="btn-save-availability">
            {saving ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>

        <div className="agenda-view-settings-row">
          <div className="agenda-view-setting">
            <label>Duración de turno</label>
            <div className="agenda-view-select-wrap">
              <select value={duracionTurno} onChange={(e) => setDuracionTurno(Number(e.target.value))}>
                <option value={30}>30 min</option>
                <option value={45}>45 min</option>
                <option value={50}>50 min</option>
                <option value={60}>60 min</option>
              </select>
            </div>
          </div>
          <div className="agenda-view-setting">
            <label>Intervalo entre turnos</label>
            <div className="agenda-view-select-wrap">
              <select value={intervaloTurno} onChange={(e) => setIntervaloTurno(Number(e.target.value))}>
                <option value={0}>Sin intervalo</option>
                <option value={5}>5 min</option>
                <option value={10}>10 min</option>
                <option value={15}>15 min</option>
              </select>
            </div>
          </div>
        </div>

        {copySourceDay && (selectedSlots[copySourceDay.num]?.size || 0) > 0 && (
          <div className="agenda-view-copy-row">
            <button type="button" className="agenda-view-copy-btn" onClick={() => copySlotsToAllDays(copySourceDay.num)}>
              Copiar horario de {copySourceDay.name} a todos los días
            </button>
          </div>
        )}

        <div className="agenda-view-preview">
          <div className="agenda-view-preview-head">
            <div className="agenda-view-preview-title">Horarios reservables</div>
            <div className="agenda-view-preview-legend">
              <span className="agenda-view-preview-legend-item">
                <span className="agenda-view-preview-legend-swatch agenda-view-preview-legend-swatch--on" /> Disponible
              </span>
              <span className="agenda-view-preview-legend-item">
                <span className="agenda-view-preview-legend-swatch" /> Clic para agregar
              </span>
            </div>
          </div>
          <div className="agenda-view-agenda">
            <div className="agenda-view-agenda-headrow">
              <div className="agenda-view-agenda-corner" />
              <div className="agenda-view-agenda-daynames">
                {weekdays.map((day) => <div key={day.num}>{day.abbr}</div>)}
              </div>
            </div>
            <div className="agenda-view-agenda-body">
              <div className="agenda-view-agenda-hours" style={{ height: `${gridHeightPx}px` }}>
                {gridHours.map((h) => (
                  <div key={h} className="agenda-view-agenda-hour-label" style={{ top: `${(h - GRID_START_HOUR) * HOUR_PX}px` }}>
                    {String(h).padStart(2, '0')}:00
                  </div>
                ))}
              </div>
              <div className="agenda-view-agenda-days">
                {weekdays.map((day) => {
                  const daySet = selectedSlots[day.num] || new Set<string>()
                  return (
                    <div
                      key={day.num}
                      className="agenda-view-agenda-day-col"
                      style={{
                        height: `${gridHeightPx}px`,
                        backgroundImage: `repeating-linear-gradient(to bottom, transparent 0, transparent ${HOUR_PX - 1}px, var(--av-line) ${HOUR_PX - 1}px, var(--av-line) ${HOUR_PX}px)`
                      }}
                    >
                      {candidateStarts.map((slotStart) => {
                        const isSelected = daySet.has(slotStart)
                        return (
                          <button
                            key={slotStart}
                            type="button"
                            className={`agenda-view-agenda-slot${isSelected ? '' : ' agenda-view-agenda-slot--empty'}`}
                            style={{
                              top: `${(toMinutes(slotStart) - GRID_START_HOUR * 60) * pxPerMinute}px`,
                              height: `${Math.max(duracionTurno * pxPerMinute, 20)}px`
                            }}
                            onClick={() => toggleSlot(day.num, slotStart)}
                            aria-pressed={isSelected}
                            aria-label={`${isSelected ? 'Quitar' : 'Agregar'} horario ${slotStart} de ${day.name}`}
                          >
                            <div className="agenda-view-agenda-slot-time">{slotStart}</div>
                          </button>
                        )
                      })}
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Notes / pendientes panel */}
      <div className="agenda-view-panel">
        <div className="agenda-view-panel__head">
          <div>
            <div className="agenda-view-panel__title">Notas y pendientes</div>
            <p className="agenda-view-panel__subtitle">Recordatorios clínicos y administrativos</p>
          </div>
        </div>

        <form onSubmit={handleAddTask}>
          <div className="agenda-view-field">
            <label>Descripción de la nota</label>
            <input
              type="text"
              placeholder="Ej. Llamar a prepaga Rossi..."
              value={newTaskText}
              onChange={(e) => setNewTaskText(e.target.value)}
            />
          </div>
          <div className="agenda-view-row-inline">
            <div className="agenda-view-field" style={{ flex: 1, marginBottom: 0 }}>
              <label>Categoría</label>
              <div className="agenda-view-select-wrap">
                <select value={newTaskCategory} onChange={(e: any) => setNewTaskCategory(e.target.value)}>
                  <option value="clinical">Nota Clínica</option>
                  <option value="admin">Nota Administrativa</option>
                  <option value="urgent">Prioridad Urgente</option>
                </select>
              </div>
            </div>
            <button type="submit" className="agenda-view-btn-primary">Añadir</button>
          </div>
        </form>

        <div className="agenda-view-notes-list">
          {tasks.length === 0 ? (
            <div className="agenda-view-empty">
              <Icon.FileText size={24} />
              <p>No tenés notas pendientes.</p>
            </div>
          ) : (
            paginatedTasks.map((task) => {
              const isUrgent = task.category === 'urgent';
              const isAdmin = task.category === 'admin';
              const BadgeIcon = isUrgent ? Icon.AlertTriangle : isAdmin ? Icon.Clipboard : Icon.Activity;
              const badgeLabel = isUrgent ? 'Urgente' : isAdmin ? 'Admin' : 'Clínica';
              const categoryClass = isUrgent ? 'agenda-view-note-item--urgent' : isAdmin ? 'agenda-view-note-item--admin' : 'agenda-view-note-item--clinical';

              return (
                <div
                  key={task.id}
                  className={`agenda-view-note-item ${categoryClass} ${task.completed ? 'agenda-view-note-item--done' : ''}`}
                >
                  <input
                    type="checkbox"
                    className="agenda-view-note-check"
                    checked={task.completed}
                    onChange={() => handleToggleTask(task.id)}
                  />
                  <div className="agenda-view-note-body">
                    <span className="agenda-view-note-title">{task.text}</span>
                    <div className="agenda-view-note-meta">
                      <span className="agenda-view-note-tag"><BadgeIcon size={10} /> {badgeLabel}</span>
                    </div>
                  </div>
                  <span className="agenda-view-note-del" onClick={() => handleDeleteTask(task.id)} title="Eliminar nota">
                    <Icon.Trash size={14} />
                  </span>
                </div>
              );
            })
          )}
        </div>

        {totalPages > 1 && (
          <div className="agenda-view-pagination">
            <button
              onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
              disabled={validCurrentPage === 1}
              className="agenda-view-page-btn"
            >
              ◀ Anterior
            </button>
            <span>Página {validCurrentPage} de {totalPages}</span>
            <button
              onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
              disabled={validCurrentPage === totalPages}
              className="agenda-view-page-btn"
            >
              Siguiente ▶
            </button>
          </div>
        )}
      </div>
    </div>
  )
}



// ── Prescription View ─────────────────────────────────────────
const MOCK_PATIENTS = [
  { id: '1', name: 'Mateo Benítez', email: 'mateo.b@gmail.com' },
  { id: '2', name: 'Matías Rodríguez', email: 'matias.r@gmail.com' },
  { id: '3', name: 'Lucía Fernández', email: 'lucia.f@gmail.com' },
  { id: '4', name: 'Santiago Torres', email: 'santiago.t@gmail.com' },
]

const COMMON_MEDS = [
  'Escitalopram 10mg',
  'Sertralina 50mg',
  'Clonazepam 0.5mg',
  'Alprazolam 0.25mg',
  'Quetiapina 25mg',
  'Risperidona 1mg',
  'Melatonina 3mg',
  'Pregabalina 75mg',
]

function PrescriptionView({ onSend }: { onSend: (data: any) => Promise<void> }) {
  const { showAlert } = useAlert();
  const [selectedPatient, setSelectedPatient] = useState('')
  const [medications, setMedications] = useState([{ name: '', dosage: '', frequency: '', duration: '' }])
  const [diagnosis, setDiagnosis] = useState('')
  const [notes, setNotes] = useState('')
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)

  const addMedication = () => {
    setMedications([...medications, { name: '', dosage: '', frequency: '', duration: '' }])
  }

  const updateMedication = (index: number, field: string, value: string) => {
    const updated = [...medications]
    updated[index] = { ...updated[index], [field]: value }
    setMedications(updated)
  }

  const removeMedication = (index: number) => {
    if (medications.length > 1) {
      setMedications(medications.filter((_, i) => i !== index))
    }
  }

  const canSend = selectedPatient && medications[0].name.trim().length > 0

  const handleSend = () => {
    setSending(true)
    onSend({
      pacienteId: Number(selectedPatient),
      medications,
      diagnosis,
      notes
    })
    .then(() => {
      setSent(true)
    })
    .catch((err) => {
      console.error("Error al emitir receta:", err)
      showAlert("Error al emitir y enviar receta", "error")
    })
    .finally(() => {
      setSending(false)
    })
  }

  const handleReset = () => {
    setSelectedPatient('')
    setMedications([{ name: '', dosage: '', frequency: '', duration: '' }])
    setDiagnosis('')
    setNotes('')
    setSent(false)
  }

  if (sent) {
    const patient = MOCK_PATIENTS.find(p => p.id === selectedPatient)
    return (
      <div className="card" style={{ textAlign: 'center', padding: 'var(--space-10)' }}>
        <div style={{
          width: 64, height: 64, borderRadius: 'var(--radius-full)',
          background: 'var(--green-50)', border: '2px solid var(--green-300)',
          color: 'var(--green-600)', display: 'flex', alignItems: 'center',
          justifyContent: 'center', margin: '0 auto var(--space-5)',
        }}>
          <Icon.Check />
        </div>
        <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-2xl)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-3)' }}>
          Receta enviada
        </h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
          Se envió la receta a <strong>{patient?.name}</strong>
        </p>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)', marginBottom: 'var(--space-8)' }}>
          {patient?.email} · PDF adjunto por email y WhatsApp
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center' }}>
          <button className="btn btn--primary" onClick={handleReset} id="btn-new-prescription">
            Nueva receta
          </button>
        </div>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {/* Header info */}
      <div className="alert-banner alert-banner--success" role="status">
        <span className="alert-banner__icon">
          <Icon.Prescription />
        </span>
        <div className="alert-banner__content">
          <div className="alert-banner__title">Receta electrónica asistida</div>
          <div className="alert-banner__body">
            Completá los datos, generamos el PDF y lo enviamos directo al paciente por email y WhatsApp.
            La firma digital del documento requiere tu certificado de firma electrónica.
          </div>
        </div>
      </div>

      {/* Patient select */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Paciente</h2>
        </div>
        <div className="form-group">
          <label className="form-label form-label--required" htmlFor="rx-patient">Seleccionar paciente</label>
          <select
            id="rx-patient"
            className="form-input"
            value={selectedPatient}
            onChange={(e) => setSelectedPatient(e.target.value)}
          >
            <option value="">Elegir paciente...</option>
            {MOCK_PATIENTS.map(p => (
              <option key={p.id} value={p.id}>{p.name} — {p.email}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Medications */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Medicación</h2>
          <button className="btn btn--secondary btn--sm" onClick={addMedication} id="btn-add-med">
            <Icon.Plus /> Agregar
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          {medications.map((med, i) => (
            <div key={i} style={{
              display: 'flex', flexDirection: 'column', gap: 'var(--space-3)',
              padding: 'var(--space-4)', background: 'var(--neutral-50)',
              borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
              position: 'relative',
            }}>
              {medications.length > 1 && (
                <button
                  onClick={() => removeMedication(i)}
                  style={{
                    position: 'absolute', top: 8, right: 8,
                    background: 'none', border: 'none', color: 'var(--color-text-secondary)',
                    fontSize: 'var(--text-lg)', cursor: 'pointer', lineHeight: 1,
                  }}
                  aria-label="Quitar medicación"
                >
                  ×
                </button>
              )}
              <div className="form-group">
                <label className="form-label form-label--required">Medicamento</label>
                <input
                  className="form-input"
                  type="text"
                  list={`meds-list-${i}`}
                  placeholder="Ej: Escitalopram 10mg"
                  value={med.name}
                  onChange={(e) => updateMedication(i, 'name', e.target.value)}
                />
                <datalist id={`meds-list-${i}`}>
                  {COMMON_MEDS.map(m => <option key={m} value={m} />)}
                </datalist>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-3)' }}>
                <div className="form-group">
                  <label className="form-label">Dosis</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej: 1 comp"
                    value={med.dosage}
                    onChange={(e) => updateMedication(i, 'dosage', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Frecuencia</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej: cada 24hs"
                    value={med.frequency}
                    onChange={(e) => updateMedication(i, 'frequency', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Duración</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej: 30 días"
                    value={med.duration}
                    onChange={(e) => updateMedication(i, 'duration', e.target.value)}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Diagnosis + Notes */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Diagnóstico e indicaciones</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label" htmlFor="rx-diagnosis">Diagnóstico (CIE-10)</label>
            <input
              id="rx-diagnosis"
              className="form-input"
              type="text"
              placeholder="Ej: F41.1 — Trastorno de ansiedad generalizada"
              value={diagnosis}
              onChange={(e) => setDiagnosis(e.target.value)}
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="rx-notes">Indicaciones para el paciente</label>
            <textarea
              id="rx-notes"
              className="form-input"
              rows={3}
              placeholder="Instrucciones adicionales, controles, próximo turno..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              style={{ resize: 'vertical', fontFamily: 'var(--font-body)' }}
            />
          </div>
        </div>
      </div>

      {/* Send */}
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Enviar receta</h2>
        </div>
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)' }}>
          Se genera un PDF con tus datos profesionales (Lic. Paula Rossi · MN 49281) y se envía al paciente por email y WhatsApp.
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <button
            className={`btn btn--primary ${sending ? 'btn--loading' : ''}`}
            disabled={!canSend || sending}
            onClick={handleSend}
            id="btn-send-prescription"
          >
            {sending ? 'Enviando...' : 'Generar PDF y enviar al paciente'}
          </button>
          <button className="btn btn--ghost" disabled={sending} onClick={handleReset}>
            Limpiar
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Settings: Tariff & Profile ─────────────────────────────────
// DEFAULT_TARIFFS mock removed since values are loaded from API

const PROVINCIAS_ARGENTINA = [
  "Buenos Aires",
  "CABA",
  "Catamarca",
  "Chaco",
  "Chubut",
  "Córdoba",
  "Corrientes",
  "Entre Ríos",
  "Formosa",
  "Jujuy",
  "La Pampa",
  "La Rioja",
  "Mendoza",
  "Misiones",
  "Neuquén",
  "Río Negro",
  "Salta",
  "San Juan",
  "San Luis",
  "Santa Cruz",
  "Santa Fe",
  "Santiago del Estero",
  "Tierra del Fuego",
  "Tucumán"
];

const ESPECIALIDADES_GRUPOS = [
  {
    "id": "clinicas",
    "nombreGrupo": "Especialidades Clínicas",
    "especialidades": [
      {"id": "alergia_inmuno", "nombre": "Alergia e Inmunología"},
      {"id": "cardiologia", "nombre": "Cardiología"},
      {"id": "dermatologia", "nombre": "Dermatología"},
      {"id": "endocrinologia", "nombre": "Endocrinología y Nutrición"},
      {"id": "gastroenterologia", "nombre": "Gastroenterología / Hepatología"},
      {"id": "geriatria", "nombre": "Geriatria"},
      {"id": "hematologia", "nombre": "Hematología"},
      {"id": "infectologia", "nombre": "Infectología"},
      {"id": "medicina_interna", "nombre": "Medicina Interna (Clínica Médica)"},
      {"id": "nefrologia", "nombre": "Nefrología"},
      {"id": "neumonologia", "nombre": "Neumonología"},
      {"id": "neurologia", "nombre": "Neurología"},
      {"id": "oncologia_medica", "nombre": "Oncología Médica"},
      {"id": "pediatria", "nombre": "Pediatría"},
      {"id": "psiquiatria", "nombre": "Psiquiatría"},
      {"id": "psiquiatria_infantil", "nombre": "Psiquiatría Infanto-Juvenil"},
      {"id": "reumatologia", "nombre": "Reumatología"}
    ]
  },
  {
    "id": "quirurgicas",
    "nombreGrupo": "Especialidades Quirúrgicas",
    "especialidades": [
      {"id": "cirugia_cardiovascular", "nombre": "Cirugía Cardiovascular"},
      {"id": "cirugia_general", "nombre": "Cirugía General y del Aparato Digestivo"},
      {"id": "cirugia_maxilofacial", "nombre": "Cirugía Oral y Maxilofacial"},
      {"id": "cirugia_traumatologia", "nombre": "Cirugía Ortopédica y Traumatología"},
      {"id": "cirugia_pediatrica", "nombre": "Cirugía Pediátrica"},
      {"id": "cirugia_plastica", "nombre": "Cirugía Plástica, Estética y Reparadora"},
      {"id": "cirugia_toracica", "nombre": "Cirugía Torácica"},
      {"id": "cirugia_vascular", "nombre": "Cirugía Vascular / Angiología"},
      {"id": "neurocirugia", "nombre": "Neurocirugía"}
    ]
  },
  {
    "id": "mixtas",
    "nombreGrupo": "Especialidades Médico-Quirúrgicas",
    "especialidades": [
      {"id": "ginecologia_obstetricia", "nombre": "Ginecología y Obstetricia (Tocoginecología)"},
      {"id": "oftalmologia", "nombre": "Oftalmología"},
      {"id": "otorrinolaringologia", "nombre": "Otorrinolaringología"},
      {"id": "urologia", "nombre": "Urología"}
    ]
  },
  {
    "id": "diagnostico_soporte",
    "nombreGrupo": "Diagnóstico, Soporte y Emergencias",
    "especialidades": [
      {"id": "anestesiologia", "nombre": "Anestesiología, Reanimación y Dolor"},
      {"id": "anatomia_patologica", "nombre": "Anatomía Patológica"},
      {"id": "diagnostico_imagenes", "nombre": "Diagnóstico por Imágenes / Radiología"},
      {"id": "medicina_deporte", "nombre": "Medicina del Deporte"},
      {"id": "medicina_emergencias", "nombre": "Medicina de Emergencias / Urgencias"},
      {"id": "medicina_intensiva", "nombre": "Medicina Intensiva / Terapia Intensiva"},
      {"id": "medicina_legal", "nombre": "Medicina Legal y Forense"},
      {"id": "medicina_nuclear", "nombre": "Medicina Nuclear"},
      {"id": "medicina_fisica_rehab", "nombre": "Medicina Física y Rehabilitación (Fisiatría)"},
      {"id": "toxicologia", "nombre": "Toxicología Médica"}
    ]
  },
  {
    "id": "salud_publica_comunitaria",
    "nombreGrupo": "Salud Pública y Atención Comunitaria",
    "especialidades": [
      {"id": "medicina_familiar", "nombre": "Medicina Familiar y General"},
      {"id": "medicina_trabajo", "nombre": "Medicina del Trabajo / Laboral"},
      {"id": "salud_publica", "nombre": "Salud Pública y Administración Sanitaria"}
    ]
  }
];

const TRATAMIENTOS_DISPONIBLES = [
  'Ansiedad', 'Depresión', 'Trauma', 'Pareja', 'Psiquiatría', 'Adolescentes',
  'Trastorno bipolar', 'Ataques de pánico', 'Insomnio', 'TDAH en adultos',
  'Estrés postraumático', 'Trastorno obsesivo compulsivo',
]

const PACIENTES_ATIENDE_OPCIONES = ['Niños', 'Adolescentes', 'Adultos', 'Adultos mayores']

export interface ExperienciaLaboral {
  id: string
  nombreLugar: string
  desde: string
  hasta: string
  descripcion: string
}

type SettingsTab = 'perfil-pro' | 'perfil-publico' | 'presencia' | 'honorarios' | 'notificaciones' | 'integraciones'

const SETTINGS_TABS: { id: SettingsTab; label: string; Icon: (props: { size?: number }) => React.JSX.Element }[] = [
  { id: 'perfil-pro', label: 'Perfil profesional', Icon: Icon.User },
  { id: 'perfil-publico', label: 'Perfil público', Icon: Icon.Globe },
  { id: 'presencia', label: 'Presencia y Experiencia', Icon: Icon.Star },
  { id: 'honorarios', label: 'Honorarios y servicios', Icon: Icon.DollarSign },
  { id: 'notificaciones', label: 'Notificaciones', Icon: Icon.BellSimple },
  { id: 'integraciones', label: 'Integraciones', Icon: Icon.MercadoPago },
]

function getMissingRequirements(m: any): string[] {
  const missing: string[] = []
  if (!m) return ["Cargando información del perfil..."]

  if (!m.name || !m.name.trim()) missing.push("Nombre profesional")
  if (!m.apellido || !m.apellido.trim()) missing.push("Apellido profesional")
  if (!m.sexo || !m.sexo.trim()) missing.push("Sexo biológico")
  if (!m.fechaNacimiento) missing.push("Fecha de nacimiento")
  if (!m.cuil) missing.push("CUIL profesional")
  if (!m.tipoDocumento || !m.numeroDocumento) missing.push("Tipo y número de documento")
  if (!m.domicilioAtencion || !m.domicilioAtencion.trim()) missing.push("Dirección física del consultorio")
  if (!m.matriculaInfo?.tipo || !m.matriculaInfo?.provincia || !m.matriculaInfo?.numero) {
    missing.push("Datos completos de matrícula (tipo, provincia y número)")
  }
  if (!m.fotoUrl || !m.fotoUrl.trim()) missing.push("Foto de perfil profesional")
  if (!m.descripcionPerfil || !m.descripcionPerfil.trim()) missing.push("Descripción de tu perfil profesional")
  if (!m.tags || m.tags.length === 0) missing.push("Al menos un tratamiento/especialidad que atiendas")
  if (!m.pacientesAtiende || m.pacientesAtiende.length === 0) missing.push("Al menos un tipo de paciente que atiendas")
  if (!m.institucionFormacion || !m.institucionFormacion.trim()) missing.push("Institución donde te formaste")
  if (m.aniosExperiencia === null || m.aniosExperiencia === undefined) missing.push("Años de experiencia clínica")
  if (!m.ofreceOnline && !m.ofrecePresencial) missing.push("Al menos una modalidad de consulta (online o presencial)")
  if (!m.experiencia || !m.experiencia.trim()) missing.push("Tu Experiencia (Presencia y Contenido)")
  if (!m.verificadoAdmin) missing.push("Verificación y validación de matrícula por el Administrador de Tranqui")

  return missing
}

function SettingsView({
  medicoInfo,
  onSave,
  mpConnected,
  onConnect,
  onDisconnect,
  googleConnected,
  onConnectGoogle,
  onDisconnectGoogle
}: {
  medicoInfo: any
  onSave: (updated: any) => Promise<void>
  mpConnected: boolean
  onConnect: () => void
  onDisconnect: () => void
  googleConnected: boolean
  onConnectGoogle: () => void
  onDisconnectGoogle: () => void
}) {
  const { showAlert } = useAlert();
  const [showUnmetList, setShowUnmetList] = useState(false);
  const [name, setName] = useState(medicoInfo?.nombre || '')
  const [apellido, setApellido] = useState(medicoInfo?.apellido || '')
  const [sexo, setSexo] = useState(medicoInfo?.sexo || 'M')
  const [fechaNacimiento, setFechaNacimiento] = useState(medicoInfo?.fechaNacimiento || '')
  const [cuil, setCuil] = useState(medicoInfo?.cuil || '')
  const [tipoDocumento, setTipoDocumento] = useState(medicoInfo?.tipoDocumento || 'DNI')
  const [numeroDocumento, setNumeroDocumento] = useState(medicoInfo?.numeroDocumento || '')
  const [domicilioAtencion, setDomicilioAtencion] = useState(medicoInfo?.domicilioAtencion || '')
  const [domicilioProvincia, setDomicilioProvincia] = useState('')
  const [domicilioLat, setDomicilioLat] = useState<number | null>(medicoInfo?.domicilioLat ?? null)
  const [domicilioLng, setDomicilioLng] = useState<number | null>(medicoInfo?.domicilioLng ?? null)
  const [codigoReFeps, setCodigoReFeps] = useState(medicoInfo?.codigoReFeps || '')

  // MatriculaInfo
  const [matTipo, setMatTipo] = useState(medicoInfo?.matriculaInfo?.tipo || 'MN')
  const [matProvincia, setMatProvincia] = useState(medicoInfo?.matriculaInfo?.provincia || '')

  const [degree, setDegree] = useState(medicoInfo?.degree || '')

  const initialSpecialty = medicoInfo?.specialty || medicoInfo?.matriculaInfo?.especialidad?.textoLibre || '';

  const initialGroup = ESPECIALIDADES_GRUPOS.find(g => 
    g.especialidades.some(esp => esp.nombre === initialSpecialty)
  )?.id || '';

  const [selectedGroup, setSelectedGroup] = useState(initialGroup);
  const [specialty, setSpecialty] = useState(initialSpecialty)

  const handleGroupChange = (groupId: string) => {
    setSelectedGroup(groupId);
    setSpecialty('');
  }

  const [matricula, setMatricula] = useState(medicoInfo?.matricula || (medicoInfo?.matriculaInfo?.numero ? String(medicoInfo.matriculaInfo.numero) : ''))
  const [tariffs, setTariffs] = useState<any[]>(medicoInfo?.tariffs || [])
  const [fotoUrl, setFotoUrl] = useState(medicoInfo?.fotoUrl || '')
  const [ofreceOnline, setOfreceOnline] = useState(medicoInfo?.ofreceOnline !== undefined ? medicoInfo.ofreceOnline : true)
  const [ofrecePresencial, setOfrecePresencial] = useState(medicoInfo?.ofrecePresencial !== undefined ? medicoInfo.ofrecePresencial : false)
  const [experienciasLaborales, setExperienciasLaborales] = useState<ExperienciaLaboral[]>(() => {
    const raw = medicoInfo?.experiencia || ''
    if (!raw) return []
    try {
      if (raw.trim().startsWith('[')) {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) return parsed
      }
    } catch (e) {
      // fallback
    }
    return [{ id: 'exp-1', nombreLugar: 'Experiencia laboral', desde: '', hasta: '', descripcion: raw }]
  })
  const [expForm, setExpForm] = useState<ExperienciaLaboral | null>(null)
  const [showExpModal, setShowExpModal] = useState(false)

  const formatMonthYearInput = (val: string) => {
    if (!val) return ''
    if (val.toLowerCase().startsWith('a')) return 'Actualidad'
    const digits = val.replace(/\D/g, '').slice(0, 6)
    if (!digits) return ''
    if (digits.length <= 2) return digits
    return `${digits.slice(0, 2)}/${digits.slice(2)}`
  }

  const handleSaveExpItem = () => {
    if (!expForm || !expForm.nombreLugar.trim()) return
    if (expForm.id) {
      setExperienciasLaborales(experienciasLaborales.map(item => item.id === expForm.id ? expForm : item))
    } else {
      setExperienciasLaborales([...experienciasLaborales, { ...expForm, id: 'exp-' + Date.now() }])
    }
    setExpForm(null)
    setShowExpModal(false)
  }

  const handleDeleteExpItem = (id: string) => {
    setExperienciasLaborales(experienciasLaborales.filter(item => item.id !== id))
  }

  const [instagram, setInstagram] = useState(medicoInfo?.redesSociales?.instagram || '')
  const [linkedin, setLinkedin] = useState(medicoInfo?.redesSociales?.linkedin || '')
  const [sitioWeb, setSitioWeb] = useState(medicoInfo?.redesSociales?.sitioWeb || '')

  // Public profile info (shown to patients on the booking page, required for account verification)
  const [descripcionPerfil, setDescripcionPerfil] = useState(medicoInfo?.descripcionPerfil || '')
  const [selectedTags, setSelectedTags] = useState<string[]>(medicoInfo?.tags || [])
  const [pacientesAtiende, setPacientesAtiende] = useState<string[]>(medicoInfo?.pacientesAtiende || [])
  const [institucionFormacion, setInstitucionFormacion] = useState(medicoInfo?.institucionFormacion || '')
  const [aniosExperiencia, setAniosExperiencia] = useState(medicoInfo?.aniosExperiencia ?? '')

  const toggleFromList = (list: string[], setList: (l: string[]) => void, value: string) => {
    setList(list.includes(value) ? list.filter(v => v !== value) : [...list, value])
  }

  const updateTariff = (id: string, field: 'price' | 'enabled' | 'label', value: number | boolean | string) => {
    setTariffs(tariffs.map(t => t.id === id ? { ...t, [field]: value } : t))
  }

  // New tariffs need a unique id (the backend's natural key for upsert/delete) — slugify the
  // label and disambiguate against whatever ids already exist so two similarly-named services
  // don't collide into the same row.
  const slugifyTariffId = (label: string) => {
    const base = label
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-+|-+$)/g, '') || 'servicio'
    let id = base
    let n = 2
    while (tariffs.some(t => t.id === id)) { id = `${base}-${n}`; n++ }
    return id
  }

  const addTariff = (label: string, price: number) => {
    setTariffs([...tariffs, { id: slugifyTariffId(label), label, price, enabled: true }])
  }

  const deleteTariff = (id: string) => {
    setTariffs(tariffs.filter(t => t.id !== id))
  }

  const [showAddTariff, setShowAddTariff] = useState(false)
  const [newTariffName, setNewTariffName] = useState('')
  const [newTariffPrice, setNewTariffPrice] = useState('')

  const handleAddTariff = () => {
    const name = newTariffName.trim()
    const price = Number(newTariffPrice)
    if (!name || newTariffPrice.trim() === '' || Number.isNaN(price)) return
    addTariff(name, price)
    setNewTariffName('')
    setNewTariffPrice('')
    setShowAddTariff(false)
  }

  const [saving, setSaving] = useState(false)
  const [activeTab, setActiveTab] = useState<SettingsTab>('perfil-pro')

  const isValidUrl = (urlStr: string) => {
    try {
      const parsed = new URL(urlStr);
      return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
      return false;
    }
  };

  const handleSave = async () => {
    // 1. Validar Número de Documento (debe contener únicamente números)
    const numDocStr = String(numeroDocumento ?? '').trim();
    if (numDocStr && !/^\d+$/.test(numDocStr)) {
      showAlert("El número de documento debe contener únicamente dígitos numéricos sin letras, puntos ni guiones.", "error");
      return;
    }

    // 2. Validar CUIL/CUIT (solo dígitos)
    const cuilStr = String(cuil ?? '').trim();
    if (cuilStr && !/^\d+$/.test(cuilStr)) {
      showAlert("El CUIL/CUIT debe ser un número válido sin letras, guiones ni puntos.", "error");
      return;
    }

    // 3. Validar Número de Matrícula (solo dígitos)
    const matStr = String(matricula ?? '').trim();
    if (matStr && !/^\d+$/.test(matStr)) {
      showAlert("El número de matrícula debe contener únicamente dígitos numéricos.", "error");
      return;
    }

    // 4. Validar Años de Experiencia (número entero no negativo)
    if (aniosExperiencia !== '' && (isNaN(Number(aniosExperiencia)) || Number(aniosExperiencia) < 0 || !/^\d+$/.test(String(aniosExperiencia).trim()))) {
      showAlert("Los años de experiencia deben ser un número entero mayor o igual a 0.", "error");
      return;
    }

    // 5. Validar Redes Sociales (Opcionales, pero si se llenan deben ser URLs válidas)
    let formattedInstagram = instagram ? instagram.trim() : '';
    if (formattedInstagram) {
      if (!/^https?:\/\//i.test(formattedInstagram)) {
        formattedInstagram = 'https://' + formattedInstagram;
      }
      if (!isValidUrl(formattedInstagram) || !formattedInstagram.toLowerCase().includes('instagram.com')) {
        showAlert("La URL de Instagram no es válida. Debe ser una dirección web válida de Instagram (ej: https://instagram.com/tu_usuario).", "error");
        return;
      }
    }

    let formattedLinkedin = linkedin ? linkedin.trim() : '';
    if (formattedLinkedin) {
      if (!/^https?:\/\//i.test(formattedLinkedin)) {
        formattedLinkedin = 'https://' + formattedLinkedin;
      }
      if (!isValidUrl(formattedLinkedin) || !formattedLinkedin.toLowerCase().includes('linkedin.com')) {
        showAlert("La URL de LinkedIn no es válida. Debe ser una dirección web válida de LinkedIn (ej: https://linkedin.com/in/tu_usuario).", "error");
        return;
      }
    }

    let formattedSitioWeb = sitioWeb ? sitioWeb.trim() : '';
    if (formattedSitioWeb) {
      if (!/^https?:\/\//i.test(formattedSitioWeb)) {
        formattedSitioWeb = 'https://' + formattedSitioWeb;
      }
      if (!isValidUrl(formattedSitioWeb)) {
        showAlert("La URL del sitio web no es válida. Debe ser una dirección web válida (ej: https://tu-sitio.com).", "error");
        return;
      }
    }

    setSaving(true)
    try {
      await onSave({
        ...medicoInfo,
        nombre: name,
        apellido,
        sexo,
        fechaNacimiento,
        cuil: cuilStr ? Number(cuilStr) : null,
        tipoDocumento,
        numeroDocumento: numDocStr ? Number(numDocStr) : null,
        domicilioAtencion: ofrecePresencial ? domicilioAtencion : '',
        domicilioLat: ofrecePresencial ? domicilioLat : null,
        domicilioLng: ofrecePresencial ? domicilioLng : null,
        codigoReFeps: codigoReFeps ? Number(codigoReFeps) : null,
        matriculaInfo: {
          tipo: matTipo,
          provincia: matProvincia,
          numero: matStr ? Number(matStr) : null,
          especialidad: {
            textoLibre: specialty
          },
          asociada: {
            tipo: medicoInfo?.matriculaInfo?.asociada?.tipo || 'MN',
            provincia: medicoInfo?.matriculaInfo?.asociada?.provincia || '',
            numero: medicoInfo?.matriculaInfo?.asociada?.numero || null
          }
        },
        degree,
        specialty,
        matricula: matStr,
        cuit: cuilStr,
        tariffs,
        fotoUrl,
        tags: selectedTags,
        ofreceOnline,
        ofrecePresencial,
        descripcionPerfil,
        pacientesAtiende,
        institucionFormacion,
        aniosExperiencia: aniosExperiencia === '' ? null : Number(aniosExperiencia),
        experiencia: JSON.stringify(experienciasLaborales),
        redesSociales: {
          instagram: formattedInstagram,
          linkedin: formattedLinkedin,
          sitioWeb: formattedSitioWeb
        }
      })
      showAlert("Configuración guardada con éxito ✓", "success")
    } catch (err) {
      console.error(err)
      showAlert("Error al guardar la configuración", "error")
    } finally {
      setSaving(false)
    }
  }

  const perfilProComplete = Boolean(name && apellido && sexo && fechaNacimiento && cuil && tipoDocumento && numeroDocumento && degree && matTipo && matProvincia && specialty && matricula)
  const perfilPublicoComplete = Boolean(descripcionPerfil && selectedTags.length > 0 && pacientesAtiende.length > 0 && institucionFormacion && aniosExperiencia !== '')
  const honorariosComplete = tariffs.some((t: any) => t.enabled)
  const integracionesPendientes = (mpConnected ? 0 : 1) + (googleConnected ? 0 : 1)

  const presenciaComplete = experienciasLaborales.length > 0
  const tabStatus: Record<SettingsTab, { label: string; tone: 'ok' | 'warn' }> = {
    'perfil-pro': perfilProComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Incompleto', tone: 'warn' },
    'perfil-publico': perfilPublicoComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Incompleto', tone: 'warn' },
    'presencia': presenciaComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Sin completar', tone: 'warn' },
    'honorarios': honorariosComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Sin configurar', tone: 'warn' },
    'notificaciones': { label: 'Activas', tone: 'ok' },
    'integraciones': integracionesPendientes === 0
      ? { label: 'Completo', tone: 'ok' }
      : { label: `${integracionesPendientes} pendiente${integracionesPendientes > 1 ? 's' : ''}`, tone: 'warn' },
  }

  const notImplementedYet = () => showAlert('Esta función va a estar disponible próximamente.', 'info')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      <p className="settings-intro">Gestioná tu perfil profesional, honorarios e integraciones.</p>

      {/* Verification status banner */}
      {medicoInfo?.verificado ? (
        <div className="settings-verified-strip">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
          <span>
            <strong style={{ color: 'var(--green-900)' }}>Cuenta verificada:</strong> tu perfil profesional cumple con todos los requisitos y es visible públicamente para reserva de turnos.
          </span>
        </div>
      ) : (
        <div style={{
          backgroundColor: '#fafaf9',
          border: '1px solid var(--color-border)',
          borderRadius: '12px',
          padding: 'var(--space-4)',
          color: 'var(--color-text-primary)',
          fontSize: 'var(--text-sm)',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-3)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: '16px', height: '16px', color: 'var(--color-warning)', flexShrink: 0 }}>
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              <strong style={{ fontSize: '13px', color: 'var(--color-text-primary)' }}>Cuenta No Verificada</strong>
            </div>
            
            <button
              onClick={() => setShowUnmetList(!showUnmetList)}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--color-primary)',
                fontSize: '12px',
                fontWeight: '600',
                cursor: 'pointer',
                padding: '2px 0',
                display: 'flex',
                alignItems: 'center',
                gap: '4px'
              }}
            >
              {showUnmetList ? 'Ocultar detalles' : 'Ver requisitos pendientes'}
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: '12px', height: '12px', transform: showUnmetList ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
          </div>
          
          <div style={{ fontSize: '12.5px', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
            Para aparecer en la lista de profesionales disponibles de la aplicación y recibir reservas, debés completar todos tus datos de perfil.
          </div>

          {showUnmetList && (
            <div style={{ 
              display: 'flex', 
              flexDirection: 'column', 
              gap: '6px', 
              backgroundColor: 'var(--color-surface)', 
              border: '1px solid var(--color-border)',
              borderRadius: '8px',
              padding: '10px 14px',
              animation: 'fadeIn 0.2s ease-out'
            }}>
              {getMissingRequirements(medicoInfo).map((req, idx) => (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: 'var(--color-text-primary)' }}>
                  <span style={{ width: '5px', height: '5px', borderRadius: '50%', backgroundColor: 'var(--color-warning)', flexShrink: 0 }} />
                  <span>{req}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="settings-layout">
        {/* Sub-navigation within the settings content area (not the app's global sidebar) */}
        <nav className="settings-submenu" aria-label="Secciones de configuración">
          {SETTINGS_TABS.map(({ id, label, Icon: TabIcon }) => {
            const status = tabStatus[id]
            return (
              <button
                key={id}
                type="button"
                className={`settings-submenu-item ${activeTab === id ? 'active' : ''}`}
                onClick={() => setActiveTab(id)}
                aria-current={activeTab === id ? 'true' : undefined}
              >
                <span className="settings-submenu-icon"><TabIcon /></span>
                <span className="settings-submenu-text">
                  <span className="settings-submenu-label">{label}</span>
                  <span className={`settings-submenu-status settings-submenu-status--${status.tone}`}>{status.label}</span>
                </span>
              </button>
            )
          })}
        </nav>

        <div className="settings-content">
      {activeTab === 'perfil-pro' && (
      <div className="card">
        <div className="card__header">
          <div>
            <h2 className="card__title">Perfil profesional</h2>
            <p className="card__subtitle">Tu foto es lo primero que ve un paciente al buscar turno — usá una imagen real y de buena calidad.</p>
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
          {/* Profile Photo Uploader */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', gridColumn: 'span 2', paddingBottom: 'var(--space-4)', borderBottom: '1px solid var(--color-border)' }}>
            <div style={{
              width: '80px',
              height: '80px',
              borderRadius: '50%',
              backgroundColor: '#e5e7eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              border: '2px solid var(--color-primary)'
            }}>
              {fotoUrl ? (
                <img src={fotoUrl} alt="Foto de perfil" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <span style={{ fontSize: '24px', color: '#9ca3af' }}>👤</span>
              )}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <label style={{
                cursor: 'pointer',
                backgroundColor: 'var(--color-primary)',
                color: 'white',
                padding: 'var(--space-2) var(--space-4)',
                borderRadius: 'var(--radius-md)',
                fontSize: 'var(--text-xs)',
                fontWeight: 'bold',
                textAlign: 'center'
              }}>
                Subir foto
                <input 
                  type="file" 
                  accept="image/*" 
                  style={{ display: 'none' }} 
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (!file) return;
                    const MAX_FOTO_BYTES = 3 * 1024 * 1024
                    if (file.size > MAX_FOTO_BYTES) {
                      showAlert(`La foto pesa ${(file.size / (1024 * 1024)).toFixed(1)}MB — el máximo permitido es 3MB. Elegí una imagen más liviana.`, 'error')
                      e.target.value = ''
                      return
                    }
                    const reader = new FileReader();
                    reader.onloadend = () => {
                      setFotoUrl(reader.result as string);
                    };
                    reader.readAsDataURL(file);
                  }}
                />
              </label>
              {fotoUrl && (
                <button 
                  onClick={() => setFotoUrl('')}
                  className="btn btn--danger btn--sm"
                  style={{ fontSize: 'var(--text-xs)' }}
                >
                  Eliminar foto
                </button>
              )}
            </div>
          </div>

          <div className="settings-section-label">Datos demográficos básicos</div>

          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-name">Nombre</label>
            <input id="input-name" className="form-input" type="text" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-apellido">Apellido</label>
            <input id="input-apellido" className="form-input" type="text" value={apellido} onChange={(e) => setApellido(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-sexo">Sexo</label>
            <select id="input-sexo" className="form-input" value={sexo} onChange={(e) => setSexo(e.target.value)}>
              <option value="M">Masculino (M)</option>
              <option value="F">Femenino (F)</option>
              <option value="Otro">Otro</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-nacimiento">Fecha de Nacimiento</label>
            <input id="input-nacimiento" className="form-input" type="date" value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-tipo-doc">Tipo Documento</label>
            <select id="input-tipo-doc" className="form-input" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)}>
              <option value="DNI">DNI</option>
              <option value="LC">Libreta Cívica (LC)</option>
              <option value="LE">Libreta de Enrolamiento (LE)</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-num-doc">Número de Documento</label>
            <input id="input-num-doc" className="form-input" type="text" inputMode="numeric" placeholder="Ej. 12345678" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-cuil">CUIL/CUIT</label>
            <input id="input-cuil" className="form-input" type="text" inputMode="numeric" placeholder="Ej. 27123456780" value={cuil} onChange={(e) => setCuil(e.target.value)} />
          </div>
          {/* Modalities selector */}
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label form-label--required">Modalidades de Consulta</label>
            <div className="modalities-chips-container" style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
              <label className={`check-chip check-chip--auto ${ofreceOnline ? 'active' : ''}`}>
                <input
                  type="checkbox"
                  checked={ofreceOnline}
                  onChange={(e) => setOfreceOnline(e.target.checked)}
                />
                <span className="check-chip__icon"><Icon.Video /></span>
                Consulta Online (Videollamada Meet)
              </label>
              <label className={`check-chip check-chip--auto ${ofrecePresencial ? 'active' : ''}`}>
                <input
                  type="checkbox"
                  checked={ofrecePresencial}
                  onChange={(e) => setOfrecePresencial(e.target.checked)}
                />
                <span className="check-chip__icon"><Icon.Building /></span>
                Consulta Presencial (Consultorio)
              </label>
            </div>
          </div>

          {ofrecePresencial && (
            <AddressMapPicker
              provincia={domicilioProvincia}
              onProvinciaChange={setDomicilioProvincia}
              direccion={domicilioAtencion}
              onDireccionChange={setDomicilioAtencion}
              lat={domicilioLat}
              lng={domicilioLng}
              onLocationChange={(lat, lng) => { setDomicilioLat(lat); setDomicilioLng(lng) }}
              provinciasList={PROVINCIAS_ARGENTINA}
            />
          )}

          <div className="settings-section-label">Título y matrícula</div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-degree">Título profesional</label>
            <input id="input-degree" className="form-input" type="text" value={degree} onChange={(e) => setDegree(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-mat-tipo">Tipo de Matrícula</label>
            <input id="input-mat-tipo" className="form-input" type="text" placeholder="Ej. MN, MP" value={matTipo} onChange={(e) => setMatTipo(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-mat-provincia">Provincia</label>
            <select id="input-mat-provincia" className="form-input" value={matProvincia} onChange={(e) => setMatProvincia(e.target.value)}>
              <option value="">Seleccioná una provincia</option>
              {PROVINCIAS_ARGENTINA.map(p => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-specialty-group">Grupo de Especialidad</label>
            <select 
              id="input-specialty-group" 
              className="form-input" 
              value={selectedGroup} 
              onChange={(e) => handleGroupChange(e.target.value)}
            >
              <option value="">Seleccioná un grupo</option>
              {ESPECIALIDADES_GRUPOS.map(g => (
                <option key={g.id} value={g.id}>{g.nombreGrupo}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-specialty">Especialidad</label>
            <select 
              id="input-specialty" 
              className="form-input" 
              value={specialty} 
              onChange={(e) => setSpecialty(e.target.value)}
              disabled={!selectedGroup}
            >
              <option value="">{selectedGroup ? 'Seleccioná una especialidad' : 'Primero seleccioná un grupo'}</option>
              {selectedGroup && ESPECIALIDADES_GRUPOS.find(g => g.id === selectedGroup)?.especialidades.map(esp => (
                <option key={esp.id} value={esp.nombre}>{esp.nombre}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-matricula">Número de Matrícula</label>
            <input id="input-matricula" className="form-input" type="text" value={matricula} onChange={(e) => setMatricula(e.target.value)} />
            <span className="form-helper">Verificada ✓</span>
          </div>

        </div>
        <div style={{ marginTop: 'var(--space-6)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-profile">
            {saving ? 'Guardando...' : 'Guardar perfil'}
          </button>
        </div>
      </div>
      )}

      {/* Public profile — shown to patients on the booking page, required to get verified */}
      {activeTab === 'perfil-publico' && (
      <div className="card">
        <div className="card__header">
          <div>
            <h2 className="card__title">Perfil público</h2>
            <p className="card__subtitle">Esta información se muestra a los pacientes en tu página de reserva. Es obligatoria para obtener la verificación de tu cuenta.</p>
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-descripcion-perfil">Descripción de tu perfil</label>
            <textarea
              id="input-descripcion-perfil"
              className="form-input"
              rows={4}
              placeholder="Contales a tus pacientes tu enfoque profesional, experiencia y cómo trabajás..."
              value={descripcionPerfil}
              onChange={(e) => setDescripcionPerfil(e.target.value)}
              style={{ resize: 'vertical', fontFamily: 'var(--font-body)' }}
            />
          </div>

          <div className="form-group">
            <label className="form-label form-label--required">Principales tratamientos</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              {TRATAMIENTOS_DISPONIBLES.map((t) => (
                <label key={t} className={`check-chip check-chip--auto ${selectedTags.includes(t) ? 'active' : ''}`}>
                  <input
                    type="checkbox"
                    checked={selectedTags.includes(t)}
                    onChange={() => toggleFromList(selectedTags, setSelectedTags, t)}
                  />
                  {t}
                </label>
              ))}
            </div>
          </div>

          <div className="form-group">
            <label className="form-label form-label--required">Pacientes que atendés</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              {PACIENTES_ATIENDE_OPCIONES.map((p) => (
                <label key={p} className={`check-chip check-chip--auto ${pacientesAtiende.includes(p) ? 'active' : ''}`}>
                  <input
                    type="checkbox"
                    checked={pacientesAtiende.includes(p)}
                    onChange={() => toggleFromList(pacientesAtiende, setPacientesAtiende, p)}
                  />
                  {p}
                </label>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="input-institucion">Institución de formación</label>
              <input
                id="input-institucion"
                className="form-input"
                type="text"
                placeholder="Ej. Universidad Nacional de Córdoba"
                value={institucionFormacion}
                onChange={(e) => setInstitucionFormacion(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label form-label--required" htmlFor="input-anios-experiencia">Años de experiencia clínica</label>
              <input
                id="input-anios-experiencia"
                className="form-input"
                type="number"
                min={0}
                placeholder="Ej. 15"
                value={aniosExperiencia}
                onChange={(e) => setAniosExperiencia(e.target.value === '' ? '' : Number(e.target.value))}
              />
            </div>
          </div>
        </div>
        <div style={{ marginTop: 'var(--space-6)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-public-profile">
            {saving ? 'Guardando...' : 'Guardar perfil público'}
          </button>
        </div>
      </div>
      )}

      {/* Presencia y Experiencia (Redes Sociales y Experiencias Laborales) */}
      {activeTab === 'presencia' && (
      <div className="card">
        <div className="card__header">
          <div>
            <h2 className="card__title">Presencia y Experiencia Laboral</h2>
            <p className="card__subtitle">Sumá tus redes sociales y tus experiencias laborales previas para dar confianza a tus pacientes.</p>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          {/* Redes Sociales */}
          <div>
            <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semi)', marginBottom: 'var(--space-3)' }}>
              Redes sociales y sitio web
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label" htmlFor="input-instagram">Instagram</label>
                <input id="input-instagram" className="form-input" type="url" placeholder="https://instagram.com/tu_usuario" value={instagram} onChange={(e) => setInstagram(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="input-linkedin">LinkedIn</label>
                <input id="input-linkedin" className="form-input" type="url" placeholder="https://linkedin.com/in/tu_usuario" value={linkedin} onChange={(e) => setLinkedin(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="input-sitio-web">Sitio web</label>
                <input id="input-sitio-web" className="form-input" type="url" placeholder="https://tu-sitio.com" value={sitioWeb} onChange={(e) => setSitioWeb(e.target.value)} />
              </div>
            </div>
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid var(--color-border)', margin: 'var(--space-2) 0' }} />

          {/* Experiencias Laborales */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
              <div>
                <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semi)', margin: 0 }}>
                  Experiencias laborales
                </h3>
                <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: '2px 0 0' }}>
                  Agregá los lugares donde trabajaste (clínicas, hospitales, consultorios) con su período y una breve descripción.
                </p>
              </div>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  setExpForm({ id: '', nombreLugar: '', desde: '', hasta: '', descripcion: '' })
                  setShowExpModal(true)
                }}
              >
                <Icon.Plus /> Agregar experiencia
              </button>
            </div>

            {experienciasLaborales.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: 'var(--space-6)',
                backgroundColor: 'var(--color-surface)',
                border: '1px dashed var(--color-border)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--color-text-secondary)',
                fontSize: 'var(--text-sm)'
              }}>
                Aún no agregaste experiencias laborales. Hacé clic en "Agregar experiencia" para sumar tu historial de trabajo.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {experienciasLaborales.map((exp) => (
                  <div
                    key={exp.id}
                    style={{
                      padding: 'var(--space-4)',
                      backgroundColor: 'var(--color-surface)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'flex-start',
                      gap: 'var(--space-3)'
                    }}
                  >
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
                        <strong style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>{exp.nombreLugar}</strong>
                        {(exp.desde || exp.hasta) && (
                          <span style={{
                            fontSize: '11px',
                            backgroundColor: 'var(--green-50)',
                            color: 'var(--green-700)',
                            padding: '2px 8px',
                            borderRadius: '12px',
                            fontWeight: '600'
                          }}>
                            {exp.desde} {exp.hasta ? `– ${exp.hasta}` : ''}
                          </span>
                        )}
                      </div>
                      {exp.descripcion && (
                        <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 'var(--space-2) 0 0', lineHeight: 1.5 }}>
                          {exp.descripcion}
                        </p>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        onClick={() => {
                          setExpForm(exp)
                          setShowExpModal(true)
                        }}
                        title="Editar"
                      >
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                        </svg>
                      </button>
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        onClick={() => handleDeleteExpItem(exp.id)}
                        title="Eliminar"
                        style={{ color: 'var(--color-error)' }}
                      >
                        <Icon.Trash size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div style={{ marginTop: 'var(--space-6)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-presencia">
            {saving ? 'Guardando...' : 'Guardar presencia y experiencia'}
          </button>
        </div>
      </div>
      )}

      {/* Tariffs */}
      {activeTab === 'honorarios' && (
      <div className="card">
        <div className="card__header">
          <div>
            <h2 className="card__title">Honorarios y servicios</h2>
            <p className="card__subtitle">Configurá los precios de cada tipo de consulta. Solo los servicios habilitados se muestran al paciente.</p>
          </div>
        </div>
        <table className="settings-fees-table">
          <thead>
            <tr>
              <th style={{ width: '44px' }}></th>
              <th>Servicio</th>
              <th>Valor (ARS)</th>
              <th style={{ width: '40px' }}></th>
            </tr>
          </thead>
          <tbody>
            {tariffs.map((t) => (
              <tr key={t.id} style={{ opacity: t.enabled ? 1 : 0.5, transition: 'opacity 150ms' }}>
                <td>
                  <label className="toggle" style={{ transform: 'scale(0.8)' }}>
                    <input
                      type="checkbox"
                      checked={t.enabled}
                      onChange={(e) => updateTariff(t.id, 'enabled', e.target.checked)}
                    />
                    <span className="toggle__track" />
                  </label>
                </td>
                <td>
                  <input
                    className="form-input settings-fee-name"
                    type="text"
                    value={t.label}
                    onChange={(e) => updateTariff(t.id, 'label', e.target.value)}
                    disabled={!t.enabled}
                  />
                </td>
                <td className="settings-fee-val">
                  <input
                    className="form-input"
                    type="number"
                    value={t.price}
                    onChange={(e) => updateTariff(t.id, 'price', Number(e.target.value))}
                    disabled={!t.enabled}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="settings-fee-delete"
                    onClick={() => deleteTariff(t.id)}
                    aria-label={`Eliminar ${t.label}`}
                    title="Eliminar servicio"
                  >
                    <Icon.Trash size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {showAddTariff ? (
          <div className="settings-fee-add-row">
            <input
              className="form-input"
              type="text"
              placeholder="Nombre del servicio (ej. Consulta domiciliaria)"
              value={newTariffName}
              onChange={(e) => setNewTariffName(e.target.value)}
              style={{ flex: 1 }}
            />
            <input
              className="form-input"
              type="number"
              placeholder="Valor ARS"
              value={newTariffPrice}
              onChange={(e) => setNewTariffPrice(e.target.value)}
              style={{ width: '140px' }}
            />
            <button type="button" className="btn btn--primary btn--sm" onClick={handleAddTariff}>Agregar</button>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={() => { setShowAddTariff(false); setNewTariffName(''); setNewTariffPrice('') }}
            >
              Cancelar
            </button>
          </div>
        ) : (
          <button type="button" className="btn btn--secondary btn--sm" style={{ marginTop: 'var(--space-4)' }} onClick={() => setShowAddTariff(true)}>
            <Icon.Plus /> Agregar servicio nuevo
          </button>
        )}

        <div style={{ marginTop: 'var(--space-5)', display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-tariffs">
            {saving ? 'Guardando...' : 'Guardar honorarios'}
          </button>
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            Tranqui es 100% libre de comisiones, por lo que recibís la totalidad de tus honorarios.
          </span>
        </div>
      </div>
      )}

      {/* Notifications */}
      {activeTab === 'notificaciones' && (
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Notificaciones</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          {[
            { id: 'notif-new-booking', label: 'Nueva reserva', desc: 'Te avisamos por email y WhatsApp cuando un paciente agenda.' },
            { id: 'notif-cancel', label: 'Cancelaciones', desc: 'Notificación cuando un paciente cancela o reprograma.' },
            { id: 'notif-reminder', label: 'Recordatorio de sesión', desc: '1 hora antes del inicio de cada sesión.' },
          ].map(({ id, label, desc }) => (
            <div key={id} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
              <label className="toggle">
                <input type="checkbox" defaultChecked id={id} />
                <span className="toggle__track" />
              </label>
              <div>
                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--font-weight-medium)' }}>{label}</div>
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
      )}

      {/* Integrations */}
      {activeTab === 'integraciones' && (
      <>
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Integración con Mercado Pago</h2>
          <p className="card__subtitle">Vinculá tu cuenta para cobrar tus sesiones directamente en tu Mercado Pago, 100% libre de comisiones.</p>
        </div>
        <MPConnectBanner connected={mpConnected} onConnect={onConnect} onDisconnect={onDisconnect} />
      </div>

      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Integración con Google Calendar</h2>
          <p className="card__subtitle">Vinculá tu cuenta para generar automáticamente reuniones de Google Meet en tu agenda.</p>
        </div>
        <GoogleCalendarConnectBanner connected={googleConnected} onConnect={onConnectGoogle} onDisconnect={onDisconnectGoogle} />
      </div>
      </>
      )}

      {/* Modal para Agregar/Editar Experiencia Laboral */}
      {showExpModal && expForm && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: 'var(--space-4)'
        }}>
          <div className="card" style={{ width: '100%', maxWidth: '500px', backgroundColor: 'white', padding: 'var(--space-6)', borderRadius: '12px' }}>
            <h3 style={{ margin: '0 0 var(--space-4)', fontSize: 'var(--text-lg)' }}>
              {expForm.id ? 'Editar experiencia laboral' : 'Nueva experiencia laboral'}
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label form-label--required">Nombre del lugar u organización</label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="Ej. Hospital Italiano, Consultorio Privado"
                  value={expForm.nombreLugar}
                  onChange={(e) => setExpForm({ ...expForm, nombreLugar: e.target.value })}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
                <div className="form-group">
                  <label className="form-label">Desde (Mes/Año)</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej. 03/2018"
                    value={expForm.desde}
                    onChange={(e) => setExpForm({ ...expForm, desde: formatMonthYearInput(e.target.value) })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Hasta (Mes/Año)</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="Ej. 12/2022 o Actualidad"
                    value={expForm.hasta}
                    onChange={(e) => setExpForm({ ...expForm, hasta: formatMonthYearInput(e.target.value) })}
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Breve descripción de lo que hiciste</label>
                <textarea
                  className="form-input"
                  rows={3}
                  placeholder="Describí brevemente tus responsabilidades, rol o tareas..."
                  value={expForm.descripcion}
                  onChange={(e) => setExpForm({ ...expForm, descripcion: e.target.value })}
                />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-6)' }}>
              <button
                type="button"
                className="btn btn--secondary"
                onClick={() => {
                  setShowExpModal(false)
                  setExpForm(null)
                }}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="btn btn--primary"
                onClick={handleSaveExpItem}
                disabled={!expForm.nombreLugar.trim()}
              >
                Guardar experiencia
              </button>
            </div>
          </div>
        </div>
      )}
        </div>
      </div>
    </div>
  )
}

function DashboardHome({
  mpConnected,
  onConnect,
  onDisconnect,
  googleConnected,
  onConnectGoogle,
  onDisconnectGoogle,
  appointments,
  allAppointments,
  externalEvents,
  availability,
  stats,
  onCancelAppointment,
  onUpdateAttendance,
  onRescheduleAppointment,
  medicoInfo,
  onNavigate
}: {
  mpConnected: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
  googleConnected: boolean;
  onConnectGoogle: () => void;
  onDisconnectGoogle: () => void;
  appointments: Appointment[];
  allAppointments: any[];
  externalEvents: ExternalEvent[];
  availability: any[];
  stats: any;
  onCancelAppointment: (id: number) => void;
  onUpdateAttendance: (id: number, status: string) => void;
  onRescheduleAppointment: (id: number, date: string, hour: string) => void;
  medicoInfo?: any;
  onNavigate?: (section: NavSection) => void;
}) {
  const dateStr = new Date().toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' });
  const capitalizedDate = dateStr.charAt(0).toUpperCase() + dateStr.slice(1);
  const fullDateStr = new Date().toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const capitalizedFullDate = fullDateStr.charAt(0).toUpperCase() + fullDateStr.slice(1);

  const [calendarView, setCalendarView] = useState<'monthly' | 'weekly' | 'today'>('monthly');
  const [showInactiveSlots, setShowInactiveSlots] = useState(false);
  const [selectedAppt, setSelectedAppt] = useState<any | null>(null);
  const [currentDate, setCurrentDate] = useState(() => new Date());
  const [currentTime, setCurrentTime] = useState(new Date());
  const [isRescheduling, setIsRescheduling] = useState(false);
  const [rescheduleDate, setRescheduleDate] = useState('');
  const [rescheduleHour, setRescheduleHour] = useState('09:00');

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentTime(new Date());
    }, 10000); // update every 10s
    return () => clearInterval(interval);
  }, []);

  // Find the next active/confirmed appointment closest to now
  const nextAppt = useMemo(() => {
    if (!allAppointments || allAppointments.length === 0) return null;
    const now = currentTime;
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const todayStr = `${year}-${month}-${day}`;
    const currentHour = now.getHours();
    const currentMin = now.getMinutes();

    const upcoming = allAppointments
      .filter(a => a.status !== 'completed' && a.status !== 'cancelled' && a.attendanceStatus !== 'AUSENTE' && a.attendanceStatus !== 'COMPLETADA')
      .sort((a, b) => {
        const dateDiff = a.fecha.localeCompare(b.fecha);
        if (dateDiff !== 0) return dateDiff;
        return a.hour.localeCompare(b.hour);
      });

    return upcoming.find(a => {
      if (a.fecha === todayStr) {
        const parts = a.hour.split(':');
        const apptHour = parseInt(parts[0]);
        const apptMin = parseInt(parts[1] || '0');
        if (apptHour > currentHour) return true;
        if (apptHour === currentHour) return apptMin >= currentMin;
        return false;
      }
      return a.fecha > todayStr;
    }) || upcoming[0];
  }, [allAppointments, currentTime]);

  // Real "today" (independent of calendar navigation) — used for the "Próximos Eventos" side panel
  const todaysAppointments = useMemo(() => {
    const now = currentTime;
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    if (!allAppointments) return [];
    return allAppointments
      .filter(a => a.fecha === todayStr && a.status !== 'cancelled')
      .sort((a, b) => a.hour.localeCompare(b.hour));
  }, [allAppointments, currentTime]);

  const todaysExternalEvents = useMemo(() => {
    const now = currentTime;
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    if (!externalEvents) return [];
    return externalEvents
      .filter(e => e.fecha === todayStr)
      .sort((a, b) => a.hour.localeCompare(b.hour));
  }, [externalEvents, currentTime]);

  // "Próximos Eventos": turnos + personal Google Calendar events for today, merged into one
  // chronological list (all-day Google events are pinned first since they have no real hour).
  const todaysCombinedEvents = useMemo(() => {
    const turnoItems = todaysAppointments.map(a => ({ kind: 'turno' as const, sortKey: a.hour, data: a }));
    const externalItems = todaysExternalEvents.map(e => ({ kind: 'external' as const, sortKey: e.allDay ? '' : e.hour, data: e }));
    return [...turnoItems, ...externalItems].sort((a, b) => a.sortKey.localeCompare(b.sortKey));
  }, [todaysAppointments, todaysExternalEvents]);

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
  };

  const getCountdownString = (appt: any) => {
    if (!appt || !appt.fecha || !appt.hour) return '';
    const parts = appt.fecha.split('-');
    const hourParts = appt.hour.split(':');
    const target = new Date(
      parseInt(parts[0]),
      parseInt(parts[1]) - 1,
      parseInt(parts[2]),
      parseInt(hourParts[0]),
      parseInt(hourParts[1] || '0')
    );
    const diffMs = target.getTime() - currentTime.getTime();
    if (diffMs <= 0) return 'Ahora';
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 60) return `En ${diffMins} min`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `En ${diffHours} hs`;
    const diffDays = Math.floor(diffHours / 24);
    return `En ${diffDays} días`;
  };

  const getDayOfWeek = (dateStr: string) => {
    if (!dateStr) return -1;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return -1;
    const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    const day = d.getDay();
    return day === 0 ? 7 : day; // Map Sunday to 7
  };

  const isSlotAvailable = (dayNum: number, time: string) => {
    return availability.some(av => av.diaSemana === dayNum && av.horaInicio.startsWith(time.substring(0, 5)) && av.activo);
  };

  // Navigations for the trimodal view
  const handlePrevPeriod = () => {
    setCurrentDate(prev => {
      const d = new Date(prev);
      if (calendarView === 'monthly') {
        d.setMonth(d.getMonth() - 1);
      } else if (calendarView === 'weekly') {
        d.setDate(d.getDate() - 7);
      } else {
        d.setDate(d.getDate() - 1);
      }
      return d;
    });
  };

  const handleNextPeriod = () => {
    setCurrentDate(prev => {
      const d = new Date(prev);
      if (calendarView === 'monthly') {
        d.setMonth(d.getMonth() + 1);
      } else if (calendarView === 'weekly') {
        d.setDate(d.getDate() + 7);
      } else {
        d.setDate(d.getDate() + 1);
      }
      return d;
    });
  };

  const getPeriodLabel = () => {
    if (calendarView === 'monthly') {
      return currentDate.toLocaleDateString('es-AR', { month: 'long', year: 'numeric' }).toUpperCase();
    } else if (calendarView === 'weekly') {
      const day = currentDate.getDay();
      const diff = currentDate.getDate() - day + (day === 0 ? -6 : 1);
      const monday = new Date(currentDate.getFullYear(), currentDate.getMonth(), diff);
      const friday = new Date(monday);
      friday.setDate(monday.getDate() + 4);
      return `${monday.getDate()} ${monday.toLocaleDateString('es-AR', { month: 'short' })} — ${friday.getDate()} ${friday.toLocaleDateString('es-AR', { month: 'short' })} ${friday.getFullYear()}`;
    } else {
      return currentDate.toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'short' }).toUpperCase();
    }
  };

  // Rescheduled appointments date calculations (weekly grid view)
  const currentWeekMonday = useMemo(() => {
    const d = new Date(currentDate);
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(d.getFullYear(), d.getMonth(), diff);
  }, [currentDate]);

  const weekdays = [
    { name: 'Lunes', abbr: 'Lun', num: 1 },
    { name: 'Martes', abbr: 'Mar', num: 2 },
    { name: 'Miércoles', abbr: 'Mié', num: 3 },
    { name: 'Jueves', abbr: 'Jue', num: 4 },
    { name: 'Viernes', abbr: 'Vie', num: 5 },
    { name: 'Sábado', abbr: 'Sáb', num: 6 },
    { name: 'Domingo', abbr: 'Dom', num: 7 },
  ];

  const weekdaysWithDates = useMemo(() => {
    const now = new Date();
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return weekdays.map((day, idx) => {
      const cellDate = new Date(currentWeekMonday);
      cellDate.setDate(currentWeekMonday.getDate() + idx);
      const year = cellDate.getFullYear();
      const month = String(cellDate.getMonth() + 1).padStart(2, '0');
      const dateNum = String(cellDate.getDate()).padStart(2, '0');
      const dateStr = `${year}-${month}-${dateNum}`;
      return {
        ...day,
        dateStr,
        dayNum: cellDate.getDate(),
        monthNum: cellDate.getMonth() + 1,
        isToday: dateStr === todayStr,
        label: `${day.abbr} ${cellDate.getDate()}/${cellDate.getMonth() + 1}`
      };
    });
  }, [currentWeekMonday]);

  // Dynamic slot height helper
  const getDynamicSlots = () => {
    let minHour = 9;
    let maxHour = 18;
    
    if (availability && availability.length > 0) {
      const activeAvailabilities = availability.filter(av => av.activo);
      if (activeAvailabilities.length > 0) {
        const startHours = activeAvailabilities.map(av => parseInt(av.horaInicio.split(':')[0]));
        const endHours = activeAvailabilities.map(av => parseInt(av.horaFin.split(':')[0]));
        
        minHour = Math.min(...startHours);
        maxHour = Math.max(...endHours);
      }
    }
    
    const slots = [];
    for (let h = minHour; h < maxHour; h++) {
      slots.push(`${String(h).padStart(2, '0')}:00`);
    }
    return slots.length > 0 ? slots : ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00'];
  };

  const baseSlots = getDynamicSlots();

  // Monthly days array (35 cells)
  const monthDays = useMemo(() => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    const firstDayOfMonth = new Date(year, month, 1);
    const startDayOfWeek = firstDayOfMonth.getDay();
    const startOffset = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1; // offset to Monday

    const startDate = new Date(year, month, 1);
    startDate.setDate(startDate.getDate() - startOffset);

    const days = [];
    for (let i = 0; i < 35; i++) {
      const d = new Date(startDate);
      d.setDate(startDate.getDate() + i);
      days.push(d);
    }
    return days;
  }, [currentDate]);

  // Appointments for the selected day in Diario view
  const selectedDayStr = `${currentDate.getFullYear()}-${String(currentDate.getMonth() + 1).padStart(2, '0')}-${String(currentDate.getDate()).padStart(2, '0')}`;
  const dayAppointments = allAppointments.filter(a => a.fecha === selectedDayStr && a.status !== 'cancelled');
  const dayExternalEvents = externalEvents.filter(e => e.fecha === selectedDayStr);

  return (
    <>
      <div className="dashboard-home-greeting">
        <div>
          <h1>Hola, {medicoInfo?.name ? medicoInfo.name : 'Doctor/a'} 👋</h1>
          <p>Este es el resumen de tu consultorio hoy</p>
        </div>
        <div className="dashboard-home-date-pill">{capitalizedFullDate}</div>
      </div>

      {(mpConnected || googleConnected) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
          {mpConnected && (
            <div className="dashboard-home-status-strip">
              <span className="dashboard-home-status-dot" />
              Mercado Pago conectado — tus próximos cobros se liquidan automáticamente cada semana.
            </div>
          )}
          {googleConnected && (
            <div className="dashboard-home-status-strip">
              <span className="dashboard-home-status-dot" />
              Google Calendar conectado — tus videollamadas generan un Meet real automáticamente.
            </div>
          )}
        </div>
      )}

      {(!mpConnected || !googleConnected) && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 'var(--space-4)',
          marginBottom: 'var(--space-4)'
        }}>
          {!mpConnected && (
            <MPConnectBanner connected={mpConnected} onConnect={onConnect} onDisconnect={onDisconnect} />
          )}
          {!googleConnected && (
            <GoogleCalendarConnectBanner connected={googleConnected} onConnect={onConnectGoogle} onDisconnect={onDisconnectGoogle} />
          )}
        </div>
      )}

      {nextAppt && (
        <div className="card" style={{ width: '100%', maxWidth: 'none', marginBottom: 'var(--space-4)', padding: 'var(--space-5)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 'var(--space-3)', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--space-4)' }}>
            <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', fontSize: 'var(--text-sm)', color: 'var(--color-primary)', textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 16, height: 16, color: 'var(--color-primary)' }}><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              Próximo Turno Programado
            </h2>
            <div style={{
              backgroundColor: 'var(--green-50)',
              color: 'var(--color-primary)',
              padding: 'var(--space-1) var(--space-3)',
              borderRadius: 'var(--radius-full)',
              fontSize: 'var(--text-xs)',
              fontWeight: 'bold',
              border: '1px solid var(--green-200)',
              display: 'flex',
              alignItems: 'center',
              gap: '6px'
            }}>
              <span style={{ display: 'inline-block', width: '6px', height: '6px', backgroundColor: 'var(--color-primary)', borderRadius: '50%' }}></span>
              {getCountdownString(nextAppt)}
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
            <div style={{ textAlign: 'left' }}>
              <p style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)' }}>
                {nextAppt.patientName}
              </p>
              <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-2)', flexWrap: 'wrap' }}>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  📅 <strong>Fecha:</strong> {formatDate(nextAppt.fecha)}
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  ⏰ <strong>Horario:</strong> {nextAppt.hour} hs
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  🩺 <strong>Modalidad:</strong> {nextAppt.type}
                </span>
              </div>
              <div style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-xs)', display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <span style={{ color: 'var(--color-text-secondary)' }}>Asistencia:</span>
                <span className="badge" style={{ 
                  backgroundColor: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--green-100)' : nextAppt.attendanceStatus === 'AUSENTE' ? '#fdf2f2' : 'var(--neutral-100)',
                  color: nextAppt.attendanceStatus === 'LLEGO' ? 'var(--green-700)' : nextAppt.attendanceStatus === 'AUSENTE' ? 'var(--color-danger)' : 'var(--color-text-secondary)',
                  fontWeight: 'bold',
                  fontSize: '11px',
                  padding: '2px 8px',
                  borderRadius: 'var(--radius-full)'
                }}>
                  {nextAppt.attendanceStatus || 'SIN CONFIRMAR'}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              {nextAppt.meetLink && nextAppt.status === 'confirmed' && (
                <a
                  href={nextAppt.meetLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn--primary"
                  id="btn-next-meet"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)' }}
                >
                  <Icon.Video />
                  Unirse al Meet
                </a>
              )}
              <button 
                onClick={() => setSelectedAppt(nextAppt)}
                className="btn btn--secondary"
              >
                Ficha de Turno
              </button>
            </div>
          </div>
        </div>
      )}

      <StatsOverview stats={stats} />

      <div className="dashboard-home-maingrid">
      <div className="card dashboard-home-cal-panel">
        <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)' }}>
          <div>
            <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
              <span>
                {calendarView === 'monthly' ? 'Calendario Mensual' : calendarView === 'weekly' ? 'Calendario Semanal' : 'Sesiones del Día'}
              </span>
              <div className="dashboard-home-month-nav">
                <button onClick={handlePrevPeriod} aria-label="Período anterior">‹</button>
                <span>{getPeriodLabel()}</span>
                <button onClick={handleNextPeriod} aria-label="Período siguiente">›</button>
              </div>
            </h2>
            <p className="card__subtitle">
              {calendarView === 'monthly'
                ? 'Vista de distribución de turnos mensual'
                : calendarView === 'weekly'
                  ? 'Cronograma de turnos por día y horario'
                  : `${capitalizedDate} · ${dayAppointments.length} sesiones programadas${dayExternalEvents.length ? ` · ${dayExternalEvents.length} eventos personales` : ''}`
              }
            </p>
          </div>

          <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center' }}>
            {calendarView === 'weekly' && (
              <button
                onClick={() => setShowInactiveSlots(!showInactiveSlots)}
                className="btn btn--secondary btn--sm"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)',
                  borderColor: showInactiveSlots ? 'var(--color-primary)' : 'var(--color-border)',
                  backgroundColor: showInactiveSlots ? 'var(--green-50)' : 'transparent',
                  color: showInactiveSlots ? 'var(--color-primary)' : 'var(--color-text-primary)'
                }}
              >
                {showInactiveSlots ? <Icon.EyeOff /> : <Icon.Eye />}
                {showInactiveSlots ? 'Ocultar no laborables' : 'Ver inactivos'}
              </button>
            )}
            <div className="dashboard-home-seg">
              <span
                onClick={() => setCalendarView('monthly')}
                className={calendarView === 'monthly' ? 'active' : ''}
              >
                Mensual
              </span>
              <span
                onClick={() => setCalendarView('weekly')}
                className={calendarView === 'weekly' ? 'active' : ''}
              >
                Semanal
              </span>
              <span
                onClick={() => setCalendarView('today')}
                className={calendarView === 'today' ? 'active' : ''}
              >
                Diario
              </span>
            </div>
          </div>
        </div>

        {calendarView === 'today' ? (
          dayAppointments.length === 0 && dayExternalEvents.length === 0 ? (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', padding: 'var(--space-6)', textAlign: 'center' }}>
              No tenés sesiones programadas para este día.
            </p>
          ) : (
            <ul className="appointment-list" role="list" aria-label="Sesiones de hoy">
              {dayAppointments.map((appt) => (
                <AppointmentCard key={appt.id} appt={appt} />
              ))}
              {dayExternalEvents.map((event) => (
                <ExternalEventCard key={event.id} event={event} />
              ))}
            </ul>
          )
        ) : calendarView === 'weekly' ? (
          /* Weekly Calendar Matrix Grid — identical design system, grid gap, and background colors as Monthly view */
          <div style={{ width: '100%', overflowX: 'auto', marginTop: 'var(--space-3)' }}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: '55px repeat(7, minmax(85px, 1fr))',
              gap: '2px',
              backgroundColor: 'var(--color-border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
              boxShadow: 'var(--shadow-sm)'
            }}>
              {/* Corner Header */}
              <div style={{
                backgroundColor: 'var(--green-50)',
                color: 'var(--color-primary)',
                padding: '6px 2px',
                textAlign: 'center',
                fontWeight: 'bold',
                fontSize: '11px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                HORA
              </div>

              {/* Day Headers (7 Days: Lun - Dom) */}
              {weekdaysWithDates.map((day) => (
                <div key={day.num} style={{
                  backgroundColor: 'var(--green-50)',
                  color: 'var(--color-primary)',
                  padding: '6px 2px',
                  textAlign: 'center',
                  fontWeight: 'bold',
                  fontSize: '11px',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '2px'
                }}>
                  <span style={{ fontSize: '10px', textTransform: 'uppercase', opacity: 0.85 }}>{day.abbr}</span>
                  <div style={{
                    fontWeight: day.isToday ? 'bold' : '500',
                    fontSize: '11px',
                    color: day.isToday ? 'white' : 'var(--color-primary)',
                    borderRadius: '50%',
                    padding: '2px 6px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: day.isToday ? 'var(--color-primary)' : 'transparent'
                  }}>
                    {day.dayNum}/{day.monthNum}
                  </div>
                </div>
              ))}

              {/* Rows by hour */}
              {baseSlots.filter((slot) => {
                if (showInactiveSlots) return true;
                const slotHour = parseInt(slot.split(':')[0]);
                const hasActiveAvailability = weekdays.some((day) => isSlotAvailable(day.num, slot));
                const hasAppointment = allAppointments.some((a) => {
                  const apptHour = parseInt(a.hour);
                  return weekdaysWithDates.some(d => d.dateStr === a.fecha) && apptHour === slotHour && a.status !== 'cancelled';
                });
                const hasExternalEvent = externalEvents.some((e) => {
                  if (e.allDay) return false;
                  const eventHour = parseInt(e.hour);
                  return weekdaysWithDates.some(d => d.dateStr === e.fecha) && eventHour === slotHour;
                });
                return hasActiveAvailability || hasAppointment || hasExternalEvent;
              }).map((slot) => {
                const slotHour = parseInt(slot.split(':')[0]);
                return (
                  <React.Fragment key={slot}>
                    {/* Hour Label Column */}
                    <div style={{
                      backgroundColor: 'var(--green-50)',
                      color: 'var(--color-primary)',
                      fontSize: '11px',
                      fontWeight: 'bold',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      padding: '4px'
                    }}>
                      {slot} hs
                    </div>

                    {/* Day Columns for this hour */}
                    {weekdaysWithDates.map((day) => {
                      const appt = allAppointments.find(a => {
                        const apptHour = parseInt(a.hour);
                        return a.fecha === day.dateStr && apptHour === slotHour && a.status !== 'cancelled';
                      });
                      const externalEvent = externalEvents.find(e => {
                        if (e.allDay) return false;
                        const eventHour = parseInt(e.hour);
                        return e.fecha === day.dateStr && eventHour === slotHour;
                      });
                      const isActive = isSlotAvailable(day.num, slot);
                      const isCellVisible = appt || externalEvent || isActive || showInactiveSlots;

                      return (
                        <div key={day.num} style={{
                          backgroundColor: day.isToday ? '#F0F9F1' : '#ffffff',
                          minHeight: '52px',
                          padding: '4px',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '3px',
                          opacity: isCellVisible ? 1 : 0.4
                        }}>
                          {externalEvent && (
                            <div style={{
                              padding: '4px 6px',
                              borderRadius: 'var(--radius-sm)',
                              backgroundColor: '#eef4fe',
                              border: '1px solid #c9dcfb',
                              borderLeft: '3px solid #4285f4',
                              fontSize: '10px',
                              fontWeight: '600',
                              color: 'var(--color-text-primary)',
                              width: '100%'
                            }}>
                              📅 {externalEvent.title}
                            </div>
                          )}
                          {appt ? (
                            <div 
                              onClick={() => setSelectedAppt(appt)}
                              style={{
                                padding: '4px 6px',
                                borderRadius: 'var(--radius-sm)',
                                backgroundColor: 'var(--color-surface)',
                                border: '1px solid var(--color-border)',
                                borderLeft: appt.status === 'confirmed' 
                                  ? '3px solid var(--color-primary)' 
                                  : appt.status === 'completed' 
                                    ? '3px solid var(--neutral-400)' 
                                    : '3px solid #f59e0b',
                                boxShadow: 'var(--shadow-xs)',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '2px',
                                cursor: 'pointer',
                                width: '100%',
                                transition: 'transform 0.15s ease'
                              }}
                              onMouseEnter={(e) => {
                                e.currentTarget.style.transform = 'translateY(-1px)';
                              }}
                              onMouseLeave={(e) => {
                                e.currentTarget.style.transform = 'translateY(0)';
                              }}
                            >
                              <div style={{ fontSize: '11px', fontWeight: '700', color: 'var(--color-text-primary)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{appt.patientName}</span>
                                {appt.meetUrl && (
                                  <span title="Videollamada Google Meet" style={{ color: '#1a73e8', fontSize: '10px' }}>📹</span>
                                )}
                              </div>
                              {appt.attendanceStatus && appt.attendanceStatus !== 'ESPERANDO' && (
                                <div style={{
                                  fontSize: '9px',
                                  fontWeight: '600',
                                  color: appt.attendanceStatus === 'LLEGO' 
                                    ? 'var(--color-primary)' 
                                    : appt.attendanceStatus === 'AUSENTE'
                                      ? 'var(--color-danger)'
                                      : '#10b981',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '2px'
                                }}>
                                  <span>{appt.attendanceStatus === 'LLEGO' ? '🚶‍♂️' : appt.attendanceStatus === 'AUSENTE' ? '❌' : '✓'}</span>
                                  {appt.attendanceStatus}
                                </div>
                              )}
                            </div>
                          ) : isActive ? (
                            <div style={{
                              textAlign: 'center',
                              fontSize: '10px',
                              color: 'var(--color-primary)',
                              fontWeight: '600',
                              backgroundColor: 'rgba(0, 166, 80, 0.08)',
                              padding: '2px 5px',
                              borderRadius: 'var(--radius-sm)'
                            }}>
                              Disponible
                            </div>
                          ) : (
                            <div style={{
                              textAlign: 'center',
                              fontSize: '10px',
                              color: 'var(--neutral-400)',
                              fontStyle: 'italic'
                            }}>
                              —
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </React.Fragment>
                );
              })}
            </div>
          </div>
        ) : (
          /* Monthly Calendar Grid — compact, icon-focused calendar */
          <div style={{ width: '100%', maxWidth: '560px', margin: 'var(--space-3) auto 0' }}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(7, 1fr)',
              gap: '2px',
              backgroundColor: 'var(--color-border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
              boxShadow: 'var(--shadow-sm)'
            }}>
              {['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'].map((dName) => (
                <div key={dName} style={{
                  backgroundColor: 'var(--green-50)',
                  color: 'var(--color-primary)',
                  padding: '6px 2px',
                  textAlign: 'center',
                  fontWeight: 'bold',
                  fontSize: '11px'
                }}>
                  {dName}
                </div>
              ))}
              {monthDays.map((d, index) => {
                const isCurrentMonth = d.getMonth() === currentDate.getMonth();
                const isToday = d.toDateString() === new Date().toDateString();
                const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
                const dayAppts = allAppointments.filter(a => a.fecha === dateStr && a.status !== 'cancelled');
                const dayExternalEvts = externalEvents.filter(e => e.fecha === dateStr);
                const dayItemsTotal = dayAppts.length + dayExternalEvts.length;

                return (
                  <div key={index} style={{
                    backgroundColor: isToday ? '#F0F9F1' : '#ffffff',
                    minHeight: '52px',
                    padding: '4px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '3px',
                    opacity: isCurrentMonth ? 1 : 0.35
                  }}>
                    <div style={{
                      fontWeight: isToday ? 'bold' : '500',
                      fontSize: '12px',
                      color: isToday ? 'white' : 'var(--color-text-primary)',
                      borderRadius: '50%',
                      width: '22px',
                      height: '22px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      backgroundColor: isToday ? 'var(--color-primary)' : 'transparent'
                    }}>
                      {d.getDate()}
                    </div>
                    {dayItemsTotal > 0 && (
                      <div style={{ display: 'flex', gap: '3px', alignItems: 'center', flexWrap: 'wrap', justifyContent: 'center' }}>
                        {dayAppts.map((a) => (
                          <span
                            key={a.id}
                            onClick={() => setSelectedAppt(a)}
                            title={`Turno: ${a.hour} hs - ${a.patientName}`}
                            style={{
                              width: '7px',
                              height: '7px',
                              borderRadius: '50%',
                              backgroundColor: a.status === 'confirmed' ? 'var(--color-primary)' : '#f59e0b',
                              display: 'inline-block',
                              cursor: 'pointer'
                            }}
                          />
                        ))}
                        {dayExternalEvts.map((evt) => (
                          <span
                            key={evt.id}
                            title={`Evento: ${evt.title}`}
                            style={{
                              width: '7px',
                              height: '7px',
                              borderRadius: '50%',
                              backgroundColor: '#3b82f6',
                              display: 'inline-block'
                            }}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>

      <div className="dashboard-home-side-stack">
        <div className="card">
          <div className="card__header">
            <div>
              <h2 className="card__title">Próximos Eventos</h2>
              <p className="card__subtitle">Hoy, {capitalizedDate.replace(/^\w+ /, '')}</p>
            </div>
          </div>
          {todaysCombinedEvents.length === 0 ? (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', textAlign: 'center', padding: 'var(--space-4) 0' }}>
              No tenés más sesiones ni eventos programados para hoy.
            </p>
          ) : (
            <ul className="appointment-list appointment-list--compact" role="list" aria-label="Próximos eventos de hoy">
              {todaysCombinedEvents.map((item) => (
                item.kind === 'turno'
                  ? <AppointmentCard key={`turno-${item.data.id}`} appt={item.data} compact />
                  : <ExternalEventCard key={`gcal-${item.data.id}`} event={item.data} compact />
              ))}
            </ul>
          )}
        </div>

        <div className="card">
          <div className="card__header">
            <h2 className="card__title">Acciones rápidas</h2>
          </div>
          <div className="dashboard-home-quick-actions">
            <button className="dashboard-home-qa-btn" onClick={() => onNavigate?.('agenda')}>
              <span className="dashboard-home-qa-icon"><Icon.Calendar /></span>
              <span className="dashboard-home-qa-label">Nuevo turno</span>
            </button>
            <button className="dashboard-home-qa-btn" onClick={() => onNavigate?.('prescriptions')}>
              <span className="dashboard-home-qa-icon"><Icon.Prescription /></span>
              <span className="dashboard-home-qa-label">Emitir receta</span>
            </button>
            <button className="dashboard-home-qa-btn" onClick={() => onNavigate?.('clinical-history')}>
              <span className="dashboard-home-qa-icon"><Icon.ClinicalRecord /></span>
              <span className="dashboard-home-qa-label">Historia clínica</span>
            </button>
            <button className="dashboard-home-qa-btn" onClick={() => onNavigate?.('patients')}>
              <span className="dashboard-home-qa-icon"><Icon.MessageCircle /></span>
              <span className="dashboard-home-qa-label">Ver mensajes</span>
            </button>
          </div>
        </div>
      </div>
      </div>

      {/* Appointment Detail Popup Modal */}
      {selectedAppt && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1100,
          padding: 'var(--space-4)'
        }}>
          <div className="card" style={{
            maxWidth: '540px',
            width: '100%',
            padding: 0,
            boxShadow: 'var(--shadow-xl)',
            backgroundColor: '#ffffff',
            borderRadius: 'var(--radius-lg)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden'
          }}>
            {/* Header */}
            <div style={{
              padding: 'var(--space-5)',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              backgroundColor: 'var(--neutral-50)'
            }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'bold', color: 'var(--color-text-primary)' }}>
                  Detalle del Turno #{selectedAppt.id}
                </h3>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Registrado el {formatDate(selectedAppt.fecha)}
                </span>
              </div>
              <button 
                onClick={() => {
                  setSelectedAppt(null);
                  setIsRescheduling(false);
                }} 
                style={{ border: 'none', background: 'none', fontSize: '20px', cursor: 'pointer', color: 'var(--color-text-secondary)' }}
              >
                &times;
              </button>
            </div>

            {/* Content */}
            <div style={{
              padding: 'var(--space-5)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-4)',
              maxHeight: '450px',
              overflowY: 'auto'
            }}>
              {/* Patient info block */}
              <div>
                <label style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>
                  Información del Paciente
                </label>
                <div className="turno-modal-grid" style={{
                  marginTop: 'var(--space-2)',
                  backgroundColor: 'var(--neutral-50)',
                  padding: 'var(--space-4)',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)'
                }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Nombre Completo</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>
                      {selectedAppt.patientName}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Email</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                      {selectedAppt.patientInfo?.email || '-'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>DNI</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                      {selectedAppt.patientInfo?.dni || '-'}
                    </span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Dato de Contacto</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '500' }}>
                      {selectedAppt.patientInfo?.telefono || '-'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Cobertura / Obra Social details */}
              <div>
                <label style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>
                  Detalles de Cobertura
                </label>
                <div className="turno-modal-grid" style={{
                  marginTop: 'var(--space-2)',
                  backgroundColor: 'var(--neutral-50)',
                  padding: 'var(--space-4)',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)'
                }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Obra Social</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>{selectedAppt.patientInfo?.obraSocial || 'Particular'}</span>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>Plan</label>
                    <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>{selectedAppt.patientInfo?.credencial?.plan || '-'}</span>
                  </div>
                  {selectedAppt.metadataAfiliado && (
                    <div style={{ gridColumn: '1 / -1' }}>
                      <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>N° de Afiliado OSDE (copago)</label>
                      <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>{selectedAppt.metadataAfiliado}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Reschedule Section */}
              <div style={{
                padding: 'var(--space-4) 0',
                borderTop: '1px solid var(--color-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-2)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>
                    Reprogramar Consulta
                  </label>
                  <button 
                    onClick={() => {
                      setIsRescheduling(!isRescheduling);
                      setRescheduleDate(selectedAppt.fecha || '');
                      setRescheduleHour(selectedAppt.hour || '09:00');
                    }}
                    style={{
                      border: 'none',
                      background: 'none',
                      color: 'var(--color-primary)',
                      fontSize: '11px',
                      fontWeight: 'bold',
                      cursor: 'pointer',
                      textDecoration: 'underline'
                    }}
                  >
                    {isRescheduling ? 'Cancelar' : 'Modificar fecha/hora'}
                  </button>
                </div>

                {isRescheduling && (
                  <div className="turno-modal-reschedule-grid" style={{
                    backgroundColor: 'var(--neutral-50)',
                    padding: 'var(--space-3)',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--color-border)',
                    marginTop: 'var(--space-2)'
                  }}>
                    <div>
                      <label style={{ fontSize: '9px', color: 'var(--color-text-secondary)', display: 'block', marginBottom: '2px' }}>Nueva Fecha</label>
                      <input 
                        type="date" 
                        value={rescheduleDate}
                        onChange={(e) => setRescheduleDate(e.target.value)}
                        style={{ width: '100%', fontSize: '11px', padding: '4px' }}
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '9px', color: 'var(--color-text-secondary)', display: 'block', marginBottom: '2px' }}>Hora</label>
                      <select
                        value={rescheduleHour}
                        onChange={(e) => setRescheduleHour(e.target.value)}
                        style={{ width: '100%', fontSize: '11px', padding: '4px' }}
                      >
                        {baseSlots.map(slot => (
                          <option key={slot} value={slot}>{slot} hs</option>
                        ))}
                      </select>
                    </div>
                    <button
                      onClick={() => {
                        onRescheduleAppointment(selectedAppt.id, rescheduleDate, rescheduleHour);
                        setSelectedAppt(null);
                        setIsRescheduling(false);
                      }}
                      className="btn btn--primary btn--sm"
                      style={{ height: '26px', fontSize: '10px', padding: '0 var(--space-2)' }}
                    >
                      Guardar
                    </button>
                  </div>
                )}
              </div>

            </div>

            {/* Footer */}
            <div style={{
              padding: 'var(--space-5)',
              borderTop: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 'var(--space-3)',
              backgroundColor: 'var(--neutral-50)'
            }}>
              <button className="btn btn--secondary" onClick={() => setSelectedAppt(null)}>
                Cerrar
              </button>
              {selectedAppt.meetLink && selectedAppt.status === 'confirmed' && (
                <a 
                  href={selectedAppt.meetLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn--primary"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-2)' }}
                >
                  <Icon.Video /> Unirse al Meet
                </a>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

// ── Root App ───────────────────────────────────────────────────
export default function App() {
  const { showAlert } = useAlert()
  const navigate = useNavigate()
  const location = useLocation()
  // Derived from the URL instead of local state, so every "page" the SPA
  // shows has its own real route (/, /login, /reserva/:proId, /panel/...).
  const view: 'landing' | 'checkout' | 'dashboard' | 'login' =
    location.pathname.startsWith('/panel') ? 'dashboard'
    : location.pathname.startsWith('/reserva/') ? 'checkout'
    : location.pathname === '/login' ? 'login'
    : 'landing'
  const activeNav = (location.pathname.startsWith('/panel')
    ? (location.pathname.split('/')[2] || 'dashboard')
    : 'dashboard') as NavSection
  // LandingPage/LoginPage/AdminDashboard/CheckoutFlow each set their own title — this covers
  // the remaining case, the professional dashboard shell rendered directly here in App().
  useDocumentTitle(view === 'dashboard' ? 'Panel Profesional — Tranqui App' : 'Tranqui App')
  const [mpConnected, setMpConnected] = useState(false)
  const [googleConnected, setGoogleConnected] = useState(false)
  const [mpEnabled, setMpEnabled] = useState(false)

  // API states
  const [currentUser, setCurrentUser] = useState<any>(null)
  const [medicoInfo, setMedicoInfo] = useState<any>(null)
  const [todayAppointments, setTodayAppointments] = useState<any[]>([])
  const [allAppointments, setAllAppointments] = useState<any[]>([])
  const [externalEvents, setExternalEvents] = useState<ExternalEvent[]>([])
  const [notifications, setNotifications] = useState<any[]>([])
  const [showNotifications, setShowNotifications] = useState(false)
  const [newPatientAlert, setNewPatientAlert] = useState<{ nombre: string; fecha: string; hora: string } | null>(null)
  const [availability, setAvailability] = useState<any[]>([])
  const [stats, setStats] = useState<any>(null)
  const [loadingDashboard, setLoadingDashboard] = useState(false)
  const [loadingSession, setLoadingSession] = useState(true)
  const [showUnverifiedAlert, setShowUnverifiedAlert] = useState(true)
  const [showDashboardAlertList, setShowDashboardAlertList] = useState(false)
  const [hasUnreadChats, setHasUnreadChats] = useState(false)
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)

  const refreshUnreadChatsStatus = () => {
    if (currentUser && (currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO')) {
      api.getTieneNoLeidos()
        .then((status) => setHasUnreadChats(!!status))
        .catch((err) => console.error("Error refreshing unread chats status:", err))
    }
  }

  const handleCloseUnverifiedAlert = () => {
    setShowUnverifiedAlert(false)
    setTimeout(() => {
      setShowUnverifiedAlert(true)
    }, 120000) // 2 minutes
  }

  // Check user session on app load (Recovery from localStorage)
  useEffect(() => {
    const cachedUser = localStorage.getItem('tranqui_user')
    if (cachedUser) {
      try {
        setCurrentUser(JSON.parse(cachedUser))
      } catch (e) {
        localStorage.removeItem('tranqui_user')
      }
    }

    api.getMe()
      .then((user) => {
        if (user) {
          setCurrentUser(user)
          localStorage.setItem('tranqui_user', JSON.stringify(user))
        } else {
          setCurrentUser(null)
          localStorage.removeItem('tranqui_user')
        }
      })
      .catch(() => {
        setCurrentUser(null)
        localStorage.removeItem('tranqui_user')
      })
      .finally(() => {
        setLoadingSession(false)
      })
  }, [])

  // Route protection guard
  useEffect(() => {
    if (loadingSession) return

    if (currentUser) {
      const isPro = currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO' || currentUser.rol === 'ADMIN'
      if (isPro && (view === 'landing' || view === 'login')) {
        navigate('/panel', { replace: true })
      } else if (!isPro && view === 'dashboard') {
        navigate('/', { replace: true })
      }
    }
  }, [currentUser, view, loadingSession])

  // Fetch Dashboard details
  useEffect(() => {
    if (loadingSession) return

    if (view === 'dashboard') {
      if (currentUser?.rol === 'ADMIN') {
        setLoadingDashboard(false)
        return
      }
      const isPro = currentUser && (currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO')
      if (!isPro) {
        navigate('/', { replace: true })
        return
      }
      setLoadingDashboard(true)
      Promise.all([
        api.getPerfil(),
        api.getTurnosHoy(),
        api.getDisponibilidad(),
        api.getStats(),
        api.getTurnos(),
        api.getNotificaciones(),
        api.getMercadoPagoStatus().catch(() => ({ connected: false })),
        api.getGoogleCalendarStatus().catch(() => ({ connected: false })),
        api.getTieneNoLeidos().catch(() => false),
        api.getEventosExternosGoogleCalendar().catch(() => [])
      ])
        .then(([perfil, turnos, disp, statsData, allTurnos, notifData, mpStatus, googleStatus, unreadStatus, eventosExternos]) => {
          setMedicoInfo(perfil)
          setTodayAppointments(turnos || [])
          setAvailability(disp || [])
          setStats(statsData)
          setAllAppointments(allTurnos || [])
          setNotifications(notifData || [])
          setMpConnected(!!mpStatus?.connected)
          setMpEnabled(!!(mpStatus as any)?.mercadopagoEnabled)
          setGoogleConnected(!!googleStatus?.connected)
          setExternalEvents((eventosExternos as ExternalEvent[]) || [])
        })
        .catch((err) => {
          console.error("Error al inicializar dashboard:", err)
          navigate('/login', { replace: true })
        })
        .finally(() => {
          setLoadingDashboard(false)
        })
    }
  }, [view, currentUser, loadingSession])

  // WebSocket Live Notifications Handler
  useEffect(() => {
    if (loadingSession) return

    if (currentUser && currentUser.rol === 'PSIQUIATRA' && view === 'dashboard') {
      let client: Client | null = null;
      api.getPerfil().then((perfil) => {
        if (perfil && perfil.id) {
          const socketUrl = window.location.protocol === 'https:' ? `https://${window.location.host}/ws-tranqui` : `http://${window.location.hostname}:8081/ws-tranqui`;
          const socket = new SockJS(socketUrl, null, { withCredentials: true } as any)
          client = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 5000,
            onConnect: () => {
              console.log("WebSocket de Notificaciones conectado para ID:", perfil.id)
              client?.subscribe(`/topic/notificaciones/${perfil.id}`, (stompMsg) => {
                try {
                  const data = JSON.parse(stompMsg.body)
                  console.log("WebSocket notification received:", data)
                  
                  // Refresh notification list from API to get full history
                  api.getNotificaciones().then((res) => {
                    setNotifications(res || [])
                  })

                  if (data.tipo === 'NUEVO_MENSAJE') {
                    setHasUnreadChats(true)
                  }

                  if (data.tipo === 'TURNO_RESERVADO' || data.tipo === 'TURNO_CANCELADO') {
                    // Refresh appointments list too so calendar updates immediately
                    api.getTurnos().then((turnos) => {
                      setAllAppointments(turnos || [])
                    })
                    api.getTurnosHoy().then((turnosHoy) => {
                      setTodayAppointments(turnosHoy || [])
                    })

                    // Parse patient name from message
                    let name = "Paciente"
                    let msg = data.mensaje || ""
                    if (msg.includes("El paciente ")) {
                      const nameMatch = msg.match(/El paciente (.*?) (?:ha|reservó)/)
                      if (nameMatch) {
                        name = nameMatch[1]
                      }
                    }
                    setNewPatientAlert({
                      nombre: name,
                      fecha: new Date().toLocaleDateString('es-AR'),
                      hora: "10:00"
                    })
                  }
                } catch (e) {
                  console.error("Error parsing WebSocket notification:", e)
                }
              })
            }
          })
          client.activate()
        }
      }).catch(err => console.error("Error fetching profile for WebSocket initialization:", err))

      return () => {
        if (client) {
          client.deactivate()
        }
      }
    }
  }, [currentUser, view, loadingSession])

  const handleSaveAvailability = async (data: any[]) => {
    const updated = await api.actualizarDisponibilidad(data)
    setAvailability(updated || data)
  }

  const handleSaveSettings = async (data: any) => {
    const updated = await api.actualizarPerfil(data)
    setMedicoInfo(updated)
  }

  const handleSendPrescription = async (data: any) => {
    await api.enviarReceta(data)
  }

  const handleConnect = async () => {
    // In local/dev environments (no real Mercado Pago credentials configured on the
    // backend) we simulate the link instead of redirecting to the real OAuth flow,
    // which would otherwise always fail with an empty client_id.
    if (!mpEnabled) {
      try {
        await api.simularConexionMercadoPago()
        setMpConnected(true)
        showAlert('Conexión simulada (modo desarrollo): tu cuenta de Mercado Pago quedó vinculada para pruebas locales.', 'success')
      } catch (err) {
        console.error("Error al simular la conexión de Mercado Pago:", err)
        showAlert('No se pudo simular la conexión con Mercado Pago.', 'error')
      }
      return
    }

    try {
      const { url } = await api.getMercadoPagoConnectUrl()
      window.location.href = url
    } catch (err) {
      console.error("Error al obtener la URL de conexión de Mercado Pago:", err)
      showAlert('No se pudo iniciar la conexión con Mercado Pago. Intentá de nuevo.', 'error')
    }
  }

  const handleDisconnectMercadoPago = async () => {
    try {
      await api.desconectarMercadoPago()
      setMpConnected(false)
      showAlert('Desvinculaste tu cuenta de Mercado Pago.', 'success')
    } catch (err) {
      console.error("Error al desvincular Mercado Pago:", err)
      showAlert('No se pudo desvincular la cuenta. Intentá de nuevo.', 'error')
    }
  }

  // Handle the ?mp=success / ?mp=error redirect coming back from the Mercado Pago OAuth callback
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const mpResult = params.get('mp')
    if (!mpResult) return

    if (mpResult === 'success') {
      showAlert('¡Tu cuenta de Mercado Pago quedó vinculada! Ya podés recibir pagos.', 'success')
      setMpConnected(true)
    } else if (mpResult === 'error') {
      showAlert('No se pudo vincular tu cuenta de Mercado Pago. Intentá de nuevo.', 'error')
    }

    params.delete('mp')
    params.delete('reason')
    const newSearch = params.toString()
    window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''))
  }, [])

  const handleConnectGoogle = async () => {
    try {
      const { url } = await api.getGoogleCalendarConnectUrl()
      window.location.href = url
    } catch (err) {
      console.error("Error al obtener la URL de conexión de Google Calendar:", err)
      showAlert('No se pudo iniciar la conexión con Google Calendar. Intentá de nuevo.', 'error')
    }
  }

  const handleDisconnectGoogle = async () => {
    try {
      await api.desconectarGoogleCalendar()
      setGoogleConnected(false)
      showAlert('Desvinculaste tu Google Calendar.', 'success')
    } catch (err) {
      console.error("Error al desvincular Google Calendar:", err)
      showAlert('No se pudo desvincular la cuenta. Intentá de nuevo.', 'error')
    }
  }

  // Handle the ?googleCalendar=success / ?googleCalendar=error redirect coming back from the Google OAuth callback
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const googleResult = params.get('googleCalendar')
    if (!googleResult) return

    if (googleResult === 'success') {
      showAlert('¡Tu Google Calendar quedó vinculado!', 'success')
      setGoogleConnected(true)
    } else if (googleResult === 'error') {
      showAlert('No se pudo vincular tu Google Calendar. Intentá de nuevo.', 'error')
    }

    params.delete('googleCalendar')
    params.delete('reason')
    const newSearch = params.toString()
    window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''))
  }, [])

  const handleBook = (pro: CheckoutTarget) => {
    if (!currentUser) {
      showAlert("Para reservar un turno, debes iniciar sesión primero.", "warning");
      navigate('/login');
      return;
    }
    navigate(`/reserva/${pro.id}`, { state: pro });
  }

  const handleLogout = async () => {
    try {
      await api.logout()
    } catch (err) {
      console.error("Error al cerrar sesión:", err)
    }
    setCurrentUser(null)
    localStorage.removeItem('tranqui_user')
    navigate('/')
  }

  const handleMarkNotificationsRead = () => {
    api.marcarNotificacionesLeidas()
      .then(() => {
        setNotifications(notifications.map(n => ({ ...n, leido: true })))
      })
      .catch((err) => console.error("Error al marcar notificaciones como leídas:", err))
  }

  const refreshDashboardAppointments = () => {
    Promise.all([
      api.getTurnosHoy(),
      api.getTurnos(),
      api.getStats()
    ]).then(([turnos, allTurnos, statsData]) => {
      setTodayAppointments(turnos || [])
      setAllAppointments(allTurnos || [])
      setStats(statsData)
    }).catch(err => console.error("Error refreshing appointments:", err));
  }

  const handleCancelAppointment = (turnoId: number) => {
    if (window.confirm("¿Estás seguro de que deseas cancelar este turno?")) {
      api.cancelarTurno(turnoId)
        .then(() => {
          showAlert("Turno cancelado con éxito.", "success");
          refreshDashboardAppointments();
        })
        .catch(err => {
          console.error(err);
          showAlert("Error al cancelar el turno.", "error");
        });
    }
  }

  const handleUpdateAttendance = (turnoId: number, asistencia: string) => {
    api.actualizarAsistencia(turnoId, asistencia)
      .then(() => {
        refreshDashboardAppointments();
      })
      .catch(err => {
        console.error(err);
        showAlert("Error al actualizar la asistencia.", "error");
      });
  }

  const handleRescheduleAppointment = (turnoId: number, fecha: string, hora: string) => {
    api.reprogramarTurno(turnoId, fecha, hora)
      .then(() => {
        showAlert("Turno reprogramado con éxito. Se ha enviado una notificación por WhatsApp al paciente.", "success");
        refreshDashboardAppointments();
      })
      .catch(err => {
        console.error(err);
        showAlert("Error al reprogramar el turno.", "error");
      });
  }

  const pageTitle: Record<NavSection, string> = {
    dashboard: 'Inicio',
    agenda: 'Mi agenda',
    patients: 'Pacientes',
    'clinical-history': 'Historia Clínica',
    prescriptions: 'Recetas',
    visitors: 'Visitadores médicos',
    payments: 'Cobros y liquidaciones',
    settings: 'Configuración',
  }

  const renderContent = () => {
    if (loadingDashboard) {
      return (
        <div style={{ textAlign: 'center', padding: 'var(--space-20)' }}>
          <div className="checkout-spinner" style={{ margin: '0 auto var(--space-4)' }} />
          <p style={{ color: 'var(--color-text-secondary)' }}>Cargando panel de control...</p>
        </div>
      )
    }

    switch (activeNav) {
      case 'dashboard': 
        return (
          <DashboardHome
            mpConnected={mpConnected}
            onConnect={handleConnect}
            onDisconnect={handleDisconnectMercadoPago}
            googleConnected={googleConnected}
            onConnectGoogle={handleConnectGoogle}
            onDisconnectGoogle={handleDisconnectGoogle}
            appointments={todayAppointments}
            allAppointments={allAppointments}
            externalEvents={externalEvents}
            availability={availability}
            stats={stats} 
            onCancelAppointment={handleCancelAppointment}
            onUpdateAttendance={handleUpdateAttendance}
            onRescheduleAppointment={handleRescheduleAppointment}
            medicoInfo={medicoInfo}
            onNavigate={(section) => navigate('/panel/' + section)}
          />
        )
      case 'agenda':
        return <AgendaView initialAvailability={availability} onSave={handleSaveAvailability} />
      case 'patients':
        return <PatientsView onUnreadChatsChange={refreshUnreadChatsStatus} />
      case 'clinical-history':
        return <ClinicalHistoryView />
      case 'prescriptions':
        return <PrescriptionView onSend={handleSendPrescription} />
      case 'visitors': 
        return <VisitorsView />
      case 'payments': return (
        <div className="card">
          <div className="card__header"><h2 className="card__title">Cobros y liquidaciones</h2></div>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>Próximamente — historial de pagos recibidos (Tranqui es 100% libre de comisiones).</p>
        </div>
      )
      case 'settings': 
        return (
          <SettingsView
            medicoInfo={medicoInfo}
            onSave={handleSaveSettings}
            mpConnected={mpConnected}
            onConnect={handleConnect}
            onDisconnect={handleDisconnectMercadoPago}
            googleConnected={googleConnected}
            onConnectGoogle={handleConnectGoogle}
            onDisconnectGoogle={handleDisconnectGoogle}
          />
        )
    }
  }

  const isPro = currentUser && (currentUser.rol === 'PSIQUIATRA' || currentUser.rol === 'MEDICO')
  const unreadCount = notifications.filter(n => !n.leido).length
  const showBanner = view === 'dashboard' && medicoInfo && !medicoInfo.verificado && showUnverifiedAlert;

  const landingElement = (
    <LandingPage
      currentUser={currentUser}
      onNavigateToDashboard={() => navigate('/login')}
      onBook={handleBook}
      onLogout={handleLogout}
      onGoToDashboard={() => navigate('/panel')}
    />
  )

  const proDashboardElement = (
    <div className="dashboard-layout">
      {showBanner && (
        <>
          <style>{`
            @keyframes slideDownAlert {
              from {
                transform: translate(-50%, -100%);
                opacity: 0;
              }
              to {
                transform: translate(-50%, 0);
                opacity: 1;
              }
            }
          `}</style>
          <div style={{
            position: 'fixed',
            top: '20px',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 10000,
            backgroundColor: '#fafaf9',
            border: '1px solid var(--color-border)',
            borderRadius: '12px',
            padding: 'var(--space-4)',
            color: 'var(--color-text-primary)',
            fontSize: 'var(--text-sm)',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
            maxWidth: '520px',
            width: '92%',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-3)',
            animation: 'slideDownAlert 0.35s cubic-bezier(0.16, 1, 0.3, 1) both'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" style={{ width: '16px', height: '16px', color: 'var(--color-warning)', flexShrink: 0 }}>
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
                <strong style={{ fontSize: '13px', color: 'var(--color-text-primary)' }}>Verificación pendiente</strong>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <button
                  onClick={() => setShowDashboardAlertList(!showDashboardAlertList)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--color-primary)',
                    fontSize: '11px',
                    fontWeight: '600',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '2px'
                  }}
                >
                  {showDashboardAlertList ? 'Ocultar' : 'Ver qué falta'}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: '10px', height: '10px', transform: showDashboardAlertList ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>
                    <polyline points="6 9 12 15 18 9" />
                  </svg>
                </button>
                <button 
                  onClick={handleCloseUnverifiedAlert}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--color-text-secondary)',
                    cursor: 'pointer',
                    padding: '2px',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    transition: 'background-color 0.2s',
                    flexShrink: 0
                  }}
                  title="Cerrar aviso"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: '14px', height: '14px' }}>
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </div>
            </div>
            
            <div style={{ fontSize: '12.5px', color: 'var(--color-text-secondary)', lineHeight: '1.4' }}>
              Completá tu perfil para aparecer en el buscador de pacientes y recibir reservas de turnos.
            </div>

            {showDashboardAlertList && (
              <div style={{ 
                display: 'flex', 
                flexDirection: 'column', 
                gap: '5px', 
                backgroundColor: 'var(--color-surface)', 
                border: '1px solid var(--color-border)',
                borderRadius: '8px',
                padding: '10px 12px',
                animation: 'fadeIn 0.2s ease-out'
              }}>
                {getMissingRequirements(medicoInfo).map((req, idx) => (
                  <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: 'var(--color-text-primary)' }}>
                    <span style={{ width: '4px', height: '4px', borderRadius: '50%', backgroundColor: 'var(--color-warning)', flexShrink: 0 }} />
                    <span>{req}</span>
                  </div>
                ))}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '2px' }}>
              <button
                onClick={() => {
                  navigate('/panel/settings')
                  window.scrollTo({ top: 0, behavior: 'smooth' })
                }}
                className="btn btn--primary btn--sm"
                style={{ 
                  padding: '4px 10px',
                  fontSize: '11px',
                  fontWeight: '600'
                }}
              >
                Configurar Perfil
              </button>
            </div>
          </div>
        </>
      )}
      <Sidebar
        activeNav={activeNav}
        onNavChange={(section) => navigate('/panel/' + section)}
        medicoInfo={medicoInfo}
        hasUnreadChats={hasUnreadChats}
        mobileOpen={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
      />

      <header className="dashboard-header" role="banner">
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          <button
            className="btn btn--icon btn--ghost dashboard-mobile-nav-toggle"
            onClick={() => setMobileSidebarOpen(!mobileSidebarOpen)}
            aria-label="Abrir menú de navegación"
          >
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <h1 className="dashboard-header__title">{pageTitle[activeNav]}</h1>
        </div>
        <div className="dashboard-header__actions" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          
          {/* Interactive Notifications Bell */}
          <div style={{ position: 'relative' }}>
            <button 
              className="btn btn--icon btn--ghost" 
              onClick={() => setShowNotifications(!showNotifications)}
              aria-label="Notificaciones"
              style={{ position: 'relative' }}
            >
              <Icon.Bell hasUnread={unreadCount > 0} />
              {unreadCount > 0 && (
                <span style={{
                  position: 'absolute',
                  top: -2,
                  right: -2,
                  backgroundColor: 'var(--color-error)',
                  color: 'white',
                  borderRadius: '50%',
                  width: '16px',
                  height: '16px',
                  fontSize: '9px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 'bold'
                }}>
                  {unreadCount}
                </span>
              )}
            </button>

            {showNotifications && (
              <div className="card" style={{
                position: 'absolute',
                top: '48px',
                right: '0',
                width: '320px',
                maxHeight: '400px',
                overflowY: 'auto',
                zIndex: 1000,
                boxShadow: 'var(--shadow-lg)',
                border: '1px solid var(--color-border)',
                padding: 'var(--space-3)',
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--space-2)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-2)' }}>
                  <strong style={{ fontSize: 'var(--text-sm)' }}>Historial de Notificaciones</strong>
                  {unreadCount > 0 && (
                    <button 
                      onClick={handleMarkNotificationsRead}
                      className="btn btn--ghost" 
                      style={{ fontSize: '10px', padding: '2px 6px', height: 'auto' }}
                    >
                      Marcar leídas
                    </button>
                  )}
                </div>

                {notifications.length === 0 ? (
                  <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', textAlign: 'center', padding: 'var(--space-4)' }}>
                    No tenés notificaciones.
                  </p>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                    {notifications.map((n) => {
                      const { Icon: NotifIcon, color: notifColor } = getNotificationVisual(n.tipo)
                      return (
                        <div key={n.id} style={{
                          padding: 'var(--space-3)',
                          borderRadius: 'var(--radius-md)',
                          backgroundColor: n.leido ? '#ffffff' : '#f0fdf4',
                          borderTop: '1px solid var(--color-border)',
                          borderRight: '1px solid var(--color-border)',
                          borderBottom: '1px solid var(--color-border)',
                          borderLeft: `4px solid ${n.leido ? 'var(--color-border)' : notifColor}`,
                          fontSize: '11px',
                          transition: 'all 0.2s',
                          color: 'var(--color-text-primary)',
                          boxShadow: 'var(--shadow-xs)',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '2px'
                        }}>
                          <div style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '6px', color: n.leido ? 'var(--color-text-secondary)' : 'var(--color-primary)' }}>
                            <span style={{ color: notifColor, display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                              <NotifIcon size={14} />
                            </span>
                            {n.titulo}
                          </div>
                          <div style={{ color: 'var(--color-text-secondary)', fontSize: '10px' }}>
                            {n.mensaje}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            {medicoInfo?.fotoUrl ? (
              <img 
                src={medicoInfo.fotoUrl} 
                alt={medicoInfo.name} 
                className="sidebar__avatar" 
                style={{ cursor: 'pointer', objectFit: 'cover', border: '1.5px solid var(--color-border)', width: '36px', height: '36px', borderRadius: '50%' }} 
                aria-label="Ir a Configuración" 
                role="button" 
                tabIndex={0}
                onClick={() => navigate('/panel/settings')}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); navigate('/panel/settings'); } }}
              />
            ) : (
              <div 
                className="sidebar__avatar" 
                aria-label="Ir a Configuración" 
                role="button" 
                tabIndex={0} 
                style={{ cursor: 'pointer' }}
                onClick={() => navigate('/panel/settings')}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); navigate('/panel/settings'); } }}
              >
                {medicoInfo?.initials || 'LP'}
              </div>
            )}
            <button 
              onClick={handleLogout}
              className="btn btn--ghost btn--sm"
              style={{ fontSize: '11px', padding: 'var(--space-1) var(--space-3)' }}
            >
              Cerrar sesión
            </button>
          </div>
        </div>
      </header>

      <main 
        className="dashboard-main" 
        role="main" 
        id="main-content"
        style={activeNav === 'patients' ? { 
          overflow: 'hidden', 
          height: 'calc(100vh - var(--header-height))', 
          maxHeight: 'calc(100vh - var(--header-height))', 
          display: 'flex', 
          flexDirection: 'column', 
          padding: 'var(--space-6)' 
        } : {}}
      >
        {renderContent()}
      </main>

      {newPatientAlert && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          animation: 'fadeIn 0.2s ease-out'
        }}>
          <div className="card" style={{
            maxWidth: '400px',
            width: '90%',
            padding: 'var(--space-6)',
            textAlign: 'center',
            boxShadow: 'var(--shadow-lg)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            backgroundColor: 'var(--color-surface)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 'var(--space-4)'
          }}>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              backgroundColor: '#d1fae5',
              color: 'var(--color-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 24, height: 24 }}>
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <h3 style={{
              fontFamily: 'var(--font-heading)',
              fontSize: 'var(--text-lg)',
              fontWeight: 'var(--font-weight-bold)',
              color: 'var(--color-text-primary)',
              margin: 0
            }}>
              ¡Nuevo Paciente Registrado!
            </h3>
            <p style={{
              fontSize: 'var(--text-sm)',
              color: 'var(--color-text-secondary)',
              lineHeight: 'var(--line-height-relaxed)',
              margin: 0
            }}>
              El paciente <strong>{newPatientAlert.nombre}</strong> ha reservado un nuevo turno y el pago ha sido aprobado correctamente.
            </p>
            <button
              className="btn btn--primary"
              onClick={() => setNewPatientAlert(null)}
              style={{ width: '100%', marginTop: 'var(--space-2)' }}
            >
              Entendido
            </button>
          </div>
        </div>
      )}
    </div>
  )

  return (
    <Suspense fallback={
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
        <div className="app-suspense-spinner" />
      </div>
    }>
      <Routes>
        <Route path="/" element={landingElement} />
        <Route
          path="/login"
          element={
            <LoginPage
              onLoginSuccess={(user) => {
                setCurrentUser(user)
                if (user.rol === 'PSIQUIATRA' || user.rol === 'MEDICO' || user.rol === 'ADMIN') {
                  navigate('/panel')
                } else {
                  navigate('/')
                }
              }}
              onBack={() => navigate('/')}
            />
          }
        />
        <Route path="/reserva/:proId" element={<CheckoutRoute currentUser={currentUser} loadingSession={loadingSession} />} />
        <Route
          path="/panel/*"
          element={
            currentUser?.rol === 'ADMIN' ? (
              <AdminDashboard currentUser={currentUser} onLogout={handleLogout} />
            ) : !isPro ? (
              <Navigate to="/" replace />
            ) : (
              proDashboardElement
            )
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
