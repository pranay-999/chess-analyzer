// How you move pieces on any of our boards: DRAG a piece, or TAP a piece and then
// TAP a square (much easier on a phone). Both end in the same onMove(move) call, so
// each page only decides what a move means (variation, bot game, online game…).
//
// We never check chess rules here: `legal` is the list the server sent for this exact
// position (GET /api/positions/moves), and a move is accepted only if it's in that list.
//
// Also here, because every board wants them:
//  - MARKS: right-click a square to colour it red (right-DRAG draws an arrow; the board
//    library does that part itself). A normal click clears them.
//  - PRE-MOVES: in a game, while your opponent is thinking you may already drag/tap your
//    next move. It's only remembered; when it becomes your turn and the server's legal
//    moves arrive, it's played if it's still legal, otherwise silently dropped.

import { useEffect, useState, type CSSProperties } from 'react'
import type { LegalMove } from './api'

/** The legal move from `from` to `to`, or undefined. Promotions always become a queen. */
export function findMove(legal: LegalMove[], from: string, to: string): LegalMove | undefined {
  const candidates = legal.filter((m) => m.uci.startsWith(from + to))
  return candidates.find((m) => m.uci.length === 4 || m.uci.endsWith('q'))
}

type Piece = { pieceType: string } | null

export function useBoardInput({ fen, legal, enabled, onMove, premoveColor = null }: {
  fen: string
  legal: LegalMove[] | null // legal moves for `fen` (null = not loaded yet)
  enabled: boolean          // false = look but don't touch (not your turn, game over…)
  onMove: (move: LegalMove) => void
  premoveColor?: 'w' | 'b' | null // your colour while a game is running = pre-moves allowed
}) {
  // The tapped piece / the marked squares, each remembered together with the position
  // they belong to, so a new position automatically clears them (no extra effect needed).
  const [picked, setPicked] = useState<{ fen: string; square: string } | null>(null)
  const [marked, setMarked] = useState<{ fen: string; squares: string[] }>({ fen: '', squares: [] })
  // The queued pre-move. NOT tied to a position: it must survive the opponent's move.
  const [queued, setQueued] = useState<{ from: string; to: string } | null>(null)

  const selected = picked?.fen === fen ? picked.square : null
  const marks = marked.fen === fen ? marked.squares : []
  const sideToMove = fen.split(' ')[1] // "w" or "b"
  const ready = enabled && legal !== null
  const premoving = !enabled && premoveColor !== null && sideToMove !== premoveColor
  const premove = premoveColor ? queued : null // game over = forget it

  // A finished game never keeps a pre-move for the next one. (React's "adjust state when a
  // prop changes" pattern: compare with the previous value during render, no effect needed.)
  const [lastColor, setLastColor] = useState(premoveColor)
  if (premoveColor !== lastColor) {
    setLastColor(premoveColor)
    setQueued(null)
  }

  // Your turn has come and the legal moves (from the server) are here: play the pre-move
  // if it's still legal. This one has to be an effect: it reacts to data arriving.
  useEffect(() => {
    if (!premove || !ready) return
    setQueued(null) // oxlint-disable-line react/set-state-in-effect
    const move = findMove(legal!, premove.from, premove.to)
    if (move) onMove(move)
  }, [premove, ready, legal, onMove])

  const movesFrom = (square: string) => (legal ?? []).filter((m) => m.uci.startsWith(square))
  const isMine = (piece: Piece, color: string) => piece !== null && piece.pieceType[0] === color

  function play(from: string, to: string): boolean {
    if (premoving) {
      if (from === to) return false
      setPicked(null)
      setQueued({ from, to })
      return false // the piece goes back; the two squares are highlighted instead
    }
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
  function onSquareClick({ piece, square }: { piece: Piece; square: string }) {
    if (premoving) {
      if (selected && selected !== square && !isMine(piece, premoveColor!)) {
        play(selected, square)
        return
      }
      setQueued(null) // tapping elsewhere cancels a pre-move
      setPicked(isMine(piece, premoveColor!) && square !== selected ? { fen, square } : null)
      return
    }
    if (!ready) return
    if (selected && play(selected, square)) return
    const own = isMine(piece, sideToMove) && movesFrom(square).length > 0
    setPicked(own && square !== selected ? { fen, square } : null) // tap the same piece again = deselect
  }

  // Where the right button went down. (We don't use the browser's "contextmenu" event:
  // on Linux and Mac it fires as the button goes DOWN, so it can't tell a click from the
  // start of an arrow drag.)
  const [rightDown, setRightDown] = useState<string | null>(null)

  /** Left button anywhere: clear the marks (the arrows clear too). Right button: remember the square. */
  function onSquareMouseDown({ square }: { square: string }, e: { button: number }) {
    if (e.button === 0 && marks.length > 0) setMarked({ fen, squares: [] })
    if (e.button === 2) setRightDown(square)
  }

  /** Right button released on the SAME square = a right-click: mark/unmark it, cancel a pre-move. */
  function onSquareMouseUp({ square }: { square: string }, e: { button: number }) {
    if (e.button !== 2) return
    setRightDown(null)
    if (rightDown !== square) return // that was an arrow drag
    setQueued(null)
    setMarked({ fen, squares: marks.includes(square) ? marks.filter((s) => s !== square) : [...marks, square] })
  }

  /** Only the side to move may pick up pieces, or your own pieces while pre-moving. */
  const canDragPiece = ({ piece }: { piece: { pieceType: string } }) =>
    premoving ? isMine(piece, premoveColor!) : ready && isMine(piece, sideToMove)

  const inputStyles: Record<string, CSSProperties> = {}
  for (const square of marks) inputStyles[square] = { background: 'rgba(235, 97, 80, 0.8)' }
  if (premove) {
    inputStyles[premove.from] = { background: 'rgba(90, 160, 255, 0.55)' }
    inputStyles[premove.to] = { background: 'rgba(90, 160, 255, 0.55)' }
  }
  // Highlight the tapped piece, and put a dot on every square it can move to.
  if (selected) {
    inputStyles[selected] = { background: 'rgba(255, 214, 79, 0.7)' }
    for (const m of movesFrom(selected)) {
      inputStyles[m.uci.slice(2, 4)] = {
        background: 'radial-gradient(circle, rgba(20, 20, 20, 0.28) 24%, transparent 26%)',
        cursor: 'pointer',
      }
    }
  }

  return { onPieceDrop, onSquareClick, onSquareMouseDown, onSquareMouseUp, canDragPiece, inputStyles, premove }
}
