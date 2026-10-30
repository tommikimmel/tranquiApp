import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AdminDashboard from './AdminDashboard'

vi.mock('../api/api', () => ({ api: { getAdminUsers: vi.fn().mockResolvedValue([]) } }))
vi.mock('../context/AlertContext', () => ({ useAlert: () => ({ showAlert: vi.fn() }) }))
vi.mock('./AdminSubscriptionsView', () => ({ default: () => <div>vista de suscripciones</div> }))
vi.mock('./AdminTicketsView', () => ({ default: () => <div>vista de tickets</div> }))
vi.mock('./AdminObservabilidadView', () => ({ default: () => <div>vista de observabilidad</div> }))

describe('AdminDashboard', () => {
  it('abre en suscripciones y la entrada Observabilidad del menú muestra su panel', async () => {
    render(<AdminDashboard currentUser={{ nombre: 'Admin', rol: 'ADMIN' }} onLogout={vi.fn()} />)

    expect(await screen.findByText('vista de suscripciones')).toBeInTheDocument()
    await userEvent.click(screen.getByText('Observabilidad'))
    expect(screen.getByText('vista de observabilidad')).toBeInTheDocument()
    expect(screen.queryByText('vista de suscripciones')).not.toBeInTheDocument()
  })
})
