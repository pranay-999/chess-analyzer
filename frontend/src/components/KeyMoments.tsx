// "Key moments": the few moves where the game turned, each with its label and the
// reason, so you know where to look first. Click one to jump there.

import type { GameDetail, MoveEval } from '../api'
import { CLASSIFICATION, explain, moverOf } from '../chess'
import { keyMoments } from '../report'

export default function KeyMoments({ game, evals, onSelect }: {
  game: GameDetail
  evals: MoveEval[]
  onSelect: (ply: number) => void
}) {
  const moments = keyMoments(evals)
  if (moments.length === 0) return null
  return (
    <div className="card compact">
      <strong>Key moments</strong>
      <ul className="key-moments">
        {moments.map((e) => {
          const move = game.moves[e.ply - 1]
          const white = moverOf(e.ply, game.startFen) === 'WHITE'
          const meta = CLASSIFICATION[e.classification]
          return (
            <li key={e.ply}>
              <button className="link-btn" onClick={() => onSelect(e.ply)}>
                <strong>{Math.ceil(e.ply / 2)}{white ? '.' : '...'} {move.san}</strong>{' '}
                <span style={{ color: meta.color }}>{meta.symbol} {meta.label}</span>
                <span className="muted"> ({white ? game.summary.white : game.summary.black})</span>
              </button>
              <span className="small muted">{explain(e, move.uci)}</span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
