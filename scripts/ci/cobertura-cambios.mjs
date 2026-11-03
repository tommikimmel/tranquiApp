#!/usr/bin/env node
// Cobertura de los cambios para el CI (Fase 10): de las líneas de Java nuevas o tocadas por el PR (solo src/main),
// qué parte ejecutan los tests según los reportes de JaCoCo de cada módulo. Falla por debajo del mínimo (90 %).
// Las líneas que JaCoCo no instrumenta (comentarios, imports, llaves, declaraciones) no cuentan.
//
// Uso: node scripts/ci/cobertura-cambios.mjs <rama-base> [minimo]   (ej. origin/develop 90)
// Requiere haber corrido antes los tests con Maven (genera <módulo>/target/site/jacoco/jacoco.xml).

import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const base = process.argv[2] || 'origin/develop'
const minimo = Number(process.argv[3] || 90)

const git = (...args) => execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })

/** Líneas agregadas o modificadas por archivo, a partir del diff unificado sin contexto. */
export function lineasCambiadas(diff) {
  const porArchivo = new Map()
  let actual = null
  for (const linea of diff.split('\n')) {
    const archivo = linea.match(/^\+\+\+ b\/(.+)$/)
    if (archivo) {
      actual = archivo[1]
      continue
    }
    const tramo = linea.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/)
    if (tramo && actual) {
      const desde = Number(tramo[1])
      const cantidad = tramo[2] === undefined ? 1 : Number(tramo[2])
      const set = porArchivo.get(actual) || new Set()
      for (let i = 0; i < cantidad; i++) set.add(desde + i)
      porArchivo.set(actual, set)
    }
  }
  return porArchivo
}

/** De un jacoco.xml: por archivo fuente ("com/tranqui/x/Foo.java"), las líneas instrumentadas y si se ejecutaron. */
export function lineasCubiertas(xml) {
  const resultado = new Map()
  const paquetes = xml.matchAll(/<package name="([^"]+)">([\s\S]*?)<\/package>/g)
  for (const [, paquete, cuerpo] of paquetes) {
    for (const [, archivo, lineas] of cuerpo.matchAll(/<sourcefile name="([^"]+)">([\s\S]*?)<\/sourcefile>/g)) {
      const mapa = new Map()
      for (const [, nr, mi, ci] of lineas.matchAll(/<line nr="(\d+)" mi="(\d+)" ci="(\d+)"/g)) {
        if (Number(mi) + Number(ci) > 0) mapa.set(Number(nr), Number(ci) > 0)
      }
      resultado.set(`${paquete}/${archivo}`, mapa)
    }
  }
  return resultado
}

function principal() {
  const diff = git('diff', '-U0', '--diff-filter=AM', `${base}...HEAD`, '--', '*/src/main/java/*.java')
  const cambios = lineasCambiadas(diff)
  if (!cambios.size) {
    console.log('El PR no modifica código Java de producción.')
    return 0
  }

  let total = 0
  let cubiertas = 0
  const faltantes = []
  for (const [archivo, lineas] of cambios) {
    const [modulo, relativo] = archivo.split('/src/main/java/')
    const reporte = path.join(ROOT, modulo, 'target/site/jacoco/jacoco.xml')
    if (!fs.existsSync(reporte)) {
      console.log(`Sin reporte de cobertura para ${modulo} (¿corrieron sus tests?).`)
      continue
    }
    const instrumentadas = lineasCubiertas(fs.readFileSync(reporte, 'utf8')).get(relativo)
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
    console.log('Las líneas que cambia el PR no son ejecutables (comentarios, imports o declaraciones).')
    return 0
  }
  const porcentaje = (100 * cubiertas) / total
  console.log(`Cobertura de los cambios: ${cubiertas}/${total} líneas (${porcentaje.toFixed(1)} %), mínimo ${minimo} %.`)
  if (faltantes.length) {
    console.log('Líneas cambiadas sin cubrir:')
    faltantes.forEach((f) => console.log(f))
  }
  return porcentaje + 1e-9 >= minimo ? 0 : 1
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('cobertura-cambios.mjs')) {
  process.exit(principal())
}
