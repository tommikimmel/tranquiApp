import React, { useState, useEffect, useMemo } from 'react'
import { Icon } from './Icon'
import AppointmentCard from './AppointmentCard'
import DocumentActions from './DocumentActions'
import ExternalEventCard from './ExternalEventCard'
import StatsOverview from './StatsOverview'
import MPConnectBanner from './MPConnectBanner'
import GoogleCalendarConnectBanner from './GoogleCalendarConnectBanner'
import DateInputDDMMYYYY from './DateInputDDMMYYYY'
import type { Appointment, ExternalEvent, NavSection } from '../types/dashboard'

export default function DashboardHome({
  mpConnected,
  mpEnabled,
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
  statsPeriod,
  onStatsPeriodChange,
  onCancelAppointment,
  onUpdateAttendance,
  onRescheduleAppointment,
  onMarcarDocumentoEnviado,
  medicoInfo,
  onNavigate
}: {
  mpConnected: boolean;
  mpEnabled?: boolean;
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
  statsPeriod: 'DIARIO' | 'SEMANAL' | 'MENSUAL';
  onStatsPeriodChange: (periodo: 'DIARIO' | 'SEMANAL' | 'MENSUAL') => void;
  onCancelAppointment: (id: number) => void;
  onUpdateAttendance: (id: number, status: string) => void;
  onRescheduleAppointment: (id: number, date: string, hour: string) => void;
  onMarcarDocumentoEnviado?: (id: string, archivo: { data: string; nombre: string }) => void;
  medicoInfo?: any;
  onNavigate?: (section: NavSection, state?: any) => void;
}) {
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

  // Pure document requests (recetas, certificados, informes — Turno.ocupaAgenda === false) have
  // no real scheduled time, just a booking timestamp, so every hour-based view (next turno, the
  // upcoming list, weekly/monthly grid cells, day min/max bounds) must exclude them — otherwise
  // they'd visually occupy a real calendar slot next to actually-scheduled turnos. They're
  // surfaced separately below via documentAppointments instead.
  const scheduledAppointments = useMemo(
    () => (allAppointments || []).filter(a => a.ocupaAgenda !== false),
    [allAppointments]
  )
  const documentAppointments = useMemo(
    () => (allAppointments || []).filter(a => a.ocupaAgenda === false && a.status !== 'cancelled'),
    [allAppointments]
  )

  // Find the next active/confirmed appointment closest to now
  const nextAppt = useMemo(() => {
    if (!scheduledAppointments || scheduledAppointments.length === 0) return null;
    const now = currentTime;
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const todayStr = `${year}-${month}-${day}`;
    const currentHour = now.getHours();
    const currentMin = now.getMinutes();

    const upcoming = scheduledAppointments
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
  }, [scheduledAppointments, currentTime]);

  const todayDateStr = (() => {
    const now = currentTime;
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  })();

  // Every turno/event from right now onward — not limited to today, so a médico with nothing
  // left today but a turno tomorrow morning still sees it in "Próximos Eventos" instead of an
  // empty "no more sessions today" message.
  const upcomingAppointments = useMemo(() => {
    const now = currentTime;
    const currentHour = now.getHours();
    const currentMin = now.getMinutes();
    if (!scheduledAppointments) return [];
    return scheduledAppointments
      .filter(a => {
        if (a.status === 'cancelled' || a.status === 'completed') return false;
        if (a.attendanceStatus === 'AUSENTE' || a.attendanceStatus === 'COMPLETADA') return false;
        if (a.fecha > todayDateStr) return true;
        if (a.fecha < todayDateStr) return false;
        const parts = a.hour.split(':');
        const apptHour = parseInt(parts[0]);
        const apptMin = parseInt(parts[1] || '0');
        if (apptHour > currentHour) return true;
        if (apptHour === currentHour) return apptMin >= currentMin;
        return false;
      })
      .sort((a, b) => a.fecha === b.fecha ? a.hour.localeCompare(b.hour) : a.fecha.localeCompare(b.fecha));
  }, [scheduledAppointments, currentTime, todayDateStr]);

  const upcomingExternalEvents = useMemo(() => {
    if (!externalEvents) return [];
    return externalEvents
      .filter(e => e.fecha >= todayDateStr)
      .sort((a, b) => a.fecha === b.fecha ? (a.hour || '').localeCompare(b.hour || '') : a.fecha.localeCompare(b.fecha));
  }, [externalEvents, todayDateStr]);

  // "Próximos Eventos": turnos + personal Google Calendar events, merged into one chronological
  // list and capped so it can't grow unbounded once it spans multiple days — paginated below,
  // 5 at a time, instead of just truncating the list with no way to see the rest.
  const upcomingCombinedEvents = useMemo(() => {
    const turnoItems = upcomingAppointments.map(a => ({ kind: 'turno' as const, sortKey: `${a.fecha} ${a.hour}`, data: a }));
    const externalItems = upcomingExternalEvents.map(e => ({ kind: 'external' as const, sortKey: `${e.fecha} ${e.allDay ? '00:00' : e.hour}`, data: e }));
    return [...turnoItems, ...externalItems].sort((a, b) => a.sortKey.localeCompare(b.sortKey)).slice(0, 50);
  }, [upcomingAppointments, upcomingExternalEvents]);

  const eventsPerPage = 5;
  const [eventsPage, setEventsPage] = useState(1);
  const eventsTotalPages = Math.max(1, Math.ceil(upcomingCombinedEvents.length / eventsPerPage));
  const eventsValidPage = Math.min(eventsPage, eventsTotalPages);
  const eventsStartIndex = (eventsValidPage - 1) * eventsPerPage;
  const paginatedUpcomingEvents = upcomingCombinedEvents.slice(eventsStartIndex, eventsStartIndex + eventsPerPage);

  // "Hoy" / "Mañana" / "DD/MM" — lets the compact "Próximos Eventos" rows stay unambiguous now
  // that the list can span more than one day.
  const relativeDayLabel = (fecha: string) => {
    if (fecha === todayDateStr) return 'Hoy';
    const tomorrow = new Date(currentTime);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
    if (fecha === tomorrowStr) return 'Mañana';
    const parts = fecha.split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}` : fecha;
  };

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
        // Pin the day to 1 before shifting months — otherwise JS rolls the date into the
        // following month whenever the target month has fewer days than the current day-of-month
        // (e.g. May 31st minus one month lands on July 1st instead of April), which made "previous
        // month" intermittently skip or get stuck depending on which day of the month it was.
        d.setDate(1);
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
        d.setDate(1);
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

    // A real turno or personal event booked outside the médico's configured availability
    // window (rescheduled by hand, or the availability got narrowed after the turno was booked)
    // still needs a row — otherwise it has nowhere to render and silently disappears from the
    // Semanal grid even though it's a real, active appointment.
    const bookedHours = [
      ...scheduledAppointments.filter(a => a.status !== 'cancelled').map(a => parseInt(a.hour)),
      ...externalEvents.filter(e => !e.allDay).map(e => parseInt(e.hour)),
    ].filter(h => !isNaN(h));

    if (bookedHours.length > 0) {
      minHour = Math.min(minHour, ...bookedHours);
      maxHour = Math.max(maxHour, Math.max(...bookedHours) + 1);
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

  // Label for the day currently navigated to in Diario view — distinct from capitalizedFullDate
  // (always "today"), since the ‹ › controls above let the médico move to other days.
  const selectedDayLabel = (() => {
    const s = currentDate.toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long' });
    return s.charAt(0).toUpperCase() + s.slice(1);
  })();

  // Appointments for the selected day in Diario view — intentionally the full allAppointments
  // (not scheduledAppointments): this is a plain card list, not an hour-bucketed grid, so
  // document-only requests belong here too — AppointmentCard renders them without a fake hour.
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
                  <Icon.CalendarSmall size={14} /> <strong>Fecha:</strong> {formatDate(nextAppt.fecha)}
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Icon.Clock size={14} /> <strong>Horario:</strong> {nextAppt.hour} hs
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Icon.Stethoscope size={14} /> <strong>Modalidad:</strong> {(nextAppt.modalidad ? nextAppt.modalidad === 'ONLINE' : !!nextAppt.meetLink) ? 'Online' : 'Presencial'}
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
              {(nextAppt.modalidad ? nextAppt.modalidad === 'ONLINE' : !!nextAppt.meetLink) && nextAppt.meetLink && nextAppt.status === 'confirmed' && (
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

      <StatsOverview stats={stats} period={statsPeriod} onPeriodChange={onStatsPeriodChange} />

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
                  : `${selectedDayLabel} · ${dayAppointments.length} sesiones programadas${dayExternalEvents.length ? ` · ${dayExternalEvents.length} eventos personales` : ''}`
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
                <AppointmentCard key={appt.id} appt={appt} onCancel={(id) => onCancelAppointment(Number(id))} onNavigate={onNavigate} onMarcarDocumentoEnviado={onMarcarDocumentoEnviado} />
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
                const hasAppointment = scheduledAppointments.some((a) => {
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
                      // Multiple turnos can fall within the same clock hour (the médico's
                      // appointments are booked in 45-minute blocks, so e.g. 09:00 and 09:45
                      // both land in the "09:00" row) — filter (not find) so a second turno in
                      // the same hour isn't silently hidden behind the first one.
                      const apptsInCell = scheduledAppointments.filter(a => {
                        const apptHour = parseInt(a.hour);
                        return a.fecha === day.dateStr && apptHour === slotHour && a.status !== 'cancelled';
                      }).sort((a, b) => (a.horaInicio || '').localeCompare(b.horaInicio || ''));
                      const externalEvent = externalEvents.find(e => {
                        if (e.allDay) return false;
                        const eventHour = parseInt(e.hour);
                        return e.fecha === day.dateStr && eventHour === slotHour;
                      });
                      const isActive = isSlotAvailable(day.num, slot);
                      const isCellVisible = apptsInCell.length > 0 || externalEvent || isActive || showInactiveSlots;

                      return (
                        <div key={day.num} style={{
                          backgroundColor: day.isToday ? '#F0F9F1' : '#ffffff',
                          minHeight: '52px',
                          padding: '4px',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: apptsInCell.length > 1 ? 'flex-start' : 'center',
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
                          {apptsInCell.length > 0 ? apptsInCell.map((appt) => (
                            <div
                              key={appt.id}
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
                                <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                  {appt.horaInicio ? `${appt.horaInicio} · ` : ''}{appt.patientName}
                                </span>
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
                          )) : isActive ? (
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
                const dayAppts = scheduledAppointments.filter(a => a.fecha === dateStr && a.status !== 'cancelled');
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
        {documentAppointments.length > 0 && (
          <div className="card">
            <div className="card__header">
              <div>
                <h2 className="card__title">Documentos solicitados</h2>
                <p className="card__subtitle">Recetas, certificados e informes pendientes de entregar</p>
              </div>
            </div>
            <ul className="appointment-list appointment-list--compact" role="list" aria-label="Documentos solicitados">
              {documentAppointments.map((appt) => (
                <AppointmentCard key={`doc-${appt.id}`} appt={appt} compact onMarcarDocumentoEnviado={onMarcarDocumentoEnviado} onNavigate={onNavigate} />
              ))}
            </ul>
          </div>
        )}

        <div className="card">
          <div className="card__header">
            <div>
              <h2 className="card__title">Próximos Eventos</h2>
              <p className="card__subtitle">Tus próximas sesiones y eventos</p>
            </div>
          </div>
          {upcomingCombinedEvents.length === 0 ? (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', textAlign: 'center', padding: 'var(--space-4) 0' }}>
              No tenés sesiones ni eventos próximos programados.
            </p>
          ) : (
            <>
              <ul className="appointment-list appointment-list--compact" role="list" aria-label="Próximos eventos">
                {paginatedUpcomingEvents.map((item) => (
                  item.kind === 'turno'
                    ? <AppointmentCard key={`turno-${item.data.id}`} appt={item.data} compact dateLabel={relativeDayLabel(item.data.fecha)} />
                    : <ExternalEventCard key={`gcal-${item.data.id}`} event={item.data} compact dateLabel={relativeDayLabel(item.data.fecha)} />
                ))}
              </ul>
              {eventsTotalPages > 1 && (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingTop: 'var(--space-3)', marginTop: 'var(--space-2)', borderTop: '1px solid var(--color-border)' }}>
                  <button
                    type="button"
                    onClick={() => setEventsPage(p => Math.max(1, p - 1))}
                    disabled={eventsValidPage === 1}
                    className="btn btn--ghost btn--sm"
                  >
                    ◀ Anterior
                  </button>
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', fontWeight: 600 }}>
                    Página {eventsValidPage} de {eventsTotalPages}
                  </span>
                  <button
                    type="button"
                    onClick={() => setEventsPage(p => Math.min(eventsTotalPages, p + 1))}
                    disabled={eventsValidPage === eventsTotalPages}
                    className="btn btn--ghost btn--sm"
                  >
                    Siguiente ▶
                  </button>
                </div>
              )}
            </>
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
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginTop: '4px' }}>
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                    Registrado el {formatDate(selectedAppt.fecha)}
                  </span>
                  {selectedAppt.modalidad && (
                    <span style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      fontSize: '11px',
                      fontWeight: 'bold',
                      padding: '2px 8px',
                      borderRadius: 'var(--radius-full, 999px)',
                      backgroundColor: selectedAppt.modalidad === 'ONLINE' ? '#e0f2fe' : '#f3e8ff',
                      color: selectedAppt.modalidad === 'ONLINE' ? '#0369a1' : '#7e22ce',
                    }}>
                      {selectedAppt.modalidad === 'ONLINE' ? <Icon.Video size={11} /> : <Icon.Building size={11} />}
                      {selectedAppt.modalidad === 'ONLINE' ? 'Online' : 'Presencial'}
                    </span>
                  )}
                </div>
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

              {/* Same document actions as the "Documentos solicitados" card (DocumentActions) —
                  the médico needs to be able to act from here too, not just from the list. */}
              {selectedAppt.ocupaAgenda === false && selectedAppt.status === 'confirmed' && (
                <div>
                  <label style={{ fontSize: 'var(--text-xs)', fontWeight: 'bold', color: 'var(--color-text-secondary)', textTransform: 'uppercase' }}>
                    Documento
                  </label>
                  <div style={{
                    marginTop: 'var(--space-2)',
                    backgroundColor: 'var(--neutral-50)',
                    padding: 'var(--space-4)',
                    borderRadius: 'var(--radius-lg)',
                    border: '1px solid var(--color-border)'
                  }}>
                    <DocumentActions appt={selectedAppt} onMarcarDocumentoEnviado={onMarcarDocumentoEnviado} onNavigate={onNavigate} />
                  </div>
                </div>
              )}

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
                      <label style={{ display: 'block', fontSize: '10px', color: 'var(--color-text-secondary)' }}>N° de Afiliado Obra Social</label>
                      <span style={{ fontSize: 'var(--text-sm)', fontWeight: '600' }}>{selectedAppt.metadataAfiliado}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Cartel de Datos Incompletos para el Profesional / Psiquiatra */}
              {(() => {
                const p = selectedAppt.patientInfo;
                const missing: string[] = [];
                if (!p?.dni && !p?.numeroDocumento) missing.push('DNI / Documento');
                if (!p?.fechaNacimiento) missing.push('Fecha de Nacimiento');
                if (!p?.telefono) missing.push('Teléfono');
                if (!p?.direccion) missing.push('Dirección');
                if (p?.obraSocial && p.obraSocial.toLowerCase() !== 'particular' && !selectedAppt.metadataAfiliado && (!p.numAfiliado || p.numAfiliado === 'N/A')) {
                  missing.push('N° de Afiliado');
                }
                if (missing.length === 0) return null;
                return (
                  <div style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px',
                    padding: '12px 16px',
                    backgroundColor: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: 'var(--radius-lg, 10px)',
                    marginTop: 'var(--space-3)',
                    color: '#92400e',
                    fontSize: '13px',
                    lineHeight: '1.5',
                    boxShadow: '0 1px 3px rgba(217, 119, 6, 0.05)'
                  }}>
                    <div style={{
                      width: '28px',
                      height: '28px',
                      borderRadius: '50%',
                      backgroundColor: '#fef3c7',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      marginTop: '1px'
                    }}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="#d97706" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                        <line x1="12" y1="9" x2="12" y2="13" />
                        <line x1="12" y1="17" x2="12.01" y2="17" />
                      </svg>
                    </div>
                    <div>
                      <strong style={{ display: 'block', color: '#78350f', fontWeight: '600', marginBottom: '2px' }}>
                        Datos Incompletos del Paciente
                      </strong>
                      <span>El perfil del paciente requiere completar los siguientes campos: <strong style={{ color: '#b45309' }}>{missing.join(', ')}</strong>.</span>
                    </div>
                  </div>
                );
              })()}

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
                      <label style={{ fontSize: '9px', color: 'var(--color-text-secondary)', display: 'block', marginBottom: '2px' }}>Nueva Fecha (DD/MM/AAAA)</label>
                      <DateInputDDMMYYYY
                        value={rescheduleDate}
                        onChange={setRescheduleDate}
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
              {selectedAppt.status !== 'completed' && (
                <button
                  className="btn btn--ghost"
                  style={{ color: 'var(--color-danger)' }}
                  onClick={() => {
                    onCancelAppointment(Number(selectedAppt.id));
                    setSelectedAppt(null);
                  }}
                >
                  Cancelar turno
                </button>
              )}
              {(selectedAppt.modalidad ? selectedAppt.modalidad === 'ONLINE' : !!selectedAppt.meetLink) && selectedAppt.meetLink && selectedAppt.status === 'confirmed' && (
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
