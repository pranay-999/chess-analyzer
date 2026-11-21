import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api, type Color } from '../api'

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

const EXAMPLES = [
  { label: "Fool's mate (PGN)", text: '[White "Me"]\n[Black "Friend"]\n[Result "0-1"]\n\n1. f3 e5 2. g4 Qh4# 0-1' },
  { label: 'Mate in 1 (FEN)', text: '6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1' },
]

/** Same rule as PastedGameService.looksLikeFen on the server, just to show a hint while typing. */
function detect(text: string): 'FEN' | 'PGN' | null {
  const trimmed = text.trim()
  if (!trimmed) return null
  const firstField = trimmed.split(/\s+/)[0]
  return !trimmed.includes('[') && (firstField.match(/\//g) ?? []).length === 7 ? 'FEN' : 'PGN'
}

/**
 * "/analyze" — paste a PGN (whole game) or FEN (one position). The server stores it
 * as a game, and we open the normal game page, so replay, Stockfish analysis and
 * drag-to-explore all work on it.
 */
export default function AnalyzePage() {
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [color, setColor] = useState<Color>('WHITE')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const kind = detect(text)

  async function open(value: string) {
    setBusy(true)
    setError(null)
    try {
      const game = await api.importGame(value, color)
      navigate(`/games/${game.id}`)
    } catch (e) {
      setError((e as Error).message) // e.g. "Illegal move 'Ke3' in position …"
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Analysis board</h1>
      <p className="muted">
        Paste a <strong>PGN</strong> (a whole game, from any site) or a <strong>FEN</strong> (a single position).
        It opens on the board, where you can run Stockfish and drag pieces to explore.
      </p>

      <textarea
        className="paste-box"
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={'[White "Me"]\n[Black "Friend"]\n\n1. e4 e5 2. Nf3 Nc6 …\n\nor a FEN:  rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1'}
        rows={9}
        spellCheck={false}
        aria-label="PGN or FEN"
      />

      <div className="paste-actions">
        <div className="segmented" role="radiogroup" aria-label="Your side">
          <span className="muted small">I played</span>
          {(['WHITE', 'BLACK'] as Color[]).map((c) => (
            <button key={c} role="radio" aria-checked={color === c}
                    className={`seg ${color === c ? 'active' : ''}`} onClick={() => setColor(c)}>
              {c === 'WHITE' ? 'White' : 'Black'}
            </button>
          ))}
        </div>
        {kind && <span className="tag plain">Looks like a {kind}</span>}
        <span className="spacer" />
        <button className="btn" onClick={() => open(START_FEN)} disabled={busy}>Empty board</button>
        <button className="btn primary" onClick={() => open(text)} disabled={busy || !kind}>
          {busy ? <span className="spinner" /> : null} Open on board
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      <p className="muted small examples">
        Try an example:{' '}
        {EXAMPLES.map((ex) => (
          <button key={ex.label} className="link-btn" onClick={() => setText(ex.text)}>{ex.label}</button>
        ))}
      </p>
    </section>
  )
}
