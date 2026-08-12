import { useState, useEffect, Suspense, lazy } from 'react'
import { Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom'
import './styles/index.css'
import './styles/dashboard.css'
// LandingPage stays a static import — it's what almost every first-time visitor (anonymous
// patients hitting "/") needs immediately, so there's nothing to gain from lazy-loading it.
// Everything below is only needed by a subset of visitors (professionals, admins, someone
// mid-checkout), so splitting them out of the main chunk shrinks what a random patient
// downloads before first paint.
import LandingPage from './components/LandingPage'
import CheckoutRoute from './components/CheckoutRoute'
import Sidebar from './components/Sidebar'
import MPConnectBanner from './components/MPConnectBanner'
import GoogleCalendarConnectBanner from './components/GoogleCalendarConnectBanner'
import StatsOverview from './components/StatsOverview'
import AppointmentCard from './components/AppointmentCard'
import ExternalEventCard from './components/ExternalEventCard'
const AgendaView = lazy(() => import('./components/AgendaView'))
const PrescriptionView = lazy(() => import('./components/PrescriptionView'))
const SettingsView = lazy(() => import('./components/SettingsView'))
const DashboardHome = lazy(() => import('./components/DashboardHome'))
const LoginPage = lazy(() => import('./components/LoginPage'))
const PatientsView = lazy(() => import('./components/PatientsView'))
const ClinicalHistoryView = lazy(() => import('./components/ClinicalHistoryView'))
const VisitorsView = lazy(() => import('./components/VisitorsView'))
const AdminDashboard = lazy(() => import('./components/AdminDashboard'))
const NotFoundView = lazy(() => import('./components/NotFoundView'))
const CompleteProfileModal = lazy(() => import('./components/CompleteProfileModal'))
const TermsAcceptanceModal = lazy(() => import('./components/TermsAcceptanceModal'))
const PrivacyPolicyPage = lazy(() => import('./components/PrivacyPolicyPage'))
const TermsPage = lazy(() => import('./components/TermsPage'))
const MiCuentaView = lazy(() => import('./components/MiCuentaView'))
import ComplaintModal from './components/ComplaintModal'
import { api } from './api/api'
import { useAlert } from './context/AlertContext'
import { useDocumentTitle } from './hooks/useDocumentTitle'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { Icon } from './components/Icon'
import type { Appointment, ExternalEvent, NavSection } from './types/dashboard'
import type { CheckoutTarget } from './types/checkout'
import { getNotificationVisual, getDoctorSlug } from './utils/dashboardHelpers'
import { getMissingRequirements } from './utils/medicoProfile'

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
  const [showComplaintModal, setShowComplaintModal] = useState(false)
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
  const [newPatientAlert, setNewPatientAlert] = useState<{ nombre: string; fecha: string; hora: string; tipo: 'RESERVADO' | 'CANCELADO' } | null>(null)
  const [availabilityPresencial, setAvailabilityPresencial] = useState<any[]>([])
  const [availabilityOnline, setAvailabilityOnline] = useState<any[]>([])
  const [stats, setStats] = useState<any>(null)
  const [statsPeriod, setStatsPeriod] = useState<'DIARIO' | 'SEMANAL' | 'MENSUAL'>('MENSUAL')
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
        api.getDisponibilidad('PRESENCIAL'),
        api.getDisponibilidad('ONLINE'),
        api.getStats(statsPeriod),
        api.getTurnos(),
        api.getNotificaciones(),
        api.getMercadoPagoStatus().catch(() => ({ connected: false })),
        api.getGoogleCalendarStatus().catch(() => ({ connected: false })),
        api.getTieneNoLeidos().catch(() => false),
        api.getEventosExternosGoogleCalendar().catch((err) => {
          console.error("Error al obtener eventos externos de Google Calendar:", err)
          return []
        })
      ])
        .then(([perfil, turnos, dispPresencial, dispOnline, statsData, allTurnos, notifData, mpStatus, googleStatus, unreadStatus, eventosExternos]) => {
          setMedicoInfo(perfil)
          setTodayAppointments(turnos || [])
          setAvailabilityPresencial(dispPresencial || [])
          setAvailabilityOnline(dispOnline || [])
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
                    // A turno reserved/cancelled by the patient (not through the médico's own
                    // cancel/reschedule actions) previously left the stat cards stale until the
                    // next full page load, since only the appointment lists were refreshed here.
                    api.getStats(statsPeriod).then(setStats)

                    // Parse patient name from message
                    let name = "Paciente"
                    let msg = data.mensaje || ""
                    const reservaMatch = msg.match(/El paciente (.*?) (?:ha|reservó)/)
                    const cancelacionMatch = msg.match(/con el paciente (.*?) ha sido cancelado/)
                    if (reservaMatch) {
                      name = reservaMatch[1]
                    } else if (cancelacionMatch) {
                      name = cancelacionMatch[1]
                    }
                    setNewPatientAlert({
                      nombre: name,
                      fecha: new Date().toLocaleDateString('es-AR'),
                      hora: "10:00",
                      tipo: data.tipo === 'TURNO_CANCELADO' ? 'CANCELADO' : 'RESERVADO'
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

  const handleStatsPeriodChange = async (periodo: 'DIARIO' | 'SEMANAL' | 'MENSUAL') => {
    setStatsPeriod(periodo)
    try {
      const updated = await api.getStats(periodo)
      setStats(updated)
    } catch (err) {
      console.error("Error al obtener las métricas del período seleccionado:", err)
    }
  }

  const handleSaveAvailability = async (modalidad: 'PRESENCIAL' | 'ONLINE', data: any[]) => {
    const updated = await api.actualizarDisponibilidad(modalidad, data)
    if (modalidad === 'PRESENCIAL') setAvailabilityPresencial(updated || data)
    else setAvailabilityOnline(updated || data)
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
      // The backend seeds the eventos externos cache during the OAuth callback, so pull it
      // now instead of waiting for the next dashboard mount/reload.
      api.getEventosExternosGoogleCalendar()
        .then((eventos) => setExternalEvents((eventos as ExternalEvent[]) || []))
        .catch((err) => console.error("Error al obtener eventos de Google Calendar tras vincular:", err))
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
    const slug = getDoctorSlug(pro.name) || String(pro.id);
    navigate(`/reserva/${slug}`, { state: pro });
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
      api.getStats(statsPeriod)
    ]).then(([turnos, allTurnos, statsData]) => {
      setTodayAppointments(turnos || [])
      setAllAppointments(allTurnos || [])
      setStats(statsData)
    }).catch(err => console.error("Error refreshing appointments:", err));
  }

  const handleCancelAppointment = (turnoId: number) => {
    // Same 48hs rule enforced server-side in ReembolsoService — surfaced here so the médico
    // isn't surprised that cancelling a paid turno didn't trigger a Mercado Pago refund.
    const appt = allAppointments.find(a => a.id === turnoId)
    let refundNote = ''
    if (appt?.status === 'confirmed' && appt.fecha && appt.hour) {
      const [y, m, d] = appt.fecha.split('-').map(Number)
      const timeStr = appt.horaInicio || `${appt.hour}:00`
      const [hh, mm] = timeStr.split(':').map(Number)
      const apptDate = new Date(y, (m || 1) - 1, d, hh || 0, mm || 0)
      const hoursUntil = (apptDate.getTime() - Date.now()) / (1000 * 60 * 60)
      refundNote = hoursUntil < 48
        ? '\n\nEste turno ya fue pagado y es en menos de 48 horas: el pago NO se reembolsa automáticamente.'
        : '\n\nEste turno ya fue pagado: al cancelarlo se reembolsa automáticamente en Mercado Pago (faltan más de 48hs).'
    }
    if (window.confirm("¿Estás seguro de que deseas cancelar este turno?" + refundNote)) {
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
            mpEnabled={mpEnabled}
            onConnect={handleConnect}
            onDisconnect={handleDisconnectMercadoPago}
            googleConnected={googleConnected}
            onConnectGoogle={handleConnectGoogle}
            onDisconnectGoogle={handleDisconnectGoogle}
            appointments={todayAppointments}
            allAppointments={allAppointments}
            externalEvents={externalEvents}
            availability={[...availabilityPresencial, ...availabilityOnline]}
            stats={stats}
            statsPeriod={statsPeriod}
            onStatsPeriodChange={handleStatsPeriodChange}
            onCancelAppointment={handleCancelAppointment}
            onUpdateAttendance={handleUpdateAttendance}
            onRescheduleAppointment={handleRescheduleAppointment}
            medicoInfo={medicoInfo}
            onNavigate={(section) => navigate('/panel/' + section)}
          />
        )
      case 'agenda':
        return (
          <AgendaView
            medicoInfo={medicoInfo}
            initialAvailabilityPresencial={availabilityPresencial}
            initialAvailabilityOnline={availabilityOnline}
            onSave={handleSaveAvailability}
          />
        )
      case 'patients':
        return <PatientsView onUnreadChatsChange={refreshUnreadChatsStatus} />
      case 'clinical-history':
        return <ClinicalHistoryView />
      case 'prescriptions':
        return <PrescriptionView onSend={handleSendPrescription} medicoInfo={medicoInfo} />
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
            mpEnabled={mpEnabled}
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
            background: 'linear-gradient(135deg, rgba(255, 246, 232, 0.96) 0%, rgba(255, 255, 255, 0.98) 100%)',
            backdropFilter: 'blur(16px) saturate(180%)',
            border: '1px solid rgba(201, 138, 27, 0.3)',
            borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-4) var(--space-5)',
            color: 'var(--color-text-primary)',
            boxShadow: '0 16px 36px -6px rgba(201, 138, 27, 0.15), 0 4px 12px rgba(0, 0, 0, 0.04)',
            maxWidth: '540px',
            width: '92%',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-3)',
            animation: 'slideDownAlert 0.35s cubic-bezier(0.16, 1, 0.3, 1) both'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '10px',
                  backgroundColor: 'var(--color-warning)',
                  color: '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 4px 12px rgba(201, 138, 27, 0.3)',
                  flexShrink: 0
                }}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: '16px', height: '16px' }}>
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                    <line x1="12" y1="9" x2="12" y2="13" />
                    <line x1="12" y1="17" x2="12.01" y2="17" />
                  </svg>
                </div>
                <div>
                  <h4 style={{ fontFamily: 'var(--font-heading)', fontSize: 'var(--text-sm)', fontWeight: 'var(--font-weight-bold)', color: '#92400e', margin: 0 }}>Verificación de perfil pendiente</h4>
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <button
                  onClick={() => setShowDashboardAlertList(!showDashboardAlertList)}
                  style={{
                    background: 'rgba(201, 138, 27, 0.1)',
                    border: '1px solid rgba(201, 138, 27, 0.25)',
                    color: '#92400e',
                    fontSize: '11.5px',
                    fontWeight: '600',
                    padding: '4px 10px',
                    borderRadius: 'var(--radius-full)',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    transition: 'all 0.2s'
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
                    color: 'var(--neutral-500)',
                    cursor: 'pointer',
                    padding: '4px',
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
            
            <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: '1.45', fontFamily: 'var(--font-body)' }}>
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
                {getMissingRequirements(medicoInfo, mpConnected, mpEnabled).map((req, idx) => (
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
              onClick={() => {
                const opening = !showNotifications
                setShowNotifications(opening)
                // Opening the panel is how a médico "reads" their notifications — clear the
                // badge right away instead of requiring a separate "Marcar leídas" click, which
                // otherwise left the bell showing unread indefinitely even after everything had
                // been seen.
                if (opening && unreadCount > 0) {
                  handleMarkNotificationsRead()
                }
              }}
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
              onClick={() => setShowComplaintModal(true)}
              className="btn btn--ghost btn--sm"
              style={{ fontSize: '11px', padding: 'var(--space-1) var(--space-3)' }}
              title="Enviar una queja o consulta a soporte"
            >
              Soporte
            </button>
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
      <ComplaintModal isOpen={showComplaintModal} onClose={() => setShowComplaintModal(false)} />

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
              backgroundColor: newPatientAlert.tipo === 'CANCELADO' ? '#fee2e2' : '#d1fae5',
              color: newPatientAlert.tipo === 'CANCELADO' ? 'var(--color-danger, #dc2626)' : 'var(--color-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              {newPatientAlert.tipo === 'CANCELADO' ? (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 24, height: 24 }}>
                  <circle cx="12" cy="12" r="9" /><path d="M15 9l-6 6M9 9l6 6" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: 24, height: 24 }}>
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                </svg>
              )}
            </div>
            <h3 style={{
              fontFamily: 'var(--font-heading)',
              fontSize: 'var(--text-lg)',
              fontWeight: 'var(--font-weight-bold)',
              color: 'var(--color-text-primary)',
              margin: 0
            }}>
              {newPatientAlert.tipo === 'CANCELADO' ? 'Turno Cancelado' : '¡Nuevo Paciente Registrado!'}
            </h3>
            <p style={{
              fontSize: 'var(--text-sm)',
              color: 'var(--color-text-secondary)',
              lineHeight: 'var(--line-height-relaxed)',
              margin: 0
            }}>
              {newPatientAlert.tipo === 'CANCELADO'
                ? <>El paciente <strong>{newPatientAlert.nombre}</strong> canceló su turno.</>
                : <>El paciente <strong>{newPatientAlert.nombre}</strong> ha reservado un nuevo turno y el pago ha sido aprobado correctamente.</>}
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
        <Route path="/privacidad" element={<PrivacyPolicyPage currentUser={currentUser} />} />
        <Route path="/terminos" element={<TermsPage currentUser={currentUser} />} />
        <Route
          path="/mi-cuenta"
          element={
            loadingSession ? null : currentUser?.rol === 'PACIENTE' ? (
              <MiCuentaView onLogout={handleLogout} onUserUpdated={setCurrentUser} />
            ) : (
              <Navigate to="/" replace />
            )
          }
        />
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
        <Route path="*" element={<NotFoundView currentUser={currentUser} />} />
      </Routes>
      {currentUser && currentUser.requiereAceptarTerminos ? (
        <TermsAcceptanceModal
          onComplete={(updatedUser) => setCurrentUser(updatedUser)}
          onLogout={handleLogout}
        />
      ) : (
        currentUser && currentUser.rol === 'PACIENTE' && currentUser.perfilCompleto === false && (
          <CompleteProfileModal
            user={currentUser}
            onComplete={(updatedUser) => setCurrentUser(updatedUser)}
            onLogout={handleLogout}
          />
        )
      )}
    </Suspense>
  )
}
