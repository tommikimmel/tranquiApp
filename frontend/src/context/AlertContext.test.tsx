import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AlertProvider, useAlert } from './AlertContext'

function TestConsumer() {
  const { showAlert, alerts, removeAlert } = useAlert()
  return (
    <div>
      <button onClick={() => showAlert('Operación realizada con éxito')}>show-default</button>
      <button onClick={() => showAlert('Algo salió mal', 'error')}>show-error</button>
      <button onClick={() => showAlert('Cuidado', 'warning', 'Título custom')}>show-warning-custom-title</button>
      <button onClick={() => alerts[0] && removeAlert(alerts[0].id)}>remove-first</button>
      <span data-testid="count">{alerts.length}</span>
    </div>
  )
}

describe('AlertContext', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('useAlert throws when used outside an AlertProvider', () => {
    // Swallow the expected React error-boundary console noise for this one assertion.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<TestConsumer />)).toThrow('useAlert must be used within an AlertProvider')
    spy.mockRestore()
  })

  it('shows a default-success alert with the default title and role="alert"', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <AlertProvider>
        <TestConsumer />
      </AlertProvider>
    )

    await user.click(screen.getByText('show-default'))

    const alertEl = screen.getByRole('alert')
    expect(alertEl).toHaveTextContent('¡Operación exitosa!')
    expect(alertEl).toHaveTextContent('Operación realizada con éxito')
    expect(alertEl.className).toContain('tranqui-toast--success')
  })

  it('shows an error alert with a custom message and the default error title', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <AlertProvider>
        <TestConsumer />
      </AlertProvider>
    )

    await user.click(screen.getByText('show-error'))

    const alertEl = screen.getByRole('alert')
    expect(alertEl).toHaveTextContent('Atención requerida')
    expect(alertEl).toHaveTextContent('Algo salió mal')
    expect(alertEl.className).toContain('tranqui-toast--error')
  })

  it('accepts an explicit custom title overriding the type default', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <AlertProvider>
        <TestConsumer />
      </AlertProvider>
    )

    await user.click(screen.getByText('show-warning-custom-title'))

    const alertEl = screen.getByRole('alert')
    expect(alertEl).toHaveTextContent('Título custom')
    expect(alertEl).not.toHaveTextContent('Aviso importante')
  })

  it('stacks multiple alerts and each can be dismissed independently via removeAlert', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <AlertProvider>
        <TestConsumer />
      </AlertProvider>
    )

    await user.click(screen.getByText('show-default'))
    await user.click(screen.getByText('show-error'))

    expect(screen.getByTestId('count')).toHaveTextContent('2')
    expect(screen.getAllByRole('alert')).toHaveLength(2)

    await user.click(screen.getByText('remove-first'))

    expect(screen.getByTestId('count')).toHaveTextContent('1')
    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  it('auto-dismisses an alert after 5 seconds', async () => {
    render(
      <AlertProvider>
        <TestConsumer />
      </AlertProvider>
    )
    const user = userEvent.setup({ delay: null, advanceTimers: vi.advanceTimersByTime })

    await user.click(screen.getByText('show-default'))
    expect(screen.getByTestId('count')).toHaveTextContent('1')

    vi.advanceTimersByTime(5000)

    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('0'))
  })

  it('can dismiss a toast via its close button', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <AlertProvider>
        <TestConsumer />
      </AlertProvider>
    )

    await user.click(screen.getByText('show-default'))
    await user.click(screen.getByLabelText('Cerrar notificación'))

    expect(screen.getByTestId('count')).toHaveTextContent('0')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
