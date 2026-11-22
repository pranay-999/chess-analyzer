import type { MouseEvent } from 'react'
import type { MoveEval } from '../api'
import { CLASSIFICATION, whiteWinPercent } from '../chess'

const W = 600
const H = 120

/**
 * The whole game at a glance: white's winning chance after every move, drawn as
 * an SVG area chart. Dots mark mistakes and blunders. Click to jump to a move.
 *
 * The SVG stretches to any width (preserveAspectRatio="none"), which would squash
 * circles into ovals, so the dots are small HTML elements placed on top with % positions.
 */
export default function EvalGraph({ evals, startFen, currentPly, onSelect }: {
  evals: MoveEval[]
  startFen: string
  currentPly: number
  onSelect: (ply: number) => void
}) {
  const n = evals.length
  if (n === 0) return null

  const xPercent = (ply: number) => (ply / n) * 100
  const x = (ply: number) => (ply / n) * W
  const y = (percent: number) => H - (percent / 100) * H

  // Points for ply 0 (start, 50%) then after every move.
  const points = [[x(0), y(50)], ...evals.map((e) => [x(e.ply), y(whiteWinPercent(e, e.ply, startFen))])]
  const line = points.map(([px, py]) => `${px.toFixed(1)},${py.toFixed(1)}`).join(' L ')
  const area = `M 0,${H} L ${line} L ${W},${H} Z`

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    const box = event.currentTarget.getBoundingClientRect()
    const ratio = (event.clientX - box.left) / box.width
    onSelect(Math.round(ratio * n))
  }

  return (
    <div className="eval-graph" onClick={handleClick} role="img" aria-label="Evaluation graph">
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
        <path d={area} className="graph-area" />
        <line x1={0} x2={W} y1={H / 2} y2={H / 2} className="graph-mid" />
      </svg>
      <div className="graph-cursor" style={{ left: `${xPercent(currentPly)}%` }} />
      {evals
        .filter((e) => ['BRILLIANT', 'GREAT', 'MISTAKE', 'BLUNDER'].includes(e.classification)) // the moments worth a dot
        .map((e) => (
          <span key={e.ply} className="graph-dot"
                style={{
                  left: `${xPercent(e.ply)}%`,
                  top: `${100 - whiteWinPercent(e, e.ply, startFen)}%`,
                  background: CLASSIFICATION[e.classification].color,
                }} />
        ))}
    </div>
  )
}
