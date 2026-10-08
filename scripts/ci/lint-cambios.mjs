#!/usr/bin/env node
// Lint incremental para el CI: compara, archivo por archivo, los problemas de ESLint en la rama base
// y en el PR, solo para los archivos del frontend que el PR modifica. Falla si el PR agrega
// problemas; los que ya existían no lo bloquean (se limpian aparte).
//
// Uso: node scripts/ci/lint-cambios.mjs <rama-base>   (ej. origin/develop)

import { execFileSync, spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const FRONT = path.join(ROOT, 'frontend')
const base = process.argv[2] || 'origin/develop'

const git = (...args) => execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })

const archivos = git('diff', '--name-only', '--diff-filter=AM', `${base}...HEAD`)
  .split('\n')
  .filter(f => /^frontend\/src\/.*\.(ts|tsx)$/.test(f))

if (!archivos.length) {
  console.log('El PR no modifica archivos TypeScript del frontend.')
  process.exit(0)
}

// Cantidad de problemas de ESLint de un contenido, usando la ruta real para aplicar la config.
function problemas(contenido, archivo) {
  const r = spawnSync('npx', ['eslint', '--format', 'json', '--stdin', '--stdin-filename', archivo], {
    cwd: FRONT, input: contenido, encoding: 'utf8', maxBuffer: 50 * 1024 * 1024,
  })
  const salida = JSON.parse(r.stdout || '[]')
  return salida.reduce((n, f) => n + f.errorCount + f.warningCount, 0)
}

let empeoraron = 0
for (const f of archivos) {
  const rel = path.relative('frontend', f)
  const antes = (() => {
    try { return problemas(git('show', `${base}:${f}`), rel) } catch { return 0 } // archivo nuevo
  })()
  const despues = problemas(git('show', `HEAD:${f}`), rel)
  const marca = despues > antes ? 'EMPEORA' : 'ok'
  console.log(`${marca.padEnd(8)} ${f}: ${antes} -> ${despues}`)
  if (despues > antes) empeoraron++
}

if (empeoraron) {
  console.error(`\n${empeoraron} archivo(s) con problemas de lint nuevos. Corré "npx eslint <archivo>" en frontend/ para verlos.`)
  process.exit(1)
}
console.log('\nSin problemas de lint nuevos.')
