// Approximate bounding boxes for each Argentine province, used only to bias
// the Google Places Autocomplete results toward the right part of the country
// (soft bias, not a hard restriction — users can still pick an address outside it).
export interface LatLngBounds {
  north: number
  south: number
  east: number
  west: number
}

export const PROVINCIA_BOUNDS: Record<string, LatLngBounds> = {
  "Buenos Aires": { north: -33.2, south: -41.0, east: -56.6, west: -63.4 },
  "CABA": { north: -34.53, south: -34.70, east: -58.33, west: -58.53 },
  "Catamarca": { north: -25.9, south: -29.3, east: -65.4, west: -69.0 },
  "Chaco": { north: -23.9, south: -27.6, east: -58.5, west: -62.6 },
  "Chubut": { north: -42.0, south: -46.3, east: -63.5, west: -72.0 },
  "Córdoba": { north: -29.5, south: -35.0, east: -61.8, west: -65.6 },
  "Corrientes": { north: -27.1, south: -30.9, east: -55.6, west: -59.7 },
  "Entre Ríos": { north: -30.2, south: -34.2, east: -57.8, west: -60.9 },
  "Formosa": { north: -22.1, south: -26.4, east: -57.9, west: -62.5 },
  "Jujuy": { north: -21.8, south: -24.6, east: -64.3, west: -67.1 },
  "La Pampa": { north: -35.0, south: -39.0, east: -63.3, west: -68.5 },
  "La Rioja": { north: -27.9, south: -31.9, east: -66.0, west: -69.7 },
  "Mendoza": { north: -32.0, south: -37.6, east: -66.5, west: -70.6 },
  "Misiones": { north: -25.0, south: -28.2, east: -53.6, west: -56.1 },
  "Neuquén": { north: -36.0, south: -41.0, east: -68.0, west: -71.2 },
  "Río Negro": { north: -37.7, south: -42.0, east: -62.3, west: -71.8 },
  "Salta": { north: -21.9, south: -26.4, east: -62.2, west: -68.6 },
  "San Juan": { north: -28.5, south: -32.5, east: -67.5, west: -70.6 },
  "San Luis": { north: -31.9, south: -36.1, east: -64.6, west: -67.3 },
  "Santa Cruz": { north: -46.0, south: -52.4, east: -65.5, west: -73.6 },
  "Santa Fe": { north: -28.0, south: -34.3, east: -59.5, west: -63.4 },
  "Santiago del Estero": { north: -25.4, south: -29.8, east: -61.6, west: -65.3 },
  "Tierra del Fuego": { north: -52.4, south: -55.1, east: -63.6, west: -68.7 },
  "Tucumán": { north: -26.1, south: -28.2, east: -64.6, west: -66.2 },
}
