// The strip above and below the board: avatar, name, rating, the pieces this player
// has captured (worked out from the position), and on the right a clock box or any
// other small badge (accuracy in a review). Used by the review, bot and online pages.

import type { ReactNode } from 'react'
import type { Color } from '../api'
import { formatClock } from '../clock'

const STANDARD_START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR'
const VALUE: Record<string, number> = { p: 1, n: 3, b: 3, r: 5, q: 9 }
const GLYPH: Record<string, string> = { p: '♟', n: '♞', b: '♝', r: '♜', q: '♛' }

/**
 * Pieces `color` has taken: whatever the OPPONENT has fewer of than in the starting position.
 * (A promoted pawn shows up as an "extra" queen; Math.max(0, …) keeps that from going negative.)
 */
function capturedBy(fen: string, startFen: string, color: Color): { pieces: string[]; points: number } {
  const count = (placement: string, letter: string) => [...placement].filter((c) => c === letter).length
  const now = fen.split(' ')[0]
  const start = startFen.split(' ')[0]
  const opponentIsWhite = color === 'BLACK'
  const pieces: string[] = []
  let points = 0
  for (const type of ['p', 'n', 'b', 'r', 'q']) {
    const letter = opponentIsWhite ? type.toUpperCase() : type
    const taken = Math.max(0, count(start, letter) - count(now, letter))
    for (let i = 0; i < taken; i++) pieces.push(GLYPH[type])
    points += taken * VALUE[type]
  }
  return { pieces, points }
}

export default function PlayerStrip({ name, rating, color, fen, startFen = STANDARD_START, clockMs = null, running = false, tags, right }: {
  name: string
  rating?: number | string | null
  color: Color
  fen: string                 // the position on the board, for the captured pieces
  startFen?: string           // where the game began (a pasted FEN may start with fewer pieces)
  clockMs?: number | null     // show a clock box (null = no clock)
  running?: boolean           // this side's clock is ticking
  tags?: ReactNode            // e.g. "you", "guest", a spinner
  right?: ReactNode           // shown instead of a clock (e.g. accuracy)
}) {
  const mine = capturedBy(fen, startFen, color)
  const theirs = capturedBy(fen, startFen, color === 'WHITE' ? 'BLACK' : 'WHITE')
  const lead = mine.points - theirs.points
  return (
    <div className="player-strip">
      <span className={`avatar ${color === 'WHITE' ? 'white' : 'black'}`} aria-hidden="true">
        {color === 'WHITE' ? '♔' : '♚'}
      </span>
      <div className="player-text">
        <div className="player-name">
          <strong>{name}</strong>
          {rating != null && <span className="muted">({rating})</span>}
          {tags}
        </div>
        <div className={`captured ${color === 'WHITE' ? 'took-black' : 'took-white'}`}>
          {mine.pieces.join('')}
          {lead > 0 && <span className="muted"> +{lead}</span>}
        </div>
      </div>
      {right}
      {clockMs !== null && (
        <span className={`clock-box ${running ? 'running' : ''} ${running && clockMs < 20_000 ? 'low' : ''}`}>
          {running && <span className="clock-icon" aria-hidden="true">⏱</span>}
          {formatClock(clockMs)}
        </span>
      )}
    </div>
  )
}
