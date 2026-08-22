async function waitFor(url: string, label: string, timeoutMs = 60000) {
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
  await waitFor('http://localhost:8081/api/health', 'backend')
  await waitFor('http://localhost:3000/', 'frontend')
}
