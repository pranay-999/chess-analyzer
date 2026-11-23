import { useEffect, useRef } from 'react'
import type { MoveEval, MoveView } from '../api'
import { CLASSIFICATION } from '../chess'

const WHITE_PIECES: Record<string, string> = { K: '♔', Q: '♕', R: '♖', B: '♗', N: '♘' }
const BLACK_PIECES: Record<string, string> = { K: '♚', Q: '♛', R: '♜', B: '♝', N: '♞' }

/** "Nf3" → "♘f3", "e8=Q" → "e8=♕": piece letters become little piece icons ("figurine" notation). */
function figurine(san: string, white: boolean): string {
  const icons = white ? WHITE_PIECES : BLACK_PIECES
  return san.replace(/^[KQRBN]|=[QRBN]/g, (m) => (m[0] === '=' ? '=' + icons[m[1]] : icons[m]))
}

/**
 * The move table: "1. e4 e5 / 2. ♘f3 ♞c6 …", with a coloured ?? / ? / ?! / ★ after analysed
 * moves. `title` (e.g. the opening name) sits above it.
 */
export default function MoveList({ moves, evals, currentPly, onSelect, title, startsWithBlack = false }: {
  moves: MoveView[]
  evals: Map<number, MoveEval>
  currentPly: number
  onSelect: (ply: number) => void
  title?: string | null
  startsWithBlack?: boolean // a pasted FEN with black to move: the first row has only a black move
}) {
  const activeRef = useRef<HTMLButtonElement>(null)

  // Keep the current move visible inside the scrolling list.
  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' })
  }, [currentPly])

  // Group plies into rows of [white move, black move] (null = an empty cell).
  const cells: (MoveView | null)[] = startsWithBlack ? [null, ...moves] : moves
  const rows: (MoveView | null)[][] = []
  for (let i = 0; i < cells.length; i += 2) rows.push(cells.slice(i, i + 2))

  return (
    <div className="move-table">
      {title && <div className="move-table-title">{title}</div>}
      <div className="move-list">
        {rows.map((pair, i) => (
          <div className="move-row" key={i}>
            <span className="move-num">{i + 1}.</span>
            {pair.map((m, col) => {
              if (!m) return <span key="empty" className="move empty">…</span>
              const verdict = evals.get(m.ply)?.classification
              const meta = verdict ? CLASSIFICATION[verdict] : null
              const active = m.ply === currentPly
              return (
                <button key={m.ply} ref={active ? activeRef : undefined}
                        className={`move ${active ? 'active' : ''}`} onClick={() => onSelect(m.ply)}>
                  {figurine(m.san, col === 0)}
                  {meta?.symbol && <span className="verdict" style={{ color: meta.color }}>{meta.symbol}</span>}
                </button>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}
