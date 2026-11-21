// How you move pieces on any of our boards: DRAG a piece, or TAP a piece and then
// TAP a square (much easier on a phone). Both end in the same onMove(move) call, so
// each page only decides what a move means (variation, bot game, online game…).
//
// We never check chess rules here: `legal` is the list the server sent for this exact
// position (GET /api/positions/moves), and a move is accepted only if it's in that list.

import { useState, type CSSProperties } from 'react'
import type { LegalMove } from './api'

/** The legal move from `from` to `to`, or undefined. Promotions always become a queen. */
export function findMove(legal: LegalMove[], from: string, to: string): LegalMove | undefined {
  const candidates = legal.filter((m) => m.uci.startsWith(from + to))
  return candidates.find((m) => m.uci.length === 4 || m.uci.endsWith('q'))
}

export function useBoardInput({ fen, legal, enabled, onMove }: {
  fen: string
  legal: LegalMove[] | null // legal moves for `fen` (null = not loaded yet)
  enabled: boolean          // false = look but don't touch (not your turn, game over…)
  onMove: (move: LegalMove) => void
}) {
  // The tapped piece, remembered together with the position it belongs to, so a new
  // position automatically clears it (no extra effect needed).
  const [picked, setPicked] = useState<{ fen: string; square: string } | null>(null)
  const selected = picked?.fen === fen ? picked.square : null
  const sideToMove = fen.split(' ')[1] // "w" or "b"
  const ready = enabled && legal !== null

  const movesFrom = (square: string) => (legal ?? []).filter((m) => m.uci.startsWith(square))

  function play(from: string, to: string): boolean {
    const move = ready ? findMove(legal!, from, to) : undefined
    if (!move) return false
    setPicked(null)
    onMove(move)
    return true
  }

  /** Drag and drop: return true = the piece stays where it was dropped, false = it snaps back. */
  function onPieceDrop({ sourceSquare, targetSquare }: { sourceSquare: string; targetSquare: string | null }): boolean {
    return targetSquare ? play(sourceSquare, targetSquare) : false
  }

  /** Tap: first tap picks a piece (dots show its moves), second tap on a dot plays it. */
  function onSquareClick({ piece, square }: { piece: { pieceType: string } | null; square: string }) {
    if (!ready) return
    if (selected && play(selected, square)) return
    const own = piece && piece.pieceType[0] === sideToMove && movesFrom(square).length > 0
    setPicked(own && square !== selected ? { fen, square } : null) // tap the same piece again = deselect
  }

  /** Only the side to move may pick up pieces. */
  const canDragPiece = ({ piece }: { piece: { pieceType: string } }) => ready && piece.pieceType[0] === sideToMove

  // Highlight the tapped piece, and put a dot on every square it can move to.
  const inputStyles: Record<string, CSSProperties> = {}
  if (selected) {
    inputStyles[selected] = { background: 'rgba(255, 214, 79, 0.7)' }
    for (const m of movesFrom(selected)) {
      inputStyles[m.uci.slice(2, 4)] = {
        background: 'radial-gradient(circle, rgba(20, 20, 20, 0.28) 24%, transparent 26%)',
        cursor: 'pointer',
      }
    }
  }

  return { onPieceDrop, onSquareClick, canDragPiece, inputStyles }
}
