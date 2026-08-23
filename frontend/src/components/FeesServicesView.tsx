import { useState, useRef } from 'react'
import { useAlert } from '../context/AlertContext'
import { OBRAS_SOCIALES } from '../constants/obrasSociales'

// ── Honorarios y servicios (sección independiente) ─────────────
// Extraído de SettingsView (antes vivía como un tab más de Configuración). Misma lógica de
// negocio 1:1 — solo se movió el estado/handlers exclusivos de tarifas a este componente propio.
const SaveIcon = ({ size = 14 }: { size?: number }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
    <polyline points="17 21 17 13 7 13 7 21" />
    <polyline points="7 3 7 8 15 8" />
  </svg>
)

export default function FeesServicesView({
  medicoInfo,
  onSave,
  canUseRecetas,
}: {
  medicoInfo: any
  onSave: (updated: any) => Promise<void>
  // false para psicólogos (plan sin recetas_electronicas): no pueden tener servicios tipo
  // "Receta" — el backend además lo fuerza server-side en MedicoService.actualizarPerfil.
  canUseRecetas?: boolean
}) {
  const { showAlert } = useAlert()

  const [tariffs, setTariffs] = useState<any[]>(medicoInfo?.tariffs || [])
  const [saving, setSaving] = useState(false)

  // Solo lectura acá — se editan en Configuración > Contacto/Presencia. Mismos defaults que
  // usaba SettingsView cuando medicoInfo todavía no trae el campo.
  const ofreceOnline = medicoInfo?.ofreceOnline !== undefined ? medicoInfo.ofreceOnline : true
  const ofrecePresencial = medicoInfo?.ofrecePresencial !== undefined ? medicoInfo.ofrecePresencial : false

  const [openTariffId, setOpenTariffId] = useState<string | null>(null)
  const [showBulkAdjust, setShowBulkAdjust] = useState(false)
  const [bulkPct, setBulkPct] = useState<number | null>(null)
  const [customBulkPct, setCustomBulkPct] = useState('')
  const [bulkRounding, setBulkRounding] = useState<number>(1000)
  const [tariffSearch, setTariffSearch] = useState('')
  const [showPatientPreview, setShowPatientPreview] = useState(false)
  const [toastMessage, setToastMessage] = useState<{ text: string; onUndo?: () => void } | null>(null)
  const toastTimerRef = useRef<any>(null)
  const [expandedAllCobs, setExpandedAllCobs] = useState<Set<string>>(new Set())

  const showToast = (text: string, onUndo?: () => void) => {
    setToastMessage({ text, onUndo })
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null)
    }, 8000)
  }

  const leerPrecio = (txt: any): number => {
    const t = String(txt ?? '').toLowerCase().trim()
    if (!t) return 0
    const m = t.match(/^[^\d]*([\d.,\s]+)\s*(k|mil)?/)
    if (!m) return 0
    let n = parseInt(m[1].replace(/[^\d]/g, ''), 10) || 0
    if (m[2]) n *= 1000
    return n
  }

  const miles = (n: number) => String(Math.round(Math.abs(n || 0))).replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const money = (n: number) => '$ ' + miles(n)

  const calculateAdjustedPrice = (val: number | null | undefined): number => {
    if (bulkPct == null || !val) return val || 0
    const paso = bulkRounding || 1
    return Math.max(0, Math.round(val * (1 + bulkPct / 100) / paso) * paso)
  }

  const revisarPrecio = (tariff: any, allTariffs: any[]) => {
    const otros = allTariffs.filter(x => !x.archivado && x.id !== tariff.id && x.price > 0).map(x => x.price)
    if (otros.length < 2 || !tariff.price) return null
    const min = Math.min(...otros)
    const max = Math.max(...otros)
    if (tariff.price * 5 < min) {
      const sug = [10, 100, 1000].map(m => tariff.price * m).find(v => v >= min && v <= max)
      return { tono: 'bajo' as const, min, max, sug }
    }
    if (tariff.price > max * 5) {
      const sug = [10, 100, 1000].map(m => Math.round(tariff.price / m)).find(v => v >= min && v <= max)
      return { tono: 'alto' as const, min, max, sug }
    }
    return null
  }

  const updateTariff = (id: string, field: string, value: any) => {
    setTariffs(tariffs.map(t => t.id === id ? { ...t, [field]: value } : t))
  }

  const getServiceType = (t: any): 'consulta' | 'receta' | 'documento' => {
    if (t.requiereAgenda === false) {
      return t.esReceta ? 'receta' : 'documento'
    }
    return 'consulta'
  }

  const setServiceType = (id: string, type: 'consulta' | 'receta' | 'documento') => {
    if (type === 'consulta') {
      updateTariff(id, 'requiereAgenda', true)
      updateTariff(id, 'esReceta', false)
    } else if (type === 'receta') {
      updateTariff(id, 'requiereAgenda', false)
      updateTariff(id, 'esReceta', true)
    } else {
      updateTariff(id, 'requiereAgenda', false)
      updateTariff(id, 'esReceta', false)
    }
  }

  const moveTariff = (id: string, direction: -1 | 1, list: any[]) => {
    const idx = list.findIndex(t => t.id === id)
    if (idx < 0) return
    const targetIdx = idx + direction
    if (targetIdx < 0 || targetIdx >= list.length) return
    const targetItem = list[targetIdx]
    const fullIdx1 = tariffs.findIndex(t => t.id === id)
    const fullIdx2 = tariffs.findIndex(t => t.id === targetItem.id)
    if (fullIdx1 < 0 || fullIdx2 < 0) return
    const updated = [...tariffs]
    const temp = updated[fullIdx1]
    updated[fullIdx1] = updated[fullIdx2]
    updated[fullIdx2] = temp
    setTariffs(updated)
  }

  const archiveTariff = (id: string) => {
    const target = tariffs.find(t => t.id === id)
    if (!target) return
    const prev = JSON.parse(JSON.stringify(tariffs))
    setTariffs(tariffs.map(t => t.id === id ? { ...t, archivado: true, enabled: false } : t))
    if (openTariffId === id) setOpenTariffId(null)
    showToast(`"${target.label}" archivado`, () => setTariffs(prev))
  }

  const restoreTariff = (id: string) => {
    const prev = JSON.parse(JSON.stringify(tariffs))
    setTariffs(tariffs.map(t => t.id === id ? { ...t, archivado: false, enabled: true } : t))
    showToast(`Servicio restaurado`, () => setTariffs(prev))
  }

  const removeTariff = (id: string) => {
    const target = tariffs.find(t => t.id === id)
    if (!target) return
    const prev = JSON.parse(JSON.stringify(tariffs))
    setTariffs(tariffs.filter(t => t.id !== id))
    if (openTariffId === id) setOpenTariffId(null)
    showToast(`"${target.label}" eliminado`, () => setTariffs(prev))
  }

  const applyBulkAdjust = () => {
    if (bulkPct == null) return
    const prev = JSON.parse(JSON.stringify(tariffs))
    const updated = tariffs.map(t => {
      const newPrice = calculateAdjustedPrice(t.price)
      const newPres = t.precioPresencial != null ? calculateAdjustedPrice(t.precioPresencial) : null
      const newVirt = t.precioOnline != null ? calculateAdjustedPrice(t.precioOnline) : null
      return {
        ...t,
        price: newPrice,
        precioPresencial: newPres,
        precioOnline: newVirt,
      }
    })
    setTariffs(updated)
    setShowBulkAdjust(false)
    const applied = bulkPct
    setBulkPct(null)
    setCustomBulkPct('')
    showToast(`Precios actualizados +${applied}%`, () => setTariffs(prev))
  }

  // Per-modalidad pricing is opt-in per service
  const [expandedPricingIds, setExpandedPricingIds] = useState<Set<string>>(
    () => new Set(tariffs.filter((t: any) => t.precioOnline != null || t.precioPresencial != null).map((t: any) => t.id))
  )
  const togglePricingExpanded = (id: string) => {
    setExpandedPricingIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
        updateTariff(id, 'precioOnline', null)
        updateTariff(id, 'precioPresencial', null)
      } else {
        next.add(id)
      }
      return next
    })
  }

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

  const addTariff = (label: string, price: number, obraSocial: string, tipo: 'consulta' | 'receta' | 'documento' = 'consulta') => {
    const newId = slugifyTariffId(label)
    const newService = {
      id: newId,
      label,
      price,
      enabled: true,
      requiereObraSocial: !!obraSocial,
      obraSocial: obraSocial || undefined,
      requiereAgenda: tipo === 'consulta',
      esReceta: tipo === 'receta',
    }
    setTariffs([...tariffs, newService])
    setOpenTariffId(newId)
  }

  const [showAddTariff, setShowAddTariff] = useState(false)
  const [newTariffName, setNewTariffName] = useState('')
  const [newTariffPrice, setNewTariffPrice] = useState('')
  const [newTariffObraSocial, setNewTariffObraSocial] = useState('')

  const handleAddTariff = () => {
    const name = newTariffName.trim()
    const price = leerPrecio(newTariffPrice)
    if (!name) return
    addTariff(name, price, newTariffObraSocial)
    setNewTariffName('')
    setNewTariffPrice('')
    setNewTariffObraSocial('')
    setShowAddTariff(false)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      await onSave({
        ...medicoInfo,
        // El servicio genérico legado Obra Social/OSDE siempre requiere cobertura + n° de
        // afiliado, y por defecto usa OSDE si el médico nunca lo editó — pero queda totalmente
        // editable, así que un médico que lo redirigió a otra obra social mantiene esa elección
        // en vez de que vuelva a OSDE en cada guardado.
        tariffs: tariffs.map(t => (t.id === 'obra_social' || t.id === 'osde')
          ? { ...t, requiereObraSocial: true, obraSocial: t.obraSocial || 'OSDE' }
          : t),
      })
      showAlert('Honorarios guardados con éxito ✓', 'success')
    } catch (err) {
      console.error(err)
      showAlert('Error al guardar los honorarios', 'error')
    } finally {
      setSaving(false)
    }
  }

  const vivos = tariffs.filter((t: any) => !t.archivado)
  const archivados = tariffs.filter((t: any) => !!t.archivado)
  const visibles = vivos.filter((t: any) => t.enabled).length
  const sinPrecio = vivos.filter((t: any) => !t.price || t.price <= 0)
  const canDifferentiateByModalidad = ofreceOnline && ofrecePresencial

  const matchFiltro = (t: any) => {
    if (!tariffSearch.trim()) return true
    const q = tariffSearch.toLowerCase()
    return (t.label || '').toLowerCase().includes(q) || (t.obraSocial || '').toLowerCase().includes(q)
  }

  const consultas = vivos.filter((t: any) => t.requiereAgenda !== false)
  const docs = vivos.filter((t: any) => t.requiereAgenda === false)
  const filteredConsultas = consultas.filter(matchFiltro)
  const filteredDocs = docs.filter(matchFiltro)

  const firstValidTariff = vivos.find((t: any) => t.price > 0)

  const usadosNombres = vivos.map((t: any) => (t.label || '').toLowerCase())
  const candidatosFrecuentes: Array<{ label: string; price: number; tipo: 'consulta' | 'receta' | 'documento' }> = [
    { label: 'Certificado médico', price: 40000, tipo: 'documento' },
    { label: 'Informe psicodiagnóstico', price: 95000, tipo: 'documento' },
    { label: 'Sesión de pareja', price: 110000, tipo: 'consulta' },
    { label: 'Constancia de asistencia', price: 25000, tipo: 'documento' },
    { label: 'Apto psicofísico', price: 50000, tipo: 'documento' },
  ]
  const frecuentesSugeridos = candidatosFrecuentes.filter(c => !usadosNombres.includes(c.label.toLowerCase())).slice(0, 3)

  const renderTariffRow = (t: any, categoryList: any[]) => {
    const p = revisarPrecio(t, tariffs)
    const isBulkPreview = bulkPct != null
    const isPricingExp = expandedPricingIds.has(t.id)
    const categoryIdx = categoryList.findIndex((item: any) => item.id === t.id)

    return (
      <div
        key={t.id}
        id={`tariff-row-${t.id}`}
        className={`honorarios-fila ${t.enabled ? '' : 'oculta'} ${!t.price ? 'marcada' : ''}`}
      >
        <div
          className="honorarios-resumen"
          onClick={() => setOpenTariffId(openTariffId === t.id ? null : t.id)}
          role="button"
          tabIndex={0}
          aria-expanded={openTariffId === t.id}
        >
          <div
            className="honorarios-sw-tap"
            onClick={(e) => {
              e.stopPropagation()
              updateTariff(t.id, 'enabled', !t.enabled)
            }}
            title={t.enabled ? 'Ocultar a los pacientes' : 'Mostrar a los pacientes'}
          >
            <div className={`honorarios-sw ${t.enabled ? 'checked' : ''}`} role="switch" aria-checked={t.enabled} />
          </div>

          <div className="honorarios-datos">
            <div className="honorarios-nombre">
              {t.label || 'Servicio sin nombre'}
              {!t.enabled && (
                <span className="tenue" style={{ fontSize: '13px', color: '#617268', fontWeight: 'normal' }}>
                  {' · oculto'}
                </span>
              )}
            </div>
            <div className="honorarios-chips">
              <span className="honorarios-chip">{t.obraSocial || 'Particular'}</span>
              {t.requiereAgenda === false && (
                <span className="honorarios-chip" style={{ background: '#ECFDF5', color: '#065F46', border: '1px solid #A7F3D0' }}>
                  {t.esReceta ? 'Receta' : 'Documento'}
                </span>
              )}
            </div>
          </div>

          <div className={`honorarios-precio-celda ${p ? 'mal' : ''} ${isBulkPreview ? 'honorarios-futuro' : ''}`}>
            {isBulkPreview && t.price ? (
              <>
                <div className="honorarios-plata">{money(calculateAdjustedPrice(t.price))}</div>
                <div className="honorarios-antes">{money(t.price)}</div>
              </>
            ) : isPricingExp && getServiceType(t) === 'consulta' && (t.precioPresencial != null || t.precioOnline != null) ? (
              <>
                <div className="honorarios-plata">{t.precioPresencial != null ? money(t.precioPresencial) : money(t.price)}</div>
                <div className="honorarios-precio-mod">{t.precioOnline != null ? `${money(t.precioOnline)} virtual` : `${money(t.price)} virtual`}</div>
              </>
            ) : (
              <div className="honorarios-plata">{t.price ? money(t.price) : '—'}</div>
            )}
          </div>

          <div style={{ color: '#617268', marginLeft: '6px', display: 'flex', alignItems: 'center' }}>
            {openTariffId === t.id ? (
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"><path d="m18 15-6-6-6 6"/></svg>
            ) : (
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2"><path d="m6 9 6 6 6-6"/></svg>
            )}
          </div>
        </div>

        {/* Inline Editor (Divulgación progresiva) */}
        {openTariffId === t.id && (
          <div className="honorarios-editor">
            <div className="honorarios-campos">
              <div className="honorarios-campo-ancho">
                <label htmlFor={`tariff-name-${t.id}`}>Nombre del servicio</label>
                <input
                  id={`tariff-name-${t.id}`}
                  className="form-input"
                  value={t.label}
                  onChange={(e) => updateTariff(t.id, 'label', e.target.value)}
                  placeholder="Ej. Primera consulta"
                />
              </div>
              <div className="honorarios-campo-angosto">
                <label htmlFor={`tariff-price-${t.id}`}>Precio base (ARS)</label>
                <input
                  id={`tariff-price-${t.id}`}
                  className={`form-input ${p ? 'mal' : ''}`}
                  type="text"
                  value={t.price ? `$ ${miles(t.price)}` : ''}
                  onChange={(e) => updateTariff(t.id, 'price', leerPrecio(e.target.value))}
                  placeholder="$ 90.000"
                  inputMode="numeric"
                />
                <p className="honorarios-pista">Podés escribir 90k o 90 mil</p>
              </div>
            </div>

            {/* Outlier Alert Banner */}
            {p && (
              <div className="honorarios-aviso">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 9v4M12 17h.01M10.3 3.9 2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/></svg>
                <div>
                  Tus otros servicios van de {money(p.min)} a {money(p.max)}. Este quedó muy por {p.tono === 'bajo' ? 'debajo' : 'encima'}.
                  {p.sug && (
                    <div>
                      <button type="button" className="arreglo" onClick={() => updateTariff(t.id, 'price', p.sug)}>
                        Cambiar a {money(p.sug)}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Service Type Selector (Ley de Tesler) */}
            <div className="honorarios-bloque">
              <label>Tipo de servicio</label>
              <div className="honorarios-tipos">
                <button
                  type="button"
                  className={`honorarios-tipo ${getServiceType(t) === 'consulta' ? 'active' : ''}`}
                  onClick={() => setServiceType(t.id, 'consulta')}
                >
                  <b>Consulta</b>
                  <span>Ocupa un turno en tu agenda</span>
                </button>
                <button
                  type="button"
                  className={`honorarios-tipo ${getServiceType(t) === 'receta' ? 'active' : ''}`}
                  onClick={() => setServiceType(t.id, 'receta')}
                >
                  <b>Receta</b>
                  <span>La emitís y le llega firmada</span>
                </button>
                <button
                  type="button"
                  className={`honorarios-tipo ${getServiceType(t) === 'documento' ? 'active' : ''}`}
                  onClick={() => setServiceType(t.id, 'documento')}
                >
                  <b>Documento</b>
                  <span>Lo enviás vos por mail</span>
                </button>
              </div>
              <p className="honorarios-detalle-tipo">
                {getServiceType(t) === 'consulta' && 'El paciente reserva, paga y recibe el link de la videoconsulta o concurre a tu consultorio. Ocupa un lugar en tu agenda.'}
                {getServiceType(t) === 'receta' && 'No ocupa turno. La emitís desde Generar receta y le llega firmada automáticamente por WhatsApp al paciente.'}
                {getServiceType(t) === 'documento' && 'No ocupa turno. Tranqui cobra y te notifica, pero el envío del archivo queda de tu lado: la app no lo manda por vos (ej. aptos médicos o historias clínicas).'}
              </p>
            </div>

            {/* Obra Social Selector Chips */}
            <div className="honorarios-bloque">
              <label>Cobertura médica a la que aplica</label>
              <div className="honorarios-cobs">
                {(() => {
                  const todas = expandedAllCobs.has(t.id)
                  const comunes = ['Particular', 'OSDE', 'Swiss Medical', 'Galeno', 'Medicus', 'Sancor Salud', 'PAMI', 'IOSFA']
                  const listaCobs = todas ? OBRAS_SOCIALES.filter(os => os !== 'Otra') : comunes
                  return (
                    <>
                      {listaCobs.map(os => {
                        const isParticular = os === 'Particular'
                        const isSelected = isParticular ? (!t.obraSocial || t.obraSocial === 'Particular') : (t.obraSocial === os)
                        return (
                          <button
                            key={os}
                            type="button"
                            className={`honorarios-cob ${isSelected ? 'active' : ''}`}
                            onClick={() => {
                              if (isParticular) {
                                updateTariff(t.id, 'obraSocial', '')
                                updateTariff(t.id, 'requiereObraSocial', false)
                              } else {
                                updateTariff(t.id, 'obraSocial', os)
                                updateTariff(t.id, 'requiereObraSocial', true)
                              }
                            }}
                          >
                            {isSelected && <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="20 6 9 17 4 12"/></svg>}
                            {os}
                          </button>
                        )
                      })}
                      {!todas && (
                        <button
                          type="button"
                          className="honorarios-cob"
                          onClick={() => setExpandedAllCobs(prev => new Set(prev).add(t.id))}
                        >
                          {OBRAS_SOCIALES.length - comunes.length} más...
                        </button>
                      )}
                    </>
                  )
                })()}
              </div>
            </div>

            {/* Per-modalidad Pricing — solo tiene sentido para consultas: una receta o documento
                no ocupa turno, así que no hay "presencial" vs "virtual" que diferenciar. */}
            {canDifferentiateByModalidad && getServiceType(t) === 'consulta' && (
              <div className="honorarios-bloque">
                <label>Modalidad de atención</label>
                {isPricingExp ? (
                  <div>
                    <div className="honorarios-campos" style={{ paddingTop: '4px' }}>
                      <div className="honorarios-campo-angosto">
                        <label htmlFor={`pres-${t.id}`}>Presencial (ARS)</label>
                        <input
                          id={`pres-${t.id}`}
                          className="form-input"
                          type="text"
                          value={t.precioPresencial != null ? `$ ${miles(t.precioPresencial)}` : ''}
                          onChange={(e) => updateTariff(t.id, 'precioPresencial', leerPrecio(e.target.value) || null)}
                          placeholder={`$ ${miles(t.price)}`}
                        />
                      </div>
                      <div className="honorarios-campo-angosto">
                        <label htmlFor={`virt-${t.id}`}>Virtual / Online (ARS)</label>
                        <input
                          id={`virt-${t.id}`}
                          className="form-input"
                          type="text"
                          value={t.precioOnline != null ? `$ ${miles(t.precioOnline)}` : ''}
                          onChange={(e) => updateTariff(t.id, 'precioOnline', leerPrecio(e.target.value) || null)}
                          placeholder={`$ ${miles(t.price)}`}
                        />
                      </div>
                    </div>
                    <button
                      type="button"
                      className="btn btn--secondary btn--sm"
                      style={{ marginTop: '10px' }}
                      onClick={() => togglePricingExpanded(t.id)}
                    >
                      Usar un precio único
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    onClick={() => togglePricingExpanded(t.id)}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                  >
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    Cobrar distinto si es virtual
                  </button>
                )}
              </div>
            )}

            {/* Position Ordering (Efecto de posición serial) */}
            <div className="honorarios-bloque">
              <label>Orden en la lista del paciente</label>
              <div className="honorarios-orden">
                <button
                  type="button"
                  onClick={() => moveTariff(t.id, -1, categoryList)}
                  disabled={categoryIdx <= 0}
                  aria-label="Subir"
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2"><path d="m6 15 6-6 6 6"/></svg>
                </button>
                <button
                  type="button"
                  onClick={() => moveTariff(t.id, 1, categoryList)}
                  disabled={categoryIdx >= categoryList.length - 1}
                  aria-label="Bajar"
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2"><path d="m6 9 6 6 6-6"/></svg>
                </button>
                <span className="tenue" style={{ fontSize: '13px', color: '#617268', marginLeft: '6px' }}>
                  {categoryIdx + 1} de {categoryList.length}
                </span>
              </div>
            </div>

            {/* Footer Actions */}
            <div className="honorarios-pie-editor">
              <div className="honorarios-acc-riesgo">
                <button
                  type="button"
                  className="honorarios-btn-riesgo"
                  onClick={() => archiveTariff(t.id)}
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 8h18v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/><path d="M2 4h20v4H2zM10 12h4"/></svg>
                  Archivar
                </button>
                <button
                  type="button"
                  className="honorarios-btn-riesgo"
                  onClick={() => removeTariff(t.id)}
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                  Eliminar
                </button>
              </div>
              <span style={{ fontSize: '13px', color: '#227A3C', display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: '500' }}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="20 6 9 17 4 12"/></svg>
                Listo para guardar
              </span>
            </div>
          </div>
        )}
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      <div className="card" style={{ overflow: 'visible' }}>
        {/* Header */}
        <div className="honorarios-cabecera">
          <div>
            <h2 className="card__title">Honorarios y servicios</h2>
            <p className="card__subtitle">
              {visibles} {visibles === 1 ? 'servicio visible' : 'servicios visibles'} para pacientes · {vivos.length - visibles} ocultos
            </p>
          </div>
          <div className="honorarios-acciones">
            <button
              type="button"
              className={`btn btn--secondary btn--sm ${showBulkAdjust ? 'active' : ''}`}
              onClick={() => setShowBulkAdjust(!showBulkAdjust)}
              aria-expanded={showBulkAdjust}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 17l6-6 4 4 8-8"/><path d="M17 7h4v4"/></svg>
              Ajustar precios
            </button>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={() => setShowPatientPreview(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              Ver como paciente
            </button>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              onClick={handleSave}
              disabled={saving}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold' }}
            >
              <SaveIcon size={14} /> {saving ? 'Guardando...' : 'Guardar honorarios'}
            </button>
          </div>
        </div>

        {/* Missing Price Alert Banner (Efecto Zeigarnik / Von Restorff) */}
        {sinPrecio.length > 0 && (
          <div className="honorarios-bloqueo">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 9v4M12 17h.01M10.3 3.9 2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/></svg>
            <span>
              {sinPrecio.length === 1 ? 'Un servicio no tiene precio' : `${sinPrecio.length} servicios no tienen precio`} y no se puede reservar.
            </span>
            <button
              type="button"
              onClick={() => {
                setOpenTariffId(sinPrecio[0].id)
                const el = document.getElementById(`tariff-row-${sinPrecio[0].id}`)
                el?.scrollIntoView({ behavior: 'smooth' })
              }}
            >
              Ir a {sinPrecio[0].label || 'servicio'}
            </button>
          </div>
        )}

        {/* Bulk Price Adjustment Panel (Feedforward con Previsualización y Redondeo) */}
        {showBulkAdjust && (
          <div className="honorarios-ajuste">
            <div className="honorarios-ajuste-lin">
              <span>Aumentar todos</span>
              <div className="honorarios-presets">
                {[10, 15, 20, 25].map(v => (
                  <button
                    key={v}
                    type="button"
                    className={`honorarios-preset ${bulkPct === v ? 'active' : ''}`}
                    onClick={() => {
                      setBulkPct(bulkPct === v ? null : v)
                      setCustomBulkPct('')
                    }}
                  >
                    +{v}%
                  </button>
                ))}
              </div>
              <input
                type="number"
                className="form-input"
                value={customBulkPct}
                onChange={(e) => {
                  setCustomBulkPct(e.target.value)
                  const num = Number(e.target.value)
                  setBulkPct(e.target.value === '' ? null : num)
                }}
                min="-90"
                max="500"
                placeholder="otro"
                aria-label="Otro porcentaje"
              />
              <span>%, redondeando a</span>
              <select
                className="form-input"
                value={bulkRounding}
                onChange={(e) => setBulkRounding(Number(e.target.value))}
                aria-label="Redondeo"
              >
                <option value={500}>$ 500</option>
                <option value={1000}>$ 1.000</option>
                <option value={1}>sin redondear</option>
              </select>
            </div>
            <div className="honorarios-ajuste-lin" style={{ marginTop: '14px' }}>
              <button
                type="button"
                className="btn btn--primary btn--sm"
                onClick={applyBulkAdjust}
                disabled={bulkPct == null}
              >
                Aplicar
              </button>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  setShowBulkAdjust(false)
                  setBulkPct(null)
                  setCustomBulkPct('')
                }}
              >
                Cancelar
              </button>
              <span className="tenue" style={{ fontSize: '13px', color: '#617268', marginLeft: '6px' }}>
                {bulkPct == null
                  ? 'Elegí un porcentaje para ver el resultado en la lista'
                  : (firstValidTariff
                      ? `${firstValidTariff.label}: ${money(firstValidTariff.price)} → ${money(calculateAdjustedPrice(firstValidTariff.price))}`
                      : '')}
              </span>
            </div>
          </div>
        )}

        {/* List Zone */}
        <div className="honorarios-zona-listas">
          {/* Search Bar (if >= 5 services) */}
          {(vivos.length >= 5 || tariffSearch) && (
            <div className="honorarios-barra-lista">
              <div className="honorarios-buscador">
                <span className="buscador-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                </span>
                <input
                  className="form-input"
                  placeholder="Buscar servicio..."
                  value={tariffSearch}
                  onChange={(e) => setTariffSearch(e.target.value)}
                  aria-label="Buscar servicio"
                />
              </div>
              {tariffSearch && (
                <span className="tenue" style={{ fontSize: '13px', color: '#617268' }}>
                  {filteredConsultas.length + filteredDocs.length} de {vivos.length}
                </span>
              )}
            </div>
          )}

          {/* Section 1: Consultas */}
          <div className="honorarios-seccion" style={{ marginTop: 0 }}>
            <div className="honorarios-seccion-tit">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg>
              <h2>Consultas</h2>
              <span className="tenue">ocupan un turno en tu agenda</span>
            </div>
            <div className="honorarios-lista">
              {filteredConsultas.length > 0 ? (
                filteredConsultas.map(t => renderTariffRow(t, consultas))
              ) : (
                <div className="honorarios-vacio">
                  <b>{tariffSearch ? 'Ningún servicio coincide' : 'Todavía no cargaste consultas'}</b>
                  <span>{tariffSearch ? 'Probá con otro texto.' : 'Sin al menos una consulta, los pacientes no pueden reservar turno.'}</span>
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Recetas y Documentos */}
          <div className="honorarios-seccion">
            <div className="honorarios-seccion-tit">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
              <h2>Recetas y documentos</h2>
              <span className="tenue">no ocupan turno</span>
            </div>
            <div className="honorarios-lista">
              {filteredDocs.length > 0 ? (
                filteredDocs.map(t => renderTariffRow(t, docs))
              ) : (
                <div className="honorarios-vacio">
                  <b>{tariffSearch ? 'Sin coincidencias' : 'Sin recetas ni documentos'}</b>
                  <span>{tariffSearch ? 'Probá con otro texto.' : 'Agregá los que cobrás aparte de la consulta.'}</span>
                </div>
              )}
            </div>
          </div>

          {/* Section 3: Archivados */}
          {archivados.length > 0 && (
            <div className="honorarios-seccion">
              <div className="honorarios-seccion-tit">
                <h2>Archivados</h2>
                <span className="tenue">no visibles para pacientes</span>
              </div>
              <div className="honorarios-lista">
                {archivados.map(t => (
                  <div key={t.id} className="honorarios-fila">
                    <div className="honorarios-resumen" style={{ cursor: 'default' }}>
                      <div className="honorarios-datos">
                        <div className="honorarios-nombre" style={{ color: '#5D7166' }}>{t.label}</div>
                        <div className="honorarios-chips">
                          <span className="honorarios-chip">{money(t.price)}</span>
                        </div>
                      </div>
                      <div className="honorarios-arch-acc">
                        <button
                          type="button"
                          className="btn btn--secondary btn--sm"
                          onClick={() => restoreTariff(t.id)}
                        >
                          Restaurar
                        </button>
                        <button
                          type="button"
                          className="honorarios-btn-riesgo"
                          onClick={() => removeTariff(t.id)}
                        >
                          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                          Eliminar
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Quick Suggestions / Add Service Shortcuts */}
        <div className="honorarios-sugeridos">
          {showAddTariff ? (
            <div style={{ width: '100%', display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
              <input
                className="form-input"
                type="text"
                placeholder="Nombre del servicio (ej. Consulta domiciliaria)"
                value={newTariffName}
                onChange={(e) => setNewTariffName(e.target.value)}
                style={{ flex: 1, minWidth: '200px' }}
              />
              <input
                className="form-input"
                type="text"
                placeholder="Valor ARS (ej: 90k)"
                value={newTariffPrice}
                onChange={(e) => setNewTariffPrice(e.target.value)}
                style={{ width: '140px' }}
              />
              <select
                className="form-input"
                value={newTariffObraSocial}
                onChange={(e) => setNewTariffObraSocial(e.target.value)}
                style={{ width: '170px' }}
              >
                <option value="">Particular</option>
                {OBRAS_SOCIALES.filter(os => os !== 'Otra').map((os) => (
                  <option key={os} value={os}>{os}</option>
                ))}
              </select>
              <button type="button" className="btn btn--primary btn--sm" onClick={handleAddTariff}>Agregar</button>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => { setShowAddTariff(false); setNewTariffName(''); setNewTariffPrice(''); setNewTariffObraSocial('') }}
              >
                Cancelar
              </button>
            </div>
          ) : (
            <>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => setShowAddTariff(true)}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                Agregar servicio
              </button>
              {frecuentesSugeridos.length > 0 && (
                <>
                  <span className="tenue" style={{ fontSize: '13px', color: '#617268', margin: '0 4px 0 8px' }}>
                    Frecuentes:
                  </span>
                  {frecuentesSugeridos.map(item => (
                    <button
                      key={item.label}
                      type="button"
                      className="btn btn--ghost btn--sm"
                      onClick={() => addTariff(item.label, item.price, '', item.tipo)}
                      style={{ background: '#fff', border: '1px solid #CFDBD2', borderRadius: 'var(--radius-md)' }}
                    >
                      {item.label}
                    </button>
                  ))}
                </>
              )}
            </>
          )}
        </div>

        {/* Bottom Save & Note */}
        <div style={{ marginTop: 'var(--space-6)', display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-tariffs">
            {saving ? 'Guardando...' : 'Guardar honorarios'}
          </button>
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            Tranqui es 100% libre de comisiones, por lo que recibís la totalidad de tus honorarios.
          </span>
        </div>

        {/* Patient Preview Modal */}
        {showPatientPreview && (
          <div style={{
            position: 'fixed', inset: 0,
            backgroundColor: 'rgba(22, 49, 31, 0.45)',
            backdropFilter: 'blur(3px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            padding: '20px', zIndex: 9999
          }}>
            <div className="card" style={{ maxWidth: '480px', width: '100%', maxHeight: '86vh', overflowY: 'auto', padding: 0 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', padding: '18px 22px 14px', borderBottom: '1px solid var(--color-border)' }}>
                <div>
                  <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>Lo que ve el paciente</h3>
                  <p style={{ margin: '4px 0 0', fontSize: '12.5px', color: 'var(--color-text-secondary)' }}>Al reservar un turno, en este orden</p>
                </div>
                <button className="btn btn--ghost btn--sm" onClick={() => setShowPatientPreview(false)}>✕</button>
              </div>
              <div style={{ padding: '18px 22px' }}>
                {vivos.filter((t: any) => t.enabled).length === 0 ? (
                  <p style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: '20px 0' }}>
                    No tenés servicios habilitados actualmente.
                  </p>
                ) : (
                  vivos.filter((t: any) => t.enabled).map((t: any) => (
                    <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <div>
                        <div style={{ fontWeight: '600', fontSize: '14px', color: 'var(--color-text-primary)' }}>{t.label}</div>
                        <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                          {t.obraSocial || 'Particular'} · {t.requiereAgenda === false ? (t.esReceta ? 'Receta' : 'Documento') : 'Consulta'}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontWeight: '700', fontSize: '15px', color: 'var(--color-primary)' }}>
                          {money(t.price)}
                        </div>
                        {expandedPricingIds.has(t.id) && t.precioOnline != null && (
                          <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                            {money(t.precioOnline)} virtual
                          </div>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        )}

        {/* Undo Toast Notification */}
        {toastMessage && (
          <div className="honorarios-tostada">
            <span>{toastMessage.text}</span>
            {toastMessage.onUndo && (
              <button
                type="button"
                onClick={() => {
                  toastMessage.onUndo?.()
                  setToastMessage(null)
                }}
              >
                Deshacer
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
