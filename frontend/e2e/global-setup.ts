import { API } from './soporte'

// En el CI el stack arranca de cero (imágenes recién construidas y npm install del frontend): hasta 10 min.
async function waitFor(url: string, label: string, timeoutMs = 600000) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url)
      if (res.ok) {
        console.log(`[global-setup] ${label} ready (${url})`)
        return
      }
    } catch {
      // still starting up
    }
    await new Promise((r) => setTimeout(r, 1500))
  }
  throw new Error(`[global-setup] ${label} did not become ready within ${timeoutMs}ms (${url})`)
}

export default async function globalSetup() {
  await waitFor(`${API}/api/health`, 'api')
  await waitFor(process.env.E2E_URL ?? 'http://localhost:5173/', 'frontend')
}
