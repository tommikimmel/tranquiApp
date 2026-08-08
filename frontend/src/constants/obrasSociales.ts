// Shared catalog of obras sociales — used both by the profesional's "Honorarios y Servicios"
// tab (to assign a specific obra social to a service) and by CheckoutFlow (to show/collect the
// obra social for the legacy generic OBRA_SOCIAL/OSDE service types). "Otra" is only meaningful
// in the checkout free-text fallback, not when a profesional assigns one specific obra social to
// a service — filter it out there.
export const OBRAS_SOCIALES = [
  'OSDE',
  'Swiss Medical',
  'Galeno',
  'Medifé',
  'Omint',
  'OSECAC',
  'IOMA',
  'PAMI',
  'SanCor Salud',
  'Medicus',
  'Accord Salud',
  'Unión Personal',
  'Prevención Salud',
  'Otra'
]
