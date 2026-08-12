// Curated list of pharmaceutical laboratories known to operate/sell in Argentina — national
// labs plus multinationals with a strong local presence. This is a best-effort reference list
// (not pulled from an official live registry — QBI2's medicamento catalog doesn't expose
// laboratorio, and ANMAT's public Vademécum is a static bulk CSV, not a queryable API), used to
// power the autocomplete/search below. A médico can still type a lab that isn't listed here;
// nothing blocks free text.
export const LABORATORIOS_ARGENTINA: string[] = [
  'Abbott',
  'AbbVie',
  'Amgen',
  'Andrómaco',
  'Astrazeneca',
  'Bagó',
  'Baliarda',
  'Bausch + Lomb',
  'Bayer',
  'Beta',
  'Bernabó',
  'Biotenk',
  'Boehringer Ingelheim',
  'Bristol Myers Squibb',
  'Casasco',
  'Chemo Argentina',
  'Chiesi',
  'Craveri',
  'Denver Farma',
  'Domínguez',
  'Duncan',
  'Eli Lilly',
  'Elea Phoenix',
  'Elvetium',
  'Eurofarma Argentina',
  'Fabra',
  'Fada Pharma',
  'Ferrer',
  'Filaxis',
  'Finadiet',
  'Fresenius Kabi',
  'Gador',
  'Gadea Pharma',
  'Gemabiotech',
  'Glenmark',
  'GlaxoSmithKline (GSK)',
  'Grünenthal',
  'Instituto Massone',
  'Investi Farma',
  'Ipsen',
  'Ivax Argentina',
  'Janssen (Johnson & Johnson)',
  'Kern Pharma',
  'Klonal',
  'Lafedar',
  'Larjan',
  'Lazar Laboratorios',
  'Merck',
  'Menarini',
  'Microsules Argentina',
  'Montpellier (Química Montpellier)',
  'MSD',
  'Mylan / Viatris',
  'Northia',
  'Novartis',
  'Panalab',
  'Pfizer',
  'Poen',
  'Prater',
  'Química Luar',
  'Raffo',
  'Richmond',
  'Rivero',
  'Roche',
  'Roemmers',
  'Ronava',
  'Rontag',
  'Roux-Ocefa',
  'Sandoz Argentina',
  'Sanofi Argentina',
  'Savant Pharm',
  'Servier',
  'Sidus',
  'Stada',
  'Synthon Argentina',
  'Takeda',
  'Tecnofarma',
  'Temis Lostaló',
  'Teva Argentina',
  'Trb Pharma',
  'Vannier Farma',
  'Wiener Lab',
  'Zambon',
]

// Strips accents/diacritics and folds "v" into "b" — the two most common ways an Argentine
// pharma lab name gets misspelled by hand ("Bago"/"Vago", "Bágo"/"Bago") — so those variants
// group under the same canonical key instead of being treated as different laboratorios.
export function normalizeLaboratorioKey(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .normalize('NFD').replace(/\p{Mn}/gu, '')
    .replace(/v/g, 'b')
    .replace(/\s+/g, ' ')
}

// Classic edit-distance metric — how many single-character insertions/deletions/substitutions
// turn `a` into `b`. Used below to tolerate typos (missing/extra/transposed letters) that
// normalizeLaboratorioKey alone doesn't catch.
function levenshtein(a: string, b: string): number {
  const dp: number[][] = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0))
  for (let i = 0; i <= a.length; i++) dp[i][0] = i
  for (let j = 0; j <= b.length; j++) dp[0][j] = j
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1]
        : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
    }
  }
  return dp[a.length][b.length]
}

// Fuzzy-searches LABORATORIOS_ARGENTINA for the query, tolerating accents, b/v mix-ups (via
// normalizeLaboratorioKey) and small typos (via Levenshtein distance). Exact/prefix/substring
// matches always rank above typo-tolerant ones.
export function searchLaboratorios(query: string, limit = 8): string[] {
  const q = normalizeLaboratorioKey(query)
  if (!q) return []
  const maxDist = q.length <= 4 ? 1 : q.length <= 8 ? 2 : 3

  const scored = LABORATORIOS_ARGENTINA
    .map((lab) => {
      const key = normalizeLaboratorioKey(lab)
      let score: number
      if (key === q) score = 0
      else if (key.startsWith(q)) score = 1
      else if (key.includes(q)) score = 2
      else {
        const dist = levenshtein(q, key)
        score = dist <= maxDist ? 3 + dist : Infinity
      }
      return { lab, score }
    })
    .filter((s) => s.score !== Infinity)
    .sort((a, b) => a.score - b.score || a.lab.localeCompare(b.lab))

  return scored.slice(0, limit).map((s) => s.lab)
}

export interface LaboratorioStat {
  key: string
  label: string
  count: number
}

// Aggregates every laboratorio entered across a médico's emitted recetas, grouped by the
// normalized key above. The display label picked for each group is whichever original spelling
// appeared most often, so the UI still shows real, human-typed text rather than a "corrected" one.
export function computeLaboratorioStats(prescriptions: any[]): LaboratorioStat[] {
  const groups = new Map<string, Map<string, number>>()
  for (const rx of prescriptions) {
    const labs: string[] = Array.isArray(rx?.laboratorios) ? rx.laboratorios : []
    for (const rawLab of labs) {
      if (!rawLab || !rawLab.trim()) continue
      const label = rawLab.trim()
      const key = normalizeLaboratorioKey(label)
      if (!key) continue
      if (!groups.has(key)) groups.set(key, new Map())
      const variants = groups.get(key)!
      variants.set(label, (variants.get(label) || 0) + 1)
    }
  }

  const stats: LaboratorioStat[] = []
  for (const [key, variants] of groups) {
    let bestLabel = ''
    let bestCount = -1
    let total = 0
    for (const [label, count] of variants) {
      total += count
      if (count > bestCount) {
        bestCount = count
        bestLabel = label
      }
    }
    stats.push({ key, label: bestLabel, count: total })
  }
  return stats.sort((a, b) => b.count - a.count)
}
