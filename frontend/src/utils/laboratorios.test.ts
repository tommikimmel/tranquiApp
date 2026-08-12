import { describe, expect, it } from 'vitest'
import {
  LABORATORIOS_ARGENTINA,
  normalizeLaboratorioKey,
  searchLaboratorios,
  computeLaboratorioStats,
} from './laboratorios'

describe('normalizeLaboratorioKey', () => {
  it('lowercases and trims', () => {
    expect(normalizeLaboratorioKey('  Bagó  ')).toBe(normalizeLaboratorioKey('bago'))
  })

  it('treats accented and unaccented spellings as equal', () => {
    expect(normalizeLaboratorioKey('Bágo')).toBe(normalizeLaboratorioKey('Bago'))
  })

  it('treats v and b as the same letter (Bago vs Vago)', () => {
    expect(normalizeLaboratorioKey('Bago')).toBe(normalizeLaboratorioKey('Vago'))
  })

  it('combines accent and b/v differences (Bágo vs Vago)', () => {
    expect(normalizeLaboratorioKey('Bágo')).toBe(normalizeLaboratorioKey('Vago'))
  })

  it('collapses repeated internal whitespace', () => {
    expect(normalizeLaboratorioKey('Roche   Argentina')).toBe(normalizeLaboratorioKey('Roche Argentina'))
  })

  it('returns an empty string for empty input', () => {
    expect(normalizeLaboratorioKey('   ')).toBe('')
  })
})

describe('searchLaboratorios', () => {
  it('returns an exact match first', () => {
    const results = searchLaboratorios('Bagó')
    expect(results[0]).toBe('Bagó')
  })

  it('finds a known lab despite a b/v typo', () => {
    const results = searchLaboratorios('Vago')
    expect(results).toContain('Bagó')
  })

  it('finds a known lab despite a missing accent', () => {
    const results = searchLaboratorios('Bago')
    expect(results).toContain('Bagó')
  })

  it('matches by substring for multi-word lab names', () => {
    const results = searchLaboratorios('montpellier')
    expect(results.some((r) => r.toLowerCase().includes('montpellier'))).toBe(true)
  })

  it('tolerates a small typo (one extra letter)', () => {
    const results = searchLaboratorios('Gadorr')
    expect(results).toContain('Gador')
  })

  it('returns an empty array for an empty query', () => {
    expect(searchLaboratorios('')).toEqual([])
    expect(searchLaboratorios('   ')).toEqual([])
  })

  it('returns an empty array when nothing is close enough', () => {
    const results = searchLaboratorios('xxxxxxxxxxzzzzzzzzzz')
    expect(results).toEqual([])
  })

  it('respects the limit parameter', () => {
    const results = searchLaboratorios('a', 3)
    expect(results.length).toBeLessThanOrEqual(3)
  })

  it('every catalog entry can find itself', () => {
    // Sanity check over the whole curated list: normalizing + searching a lab's own exact name
    // should always surface it, catching any accidental duplicate/malformed entry.
    for (const lab of LABORATORIOS_ARGENTINA) {
      expect(searchLaboratorios(lab, 20)).toContain(lab)
    }
  })
})

describe('computeLaboratorioStats', () => {
  it('returns an empty list when there are no laboratorios', () => {
    expect(computeLaboratorioStats([])).toEqual([])
    expect(computeLaboratorioStats([{ laboratorios: [] }])).toEqual([])
  })

  it('groups spelling variants of the same lab under one entry', () => {
    const stats = computeLaboratorioStats([
      { laboratorios: ['Bagó'] },
      { laboratorios: ['Bago', 'Vago'] },
      { laboratorios: ['Bágo'] },
    ])
    expect(stats).toHaveLength(1)
    expect(stats[0].count).toBe(4)
  })

  it('keeps genuinely different laboratorios separate', () => {
    const stats = computeLaboratorioStats([
      { laboratorios: ['Bagó'] },
      { laboratorios: ['Gador'] },
      { laboratorios: ['Gador'] },
    ])
    expect(stats).toHaveLength(2)
    const gador = stats.find((s) => s.label === 'Gador')
    expect(gador?.count).toBe(2)
  })

  it('sorts by count descending', () => {
    const stats = computeLaboratorioStats([
      { laboratorios: ['Gador'] },
      { laboratorios: ['Bagó'] },
      { laboratorios: ['Bagó'] },
      { laboratorios: ['Bagó'] },
    ])
    expect(stats[0].label).toBe('Bagó')
    expect(stats[0].count).toBe(3)
  })

  it('picks the most frequent original spelling as the display label', () => {
    const stats = computeLaboratorioStats([
      { laboratorios: ['bago'] },
      { laboratorios: ['bago'] },
      { laboratorios: ['Bagó'] },
    ])
    expect(stats[0].label).toBe('bago')
  })

  it('ignores blank/whitespace-only entries and missing laboratorios fields', () => {
    const stats = computeLaboratorioStats([
      { laboratorios: ['', '   ', 'Bagó'] },
      {},
      { laboratorios: null },
    ])
    expect(stats).toHaveLength(1)
    expect(stats[0].count).toBe(1)
  })
})
