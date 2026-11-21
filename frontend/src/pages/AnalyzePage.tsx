import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { api, type Color } from '../api'
import { savedChessUsername } from '../prefs'

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

const EXAMPLES = [
  { label: "Fool's mate (PGN)", text: '[White "Me"]\n[Black "Friend"]\n[Result "0-1"]\n\n1. f3 e5 2. g4 Qh4# 0-1' },
  { label: 'Mate in 1 (FEN)', text: '6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1' },
]

/** Roughly the same rules as PastedGameService on the server, just to show a hint while typing. */
function detect(text: string): 'FEN' | 'PGN' | 'game link' | null {
  const trimmed = text.trim()
  if (!trimmed) return null
  if (!trimmed.includes('[') && /chess\.com\/|lichess\.org\//.test(trimmed)) return 'game link'
  const firstField = trimmed.split(/\s+/)[0]
  return !trimmed.includes('[') && (firstField.match(/\//g) ?? []).length === 7 ? 'FEN' : 'PGN'
}

/**
 * "/analyze" — paste a PGN (whole game), a FEN (one position) or a Chess.com / Lichess
 * game link. The server stores it as a game, and we open the normal game page, so
 * replay, Stockfish analysis and drag-to-explore all work on it.
 */
export default function AnalyzePage() {
  const navigate = useNavigate()
  const location = useLocation()
  // Arriving from /import after an error? Start with that text so it can be fixed.
  const [text, setText] = useState<string>((location.state as { text?: string } | null)?.text ?? '')
  const [color, setColor] = useState<Color>('WHITE')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const kind = detect(text)

  /** iPhone path: in the Chess.com app tap Share → Copy PGN (or Copy link), then this one button. */
  async function pasteAndOpen() {
    setError(null)
    try {
      const clip = await navigator.clipboard.readText() // the browser asks permission the first time
      if (!clip.trim()) {
        setError('The clipboard is empty. Copy a PGN or a game link first.')
        return
      }
      setText(clip)
      await open(clip)
    } catch {
      setError('Couldn\'t read the clipboard. Long-press the box above and choose Paste instead.')
    }
  }

  async function open(value: string) {
    setBusy(true)
    setError(null)
    try {
      const game = await api.importGame(value, color, savedChessUsername())
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
        Paste a <strong>PGN</strong> (a whole game), a <strong>FEN</strong> (one position), or a{' '}
        <strong>Chess.com / Lichess game link</strong>. It opens on the board, where you can run Stockfish and
        drag pieces to explore. Only finished games can be imported (fair-play rules).
      </p>

      <button className="btn primary paste-btn" onClick={pasteAndOpen} disabled={busy}>
        📋 Paste from clipboard &amp; open
      </button>

      <textarea
        className="paste-box"
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={'https://www.chess.com/game/live/123456789\n\nor a PGN:  1. e4 e5 2. Nf3 Nc6 …\n\nor a FEN:  rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1'}
        rows={9}
        spellCheck={false}
        aria-label="PGN, FEN or game link"
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

      <details className="phone-help">
        <summary>Using the Chess.com or Lichess phone app?</summary>
        <p><strong>Android:</strong> open this site in Chrome → menu ⋮ → <em>Install app</em>. Then, after a game,
          tap <em>Share</em> in the Chess.com app and pick <strong>Chess Analyzer</strong>: the analysis opens directly.</p>
        <p><strong>iPhone:</strong> in the Chess.com app tap <em>Share → Copy PGN</em> (or copy the game link), come
          here and tap <strong>📋 Paste from clipboard</strong>. Tip: Safari → Share → <em>Add to Home Screen</em> makes
          this one tap from your home screen.</p>
      </details>
    </section>
  )
}
