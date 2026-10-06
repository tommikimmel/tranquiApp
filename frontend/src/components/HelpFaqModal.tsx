import { Fragment, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { FAQ_ITEMS, faqVideoSrc, faqVideoSubtitles, type FaqAudience, type FaqItem, type FaqVideo } from '../constants/helpFaq'
import '../styles/help.css'

function IconClose({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function IconHelp({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }}>
      <circle cx="12" cy="12" r="10" />
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

function IconPlay({ size = 14 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" style={{ width: size, height: size, display: 'inline-block', verticalAlign: 'middle' }} aria-hidden="true">
      <path d="M8 5.14v13.72a1 1 0 0 0 1.52.85l10.6-6.86a1 1 0 0 0 0-1.7L9.52 4.29A1 1 0 0 0 8 5.14z" />
    </svg>
  )
}

function IconChevron({ open }: { open: boolean }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
      style={{ width: 18, height: 18, flexShrink: 0, transition: 'transform var(--transition-fast)', transform: open ? 'rotate(180deg)' : 'none' }} aria-hidden="true">
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

// **negrita** dentro de una línea.
function renderInline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**')
      ? <strong key={i}>{part.slice(2, -2)}</strong>
      : <Fragment key={i}>{part}</Fragment>
  )
}

// Markup mínimo de constants/helpFaq.ts: párrafos separados por línea en blanco, viñetas "- " y
// listas numeradas "1. ".
function renderAnswer(answer: string): ReactNode {
  return answer.split(/\n\s*\n/).map((block, i) => {
    const lines = block.split('\n').map(l => l.trim()).filter(Boolean)
    if (lines.every(l => l.startsWith('- '))) {
      return <ul key={i}>{lines.map((l, j) => <li key={j}>{renderInline(l.slice(2))}</li>)}</ul>
    }
    if (lines.every(l => /^\d+\.\s/.test(l))) {
      return <ol key={i}>{lines.map((l, j) => <li key={j}>{renderInline(l.replace(/^\d+\.\s/, ''))}</li>)}</ol>
    }
    return <p key={i}>{renderInline(lines.join(' '))}</p>
  })
}

const normalize = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

function FaqVideoPlayer({ video, onClose }: { video: FaqVideo; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.stopPropagation(); onClose() }
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [onClose])

  return (
    <div className="help-video-overlay" role="dialog" aria-modal="true" aria-label={video.title} onClick={onClose}>
      <div className="help-video-card" onClick={e => e.stopPropagation()}>
        <div className="help-video-card__header">
          <h4 className="help-video-card__title"><IconPlay size={14} /> {video.title}</h4>
          <button onClick={onClose} className="btn btn--ghost btn--sm" aria-label="Cerrar video"><IconClose /></button>
        </div>
        {failed ? (
          <p className="help-video-card__error">
            No pudimos cargar el video. Probá de nuevo en unos minutos o escribinos desde <strong>Soporte</strong>.
          </p>
        ) : (
          <video
            ref={videoRef}
            className="help-video-card__video"
            src={faqVideoSrc(video)}
            controls
            autoPlay
            playsInline
            preload="metadata"
            onLoadedMetadata={() => {
              if (video.start && videoRef.current) videoRef.current.currentTime = video.start
            }}
            onError={() => setFailed(true)}
          >
            <track kind="subtitles" src={faqVideoSubtitles(video)} srcLang="es" label="Español" default />
          </video>
        )}
        <p className="help-video-card__footer">¿Te quedó alguna duda? Escribinos desde <strong>Soporte</strong>.</p>
      </div>
    </div>
  )
}

function FaqEntry({ item, open, onToggle, onPlay }: { item: FaqItem; open: boolean; onToggle: () => void; onPlay: (v: FaqVideo) => void }) {
  const panelId = `faq-panel-${item.id}`
  return (
    <div className={`help-faq-item ${open ? 'help-faq-item--open' : ''}`}>
      <button className="help-faq-item__question" onClick={onToggle} aria-expanded={open} aria-controls={panelId}>
        <span>{item.question}</span>
        {item.video && <span className="help-faq-item__video-badge" title="Tiene video explicativo"><IconPlay size={10} /> Video</span>}
        <IconChevron open={open} />
      </button>
      {open && (
        <div className="help-faq-item__answer" id={panelId}>
          {renderAnswer(item.answer)}
          {item.video && (
            <button className="btn btn--primary btn--sm help-faq-item__play" onClick={() => onPlay(item.video!)}>
              <IconPlay /> Ver video explicativo
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// Centro de ayuda: FAQ por rol con buscador y video explicativo por pregunta. Lazy-loaded (ver
// LandingPage y App) para que el contenido no viaje en el bundle inicial.
export default function HelpFaqModal({ onClose, defaultAudience = 'paciente' }: { onClose: () => void; defaultAudience?: FaqAudience }) {
  const [audience, setAudience] = useState<FaqAudience>(defaultAudience)
  const [query, setQuery] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  const [playing, setPlaying] = useState<FaqVideo | null>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !playing) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, playing])

  const items = useMemo(() => {
    const q = normalize(query.trim())
    const byAudience = FAQ_ITEMS.filter(i => i.audience === audience)
    if (!q) return byAudience
    return byAudience.filter(i => normalize(i.question).includes(q) || normalize(i.answer).includes(q))
  }, [audience, query])

  return (
    <div className="help-faq-overlay" onClick={onClose}>
      <div className="card mobile-modal-card help-faq-card" role="dialog" aria-modal="true" aria-labelledby="help-faq-title" onClick={e => e.stopPropagation()}>
        <div className="help-faq-card__header">
          <h3 id="help-faq-title"><IconHelp size={20} /> Ayuda y preguntas frecuentes</h3>
          <button onClick={onClose} className="btn btn--ghost btn--sm mobile-modal-close" aria-label="Cerrar ayuda"><IconClose /></button>
        </div>

        <div className="help-faq-tabs" role="tablist" aria-label="Tipo de usuario">
          {([['paciente', 'Soy paciente'], ['profesional', 'Soy profesional']] as const).map(([value, label]) => (
            <button
              key={value}
              role="tab"
              aria-selected={audience === value}
              className={`help-faq-tabs__tab ${audience === value ? 'help-faq-tabs__tab--active' : ''}`}
              onClick={() => { setAudience(value); setOpenId(null) }}
            >
              {label}
            </button>
          ))}
        </div>

        <input
          type="search"
          className="help-faq-search"
          placeholder="Buscá tu duda: pago, cancelar, Mercado Pago…"
          value={query}
          onChange={e => setQuery(e.target.value)}
          aria-label="Buscar en las preguntas frecuentes"
        />

        <div className="help-faq-list">
          {items.length === 0 ? (
            <p className="help-faq-empty">
              No encontramos preguntas con esa búsqueda. Escribinos desde <strong>Soporte</strong> y te ayudamos.
            </p>
          ) : items.map(item => (
            <FaqEntry
              key={item.id}
              item={item}
              open={openId === item.id || (query.trim() !== '' && items.length === 1)}
              onToggle={() => setOpenId(openId === item.id ? null : item.id)}
              onPlay={setPlaying}
            />
          ))}
        </div>
      </div>

      {playing && (
        <div onClick={e => e.stopPropagation()}>
          <FaqVideoPlayer video={playing} onClose={() => setPlaying(null)} />
        </div>
      )}
    </div>
  )
}
