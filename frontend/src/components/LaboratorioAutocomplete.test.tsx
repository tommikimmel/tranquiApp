import { useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import LaboratorioAutocomplete from './LaboratorioAutocomplete'

// LaboratorioAutocomplete is a controlled input (value comes from props) — this thin wrapper
// mirrors how it's actually wired up in PrescriptionView (parent owns the state), since typing
// into a purely-controlled input with a fixed prop value wouldn't visibly update otherwise.
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
  it('shows no dropdown before the user types anything', () => {
    render(<ControlledWrapper />)
    expect(screen.queryByText('Bagó')).not.toBeInTheDocument()
  })

  it('suggests the matching laboratorio while typing, even with a typo', async () => {
    const user = userEvent.setup()
    render(<ControlledWrapper />)

    await user.type(screen.getByPlaceholderText('Ej: Bagó'), 'Vago')

    await waitFor(() => expect(screen.getByText('Bagó')).toBeInTheDocument())
  })

  it('fills the input and closes the dropdown when a suggestion is clicked', async () => {
    const user = userEvent.setup()
    const onChangeSpy = vi.fn()
    render(<ControlledWrapper onChangeSpy={onChangeSpy} />)

    const input = screen.getByPlaceholderText('Ej: Bagó') as HTMLInputElement
    await user.type(input, 'Gador')
    await waitFor(() => expect(screen.getByText('Gador')).toBeInTheDocument())

    await user.click(screen.getByText('Gador'))

    expect(input.value).toBe('Gador')
    expect(onChangeSpy).toHaveBeenLastCalledWith('Gador')
    expect(screen.queryByText('Gador', { selector: 'div' })).not.toBeInTheDocument()
  })

  it('allows free text that does not match any known laboratorio', async () => {
    const user = userEvent.setup()
    const onChangeSpy = vi.fn()
    render(<ControlledWrapper onChangeSpy={onChangeSpy} />)

    const input = screen.getByPlaceholderText('Ej: Bagó') as HTMLInputElement
    await user.type(input, 'Laboratorio Casero XYZ')

    expect(input.value).toBe('Laboratorio Casero XYZ')
    expect(onChangeSpy).toHaveBeenLastCalledWith('Laboratorio Casero XYZ')
  })
})
