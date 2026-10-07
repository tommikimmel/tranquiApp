import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/index.css'
import App from './App.tsx'

import { AlertProvider } from './context/AlertContext.tsx'

// A tab left open across a deploy still has the *old* index.html/main bundle in memory, which
// references lazy chunk files (e.g. CheckoutFlow-<hash>.js) by their old hash — those files no
// longer exist once the new build replaces them, so the dynamic import() 404s and throws
// "Failed to fetch dynamically imported module". Vite dispatches `vite:preloadError` for exactly
// this case; reloading once fetches the fresh index.html (pointing at the new hashes) instead of
// leaving the user stuck on a dead click. Guarded by sessionStorage so a *genuinely* broken chunk
// (offline, real 404) reloads once and then surfaces the error instead of loop-reloading forever.
const RELOAD_GUARD_KEY = 'tranqui-reload-on-preload-error'
// This module re-executes on every fresh page load (including the reload below), so clearing the
// guard here — rather than only setting it — means a *second*, unrelated stale-chunk incident
// later in the same tab session (e.g. another deploy right after) still gets its own reload
// instead of being silently swallowed by a guard left over from the first one.
sessionStorage.removeItem(RELOAD_GUARD_KEY)

window.addEventListener('vite:preloadError', () => {
  if (sessionStorage.getItem(RELOAD_GUARD_KEY)) return
  sessionStorage.setItem(RELOAD_GUARD_KEY, '1')
  window.location.reload()
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AlertProvider>
        <App />
      </AlertProvider>
    </BrowserRouter>
  </StrictMode>,
)
