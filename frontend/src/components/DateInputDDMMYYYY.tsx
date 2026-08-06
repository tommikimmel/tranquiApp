import { useEffect, useRef, useState } from 'react'

// Native <input type="date"> renders in whatever format the browser/OS decides — Chrome/Edge
// honor the page's `lang` attribute, but Firefox and Safari always use the OS locale regardless,
// which is how DD/MM/AAAA kept showing as MM/DD/AAAA for users on those browsers. This is a
// self-contained masked text input that guarantees DD/MM/AAAA everywhere, while still exchanging
// plain ISO (yyyy-mm-dd) strings with the rest of the app so call sites don't need to change.
interface DateInputDDMMYYYYProps {
  id?: string
  value: string // ISO yyyy-mm-dd, or ''
  onChange: (isoDate: string) => void
  min?: string // ISO yyyy-mm-dd
  max?: string // ISO yyyy-mm-dd
  className?: string
  style?: React.CSSProperties
  required?: boolean
}

function isoToDisplay(iso: string): string {
  const v = (iso || '').trim()
  if (!v) return ''
  const isoMatch = v.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (isoMatch) {
    const [, y, m, d] = isoMatch
    return `${d}/${m}/${y}`
  }
  // Defensive: some backend DTOs (e.g. MedicoDto.fechaNacimiento) serialize LocalDate as
  // dd/MM/yyyy directly rather than ISO (see DateConfig.java's global LocalDate serializer) —
  // if a value already arrives in display format, use it as-is instead of blanking the field.
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(v)) return v
  return ''
}

function displayToIso(display: string): string | null {
  const match = display.match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (!match) return null
  const [, d, m, y] = match
  const day = Number(d)
  const month = Number(m)
  const year = Number(y)
  if (month < 1 || month > 12) return null
  const daysInMonth = new Date(year, month, 0).getDate()
  if (day < 1 || day > daysInMonth) return null
  return `${y}-${m}-${d}`
}

// Auto-inserts the "/" separators as the user types digits, e.g. "10102006" -> "10/10/2006"
function maskDigits(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8)
  if (digits.length > 4) return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`
  if (digits.length > 2) return `${digits.slice(0, 2)}/${digits.slice(2)}`
  return digits
}

// How many digits sit to the left of the cursor in the raw (pre-mask) input value.
function digitsBeforeCursor(raw: string, cursorPos: number): number {
  return raw.slice(0, cursorPos).replace(/\D/g, '').length
}

// Where the cursor should land in the masked string so it stays right after the digit the
// user just typed, instead of jumping to the end (the default browser behavior whenever a
// controlled input's .value is reassigned — the classic bug with DIY masked inputs).
function cursorPosForDigitCount(masked: string, digitCount: number): number {
  if (digitCount <= 0) return 0
  let seen = 0
  for (let i = 0; i < masked.length; i++) {
    if (/\d/.test(masked[i])) {
      seen++
      if (seen === digitCount) return i + 1
    }
  }
  return masked.length
}

export default function DateInputDDMMYYYY({ id, value, onChange, min, max, className, style, required }: DateInputDDMMYYYYProps) {
  const [text, setText] = useState(() => isoToDisplay(value))
  const inputRef = useRef<HTMLInputElement>(null)

  // Keep the displayed text in sync when the ISO value changes from outside (e.g. loading a
  // saved profile), but don't fight the user's own keystrokes while they're actively typing.
  useEffect(() => {
    setText(isoToDisplay(value))
  }, [value])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value
    const cursorPos = e.target.selectionStart ?? raw.length
    const digitCount = digitsBeforeCursor(raw, cursorPos)
    const masked = maskDigits(raw)
    setText(masked)

    // Restore the caret instead of letting the browser snap it to the end of the field —
    // without this, correcting a single digit in an already-complete date (e.g. fixing just
    // the year) is nearly impossible because every keystroke bounces the cursor to the end.
    const newCursorPos = cursorPosForDigitCount(masked, digitCount)
    const el = e.target
    requestAnimationFrame(() => {
      el.setSelectionRange(newCursorPos, newCursorPos)
    })

    if (masked === '') {
      onChange('')
      return
    }
    const iso = displayToIso(masked)
    if (!iso) return
    if (min && iso < min) return
    if (max && iso > max) return
    onChange(iso)
  }

  return (
    <input
      ref={inputRef}
      id={id}
      type="text"
      inputMode="numeric"
      autoComplete="bday"
      placeholder="DD/MM/AAAA"
      maxLength={10}
      className={className}
      style={style}
      value={text}
      onChange={handleChange}
      required={required}
    />
  )
}
