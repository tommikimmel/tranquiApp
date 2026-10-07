import { useState, useEffect, useRef, Fragment } from 'react'
import ConfirmDialog from './ConfirmDialog'
import { api } from '../api/api'
import { useAlert } from '../context/AlertContext'
import { Icon } from './Icon'
import AddressMapPicker from './AddressMapPicker'
import DateInputDDMMYYYY from './DateInputDDMMYYYY'
import MPConnectBanner from './MPConnectBanner'
import GoogleCalendarConnectBanner from './GoogleCalendarConnectBanner'
import { OBRAS_SOCIALES } from '../constants/obrasSociales'
import { normalizeToIsoDate } from '../utils/dashboardHelpers'
import { fileToProfilePhoto, renderProfilePhoto } from '../utils/profilePhoto'
import {
  getMissingRequirements,
  PROVINCIAS_ARGENTINA,
  ESPECIALIDADES_GRUPOS,
  TRATAMIENTOS_DISPONIBLES,
  PACIENTES_ATIENDE_OPCIONES,
  SETTINGS_TABS,
  type ExperienciaLaboral,
  type Publicacion,
  type SettingsTab,
} from '../utils/medicoProfile'

// ── Settings: Tariff & Profile ─────────────────────────────────
const SaveIcon = ({ size = 14 }: { size?: number }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
    <polyline points="17 21 17 13 7 13 7 21" />
    <polyline points="7 3 7 8 15 8" />
  </svg>
)

export default function SettingsView({
  medicoInfo,
  onSave,
  mpConnected,
  mpEnabled,
  onConnect,
  onDisconnect,
  googleConnected,
  onConnectGoogle,
  onDisconnectGoogle,
  initialTab = 'perfil-pro'
}: {
  medicoInfo: any
  onSave: (updated: any) => Promise<void>
  mpConnected: boolean
  mpEnabled?: boolean
  onConnect: () => void
  onDisconnect: () => void
  googleConnected: boolean
  onConnectGoogle: () => void
  onDisconnectGoogle: () => void
  initialTab?: SettingsTab
}) {
  const { showAlert } = useAlert();
  const [showUnmetList, setShowUnmetList] = useState(false);
  const [name, setName] = useState(medicoInfo?.nombre || '')
  const [apellido, setApellido] = useState(medicoInfo?.apellido || '')
  const [sexo, setSexo] = useState(medicoInfo?.sexo || 'M')
  const [fechaNacimiento, setFechaNacimiento] = useState(normalizeToIsoDate(medicoInfo?.fechaNacimiento))
  const [cuil, setCuil] = useState(medicoInfo?.cuil || '')
  const [tipoDocumento, setTipoDocumento] = useState(medicoInfo?.tipoDocumento || 'DNI')
  const [numeroDocumento, setNumeroDocumento] = useState(medicoInfo?.numeroDocumento || '')
  const [domicilioAtencion, setDomicilioAtencion] = useState(medicoInfo?.domicilioAtencion || '')
  const [domicilioLat, setDomicilioLat] = useState<number | null>(medicoInfo?.domicilioLat ?? null)
  const [domicilioLng, setDomicilioLng] = useState<number | null>(medicoInfo?.domicilioLng ?? null)
  const [domicilioAtencionTorre, setDomicilioAtencionTorre] = useState(medicoInfo?.domicilioAtencionTorre || '')
  const [domicilioAtencionPiso, setDomicilioAtencionPiso] = useState(medicoInfo?.domicilioAtencionPiso || '')
  const [domicilioAtencionDepto, setDomicilioAtencionDepto] = useState(medicoInfo?.domicilioAtencionDepto || '')
  const [domicilioAtencionBarrio, setDomicilioAtencionBarrio] = useState(medicoInfo?.domicilioAtencionBarrio || '')

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
  const [fotoUrl, setFotoUrl] = useState(medicoInfo?.fotoUrl || '')

  // Foto de perfil por cámara — alternativa a "Subir foto" para médicos que no tienen una
  // foto a mano en el dispositivo. Usa getUserMedia; el stream se corta al cerrar el modal
  // (cancelar, capturar, o desmontar el componente) para no dejar la cámara del navegador
  // encendida de fondo.
  const [showCameraModal, setShowCameraModal] = useState(false)
  const videoRef = useRef<HTMLVideoElement>(null)
  const cameraStreamRef = useRef<MediaStream | null>(null)

  const stopCameraStream = () => {
    cameraStreamRef.current?.getTracks().forEach(track => track.stop())
    cameraStreamRef.current = null
  }

  const handleOpenCamera = async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      showAlert('Tu navegador no permite acceder a la cámara. Probá subir una foto desde tu dispositivo.', 'error')
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } })
      cameraStreamRef.current = stream
      setShowCameraModal(true)
    } catch (err) {
      console.error(err)
      showAlert('No pudimos acceder a la cámara. Revisá los permisos del navegador para este sitio.', 'error')
    }
  }

  const handleCloseCamera = () => {
    stopCameraStream()
    setShowCameraModal(false)
  }

  const handleCapturePhoto = () => {
    const video = videoRef.current
    if (!video || !video.videoWidth) return
    const dataUrl = renderProfilePhoto(video, video.videoWidth, video.videoHeight)
    if (!dataUrl) return
    setFotoUrl(dataUrl)
    handleCloseCamera()
  }

  // The video element only exists once the modal is open, so attach the already-granted
  // stream after that render instead of inside handleOpenCamera.
  useEffect(() => {
    if (showCameraModal && videoRef.current && cameraStreamRef.current) {
      videoRef.current.srcObject = cameraStreamRef.current
    }
  }, [showCameraModal])

  // Safety net: if the médico navigates away from Configuración with the modal still open,
  // don't leave the browser's camera indicator lit.
  useEffect(() => () => stopCameraStream(), [])
  // Sello y código REFEPS para recetas electrónicas — QBI2 los usa para generar la receta
  // (incluida la firma electrónica, que ahora arma la API automáticamente a partir del REFEPS).
  // El sello se pre-completa con un texto sugerido a partir de los datos que ya cargó el
  // médico, para que la mayoría no tenga que escribir nada — solo revisar y guardar.
  const [codigoRefeps, setCodigoRefeps] = useState(medicoInfo?.codigoRefeps || '')
  // selloLinea1/2/3 columns are varchar(40)/varchar(40)/varchar(25) — the auto-generated
  // "Dr. {nombre} {apellido}" default bypasses the inputs' maxLength (that HTML attribute only
  // limits typing, not a value set programmatically via React state) and, for médicos with long
  // names, crashed the save with an unhandled Postgres "value too long" 500. Same slice() bound
  // as the input's maxLength and the backend check in MedicoService.actualizarPerfil.
  const [selloLinea1, setSelloLinea1] = useState(
    (medicoInfo?.selloLinea1 || (medicoInfo?.nombre ? `Dr. ${medicoInfo.nombre} ${medicoInfo.apellido || ''}`.trim() : '')).slice(0, 40)
  )
  const [selloLinea2, setSelloLinea2] = useState((medicoInfo?.selloLinea2 || medicoInfo?.specialty || '').slice(0, 40))
  const [selloLinea3, setSelloLinea3] = useState(() => {
    if (medicoInfo?.selloLinea3) return medicoInfo.selloLinea3.slice(0, 25)
    const tipo = medicoInfo?.matriculaInfo?.tipo || 'MP'
    const numero = medicoInfo?.matricula || medicoInfo?.matriculaInfo?.numero || ''
    return numero ? `${tipo} ${numero}`.slice(0, 25) : ''
  })
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

  // Contacto (obligatorio para verificación) — el email es de solo lectura (es la identidad de
  // login), solo el teléfono se edita acá.
  const [telefono, setTelefono] = useState(medicoInfo?.telefono || '')
  const [emailContacto, setEmailContacto] = useState(medicoInfo?.emailContacto || '')

  // Publicaciones (opcional) — mismo patrón de blob JSON que experienciasLaborales arriba.
  const [publicaciones, setPublicaciones] = useState<Publicacion[]>(() => {
    const raw = medicoInfo?.publicaciones || ''
    if (!raw) return []
    try {
      if (raw.trim().startsWith('[')) {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) return parsed
      }
    } catch (e) {
      // fallback
    }
    return []
  })
  const [pubForm, setPubForm] = useState<Publicacion | null>(null)
  const [showPubModal, setShowPubModal] = useState(false)

  const handleSavePubItem = () => {
    if (!pubForm || !pubForm.titulo.trim() || !pubForm.link.trim()) return
    if (pubForm.id) {
      setPublicaciones(publicaciones.map(item => item.id === pubForm.id ? pubForm : item))
    } else {
      setPublicaciones([...publicaciones, { ...pubForm, id: 'pub-' + Date.now() }])
    }
    setPubForm(null)
    setShowPubModal(false)
  }

  const handleDeletePubItem = (id: string) => {
    setPublicaciones(publicaciones.filter(item => item.id !== id))
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

  const [saving, setSaving] = useState(false)
  const [activeTab, setActiveTab] = useState<SettingsTab>(initialTab)

  useEffect(() => {
    if (initialTab) {
      setActiveTab(initialTab)
    }
  }, [initialTab])

  // Suscripción — mismo endpoint que ya usa Mi Cuenta (paciente), portado
  // acá porque el profesional gestiona su perfil desde Configuración, no desde /mi-cuenta.
  const [mySub, setMySub] = useState<any>(null)
  const [cancellingSub, setCancellingSub] = useState(false)

  useEffect(() => {
    api.getMySubscription().then((s: any) => setMySub(s)).catch(() => {})
  }, [])

  const [showCancelSubDialog, setShowCancelSubDialog] = useState(false);
  const fechaFinSuscripcion = mySub?.currentPeriodEnd ? new Date(mySub.currentPeriodEnd).toLocaleDateString('es-AR') : 'el fin del período abonado';
  const handleCancelarSuscripcion = () => setShowCancelSubDialog(true);
  const confirmarCancelacionSuscripcion = () => {
    setShowCancelSubDialog(false);
    setCancellingSub(true);
    api.cancelMySubscription()
      .then((res: any) => {
        showAlert(res?.message || 'Suscripción cancelada exitosamente.', 'success');
        return api.getMySubscription();
      })
      .then((s: any) => setMySub(s))
      .catch((err: any) => {
        showAlert(err?.message || 'Error al cancelar la suscripción.', 'error');
      })
      .finally(() => setCancellingSub(false));
  };

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
    if (numDocStr && (numDocStr.length < 7 || numDocStr.length > 8)) {
      showAlert("El número de documento debe tener entre 7 y 8 dígitos.", "error");
      return;
    }

    const todayStr = new Date().toISOString().split('T')[0];
    if (fechaNacimiento && (fechaNacimiento > todayStr || fechaNacimiento < '1900-01-01')) {
      showAlert("La fecha de nacimiento debe ser una fecha verídica (entre 1900 y hoy).", "error");
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
    if (matStr && (matStr.length < 3 || matStr.length > 10)) {
      showAlert("El número de matrícula debe tener entre 3 y 10 dígitos.", "error");
      return;
    }

    // 3b. Validar Código REFEPS (12 dígitos exactos — QBI2 lo rechaza con QBI235 si no cumple
    // el largo, y ese rechazo recién aparecía al emitir una receta, muy tarde para el médico)
    const refepsStr = String(codigoRefeps ?? '').trim();
    if (refepsStr && !/^\d{12}$/.test(refepsStr)) {
      showAlert("El código REFEPS debe tener exactamente 12 dígitos numéricos. Lo podés consultar en sisa.msal.gov.ar.", "error");
      return;
    }

    // 3b-2. Validar largo del Sello (varchar(40)/varchar(40)/varchar(25) en la base) — el valor
    // por defecto autogenerado ("Dr. {nombre} {apellido}") ya se recorta al crear el estado, pero
    // validamos igual antes de guardar: sin este chequeo, un médico con nombre largo se topaba con
    // un 500 sin explicación al guardar el perfil.
    if (selloLinea1.length > 40 || selloLinea2.length > 40 || selloLinea3.length > 25) {
      showAlert("El sello de la receta es demasiado largo. Línea 1 y 2: máx. 40 caracteres. Línea 3: máx. 25 caracteres.", "error");
      return;
    }

    // 3c. Validar largo del Domicilio de Atención — el buscador de direcciones (AddressMapPicker)
    // podía guardar el display_name completo de Nominatim con todos los niveles administrativos
    // ("Fray Miguel de Mojica 800, Miguel de Mojica, Jerónimo Luis de Cabrera, Córdoba, Municipio
    // de Córdoba, Pedanía Capital, Departamento Capital, Córdoba, X5019, Argentina") en vez de una
    // dirección corta — ya se corrigió en el picker, pero validamos igual por si se tipea a mano.
    const domicilioStr = String(domicilioAtencion ?? '').trim();
    if (domicilioStr && (domicilioStr.length < 8 || domicilioStr.length > 140)) {
      showAlert("El domicilio de atención debe tener entre 8 y 140 caracteres. Usá el buscador y elegí una sugerencia en vez de pegar la dirección completa.", "error");
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
        telefono: telefono.trim(),
        emailContacto: emailContacto.trim(),
        sexo,
        fechaNacimiento,
        cuil: cuilStr ? Number(cuilStr) : null,
        tipoDocumento,
        numeroDocumento: numDocStr ? Number(numDocStr) : null,
        domicilioAtencion,
        domicilioLat: ofrecePresencial ? domicilioLat : null,
        domicilioLng: ofrecePresencial ? domicilioLng : null,
        domicilioAtencionTorre: ofrecePresencial ? domicilioAtencionTorre.trim() : '',
        domicilioAtencionPiso: ofrecePresencial ? domicilioAtencionPiso.trim() : '',
        domicilioAtencionDepto: ofrecePresencial ? domicilioAtencionDepto.trim() : '',
        domicilioAtencionBarrio: ofrecePresencial ? domicilioAtencionBarrio.trim() : '',
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
        codigoRefeps,
        selloLinea1,
        selloLinea2,
        selloLinea3,
        fotoUrl,
        tags: selectedTags,
        ofreceOnline,
        ofrecePresencial,
        descripcionPerfil,
        pacientesAtiende,
        institucionFormacion,
        aniosExperiencia: aniosExperiencia === '' ? null : Number(aniosExperiencia),
        experiencia: JSON.stringify(experienciasLaborales),
        publicaciones: JSON.stringify(publicaciones),
        redesSociales: {
          instagram: formattedInstagram,
          linkedin: formattedLinkedin,
          sitioWeb: formattedSitioWeb
        }
      })
      showAlert("Configuración guardada con éxito", "success")
    } catch (err) {
      console.error(err)
      showAlert("Error al guardar la configuración", "error")
    } finally {
      setSaving(false)
    }
  }

  const perfilProComplete = Boolean(name && apellido && sexo && fechaNacimiento && cuil && tipoDocumento && numeroDocumento && degree && matTipo && matProvincia && specialty && matricula)
  const perfilPublicoComplete = Boolean(descripcionPerfil && selectedTags.length > 0 && pacientesAtiende.length > 0 && institucionFormacion && aniosExperiencia !== '')
  const contactoComplete = Boolean(telefono.trim() && emailContacto.trim())
  const integracionesPendientes = (mpConnected ? 0 : 1) + (googleConnected ? 0 : 1)

  const presenciaComplete = experienciasLaborales.length > 0
  const tabStatus: Record<SettingsTab, { label: string; tone: 'ok' | 'warn' }> = {
    'perfil-pro': perfilProComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Incompleto', tone: 'warn' },
    'perfil-publico': perfilPublicoComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Incompleto', tone: 'warn' },
    'contacto': contactoComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Incompleto', tone: 'warn' },
    'presencia': presenciaComplete ? { label: 'Completo', tone: 'ok' } : { label: 'Sin completar', tone: 'warn' },
    'notificaciones': { label: 'Activas', tone: 'ok' },
    'integraciones': integracionesPendientes === 0
      ? { label: 'Completo', tone: 'ok' }
      : { label: `${integracionesPendientes} pendiente${integracionesPendientes > 1 ? 's' : ''}`, tone: 'warn' },
    'suscripcion': mySub?.status === 'ACTIVE' ? { label: 'Activa', tone: 'ok' } : { label: 'Sin activar', tone: 'warn' },
    'privacidad': { label: 'Ley 25.326', tone: 'ok' },
  }

  const notImplementedYet = () => showAlert('Esta función va a estar disponible próximamente.', 'info')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      <p className="settings-intro">Gestioná tu perfil profesional, contacto e integraciones.</p>

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
              {getMissingRequirements(medicoInfo, mpConnected, mpEnabled).map((req, idx) => (
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
        <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)', position: 'relative', zIndex: 10 }}>
          <div>
            <h2 className="card__title">Perfil profesional</h2>
            <p className="card__subtitle">Tu foto es lo primero que ve un paciente al buscar turno — usá una imagen real y de buena calidad.</p>
          </div>
          <button className="btn btn--primary btn--sm" onClick={handleSave} disabled={saving} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold' }}>
            <SaveIcon size={14} /> {saving ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
        <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)' }}>
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
                <span style={{ color: '#9ca3af', display: 'inline-flex' }}><Icon.User size={28} /></span>
              )}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
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
                      // Se reduce a 400 px antes de guardarla (utils/profilePhoto), así que el límite solo evita
                      // decodificar archivos enormes en el navegador.
                      const MAX_FOTO_BYTES = 10 * 1024 * 1024
                      if (file.size > MAX_FOTO_BYTES) {
                        showAlert(`La foto pesa ${(file.size / (1024 * 1024)).toFixed(1)}MB — el máximo permitido es 10MB. Elegí una imagen más liviana.`, 'error')
                        e.target.value = ''
                        return
                      }
                      fileToProfilePhoto(file)
                        .then(setFotoUrl)
                        .catch(() => showAlert('No pudimos leer esa imagen. Probá con una foto JPG o PNG.', 'error'))
                        .finally(() => { e.target.value = '' })
                    }}
                  />
                </label>
                <button
                  type="button"
                  onClick={handleOpenCamera}
                  style={{
                    cursor: 'pointer',
                    backgroundColor: 'white',
                    color: 'var(--color-primary)',
                    border: '1.5px solid var(--color-primary)',
                    padding: 'var(--space-2) var(--space-4)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: 'var(--text-xs)',
                    fontWeight: 'bold',
                    textAlign: 'center'
                  }}
                >
                  Usar cámara
                </button>
              </div>
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
            <label className="form-label form-label--required" htmlFor="input-nacimiento">Fecha de Nacimiento (DD/MM/AAAA)</label>
            <DateInputDDMMYYYY id="input-nacimiento" className="form-input" min="1900-01-01" max={new Date().toISOString().split('T')[0]} value={fechaNacimiento} onChange={setFechaNacimiento} />
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
            <input id="input-num-doc" className="form-input" type="text" inputMode="numeric" maxLength={8} placeholder="Ej. 12345678" value={numeroDocumento} onChange={(e) => setNumeroDocumento(e.target.value.replace(/[^\d]/g, '').slice(0, 8))} />
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

          {/* QBI2/Innovamed rechaza toda receta electrónica que no declare un domicilio (error QBI248 "DEBE
              INFORMAR EL DOMICILIO DONDE SE REALIZÓ LA ATENCIÓN"), sin excepción para médicos 100% online
              — confirmado contra el ambiente real el 2026-08-06. No hace falta un consultorio físico ni
              que el paciente lo visite; alcanza con declarar el domicilio profesional (puede ser el
              particular). Por eso este campo ya no depende de "Consulta Presencial". */}
          <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', gridColumn: 'span 2', margin: '0' }}>
            {ofrecePresencial
              ? 'Esta dirección es la que verán los pacientes para ubicar tu consultorio.'
              : 'Aunque atiendas 100% online, QBI2/Innovamed exige declarar un domicilio profesional para poder emitir recetas electrónicas (puede ser tu domicilio particular — no se muestra a los pacientes).'}
          </p>
          <AddressMapPicker
            direccion={domicilioAtencion}
            onDireccionChange={setDomicilioAtencion}
            lat={domicilioLat}
            lng={domicilioLng}
            onLocationChange={(lat, lng) => { setDomicilioLat(lat); setDomicilioLng(lng) }}
          />

          {ofrecePresencial && (
            <>
              <div className="form-group">
                <label className="form-label" htmlFor="input-domicilio-torre">Torre</label>
                <input id="input-domicilio-torre" className="form-input" type="text" maxLength={50} placeholder="Ej: B" value={domicilioAtencionTorre} onChange={(e) => setDomicilioAtencionTorre(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="input-domicilio-piso">Piso</label>
                <input id="input-domicilio-piso" className="form-input" type="text" maxLength={20} placeholder="Ej: 3" value={domicilioAtencionPiso} onChange={(e) => setDomicilioAtencionPiso(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="input-domicilio-depto">Depto</label>
                <input id="input-domicilio-depto" className="form-input" type="text" maxLength={20} placeholder="Ej: A" value={domicilioAtencionDepto} onChange={(e) => setDomicilioAtencionDepto(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="input-domicilio-barrio">Barrio</label>
                <input id="input-domicilio-barrio" className="form-input" type="text" maxLength={100} placeholder="Ej: Nueva Córdoba" value={domicilioAtencionBarrio} onChange={(e) => setDomicilioAtencionBarrio(e.target.value)} />
              </div>
            </>
          )}

          <div className="settings-section-label">Título y matrícula</div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-degree">Título profesional</label>
            <input id="input-degree" className="form-input" type="text" value={degree} onChange={(e) => setDegree(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-mat-tipo">Tipo de Matrícula</label>
            <select id="input-mat-tipo" className="form-input" value={matTipo} onChange={(e) => setMatTipo(e.target.value)}>
              <option value="MN">MN - Matrícula Nacional</option>
              <option value="MP">MP - Matrícula Provincial</option>
              <option value="MN_MP">MN / MP - Nacional y Provincial</option>
            </select>
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
            <input id="input-matricula" className="form-input" type="text" inputMode="numeric" maxLength={10} value={matricula} onChange={(e) => setMatricula(e.target.value.replace(/[^\d]/g, '').slice(0, 10))} />
            <span className="form-helper" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}><Icon.Check size={12} /> Verificada</span>
          </div>

          <div className="settings-section-label" style={{ gridColumn: 'span 2' }}>Sello y código REFEPS para recetas electrónicas</div>
          <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', gridColumn: 'span 2', margin: '-6px 0 0' }}>
            Aparecen en el PDF de tus recetas electrónicas. Ya completamos un sello sugerido con tus datos — revisalo y ajustalo si querés. La firma electrónica la genera QBI2/Innovamed automáticamente a partir de tu código REFEPS.
          </p>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-sello-1">Sello — Línea 1</label>
            <input id="input-sello-1" className="form-input" type="text" maxLength={40} placeholder="Ej: Dr. Juan Pérez" value={selloLinea1} onChange={(e) => setSelloLinea1(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-sello-2">Sello — Línea 2</label>
            <input id="input-sello-2" className="form-input" type="text" maxLength={40} placeholder="Ej: Psiquiatría" value={selloLinea2} onChange={(e) => setSelloLinea2(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-sello-3">Sello — Línea 3</label>
            <input id="input-sello-3" className="form-input" type="text" maxLength={25} placeholder="Ej: MN 12345" value={selloLinea3} onChange={(e) => setSelloLinea3(e.target.value)} />
          </div>

          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Vista previa del sello</label>
            <div style={{
              width: '220px',
              padding: 'var(--space-3) var(--space-4)',
              borderRadius: 'var(--radius-md)',
              backgroundColor: '#f8fafc',
              border: '1.5px solid var(--color-border)',
              boxShadow: 'inset 0 0 0 3px #f8fafc, inset 0 0 0 4px var(--color-border)'
            }}>
              {[selloLinea1, selloLinea2, selloLinea3].map((linea, i) => (
                <div
                  key={i}
                  style={{
                    fontFamily: 'var(--font-mono, monospace)',
                    fontSize: 'var(--text-xs)',
                    color: linea ? 'var(--color-text-primary)' : '#9ca3af',
                    textAlign: 'center',
                    lineHeight: 1.6,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis'
                  }}
                >
                  {linea || '—'}
                </div>
              ))}
            </div>
            <span className="form-helper">Así se verá el sello impreso en el PDF de tus recetas.</span>
          </div>

          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-codigo-refeps">Código REFEPS</label>
            <input
              id="input-codigo-refeps"
              className="form-input"
              type="text"
              inputMode="numeric"
              maxLength={12}
              placeholder="Ej: 541025987654 (12 dígitos)"
              value={codigoRefeps}
              onChange={(e) => setCodigoRefeps(e.target.value.replace(/[^\d]/g, '').slice(0, 12))}
            />
            <span className="form-helper">
              Código numérico de 12 dígitos que te asigna SISA al matricularte en el Registro Federal de
              Profesionales de la Salud (REFEPS) — no es un dato que se invente, lo tenés que consultar en{' '}
              <a href="https://sisa.msal.gov.ar/sisa/" target="_blank" rel="noreferrer">sisa.msal.gov.ar</a>.
              QBI2/Innovamed lo exige para generar la firma electrónica de tus recetas y rechaza cualquier
              valor que no tenga exactamente 12 dígitos.
            </span>
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
        <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)', position: 'relative', zIndex: 10 }}>
          <div>
            <h2 className="card__title">Perfil público</h2>
            <p className="card__subtitle">Esta información se muestra a los pacientes en tu página de reserva. Es obligatoria para obtener la verificación de tu cuenta.</p>
          </div>
          <button className="btn btn--primary btn--sm" onClick={handleSave} disabled={saving} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold' }}>
            <SaveIcon size={14} /> {saving ? 'Guardando...' : 'Guardar cambios'}
          </button>
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

          <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
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

      {/* Contacto — obligatorio para la verificación del profesional */}
      {activeTab === 'contacto' && (
      <div className="card">
        <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)', position: 'relative', zIndex: 10 }}>
          <div>
            <h2 className="card__title">Contacto</h2>
            <p className="card__subtitle">
              Estos datos son obligatorios para que tu cuenta quede verificada, y se muestran
              públicamente en tu card de profesional para que los pacientes puedan contactarte.
              Son independientes del email con el que iniciás sesión.
            </p>
          </div>
          <button className="btn btn--primary btn--sm" onClick={handleSave} disabled={saving} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold' }}>
            <SaveIcon size={14} /> {saving ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
        <div className="form-grid">
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-contacto-email">
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><Icon.Mail size={14} /> Email de contacto</span>
            </label>
            <input
              id="input-contacto-email"
              className="form-input"
              type="email"
              placeholder="Ej: contacto@tuconsultorio.com"
              value={emailContacto}
              onChange={(e) => setEmailContacto(e.target.value)}
            />
            <span className="form-helper">
              Puede ser distinto al email con el que iniciás sesión ({medicoInfo?.email || 's/d'}) —
              es el que va a ver el paciente en tu perfil público.
            </span>
          </div>
          <div className="form-group">
            <label className="form-label form-label--required" htmlFor="input-contacto-telefono">
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><Icon.Phone size={14} /> Número telefónico</span>
            </label>
            <input
              id="input-contacto-telefono"
              className="form-input"
              type="tel"
              placeholder="Ej: +54 9 351 1234567"
              value={telefono}
              onChange={(e) => setTelefono(e.target.value)}
            />
            <span className="form-helper">
              Incluí el código de país (+54) y de área. Lo usamos para que el equipo de Tranqui App
              pueda contactarte si hace falta.
            </span>
          </div>
        </div>
        <div style={{ marginTop: 'var(--space-6)' }}>
          <button className="btn btn--primary" onClick={handleSave} disabled={saving} id="btn-save-contacto">
            {saving ? 'Guardando...' : 'Guardar contacto'}
          </button>
        </div>
      </div>
      )}

      {/* Presencia y Experiencia (Redes Sociales y Experiencias Laborales) */}
      {activeTab === 'presencia' && (
      <div className="card">
        <div className="card__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-3)', position: 'relative', zIndex: 10 }}>
          <div>
            <h2 className="card__title">Presencia y Experiencia Laboral</h2>
            <p className="card__subtitle">Sumá tus redes sociales y tus experiencias laborales previas para dar confianza a tus pacientes.</p>
          </div>
          <button className="btn btn--primary btn--sm" onClick={handleSave} disabled={saving} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold' }}>
            <SaveIcon size={14} /> {saving ? 'Guardando...' : 'Guardar cambios'}
          </button>
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

          <hr style={{ border: 'none', borderTop: '1px solid var(--color-border)', margin: 'var(--space-2) 0' }} />

          {/* Publicaciones — medios gráficos, notas de TV, diarios, etc. (opcional) */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
              <div>
                <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semi)', margin: 0 }}>
                  Publicaciones
                </h3>
                <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: '2px 0 0' }}>
                  Opcional. Sumá notas o menciones en medios gráficos, televisión, diarios u otros medios periodísticos.
                </p>
              </div>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  setPubForm({ id: '', titulo: '', descripcion: '', link: '' })
                  setShowPubModal(true)
                }}
              >
                <Icon.Plus /> Agregar publicación
              </button>
            </div>

            {publicaciones.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: 'var(--space-6)',
                backgroundColor: 'var(--color-surface)',
                border: '1px dashed var(--color-border)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--color-text-secondary)',
                fontSize: 'var(--text-sm)'
              }}>
                Aún no agregaste publicaciones. Hacé clic en "Agregar publicación" para sumar una nota o mención en medios.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {publicaciones.map((pub) => (
                  <div
                    key={pub.id}
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
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
                        <Icon.Newspaper size={14} />
                        <strong style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>{pub.titulo}</strong>
                      </div>
                      {pub.descripcion && (
                        <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 'var(--space-2) 0 0', lineHeight: 1.5 }}>
                          {pub.descripcion}
                        </p>
                      )}
                      {pub.link && (
                        <a
                          href={pub.link}
                          target="_blank"
                          rel="noreferrer"
                          style={{ fontSize: '11px', color: 'var(--color-primary)', display: 'inline-block', marginTop: 'var(--space-2)', wordBreak: 'break-all' }}
                        >
                          {pub.link}
                        </a>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        onClick={() => {
                          setPubForm(pub)
                          setShowPubModal(true)
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
                        onClick={() => handleDeletePubItem(pub.id)}
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

      {/* Notifications */}
      {activeTab === 'notificaciones' && (
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Notificaciones</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          {[
            { id: 'notif-new-booking', label: 'Nueva reserva', desc: 'Te avisamos por email cuando un paciente agenda.' },
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

      {activeTab === 'suscripcion' && (
        <div className="card">
          <div className="card__header">
            <h2 className="card__title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Icon.CreditCard /> Mi Suscripción
            </h2>
            <p className="card__subtitle">
              Gestioná tu membresía profesional.
            </p>
          </div>

          {mySub ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
              <div style={{
                padding: 'var(--space-4)',
                backgroundColor: 'var(--green-50)',
                border: '1px solid var(--green-200)',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 'var(--space-3)'
              }}>
                <div>
                  <span style={{ fontSize: '11px', color: 'var(--color-primary-hover)', fontWeight: 'bold', display: 'block' }}>PLAN PROFESIONAL</span>
                  <strong style={{ fontSize: '18px', color: 'var(--color-primary)' }}>{mySub.plan?.name || 'Plan Profesional'}</strong>
                  <span style={{ display: 'block', fontSize: '13px', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    {mySub.currentPeriodEnd ? `Período cubierto hasta el ${new Date(mySub.currentPeriodEnd).toLocaleDateString('es-AR')}` : 'Sin fecha de vencimiento'}
                  </span>
                </div>
                <span className={`badge ${mySub.status === 'ACTIVE' ? 'badge--success' : 'badge--warning'}`} style={{ fontSize: '13px', padding: '6px 12px' }}>
                  {mySub.cancelAtPeriodEnd ? 'Cancelada — activa hasta el vencimiento' : mySub.status === 'ACTIVE' ? 'Suscripción Activa' : mySub.status}
                </span>
              </div>

              {mySub.cancelAtPeriodEnd ? (
                <div style={{
                  padding: 'var(--space-3) var(--space-4)',
                  backgroundColor: '#fffbeb',
                  border: '1px solid #fde68a',
                  borderRadius: 'var(--radius-md)',
                  color: '#92400e',
                  fontSize: '13px',
                  lineHeight: 1.5,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px'
                }}>
                  <span style={{ display: 'inline-flex', flexShrink: 0 }}><Icon.AlertTriangle size={18} /></span>
                  <div>
                    <strong>Renovación automática cancelada:</strong> No se realizarán más cobros en Mercado Pago.
                    Mantendrás acceso total a todas las funciones profesionales hasta el <strong>{mySub.currentPeriodEnd ? new Date(mySub.currentPeriodEnd).toLocaleDateString('es-AR') : ''}</strong>.
                  </div>
                </div>
              ) : mySub.status === 'ACTIVE' && (
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <button
                    type="button"
                    className="btn btn--outline-danger btn--sm"
                    onClick={handleCancelarSuscripcion}
                    disabled={cancellingSub}
                    style={{ fontSize: '12px', padding: '6px 12px' }}
                  >
                    {cancellingSub ? 'Cancelando...' : 'Cancelar renovación automática'}
                  </button>
                  {showCancelSubDialog && (
                    <ConfirmDialog
                      title="¿Cancelar la renovación automática?"
                      confirmLabel="Sí, cancelar renovación"
                      cancelLabel="No, mantener"
                      danger
                      busy={cancellingSub}
                      onConfirm={confirmarCancelacionSuscripcion}
                      onClose={() => setShowCancelSubDialog(false)}
                    >
                      <p style={{ margin: '0 0 8px' }}>No se te va a cobrar ningún período nuevo en Mercado Pago.</p>
                      <p style={{ margin: '0 0 8px' }}>Mantenés el acceso completo a tu cuenta y tu agenda hasta el <strong>{fechaFinSuscripcion}</strong>.</p>
                      <p style={{ margin: 0 }}>Después de esa fecha, tu perfil deja de aparecer en el buscador.</p>
                    </ConfirmDialog>
                  )}
                </div>
              )}

              {mySub.activeFeatures && mySub.activeFeatures.length > 0 && (
                <div>
                  <strong style={{ fontSize: '13px', display: 'block', marginBottom: '8px' }}>Funcionalidades habilitadas:</strong>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {mySub.activeFeatures.map((feat: string) => (
                      <span key={feat} className="badge badge--neutral" style={{ fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <Icon.Check /> {feat.replace(/_/g, ' ')}
                      </span>
                    ))}
                  </div>
                </div>
              )}

            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-secondary)' }}>
              No contás con una suscripción profesional activa.
            </div>
          )}
        </div>
      )}

      {/* Privacidad y Datos personales — derechos ARCO (Ley 25.326) */}
      {activeTab === 'privacidad' && (
      <div className="card">
        <div className="card__header">
          <h2 className="card__title">Privacidad y Datos Personales</h2>
          <p className="card__subtitle">Cómo accedemos, usamos, guardamos y compartimos tus datos, y cómo ejercer tus derechos.</p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.6, margin: 0 }}>
            De acuerdo con la Ley N° 25.326 de Protección de los Datos Personales, tenés derecho a
            acceder, rectificar, actualizar y suprimir tus datos personales, así como a oponerte a su
            tratamiento en los casos que la ley prevé (derechos ARCO). También podés solicitar
            información sobre a quién se los cedimos — el detalle completo está en nuestra{' '}
            <a href="/privacidad" target="_blank" rel="noreferrer">Política de Privacidad</a>.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--space-3)' }}>
            {[
              { title: 'Acceso', desc: 'Pedir una copia de todos los datos que tenemos sobre vos.' },
              { title: 'Rectificación', desc: 'Corregir datos incompletos o inexactos.' },
              { title: 'Supresión', desc: 'Solicitar la eliminación de tus datos cuando ya no sean necesarios.' },
              { title: 'Oposición', desc: 'Oponerte a un uso puntual de tus datos.' },
            ].map(r => (
              <div key={r.title} style={{ padding: 'var(--space-3) var(--space-4)', background: 'var(--neutral-50)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
                <strong style={{ fontSize: 'var(--text-sm)' }}>{r.title}</strong>
                <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>{r.desc}</p>
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <a
              className="btn btn--secondary btn--sm"
              href="mailto:soporte@tranquisalud.com?subject=Ejercicio%20de%20derechos%20ARCO"
            >
              Solicitar acceso, rectificación o eliminación de mis datos
            </a>
            <a className="btn btn--ghost btn--sm" href="/privacidad" target="_blank" rel="noreferrer">
              Ver Política de Privacidad completa
            </a>
          </div>

          <p style={{ fontSize: '11px', color: 'var(--color-text-secondary)', margin: 0 }}>
            La AGENCIA DE ACCESO A LA INFORMACIÓN PÚBLICA (AAIP), Órgano de Control de la Ley N° 25.326,
            tiene la atribución de atender denuncias y reclamos por incumplimiento de las normas de
            protección de datos personales.
          </p>
        </div>
      </div>
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
          <div className="card mobile-modal-card" style={{ width: '100%', maxWidth: '500px', backgroundColor: 'white', padding: 'var(--space-6)', borderRadius: '12px' }}>
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
              <div className="stack-mobile-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
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

      {showCameraModal && (
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
          <div className="card mobile-modal-card" style={{ width: '100%', maxWidth: '480px', backgroundColor: 'white', padding: 'var(--space-6)', borderRadius: '12px' }}>
            <h3 style={{ margin: '0 0 var(--space-4)', fontSize: 'var(--text-lg)' }}>Sacar foto de perfil</h3>
            <div style={{ borderRadius: 'var(--radius-md)', overflow: 'hidden', backgroundColor: '#000', aspectRatio: '4 / 3' }}>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block', transform: 'scaleX(-1)' }}
              />
            </div>
            <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 'var(--space-2) 0 0' }}>
              Encuadrá tu rostro y hacé clic en "Capturar foto".
            </p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-6)' }}>
              <button type="button" className="btn btn--secondary" onClick={handleCloseCamera}>
                Cancelar
              </button>
              <button type="button" className="btn btn--primary" onClick={handleCapturePhoto}>
                Capturar foto
              </button>
            </div>
          </div>
        </div>
      )}

      {showPubModal && pubForm && (
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
          <div className="card mobile-modal-card" style={{ width: '100%', maxWidth: '500px', backgroundColor: 'white', padding: 'var(--space-6)', borderRadius: '12px' }}>
            <h3 style={{ margin: '0 0 var(--space-4)', fontSize: 'var(--text-lg)' }}>
              {pubForm.id ? 'Editar publicación' : 'Nueva publicación'}
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label form-label--required">Título</label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="Ej. Nota en La Voz del Interior sobre salud mental"
                  value={pubForm.titulo}
                  onChange={(e) => setPubForm({ ...pubForm, titulo: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label form-label--required">Link al medio</label>
                <input
                  className="form-input"
                  type="url"
                  placeholder="https://..."
                  value={pubForm.link}
                  onChange={(e) => setPubForm({ ...pubForm, link: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Breve descripción</label>
                <textarea
                  className="form-input"
                  rows={3}
                  placeholder="Contanos de qué trata la nota o publicación..."
                  value={pubForm.descripcion}
                  onChange={(e) => setPubForm({ ...pubForm, descripcion: e.target.value })}
                />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-6)' }}>
              <button
                type="button"
                className="btn btn--secondary"
                onClick={() => {
                  setShowPubModal(false)
                  setPubForm(null)
                }}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="btn btn--primary"
                onClick={handleSavePubItem}
                disabled={!pubForm.titulo.trim() || !pubForm.link.trim()}
              >
                Guardar publicación
              </button>
            </div>
          </div>
        </div>
      )}
        </div>
      </div>

      {/* Floating Sticky Save Bar */}
      <div className="settings-save-bar">
        <span className="settings-save-bar__label">
          ¿Terminaste de editar?
        </span>
        <button
          className="btn btn--primary btn--sm"
          onClick={handleSave}
          disabled={saving}
          style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold', borderRadius: '999px', padding: '6px 16px' }}
        >
          <SaveIcon size={14} /> {saving ? 'Guardando...' : 'Guardar cambios'}
        </button>
      </div>
    </div>
  )
}
