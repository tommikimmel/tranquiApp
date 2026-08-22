import { describe, expect, it } from 'vitest'
import { getMissingRequirements } from './medicoProfile'

// A fully-complete médico profile — the baseline every "single field missing" test starts
// from and mutates one property away from.
const COMPLETE_MEDICO = {
  name: 'Ana',
  apellido: 'García',
  telefono: '+54 9 11 1234-5678',
  emailContacto: 'ana@example.com',
  sexo: 'Femenino',
  fechaNacimiento: '1985-01-01',
  cuil: '27-12345678-3',
  tipoDocumento: 'DNI',
  numeroDocumento: '12345678',
  domicilioAtencion: 'Av. Siempreviva 123',
  matriculaInfo: { tipo: 'MN', provincia: 'CABA', numero: '12345' },
  degree: 'Médica',
  specialty: 'Psiquiatría',
  fotoUrl: 'https://example.com/foto.jpg',
  codigoRefeps: 'ABC123456789',
  selloLinea1: 'Dra. Ana García',
  selloLinea2: 'MN 12345',
  selloLinea3: 'Psiquiatría',
  descripcionPerfil: 'Especialista en salud mental.',
  tags: ['Ansiedad'],
  pacientesAtiende: ['Adultos'],
  institucionFormacion: 'UBA',
  aniosExperiencia: 10,
  ofreceOnline: true,
  ofrecePresencial: false,
  experiencia: JSON.stringify([{ id: '1', nombreLugar: 'Hospital X', desde: '2015', hasta: '2020', descripcion: '' }]),
  verificadoAdmin: true,
}

describe('getMissingRequirements', () => {
  it('returns a loading placeholder when médico info is not yet available', () => {
    expect(getMissingRequirements(null)).toEqual(['Cargando información del perfil...'])
    expect(getMissingRequirements(undefined)).toEqual(['Cargando información del perfil...'])
  })

  it('returns an empty list when every requirement is satisfied and MP is not required', () => {
    expect(getMissingRequirements(COMPLETE_MEDICO, undefined, false)).toEqual([])
  })

  it('flags a missing/blank nombre and apellido independently', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, name: '' })).toContain('Nombre profesional')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, name: '   ' })).toContain('Nombre profesional')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, apellido: undefined })).toContain('Apellido profesional')
  })

  it('flags missing contact telefono/email with the settings-tab hint', () => {
    const missing = getMissingRequirements({ ...COMPLETE_MEDICO, telefono: '', emailContacto: '' })
    expect(missing).toContain('Número telefónico de contacto (en Configuración > Contacto)')
    expect(missing).toContain('Email de contacto (en Configuración > Contacto)')
  })

  it('flags missing sexo, fechaNacimiento and cuil', () => {
    const missing = getMissingRequirements({ ...COMPLETE_MEDICO, sexo: '', fechaNacimiento: null, cuil: null })
    expect(missing).toContain('Sexo biológico')
    expect(missing).toContain('Fecha de nacimiento')
    expect(missing).toContain('CUIL profesional')
  })

  it('flags missing tipo/numero documento as a single combined requirement', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, tipoDocumento: '' }))
      .toContain('Tipo y número de documento')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, numeroDocumento: '' }))
      .toContain('Tipo y número de documento')
  })

  it('flags a missing domicilioAtencion even for a fully-online médico (QBI2 needs it regardless)', () => {
    const missing = getMissingRequirements({ ...COMPLETE_MEDICO, domicilioAtencion: '', ofreceOnline: true, ofrecePresencial: false })
    expect(missing.some(m => m.includes('Dirección profesional'))).toBe(true)
  })

  it('flags incomplete matriculaInfo when any sub-field is missing', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, matriculaInfo: { tipo: 'MN', provincia: '', numero: '123' } }))
      .toContain('Datos completos de matrícula (tipo, provincia y número)')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, matriculaInfo: undefined }))
      .toContain('Datos completos de matrícula (tipo, provincia y número)')
  })

  it('flags missing degree, specialty and fotoUrl', () => {
    const missing = getMissingRequirements({ ...COMPLETE_MEDICO, degree: '', specialty: '', fotoUrl: '' })
    expect(missing).toContain('Título profesional')
    expect(missing).toContain('Especialidad')
    expect(missing).toContain('Foto de perfil profesional')
  })

  it('flags missing codigoRefeps and an incomplete sello (any of the 3 lines)', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, codigoRefeps: '' }))
      .toContain('Código REFEPS (en Configuración > Perfil profesional)')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, selloLinea2: '' }))
      .toContain('Sello para recetas electrónicas (en Configuración > Perfil profesional)')
  })

  it('flags missing descripcion, tags and pacientesAtiende', () => {
    const missing = getMissingRequirements({ ...COMPLETE_MEDICO, descripcionPerfil: '', tags: [], pacientesAtiende: [] })
    expect(missing).toContain('Descripción de tu perfil profesional')
    expect(missing).toContain('Al menos un tratamiento/especialidad que atiendas')
    expect(missing).toContain('Al menos un tipo de paciente que atiendas')
  })

  it('flags missing institucionFormacion and aniosExperiencia (0 is valid, null/undefined is not)', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, institucionFormacion: '' }))
      .toContain('Institución donde te formaste')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, aniosExperiencia: null }))
      .toContain('Años de experiencia clínica')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, aniosExperiencia: 0 }))
      .not.toContain('Años de experiencia clínica')
  })

  it('flags missing modalidad when neither online nor presencial is offered', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, ofreceOnline: false, ofrecePresencial: false }))
      .toContain('Al menos una modalidad de consulta (online o presencial)')
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, ofreceOnline: false, ofrecePresencial: true }))
      .not.toContain('Al menos una modalidad de consulta (online o presencial)')
  })

  it('parses experiencia as a JSON array and flags it missing when empty/absent', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, experiencia: '[]' }))
      .toContain("Al menos una experiencia laboral en 'Presencia y Experiencia'")
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, experiencia: undefined }))
      .toContain("Al menos una experiencia laboral en 'Presencia y Experiencia'")
  })

  it('falls back to treating experiencia as free text when it is not valid JSON', () => {
    // Non-JSON but non-blank text: caught by the try/catch, satisfies the requirement.
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, experiencia: 'Consultorio propio desde 2015' }))
      .not.toContain("Al menos una experiencia laboral en 'Presencia y Experiencia'")
    // Non-JSON and blank: still missing.
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, experiencia: '   ' }))
      .toContain("Al menos una experiencia laboral en 'Presencia y Experiencia'")
  })

  it('flags Mercado Pago connection only when MP is enabled and not connected', () => {
    expect(getMissingRequirements(COMPLETE_MEDICO, false, true))
      .toContain('Conectar tu cuenta de Mercado Pago (en Integraciones)')
    expect(getMissingRequirements(COMPLETE_MEDICO, true, true))
      .not.toContain('Conectar tu cuenta de Mercado Pago (en Integraciones)')
    expect(getMissingRequirements(COMPLETE_MEDICO, false, false))
      .not.toContain('Conectar tu cuenta de Mercado Pago (en Integraciones)')
  })

  it('flags a médico not yet verified by the admin', () => {
    expect(getMissingRequirements({ ...COMPLETE_MEDICO, verificadoAdmin: false }))
      .toContain('Verificación y validación de matrícula por el Administrador de Tranqui')
    expect(getMissingRequirements(COMPLETE_MEDICO))
      .not.toContain('Verificación y validación de matrícula por el Administrador de Tranqui')
  })
})
