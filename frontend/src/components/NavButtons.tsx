// The big ⏮ ‹ ▶ › ⏭ buttons under the move list. `extra` adds buttons (e.g. flip board).

import type { ReactNode } from 'react'

export default function NavButtons({ onFirst, onPrev, onNext, onLast, onPlay, playing = false, extra }: {
  onFirst: () => void
  onPrev: () => void
  onNext: () => void
  onLast: () => void
  onPlay?: () => void   // optional ▶ autoplay through the game
  playing?: boolean
  extra?: ReactNode
}) {
  return (
    <div className="nav-buttons">
      <button className="nav-btn" onClick={onFirst} title="First move (Home)" aria-label="First move">⏮</button>
      <button className="nav-btn" onClick={onPrev} title="Previous move (←)" aria-label="Previous move">‹</button>
      {onPlay && (
        <button className="nav-btn" onClick={onPlay} title={playing ? 'Pause' : 'Play through the game'}
                aria-label={playing ? 'Pause' : 'Play'}>{playing ? '❚❚' : '▶'}</button>
      )}
      <button className="nav-btn" onClick={onNext} title="Next move (→)" aria-label="Next move">›</button>
      <button className="nav-btn" onClick={onLast} title="Last move (End)" aria-label="Last move">⏭</button>
      {extra}
    </div>
  )
}
