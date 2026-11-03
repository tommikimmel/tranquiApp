#!/usr/bin/env node
// Cobertura de los cambios del frontend para el CI (Fase 10, T4), igual que cobertura-cambios.mjs para Java: de las
// líneas nuevas o tocadas por el PR en frontend/src (sin los tests), qué parte ejecutan los tests de Vitest según
// frontend/coverage/lcov.info. Falla por debajo del mínimo (90 %). Las líneas que v8 no instrumenta no cuentan.
//
// Uso: node scripts/ci/cobertura-cambios-frontend.mjs <rama-base> [minimo]   (ej. origin/develop 90)
// Requiere antes: cd frontend && npx vitest run --coverage --coverage.reporter=lcov

import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { lineasCambiadas } from './cobertura-cambios.mjs'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const FRONTEND = path.join(ROOT, 'frontend')
const base = process.argv[2] || 'origin/develop'
const minimo = Number(process.argv[3] || 90)

/** De un lcov.info: por archivo (relativo a frontend, con /), las líneas instrumentadas y si se ejecutaron. */
export function lineasCubiertasLcov(lcov, raiz = FRONTEND) {
  const resultado = new Map()
  let actual = null
  for (const linea of lcov.split(/\r?\n/)) {
    if (linea.startsWith('SF:')) {
      const archivo = linea.slice(3)
      const relativo = path.isAbsolute(archivo) ? path.relative(raiz, archivo) : archivo
      actual = new Map()
      resultado.set(relativo.split(path.sep).join('/'), actual)
    } else if (linea.startsWith('DA:') && actual) {
      const [nr, veces] = linea.slice(3).split(',')
      actual.set(Number(nr), Number(veces) > 0)
    }
  }
  return resultado
}

function principal() {
  const diff = execFileSync('git', ['diff', '-U0', '--diff-filter=AM', `${base}...HEAD`, '--',
    'frontend/src/*.ts', 'frontend/src/*.tsx', ':!frontend/src/**/*.test.*', ':!frontend/src/test/**'],
  { cwd: ROOT, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
  const cambios = lineasCambiadas(diff)
  if (!cambios.size) {
    console.log('El PR no modifica código del frontend.')
    return 0
  }
  const reporte = path.join(FRONTEND, 'coverage/lcov.info')
  if (!fs.existsSync(reporte)) {
    console.log('Sin frontend/coverage/lcov.info (¿corrió vitest con --coverage.reporter=lcov?).')
    return 1
  }
  const cubiertasPorArchivo = lineasCubiertasLcov(fs.readFileSync(reporte, 'utf8'))

  let total = 0
  let cubiertas = 0
  const faltantes = []
  for (const [archivo, lineas] of cambios) {
    // Sin entrada en el lcov: excluido de la cobertura (main.tsx, .d.ts) o sin código ejecutable.
    const instrumentadas = cubiertasPorArchivo.get(archivo.replace(/^frontend\//, ''))
    if (!instrumentadas) continue
    const sinCubrir = []
    for (const n of lineas) {
      if (!instrumentadas.has(n)) continue
      total++
      if (instrumentadas.get(n)) cubiertas++
      else sinCubrir.push(n)
    }
    if (sinCubrir.length) faltantes.push(`  ${archivo}: líneas ${sinCubrir.sort((a, b) => a - b).join(', ')}`)
  }

  if (!total) {
    console.log('Las líneas que cambia el PR en el frontend no son ejecutables.')
    return 0
  }
  const porcentaje = (100 * cubiertas) / total
  console.log(`Cobertura de los cambios del frontend: ${cubiertas}/${total} líneas (${porcentaje.toFixed(1)} %), mínimo ${minimo} %.`)
  if (faltantes.length) {
    console.log('Líneas cambiadas sin cubrir:')
    faltantes.forEach((f) => console.log(f))
  }
  return porcentaje + 1e-9 >= minimo ? 0 : 1
}

if (process.argv[1]?.endsWith('cobertura-cambios-frontend.mjs')) {
  process.exit(principal())
}
