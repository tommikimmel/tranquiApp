import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import HelpFaqModal from './HelpFaqModal'
import { FAQ_ITEMS } from '../constants/helpFaq'

describe('HelpFaqModal', () => {
  it('shows only the selected audience questions and switches tabs', async () => {
    render(<HelpFaqModal onClose={() => {}} />)

    expect(screen.getByRole('button', { name: /¿Cómo me creo una cuenta\?/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /¿Cómo me registro como profesional\?/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'Soy profesional' }))
    expect(screen.getByRole('button', { name: /¿Cómo me registro como profesional\?/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /¿Cómo me creo una cuenta\?/ })).not.toBeInTheDocument()
  })

  it('opens on the professional tab when asked to', () => {
    render(<HelpFaqModal onClose={() => {}} defaultAudience="profesional" />)
    expect(screen.getByRole('tab', { name: 'Soy profesional' })).toHaveAttribute('aria-selected', 'true')
  })

  it('filters by search text ignoring accents and opens the single match', async () => {
    render(<HelpFaqModal onClose={() => {}} />)

    await userEvent.type(screen.getByRole('searchbox'), 'videollamada')
    expect(screen.getAllByRole('button', { name: /videollamada/i })).toHaveLength(1)
    expect(screen.getByText(/se activa 10 minutos antes/)).toBeInTheDocument()

    await userEvent.clear(screen.getByRole('searchbox'))
    await userEvent.type(screen.getByRole('searchbox'), 'xyz-sin-resultados')
    expect(screen.getByText(/No encontramos preguntas con esa búsqueda/)).toBeInTheDocument()
  })

  it('plays the explanatory video with Spanish subtitles', async () => {
    const { container } = render(<HelpFaqModal onClose={() => {}} />)

    await userEvent.click(screen.getByRole('button', { name: /Cerré la ventana de pago/ }))
    await userEvent.click(screen.getByRole('button', { name: /Ver video explicativo/ }))

    const video = container.ownerDocument.querySelector('video')
    expect(video).toHaveAttribute('src', '/videos/V-P07.webm')
    const track = video?.querySelector('track')
    expect(track).toHaveAttribute('src', '/videos/V-P07.vtt')
    expect(track).toHaveAttribute('srclang', 'es')

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar video' }))
    expect(container.ownerDocument.querySelector('video')).toBeNull()
  })

  it('closes with the close button', async () => {
    const onClose = vi.fn()
    render(<HelpFaqModal onClose={onClose} />)
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar ayuda' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('has unique question ids', () => {
    const ids = FAQ_ITEMS.map(i => i.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('only shows the video button on questions that have a video', async () => {
    render(<HelpFaqModal onClose={() => {}} />)
    await userEvent.click(screen.getByRole('button', { name: /¿Cómo cancelo un turno\?/ }))
    expect(screen.queryByRole('button', { name: /Ver video explicativo/ })).not.toBeInTheDocument()
  })
})
