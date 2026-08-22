import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import LaboratorioAutocomplete from './LaboratorioAutocomplete'
import { LABORATORIOS_ARGENTINA } from '../utils/laboratorios'

// LaboratorioAutocomplete is a controlled <select> (value comes from props) — this thin
// wrapper mirrors how it's actually wired up in PrescriptionView (parent owns the state).
function ControlledWrapper({ onChangeSpy }: { onChangeSpy?: (v: string) => void }) {
  const [value, setValue] = useState('')
  return (
    <LaboratorioAutocomplete
      value={value}
      onChange={(v) => {
        setValue(v)
        onChangeSpy?.(v)
      }}
    />
  )
}

describe('LaboratorioAutocomplete', () => {
  it('renders a placeholder option plus every laboratorio in the catalog', () => {
    render(<ControlledWrapper />)

    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('')
    expect(screen.getByRole('option', { name: 'Seleccionar laboratorio...' })).toBeInTheDocument()
    expect(select.options.length).toBe(LABORATORIOS_ARGENTINA.length + 1)
  })

  it('selecting a laboratorio calls onChange and updates the controlled value', async () => {
    const user = userEvent.setup()
    const onChangeSpy = vi.fn()
    render(<ControlledWrapper onChangeSpy={onChangeSpy} />)

    const select = screen.getByRole('combobox') as HTMLSelectElement
    await user.selectOptions(select, 'Gador')

    expect(select.value).toBe('Gador')
    expect(onChangeSpy).toHaveBeenLastCalledWith('Gador')
  })

  it('falls back to the empty option when the controlled value is not in the catalog', () => {
    render(
      <LaboratorioAutocomplete value="Laboratorio Casero XYZ" onChange={() => {}} />
    )

    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('')
  })

  it('reflects a value already present in the catalog as selected', () => {
    render(<LaboratorioAutocomplete value="Bagó" onChange={() => {}} />)

    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('Bagó')
  })
})
