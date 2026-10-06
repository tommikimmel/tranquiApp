import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Los tests de flujos largos con user-event (CheckoutFlow, AgendaView) tardan más de los 5 s
    // por defecto cuando la máquina está cargada y fallaban por timeout, no por un bug.
    testTimeout: 20000,
    exclude: ['**/node_modules/**', '**/dist/**', './e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/**/*.d.ts',
        'src/test/**',
      ],
    },
  },
})
