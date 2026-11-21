import { useEffect, useRef } from 'react'
import type { MoveEval, MoveView } from '../api'
import { CLASSIFICATION } from '../chess'

/** "1. e4 e5  2. Nf3 Nc6 …" with a coloured ?? / ? / ?! / ★ after analysed moves. */
export default function MoveList({ moves, evals, currentPly, onSelect }: {
  moves: MoveView[]
  evals: Map<number, MoveEval>
  currentPly: number
  onSelect: (ply: number) => void
}) {
  const activeRef = useRef<HTMLButtonElement>(null)

  // Keep the current move visible inside the scrolling list.
  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' })
  }, [currentPly])

  // Group plies into rows of [white move, black move].
  const rows: MoveView[][] = []
  for (let i = 0; i < moves.length; i += 2) rows.push(moves.slice(i, i + 2))

  return (
    <div className="move-list">
      {rows.map((pair, i) => (
        <div className="move-row" key={i}>
          <span className="move-num">{i + 1}.</span>
          {pair.map((m) => {
            const verdict = evals.get(m.ply)?.classification
            const meta = verdict ? CLASSIFICATION[verdict] : null
            const active = m.ply === currentPly
            return (
              <button key={m.ply} ref={active ? activeRef : undefined}
                      className={`move ${active ? 'active' : ''}`} onClick={() => onSelect(m.ply)}>
                {m.san}
                {meta?.symbol && <span className="verdict" style={{ color: meta.color }}>{meta.symbol}</span>}
              </button>
            )
          })}
        </div>
      ))}
    </div>
  )
}
