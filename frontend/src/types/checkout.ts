export interface CheckoutTarget {
  id: string
  name: string
  degree: string
  specialty: string
  matricula: string
  price: number
  nextSlot: string
  nextSlotDay: string
  fotoUrl?: string
  telefono?: string
  emailContacto?: string
  publicaciones?: string
  domicilioAtencion?: string
  domicilioLat?: number | null
  domicilioLng?: number | null
  domicilioAtencionTorre?: string
  domicilioAtencionPiso?: string
  domicilioAtencionDepto?: string
  domicilioAtencionBarrio?: string
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
  tariffs?: { id: string; label: string; price: number; enabled: boolean; requiereObraSocial?: boolean; obraSocial?: string; precioOnline?: number | null; precioPresencial?: number | null; requiereAgenda?: boolean }[]
}
