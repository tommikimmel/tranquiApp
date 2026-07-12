import { useEffect, useRef, useState } from 'react'
import { loadGoogleMapsScript } from '../utils/loadGoogleMaps'
import { PROVINCIA_BOUNDS } from '../utils/provinciaBounds'

const GOOGLE_MAPS_API_KEY = (import.meta as any).env?.VITE_GOOGLE_MAPS_API_KEY as string | undefined

// Roughly the geographic center of Argentina (used only as the initial map
// view before the professional has picked/typed anything).
const ARGENTINA_CENTER = { lat: -35.5, lng: -65.0 }
const ZOOM_NO_PIN = 4
const ZOOM_WITH_PIN = 16

function IconMapPin({ size = 14 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, flexShrink: 0 }}>
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  )
}

interface AddressMapPickerProps {
  provincia: string
  onProvinciaChange: (provincia: string) => void
  direccion: string
  onDireccionChange: (direccion: string) => void
  lat: number | null
  lng: number | null
  onLocationChange: (lat: number, lng: number, formattedAddress: string) => void
  provinciasList: string[]
}

export default function AddressMapPicker({
  provincia,
  onProvinciaChange,
  direccion,
  onDireccionChange,
  lat,
  lng,
  onLocationChange,
  provinciasList,
}: AddressMapPickerProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markerRef = useRef<any>(null)
  const autocompleteRef = useRef<any>(null)
  const direccionRef = useRef(direccion)
  direccionRef.current = direccion

  const [mapsReady, setMapsReady] = useState(false)
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
    if (!GOOGLE_MAPS_API_KEY) {
      setLoadError(true)
      return
    }
    let cancelled = false
    loadGoogleMapsScript(GOOGLE_MAPS_API_KEY)
      .then(() => { if (!cancelled) setMapsReady(true) })
      .catch(() => { if (!cancelled) setLoadError(true) })
    return () => { cancelled = true }
  }, [])

  // Initialize the map + marker + autocomplete once the script is loaded.
  // Intentionally runs only once: later prop changes for lat/lng come from
  // this same component's own callbacks, not from an external re-sync need.
  useEffect(() => {
    if (!mapsReady || !mapContainerRef.current) return
    const google = (window as any).google

    const hasInitialPin = lat != null && lng != null
    const initialCenter = hasInitialPin ? { lat, lng } : ARGENTINA_CENTER

    const map = new google.maps.Map(mapContainerRef.current, {
      center: initialCenter,
      zoom: hasInitialPin ? ZOOM_WITH_PIN : ZOOM_NO_PIN,
      streetViewControl: false,
      mapTypeControl: false,
      fullscreenControl: false,
    })
    mapRef.current = map

    const marker = new google.maps.Marker({
      map,
      position: initialCenter,
      draggable: true,
      visible: hasInitialPin,
    })
    markerRef.current = marker

    const geocoder = new google.maps.Geocoder()
    const applyPosition = (newLat: number, newLng: number) => {
      geocoder.geocode({ location: { lat: newLat, lng: newLng } }, (results: any, status: string) => {
        const formatted = (status === 'OK' && results?.[0]) ? results[0].formatted_address : direccionRef.current
        onLocationChange(newLat, newLng, formatted)
      })
    }

    marker.addListener('dragend', () => {
      const pos = marker.getPosition()
      if (!pos) return
      applyPosition(pos.lat(), pos.lng())
    })

    // Clicking the map is a secondary way to place/adjust the pin — the
    // primary flow is typing the address above via the autocomplete search.
    map.addListener('click', (e: any) => {
      const newLat = e.latLng.lat()
      const newLng = e.latLng.lng()
      marker.setPosition({ lat: newLat, lng: newLng })
      marker.setVisible(true)
      applyPosition(newLat, newLng)
    })

    if (inputRef.current) {
      const autocomplete = new google.maps.places.Autocomplete(inputRef.current, {
        componentRestrictions: { country: 'ar' },
        fields: ['geometry', 'formatted_address'],
      })
      autocompleteRef.current = autocomplete

      autocomplete.addListener('place_changed', () => {
        const place = autocomplete.getPlace()
        if (!place.geometry?.location) return
        const newLat = place.geometry.location.lat()
        const newLng = place.geometry.location.lng()
        map.setCenter({ lat: newLat, lng: newLng })
        map.setZoom(ZOOM_WITH_PIN)
        marker.setPosition({ lat: newLat, lng: newLng })
        marker.setVisible(true)
        const formatted = place.formatted_address || direccionRef.current
        onDireccionChange(formatted)
        onLocationChange(newLat, newLng, formatted)
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapsReady])

  // Bias the autocomplete toward the selected province (soft hint, not a hard restriction).
  useEffect(() => {
    if (!mapsReady || !autocompleteRef.current) return
    const bounds = PROVINCIA_BOUNDS[provincia]
    if (!bounds) return
    const google = (window as any).google
    autocompleteRef.current.setBounds(new google.maps.LatLngBounds(
      { lat: bounds.south, lng: bounds.west },
      { lat: bounds.north, lng: bounds.east }
    ))
  }, [provincia, mapsReady])

  if (!GOOGLE_MAPS_API_KEY || loadError) {
    return (
      <div className="form-group" style={{ gridColumn: 'span 2' }}>
        <label className="form-label form-label--required">Domicilio de Atención</label>
        <input
          className="form-input"
          type="text"
          placeholder="Ej: Av. Colón 123, Córdoba"
          value={direccion}
          onChange={(e) => onDireccionChange(e.target.value)}
        />
        <span className="form-helper">
          El buscador con mapa no está disponible (falta configurar Google Maps). Por ahora podés escribir la dirección manualmente.
        </span>
      </div>
    )
  }

  return (
    <div className="form-group" style={{ gridColumn: 'span 2' }}>
      <label className="form-label form-label--required">Domicilio de Atención</label>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr', gap: 'var(--space-3)', marginBottom: 'var(--space-2)' }}>
        <select className="form-input" value={provincia} onChange={(e) => onProvinciaChange(e.target.value)}>
          <option value="">Provincia (opcional)</option>
          {provinciasList.map(p => <option key={p} value={p}>{p}</option>)}
        </select>
        <input
          ref={inputRef}
          className="form-input"
          type="text"
          placeholder="Calle y altura, ej: Av. Colón 123"
          value={direccion}
          onChange={(e) => onDireccionChange(e.target.value)}
        />
      </div>
      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '220px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)' }}
      />
      <span className="form-helper" style={{ display: 'flex', alignItems: 'center', gap: '4px', marginTop: '4px' }}>
        <IconMapPin />
        {lat != null && lng != null
          ? 'Ubicación seleccionada. Podés arrastrar el pin o hacer clic en el mapa para ajustarla.'
          : 'Escribí tu calle y altura para buscarla, o hacé clic en el mapa para marcar tu ubicación.'}
      </span>
    </div>
  )
}
