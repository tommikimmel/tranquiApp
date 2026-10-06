import React from 'react'
import { Icon } from '../components/Icon'

export const PROVINCIAS_ARGENTINA = [
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

export const ESPECIALIDADES_GRUPOS = [
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

export const TRATAMIENTOS_DISPONIBLES = [
  'Ansiedad', 'Depresión', 'Trauma', 'Pareja', 'Psiquiatría', 'Adolescentes',
  'Trastorno bipolar', 'Ataques de pánico', 'Insomnio', 'TDAH en adultos',
  'Estrés postraumático', 'Trastorno obsesivo compulsivo',
]

export const PACIENTES_ATIENDE_OPCIONES = ['Niños', 'Adolescentes', 'Adultos', 'Adultos mayores']

export interface ExperienciaLaboral {
  id: string
  nombreLugar: string
  desde: string
  hasta: string
  descripcion: string
}

export interface Publicacion {
  id: string
  titulo: string
  descripcion: string
  link: string
}

export type SettingsTab = 'perfil-pro' | 'perfil-publico' | 'contacto' | 'presencia' | 'notificaciones' | 'integraciones' | 'suscripcion' | 'privacidad'

export const SETTINGS_TABS: { id: SettingsTab; label: string; Icon: (props: { size?: number }) => React.JSX.Element }[] = [
  { id: 'perfil-pro', label: 'Perfil profesional', Icon: Icon.User },
  { id: 'perfil-publico', label: 'Perfil público', Icon: Icon.Globe },
  { id: 'contacto', label: 'Contacto', Icon: Icon.Phone },
  { id: 'presencia', label: 'Presencia y Experiencia', Icon: Icon.Star },
  { id: 'notificaciones', label: 'Notificaciones', Icon: Icon.BellSimple },
  { id: 'integraciones', label: 'Integraciones', Icon: Icon.MercadoPago },
  { id: 'suscripcion', label: 'Suscripción', Icon: Icon.CreditCard },
  { id: 'privacidad', label: 'Privacidad y Datos', Icon: Icon.Shield },
]

export function getMissingRequirements(m: any, mpConnected?: boolean, mpEnabled?: boolean): string[] {
  const missing: string[] = []
  if (!m) return ["Cargando información del perfil..."]

  if (!m.name || !m.name.trim()) missing.push("Nombre profesional")
  if (!m.apellido || !m.apellido.trim()) missing.push("Apellido profesional")
  if (!m.telefono || !m.telefono.trim()) missing.push("Número telefónico de contacto (en Configuración > Contacto)")
  if (!m.emailContacto || !m.emailContacto.trim()) missing.push("Email de contacto (en Configuración > Contacto)")
  if (!m.sexo || !m.sexo.trim()) missing.push("Sexo biológico")
  if (!m.fechaNacimiento) missing.push("Fecha de nacimiento")
  if (!m.cuil) missing.push("CUIL profesional")
  if (!m.tipoDocumento || !m.numeroDocumento) missing.push("Tipo y número de documento")
  // Requerido para cualquier médico, no solo los presenciales: QBI2/Innovamed exige un domicilio
  // declarado para emitir recetas electrónicas incluso en atención 100% online (error QBI248).
  if (!m.domicilioAtencion || !m.domicilioAtencion.trim()) missing.push("Dirección profesional (la exige QBI2/Innovamed para emitir recetas, aunque atiendas 100% online)")
  if (!m.matriculaInfo?.tipo || !m.matriculaInfo?.provincia || !m.matriculaInfo?.numero) {
    missing.push("Datos completos de matrícula (tipo, provincia y número)")
  }
  if (!m.degree || !m.degree.trim()) missing.push("Título profesional")
  if (!m.specialty || !m.specialty.trim()) missing.push("Especialidad")
  if (!m.fotoUrl || !m.fotoUrl.trim()) missing.push("Foto de perfil profesional")
  if (!m.codigoRefeps || !m.codigoRefeps.trim()) missing.push("Código REFEPS (en Configuración > Perfil profesional)")
  if (!m.selloLinea1?.trim() || !m.selloLinea2?.trim() || !m.selloLinea3?.trim()) {
    missing.push("Sello para recetas electrónicas (en Configuración > Perfil profesional)")
  }
  if (!m.descripcionPerfil || !m.descripcionPerfil.trim()) missing.push("Descripción de tu perfil profesional")
  if (!m.tags || m.tags.length === 0) missing.push("Al menos un tratamiento/especialidad que atiendas")
  if (!m.pacientesAtiende || m.pacientesAtiende.length === 0) missing.push("Al menos un tipo de paciente que atiendas")
  if (!m.institucionFormacion || !m.institucionFormacion.trim()) missing.push("Institución donde te formaste")
  if (m.aniosExperiencia === null || m.aniosExperiencia === undefined) missing.push("Años de experiencia clínica")
  if (!m.ofreceOnline && !m.ofrecePresencial) missing.push("Al menos una modalidad de consulta (online o presencial)")

  let hasExperienciaLaboral = false
  try {
    const parsed = m.experiencia ? JSON.parse(m.experiencia) : []
    hasExperienciaLaboral = Array.isArray(parsed) && parsed.length > 0
  } catch {
    hasExperienciaLaboral = !!(m.experiencia && m.experiencia.trim())
  }
  if (!hasExperienciaLaboral) missing.push("Al menos una experiencia laboral en 'Presencia y Experiencia'")

  if (mpEnabled && mpConnected === false) missing.push("Conectar tu cuenta de Mercado Pago (en Integraciones)")
  if (!m.verificadoAdmin) missing.push("Verificación y validación de matrícula por el Administrador de Tranqui")

  return missing
}
