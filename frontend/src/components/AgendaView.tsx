import React, { useState, useEffect } from 'react'
import { useAlert } from '../context/AlertContext'
import { Icon } from './Icon'

type AgendaModalidadGridHandle = {
  getDtos: () => any[]
  hasSlots: () => boolean
  applySlots: (slots: { [key: number]: Set<string> }) => void
  getSelectedSlots: () => { [key: number]: Set<string> }
}

// One weekly grid of bookable slots for a single modalidad (presencial o online). Extracted
// out of AgendaView so a médico who offers both modalidades can have two of these mounted at
// once (see AgendaView) — each keeps its own selectedSlots, but duracionTurno/intervaloTurno
// are shared settings passed down as props since they're the same underlying médico setting
// regardless of modalidad. Exposes getDtos/applySlots via ref so AgendaView's single "Guardar
// cambios" button and "copiar de la otra agenda" action can reach into whichever grid(s) are
// mounted without lifting all of selectedSlots up.
const AgendaModalidadGrid = React.forwardRef(function AgendaModalidadGrid(
  { initialAvailability, duracionTurno, intervaloTurno }: {
    initialAvailability: any[]
    duracionTurno: number
    intervaloTurno: number
  },
  ref: React.Ref<AgendaModalidadGridHandle>
) {
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
  // against it.
  const prevGridRef = React.useRef<{ duracion: number; intervalo: number } | null>(null)

  // One-time import: translate the médico's previously saved availability ranges for this
  // modalidad (already fetched by AgendaView before this component ever mounts) into the
  // equivalent set of selected grid slots. Any saved boundary that doesn't line up with the
  // fixed grid is naturally dropped — expected, since availability is now defined purely by
  // toggling grid slots.
  const importedRef = React.useRef(false)
  useEffect(() => {
    if (importedRef.current) return
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
  }, [])

  // Whenever duración/intervalo change AFTER the initial import (i.e. el médico tweaks the
  // shared selects in AgendaView mid-session), the grid's candidate positions shift. Rather
  // than dropping every selected slot that no longer lines up (wiping out the whole schedule),
  // we translate the previously selected slots into { start, end } ranges under the OLD grid,
  // then re-fit those same ranges onto the NEW grid — the médico's franjas horarias survive.
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
  // Distinct from AgendaView's "copiar de la otra agenda" (between modalidades) — this one only
  // ever touches days within THIS grid.
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

  React.useImperativeHandle(ref, () => ({
    getDtos: () => {
      const dtos: any[] = []
      weekdays.forEach((day) => {
        buildRangesForDay(day.num).forEach((range) => {
          dtos.push({ diaSemana: day.num, horaInicio: range.start, horaFin: range.end })
        })
      })
      return dtos
    },
    hasSlots: () => weekdays.some((d) => (selectedSlots[d.num]?.size || 0) > 0),
    applySlots: (slots) => {
      setSelectedSlots({
        1: new Set(slots[1] || []), 2: new Set(slots[2] || []), 3: new Set(slots[3] || []),
        4: new Set(slots[4] || []), 5: new Set(slots[5] || [])
      })
    },
    getSelectedSlots: () => selectedSlots,
  }), [selectedSlots, duracionTurno])

  return (
    <>
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
    </>
  )
})

export default function AgendaView({ medicoInfo, initialAvailabilityPresencial, initialAvailabilityOnline, onSave, onSaveConfig }: {
  medicoInfo: any
  initialAvailabilityPresencial: any[]
  initialAvailabilityOnline: any[]
  onSave: (modalidad: 'PRESENCIAL' | 'ONLINE', data: any[]) => Promise<void>
  onSaveConfig: (config: { duracionTurnoMinutos: number; intervaloEntreTurnosMinutos: number }) => Promise<void>
}) {
  const { showAlert } = useAlert();

  // Same detection the backend uses (Usuario.isOfrecePresencial/isOfreceOnline): ofrecePresencial
  // defaults to false, ofreceOnline defaults to true, when the médico never set them explicitly.
  const ofrecePresencial = !!medicoInfo?.ofrecePresencial
  const ofreceOnline = medicoInfo?.ofreceOnline !== false
  const ofreceAmbasModalidades = ofrecePresencial && ofreceOnline

  // Sin intervalo por defecto: la mayoría de los médicos prefiere agenda corrida, y quien
  // quiera un colchón entre turnos lo agrega explícitamente.
  const [duracionTurno, setDuracionTurno] = useState(medicoInfo?.duracionTurnoMinutos ?? 45)
  const [intervaloTurno, setIntervaloTurno] = useState(medicoInfo?.intervaloEntreTurnosMinutos ?? 0)
  const [activeModalidad, setActiveModalidad] = useState<'PRESENCIAL' | 'ONLINE'>(ofrecePresencial ? 'PRESENCIAL' : 'ONLINE')

  const presencialGridRef = React.useRef<AgendaModalidadGridHandle>(null)
  const onlineGridRef = React.useRef<AgendaModalidadGridHandle>(null)

  // Copies the slots already loaded into the OTHER modalidad's grid onto the currently active
  // one, in memory only — nothing is persisted until "Guardar cambios" is pressed, so it's a
  // reversible starting point rather than a permanent link between the two agendas.
  const handleCopyFromOtherAgenda = () => {
    const sourceRef = activeModalidad === 'PRESENCIAL' ? onlineGridRef : presencialGridRef
    const targetRef = activeModalidad === 'PRESENCIAL' ? presencialGridRef : onlineGridRef
    const source = sourceRef.current
    if (!source || !source.hasSlots()) return
    targetRef.current?.applySlots(source.getSelectedSlots())
  }

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

  const handleSave = async () => {
    setSaving(true)
    try {
      const saves: Promise<any>[] = [
        onSaveConfig({ duracionTurnoMinutos: duracionTurno, intervaloEntreTurnosMinutos: intervaloTurno })
      ]
      if (ofrecePresencial) saves.push(onSave('PRESENCIAL', presencialGridRef.current?.getDtos() || []))
      if (ofreceOnline) saves.push(onSave('ONLINE', onlineGridRef.current?.getDtos() || []))
      await Promise.all(saves)
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
                <option value={15}>15 min</option>
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

        {ofreceAmbasModalidades && (
          <div className="agenda-view-copy-row" style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 'var(--space-3)' }}>
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <button
                type="button"
                className={`btn btn--sm ${activeModalidad === 'PRESENCIAL' ? 'btn--primary' : 'btn--secondary'}`}
                onClick={() => setActiveModalidad('PRESENCIAL')}
              >
                Presencial
              </button>
              <button
                type="button"
                className={`btn btn--sm ${activeModalidad === 'ONLINE' ? 'btn--primary' : 'btn--secondary'}`}
                onClick={() => setActiveModalidad('ONLINE')}
              >
                Online
              </button>
            </div>
            <button type="button" className="agenda-view-copy-btn" onClick={handleCopyFromOtherAgenda}>
              Copiar horarios de la agenda {activeModalidad === 'PRESENCIAL' ? 'online' : 'presencial'}
            </button>
          </div>
        )}

        {ofrecePresencial && (
          <div style={ofreceAmbasModalidades && activeModalidad !== 'PRESENCIAL' ? { display: 'none' } : undefined}>
            <AgendaModalidadGrid
              ref={presencialGridRef}
              initialAvailability={initialAvailabilityPresencial}
              duracionTurno={duracionTurno}
              intervaloTurno={intervaloTurno}
            />
          </div>
        )}
        {ofreceOnline && (
          <div style={ofreceAmbasModalidades && activeModalidad !== 'ONLINE' ? { display: 'none' } : undefined}>
            <AgendaModalidadGrid
              ref={onlineGridRef}
              initialAvailability={initialAvailabilityOnline}
              duracionTurno={duracionTurno}
              intervaloTurno={intervaloTurno}
            />
          </div>
        )}
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
