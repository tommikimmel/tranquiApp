import { useEffect, useRef, useState } from 'react'
import { loadGoogleMapsScript } from '../utils/loadGoogleMaps'
import { PROVINCIA_BOUNDS } from '../utils/provinciaBounds'

const GOOGLE_MAPS_API_KEY = (import.meta as any).env?.VITE_GOOGLE_MAPS_API_KEY as string | undefined

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

// Global promise to prevent double loading Leaflet script/styles
let leafletLoadingPromise: Promise<void> | null = null

function loadLeafletScript(): Promise<void> {
  if ((window as any).L) {
    return Promise.resolve()
  }
  if (leafletLoadingPromise) return leafletLoadingPromise

  leafletLoadingPromise = new Promise((resolve, reject) => {
    // Load CSS
    const linkId = 'leaflet-css'
    if (!document.getElementById(linkId)) {
      const link = document.createElement('link')
      link.id = linkId
      link.rel = 'stylesheet'
      link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'
      document.head.appendChild(link)
    }

    // Load JS
    const scriptId = 'leaflet-script'
    const script = document.createElement('script')
    script.id = scriptId
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('No se pudo cargar Leaflet'))
    document.body.appendChild(script)
  })

  return leafletLoadingPromise
}

interface AddressMapPickerProps {
  provincia: string
  onProvinciaChange: (provincia: string) => void
  direccion: string
  onDireccionChange: (direccion: string) => void
  lat: number | null
  lng: number | null
  onLocationChange: (lat: number, lng: number) => void
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
  
  // Google Maps refs
  const mapRef = useRef<any>(null)
  const markerRef = useRef<any>(null)
  const autocompleteRef = useRef<any>(null)
  
  // Leaflet refs
  const leafletMapRef = useRef<any>(null)
  const leafletMarkerRef = useRef<any>(null)

  const [mapsReady, setMapsReady] = useState(false)
  const [leafletReady, setLeafletReady] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [searching, setSearching] = useState(false)
  
  // Controls if manual adjustment (dragging marker or clicking map) is allowed
  const [manualAdjustmentEnabled, setManualAdjustmentEnabled] = useState(false)
  const manualAdjustmentRef = useRef(manualAdjustmentEnabled)
  manualAdjustmentRef.current = manualAdjustmentEnabled

  const searchedRef = useRef('')

  // Load appropriate map library
  useEffect(() => {
    let cancelled = false
    if (!GOOGLE_MAPS_API_KEY) {
      loadLeafletScript()
        .then(() => { if (!cancelled) setLeafletReady(true) })
        .catch(() => { if (!cancelled) setLoadError(true) })
      return
    }

    loadGoogleMapsScript(GOOGLE_MAPS_API_KEY)
      .then(() => {
        if (!cancelled) setMapsReady(true)
      })
      .catch(() => {
        // Fallback to Leaflet if Google Maps fails to load
        loadLeafletScript()
          .then(() => { if (!cancelled) setLeafletReady(true) })
          .catch(() => { if (!cancelled) setLoadError(true) })
      })

    return () => { cancelled = true }
  }, [])

  // Geocoding function: Types the address, clicks geolocate, finds on map
  const handleSearchAddress = async () => {
    const queryStr = direccion.trim()
    if (!queryStr) return
    
    // Prevent double geocoding if nothing changed
    const fullQuery = `${queryStr}, ${provincia ? provincia + ', ' : ''}Argentina`
    if (fullQuery === searchedRef.current) return
    searchedRef.current = fullQuery

    setSearching(true)
    setManualAdjustmentEnabled(false) // Lock location when a new address search starts
    try {
      if (mapsReady) {
        // Google Geocoder
        const google = (window as any).google
        const geocoder = new google.maps.Geocoder()
        geocoder.geocode({ address: fullQuery }, (results: any, status: string) => {
          setSearching(false)
          if (status === 'OK' && results?.[0]) {
            const loc = results[0].geometry.location
            const newLat = loc.lat()
            const newLng = loc.lng()
            
            if (mapRef.current) {
              mapRef.current.setCenter({ lat: newLat, lng: newLng })
              mapRef.current.setZoom(ZOOM_WITH_PIN)
            }
            if (markerRef.current) {
              markerRef.current.setPosition({ lat: newLat, lng: newLng })
              markerRef.current.setVisible(true)
            }
            
            onLocationChange(newLat, newLng)
          }
        })
      } else if (leafletReady) {
        // Nominatim Geocoder (OpenStreetMap)
        const response = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(fullQuery)}&limit=1`)
        setSearching(false)
        if (response.ok) {
          const data = await response.json()
          if (data && data.length > 0) {
            const newLat = parseFloat(data[0].lat)
            const newLng = parseFloat(data[0].lon)
            
            if (leafletMapRef.current) {
              leafletMapRef.current.setView([newLat, newLng], ZOOM_WITH_PIN)
            }
            if (leafletMarkerRef.current) {
              leafletMarkerRef.current.setLatLng([newLat, newLng])
              if (!leafletMapRef.current.hasLayer(leafletMarkerRef.current)) {
                leafletMarkerRef.current.addTo(leafletMapRef.current)
              }
            }
            onLocationChange(newLat, newLng)
          }
        }
      } else {
        setSearching(false)
      }
    } catch (err) {
      console.error('Error al geolocalizar dirección:', err)
      setSearching(false)
    }
  }

  // Handle Enter key inside the Address field
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      handleSearchAddress()
    }
  }

  // Initialize Google Map
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
      draggable: manualAdjustmentRef.current,
      visible: hasInitialPin,
    })
    markerRef.current = marker

    // Marker drag updates lat/lng (only if manual adjustment is enabled)
    marker.addListener('dragend', () => {
      if (!manualAdjustmentRef.current) return
      const pos = marker.getPosition()
      if (!pos) return
      onLocationChange(pos.lat(), pos.lng())
    })

    // Map click places/updates marker (only if manual adjustment is enabled)
    map.addListener('click', (e: any) => {
      if (!manualAdjustmentRef.current) return
      const newLat = e.latLng.lat()
      const newLng = e.latLng.lng()
      marker.setPosition({ lat: newLat, lng: newLng })
      marker.setVisible(true)
      onLocationChange(newLat, newLng)
    })

    // Auto-search bias integration
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
        const formatted = place.formatted_address || direccion
        onDireccionChange(formatted)
        onLocationChange(newLat, newLng)
        setManualAdjustmentEnabled(false) // Lock pin position on new Autocomplete selection
      })
    }

    return () => {
      // Clean up Google Maps references if needed
    }
  }, [mapsReady])

  // Initialize Leaflet Map
  useEffect(() => {
    if (!leafletReady || !mapContainerRef.current) return
    const L = (window as any).L
    if (!L) return

    const hasInitialPin = lat != null && lng != null
    const initialCenter = hasInitialPin ? [lat, lng] : [ARGENTINA_CENTER.lat, ARGENTINA_CENTER.lng]

    const map = L.map(mapContainerRef.current, {
      zoomControl: true,
    }).setView(
      initialCenter,
      hasInitialPin ? ZOOM_WITH_PIN : ZOOM_NO_PIN
    )
    leafletMapRef.current = map

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map)

    // Premium custom SVG marker styled to match the green primary theme of Tranqui App
    const customIcon = L.divIcon({
      html: `
        <svg viewBox="0 0 24 24" fill="none" stroke="#2E7D5B" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="width: 32px; height: 32px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3)); display: block;">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" fill="#ffffff" />
          <circle cx="12" cy="10" r="3.2" fill="#2E7D5B" />
        </svg>
      `,
      className: 'custom-leaflet-marker',
      iconSize: [32, 32],
      iconAnchor: [16, 32]
    })

    const marker = L.marker(initialCenter, {
      draggable: manualAdjustmentRef.current,
      icon: customIcon
    })
    leafletMarkerRef.current = marker

    if (hasInitialPin) {
      marker.addTo(map)
    }

    // Drag marker updates lat/lng (only if manual adjustment is enabled)
    marker.on('dragend', () => {
      if (!manualAdjustmentRef.current) return
      const latlng = marker.getLatLng()
      onLocationChange(latlng.lat, latlng.lng)
    })

    // Click map updates marker (only if manual adjustment is enabled)
    map.on('click', (e: any) => {
      if (!manualAdjustmentRef.current) return
      const newLat = e.latlng.lat
      const newLng = e.latlng.lng
      marker.setLatLng([newLat, newLng])
      if (!map.hasLayer(marker)) {
        marker.addTo(map)
      }
      onLocationChange(newLat, newLng)
    })

    return () => {
      map.remove()
    }
  }, [leafletReady])

  // Bias Google Autocomplete bounds towards the selected province
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

  // Synchronize Google Maps marker position with lat/lng props (if changed externally)
  useEffect(() => {
    if (!mapRef.current || !markerRef.current || lat == null || lng == null) return
    const currentPos = markerRef.current.getPosition()
    if (!currentPos || currentPos.lat() !== lat || currentPos.lng() !== lng) {
      markerRef.current.setPosition({ lat, lng })
      markerRef.current.setVisible(true)
      mapRef.current.setCenter({ lat, lng })
      mapRef.current.setZoom(ZOOM_WITH_PIN)
    }
  }, [lat, lng])

  // Synchronize Leaflet marker position with lat/lng props (if changed externally)
  useEffect(() => {
    if (!leafletMapRef.current || !leafletMarkerRef.current || lat == null || lng == null) return
    const currentLatLng = leafletMarkerRef.current.getLatLng()
    if (currentLatLng.lat !== lat || currentLatLng.lng !== lng) {
      leafletMarkerRef.current.setLatLng([lat, lng])
      if (!leafletMapRef.current.hasLayer(leafletMarkerRef.current)) {
        leafletMarkerRef.current.addTo(leafletMapRef.current)
      }
      leafletMapRef.current.setView([lat, lng], ZOOM_WITH_PIN)
    }
  }, [lat, lng])

  // Dynamically update marker draggable states on changes to manualAdjustmentEnabled
  useEffect(() => {
    if (markerRef.current) {
      markerRef.current.setDraggable(manualAdjustmentEnabled)
    }
  }, [manualAdjustmentEnabled, mapsReady])

  useEffect(() => {
    if (leafletMarkerRef.current && leafletMarkerRef.current.dragging) {
      if (manualAdjustmentEnabled) {
        leafletMarkerRef.current.dragging.enable()
      } else {
        leafletMarkerRef.current.dragging.disable()
      }
    }
  }, [manualAdjustmentEnabled, leafletReady])

  // Double fallback check
  const hasMapLoaded = mapsReady || leafletReady

  if (loadError || !hasMapLoaded) {
    // If map libraries fail to load completely (e.g. no connection/CDN error), render fallback manual inputs
    return (
      <div className="form-group" style={{ gridColumn: 'span 2' }}>
        <label className="form-label form-label--required">Domicilio de Atención</label>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 'var(--space-3)' }}>
          <select className="form-input" value={provincia} onChange={(e) => onProvinciaChange(e.target.value)}>
            <option value="">Provincia</option>
            {provinciasList.map(p => <option key={p} value={p}>{p}</option>)}
          </select>
          <input
            className="form-input"
            type="text"
            placeholder="Calle y altura, ej: Av. Colón 123"
            value={direccion}
            onChange={(e) => onDireccionChange(e.target.value)}
          />
        </div>
        <span className="form-helper">
          El mapa interactivo no está disponible. Podés ingresar tu dirección de consultorio en el campo de texto.
        </span>
      </div>
    )
  }

  return (
    <div className="form-group" style={{ gridColumn: 'span 2' }}>
      <label className="form-label form-label--required">Domicilio de Atención</label>
      <div style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
        <select 
          className="form-input" 
          value={provincia} 
          onChange={(e) => onProvinciaChange(e.target.value)}
          style={{ width: '150px', flexShrink: 0 }}
        >
          <option value="">Provincia</option>
          {provinciasList.map(p => <option key={p} value={p}>{p}</option>)}
        </select>
        <input
          ref={inputRef}
          className="form-input"
          type="text"
          placeholder="Calle y altura, ej: Av. Colón 123"
          value={direccion}
          onChange={(e) => onDireccionChange(e.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={handleSearchAddress}
          style={{ flexGrow: 1 }}
        />
        <button
          type="button"
          onClick={handleSearchAddress}
          disabled={searching}
          className="btn btn--secondary"
          style={{ padding: '0 16px', height: '38px', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 'var(--text-sm)', fontWeight: 'bold' }}
        >
          {searching ? 'Buscando...' : 'Ubicar'}
        </button>
      </div>
      
      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '220px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', marginTop: '8px' }}
      />
      
      {lat != null && lng != null ? (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '8px', gap: 'var(--space-2)' }}>
          <span className="form-helper" style={{ display: 'flex', alignItems: 'center', gap: '6px', margin: 0 }}>
            <IconMapPin />
            {manualAdjustmentEnabled 
              ? 'Ajuste manual activo. Hacé clic en el mapa o arrastrá el marcador verde.' 
              : 'Ubicación establecida y bloqueada.'}
          </span>
          <button
            type="button"
            className={`btn ${manualAdjustmentEnabled ? 'btn--primary' : 'btn--secondary'}`}
            onClick={() => setManualAdjustmentEnabled(!manualAdjustmentEnabled)}
            style={{ padding: '6px 14px', fontSize: '12px', height: '32px', fontWeight: 'bold' }}
          >
            {manualAdjustmentEnabled ? 'Confirmar Ubicación' : 'Ubicación Incorrecta'}
          </button>
        </div>
      ) : (
        <span className="form-helper" style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '6px' }}>
          <IconMapPin />
          Escribí la dirección y hacé clic en "Ubicar" para posicionar el marcador.
        </span>
      )}
    </div>
  )
}
