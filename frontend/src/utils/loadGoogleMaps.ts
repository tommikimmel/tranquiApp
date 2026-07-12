// Loads the Google Maps JS API (with the Places library) exactly once, even if
// multiple components mounting around the app all ask for it concurrently.
let loadingPromise: Promise<void> | null = null

export function loadGoogleMapsScript(apiKey: string): Promise<void> {
  if ((window as any).google?.maps?.places) {
    return Promise.resolve()
  }
  if (loadingPromise) return loadingPromise

  loadingPromise = new Promise((resolve, reject) => {
    const scriptId = 'google-maps-script'
    const existing = document.getElementById(scriptId) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', () => resolve())
      existing.addEventListener('error', () => reject(new Error('No se pudo cargar Google Maps')))
      return
    }
    const script = document.createElement('script')
    script.id = scriptId
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&libraries=places&loading=async`
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('No se pudo cargar Google Maps'))
    document.body.appendChild(script)
  })

  return loadingPromise
}
