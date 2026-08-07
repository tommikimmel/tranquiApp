import { useEffect, useRef, useState } from 'react'
import { loadGoogleMapsScript } from '../utils/loadGoogleMaps'

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
  direccion: string
  onDireccionChange: (direccion: string) => void
  lat: number | null
  lng: number | null
  onLocationChange: (lat: number, lng: number) => void
}

export default function AddressMapPicker({
  direccion,
  onDireccionChange,
  lat,
  lng,
  onLocationChange,
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

  // Live suggestions-as-you-type for the Nominatim/Leaflet path (no Google Maps API key
  // configured today, so this is the branch actually in use). Google's own Places Autocomplete
  // widget already gives native live suggestions when mapsReady, so this only kicks in there.
  const [suggestions, setSuggestions] = useState<{ label: string; lat: number; lon: number }[]>([])
  const [showSuggestions, setShowSuggestions] = useState(false)
  const [suggestLoading, setSuggestLoading] = useState(false)
  const suggestDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const skipNextSuggestFetch = useRef(false)
  const suggestBoxRef = useRef<HTMLDivElement>(null)

  const onDireccionChangeRef = useRef(onDireccionChange)
  useEffect(() => {
    onDireccionChangeRef.current = onDireccionChange
  }, [onDireccionChange])

  const performReverseGeocode = async (newLat: number, newLng: number) => {
    try {
      if (mapsReady) {
        const google = (window as any).google
        const geocoder = new google.maps.Geocoder()
        geocoder.geocode({ location: { lat: newLat, lng: newLng } }, (results: any, status: string) => {
          if (status === 'OK' && results?.[0]) {
            const formatted = results[0].formatted_address
            onDireccionChangeRef.current(formatted)
          }
        })
      } else if (leafletReady) {
        const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${newLat}&lon=${newLng}`, {
          headers: {
            'Accept-Language': 'es',
            'User-Agent': 'TranquiApp/1.0'
          }
        })
        if (res.ok) {
          const data = await res.json()
          if (data && data.address) {
            const road = data.address.road || data.address.pedestrian || data.address.suburb || ''
            const num = data.address.house_number || ''
            const city = data.address.city || data.address.town || data.address.village || ''
            let formatted = ''
            if (road) {
              formatted = `${road} ${num}`.trim()
              if (city) formatted += `, ${city}`
            } else {
              formatted = data.display_name.split(',').slice(0, 3).join(',').trim()
            }
            onDireccionChangeRef.current(formatted)
          }
        }
      }
    } catch (err) {
      console.error('Error in reverse geocoding:', err)
    }
  }

  // Controls if manual adjustment (dragging marker or clicking map) is allowed
  const [manualAdjustmentEnabled, setManualAdjustmentEnabled] = useState(false)
  const manualAdjustmentRef = useRef(manualAdjustmentEnabled)
  manualAdjustmentRef.current = manualAdjustmentEnabled

  const searchedRef = useRef('')

  // Debounced live suggestions from Nominatim as the user types — this replaces the old "type
  // then click Ubicar" flow with normal map-style autocomplete. No provincia filter: the address
  // text itself is enough for Nominatim to disambiguate within Argentina.
  useEffect(() => {
    if (mapsReady) return // Google's native Places widget already provides its own dropdown
    if (skipNextSuggestFetch.current) { skipNextSuggestFetch.current = false; return }
    if (suggestDebounceRef.current) clearTimeout(suggestDebounceRef.current)

    const query = direccion.trim()
    if (query.length < 3) {
      setSuggestions([])
      setShowSuggestions(false)
      return
    }

    suggestDebounceRef.current = setTimeout(async () => {
      setSuggestLoading(true)
      try {
        const params = new URLSearchParams({
          format: 'json',
          q: `${query}, Argentina`,
          countrycodes: 'ar',
          limit: '6',
          addressdetails: '1',
        })
        const res = await fetch(`https://nominatim.openstreetmap.org/search?${params.toString()}`, {
          headers: { 'Accept-Language': 'es', 'User-Agent': 'TranquiApp/1.0' }
        })
        if (res.ok) {
          const data = await res.json()
          setSuggestions((Array.isArray(data) ? data : []).map((d: any) => ({
            label: d.display_name as string,
            lat: parseFloat(d.lat),
            lon: parseFloat(d.lon),
          })))
          setShowSuggestions(true)
        }
      } catch (err) {
        console.error('Error buscando sugerencias de dirección:', err)
      } finally {
        setSuggestLoading(false)
      }
    }, 400)

    return () => { if (suggestDebounceRef.current) clearTimeout(suggestDebounceRef.current) }
  }, [direccion, mapsReady])

  // Close the suggestions dropdown when clicking outside of it or the input
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (suggestBoxRef.current?.contains(e.target as Node)) return
      if (inputRef.current?.contains(e.target as Node)) return
      setShowSuggestions(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleSelectSuggestion = (s: { label: string; lat: number; lon: number }) => {
    skipNextSuggestFetch.current = true
    setSuggestions([])
    setShowSuggestions(false)
    setManualAdjustmentEnabled(false) // Lock pin position on new selection, same as a normal search
    onDireccionChange(s.label)
    onLocationChange(s.lat, s.lon)

    if (leafletMapRef.current) {
      leafletMapRef.current.setView([s.lat, s.lon], ZOOM_WITH_PIN)
    }
    if (leafletMarkerRef.current) {
      leafletMarkerRef.current.setLatLng([s.lat, s.lon])
      if (!leafletMapRef.current.hasLayer(leafletMarkerRef.current)) {
        leafletMarkerRef.current.addTo(leafletMapRef.current)
      }
    }
  }

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
    const fullQuery = `${queryStr}, Argentina`
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
      const newLat = pos.lat()
      const newLng = pos.lng()
      onLocationChange(newLat, newLng)
      performReverseGeocode(newLat, newLng)
    })

    // Map click places/updates marker (only if manual adjustment is enabled)
    map.addListener('click', (e: any) => {
      if (!manualAdjustmentRef.current) return
      const newLat = e.latLng.lat()
      const newLng = e.latLng.lng()
      marker.setPosition({ lat: newLat, lng: newLng })
      marker.setVisible(true)
      onLocationChange(newLat, newLng)
      performReverseGeocode(newLat, newLng)
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
      performReverseGeocode(latlng.lat, latlng.lng)
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
      performReverseGeocode(newLat, newLng)
    })

    return () => {
      map.remove()
    }
  }, [leafletReady])

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
        <input
          className="form-input"
          type="text"
          placeholder="Calle y altura, ej: Av. Colón 123"
          value={direccion}
          onChange={(e) => onDireccionChange(e.target.value)}
        />
        <span className="form-helper">
          El mapa interactivo no está disponible. Podés ingresar tu dirección de consultorio en el campo de texto.
        </span>
      </div>
    )
  }

  // With Google Maps configured, its native Places widget already renders its own dropdown —
  // our custom one is Nominatim-only so the two never show up at the same time.
  const showCustomSuggestions = !mapsReady && showSuggestions && (suggestLoading || suggestions.length > 0)

  return (
    <div className="form-group" style={{ gridColumn: 'span 2' }}>
      <label className="form-label form-label--required">Domicilio de Atención</label>
      <div style={{ marginBottom: 'var(--space-2)' }}>
        <div style={{ position: 'relative' }}>
          <input
            ref={inputRef}
            className="form-input"
            type="text"
            placeholder="Calle y altura, ej: Av. Colón 123"
            value={direccion}
            onChange={(e) => onDireccionChange(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={() => { if (suggestions.length > 0) setShowSuggestions(true) }}
            autoComplete="off"
            style={{ width: '100%' }}
          />
          {showCustomSuggestions && (
            <div
              ref={suggestBoxRef}
              style={{
                // Leaflet's own controls (.leaflet-top/.leaflet-bottom) use z-index up to 1000 and
                // aren't trapped in a local stacking context by .leaflet-container, so they leak
                // above lower z-index siblings — this has to clear that.
                position: 'absolute', top: 'calc(100% + 4px)', left: 0, right: 0, zIndex: 1001,
                backgroundColor: 'var(--color-surface)', border: '1.5px solid var(--color-border)',
                borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-md, 0 4px 12px rgba(0,0,0,0.12))',
                maxHeight: '220px', overflowY: 'auto',
              }}
            >
              {suggestLoading && suggestions.length === 0 ? (
                <div style={{ padding: '10px 12px', fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
                  Buscando direcciones...
                </div>
              ) : (
                suggestions.map((s, i) => (
                  <div
                    key={`${s.lat}-${s.lon}-${i}`}
                    onClick={() => handleSelectSuggestion(s)}
                    style={{
                      padding: '9px 12px', fontSize: 'var(--text-sm)', cursor: 'pointer',
                      display: 'flex', alignItems: 'center', gap: '8px',
                      borderBottom: i < suggestions.length - 1 ? '1px solid var(--color-border)' : 'none',
                    }}
                    onMouseDown={(e) => e.preventDefault()}
                  >
                    <IconMapPin size={13} />
                    <span>{s.label}</span>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
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
          Escribí la dirección: te van a aparecer sugerencias para seleccionar.
        </span>
      )}
    </div>
  )
}
