import type { PositionEval } from '../api'
import { formatEval, moverOf } from '../chess'
import { asMoveEval, type Variation } from '../variation'

/** Shown instead of the move verdict while you're exploring your own line. */
export default function VariationCard({ variation, startFen, evaluation, onSelect, onExit }: {
  variation: Variation
  startFen: string
  evaluation: PositionEval | null // null while Stockfish is still thinking
  onSelect: (index: number) => void
  onExit: () => void
}) {
  const ply = variation.fromPly + variation.index // "as if" this many moves were played
  return (
    <div className="card compact insight variation">
      <div className="insight-head">
        <strong>Your variation</strong>
        {variation.index > 0 && evaluation && <span className="eval-chip">{formatEval(asMoveEval(evaluation, ply), ply, startFen)}</span>}
        <button className="btn small-btn" onClick={onExit} title="Back to the game (Esc)">✕ Back to game</button>
      </div>

      <div className="variation-line">
        {variation.moves.map((m, i) => {
          const p = variation.fromPly + i + 1
          const white = moverOf(p, startFen) === 'WHITE'
          const number = white ? `${Math.ceil(p / 2)}.` : i === 0 ? `${Math.ceil(p / 2)}...` : ''
          return (
            <button key={i} className={`move ${i + 1 === variation.index ? 'active' : ''}`} onClick={() => onSelect(i + 1)}>
              {number && <span className="muted">{number}</span>}{m.san}
            </button>
          )
        })}
      </div>

      {variation.index > 0 && (evaluation
        ? evaluation.bestMoveSan && (
            <span className="small">
              Stockfish suggests <strong className="best-text">{evaluation.bestMoveSan}</strong> (green arrow) · depth {evaluation.depth}
            </span>
          )
        : <span className="muted small"><span className="spinner inline" /> Stockfish is thinking…</span>)}
      <span className="muted small">Drag pieces to keep exploring · ← → to step · Esc to return.</span>
    </div>
  )
}
