// The Retry box: what to do, and how your try went (judged in retry.ts).

import type { GameDetail } from '../api'
import { moverOf } from '../chess'
import type { RetryVerdict } from '../retry'

export default function RetryCard({ ply, game, verdict, onTryAgain, onReveal, onBack }: {
  ply: number
  game: GameDetail
  verdict: RetryVerdict | null
  onTryAgain: () => void
  onReveal: () => void
  onBack: () => void
}) {
  const white = moverOf(ply, game.startFen) === 'WHITE'
  const number = `${Math.ceil(ply / 2)}${white ? '.' : '...'}`
  const solved = verdict?.kind === 'correct' || verdict?.kind === 'good'
  const color = solved ? 'var(--best)' : verdict?.kind === 'wrong' ? 'var(--blunder)' : 'var(--great)'

  return (
    <div className="card compact insight" style={{ borderLeftColor: color }}>
      <strong>↻ Retry: instead of {number} {game.moves[ply - 1].san}</strong>
      {!verdict && <span className="small">Find a better move for {white ? 'White' : 'Black'}. Drag or tap a piece.</span>}
      {verdict?.kind === 'checking' && <span className="small muted"><span className="spinner inline" /> Stockfish is checking your move…</span>}
      {verdict && verdict.kind !== 'checking' && (
        <span className="small" style={{ color: solved ? 'var(--best)' : undefined }}>{solved ? '✓ ' : verdict.kind === 'wrong' ? '✗ ' : ''}{verdict.text}</span>
      )}
      <div className="analyse-controls">
        {verdict?.kind === 'wrong' && <button className="btn primary" onClick={onTryAgain}>Try again</button>}
        {verdict?.kind !== 'checking' && !solved && verdict?.kind !== 'revealed' && <button className="btn" onClick={onReveal}>Show answer</button>}
        <button className="btn" onClick={onBack}>Back to the game</button>
      </div>
    </div>
  )
}
