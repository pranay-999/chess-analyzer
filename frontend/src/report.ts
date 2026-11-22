// Extra Game Review numbers, worked out in the browser from what the analysis already
// stored (each move's lost winning chance, `winDrop`). No new server work needed.

import type { Color, MoveEval, MoveView } from './api'
import { moverOf } from './chess'

export type Phase = 'Opening' | 'Middlegame' | 'Endgame'
export const PHASES: Phase[] = ['Opening', 'Middlegame', 'Endgame']

const PIECE_POINTS: Record<string, number> = { q: 9, r: 5, b: 3, n: 3 }

/** Queens, rooks, bishops and knights left on the board, both sides together (62 at the start). */
export function pieceMaterial(fen: string): number {
  let total = 0
  for (const ch of fen.split(' ')[0]) total += PIECE_POINTS[ch.toLowerCase()] ?? 0
  return total
}

/**
 * Which phase each move belongs to:
 *  - Endgame: from the first position with at most 26 points of pieces left
 *    (e.g. a rook and two minor pieces each, or queen + rook vs queen + rook = 28 is still middlegame).
 *  - Opening: the first 10 moves each (unless the endgame already started).
 *  - Middlegame: everything in between.
 */
export function phasesOf(moves: MoveView[]): Phase[] {
  let endgame = false
  return moves.map((m) => {
    endgame = endgame || pieceMaterial(m.fenAfter) <= 26
    return endgame ? 'Endgame' : m.ply <= 20 ? 'Opening' : 'Middlegame'
  })
}

/** Same formula as the Java MoveAnalyzer.moveAccuracy (Lichess): 100 = no winning chance lost. */
export function moveAccuracy(winDrop: number): number {
  return Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * winDrop) - 3.1669))
}

/** Average accuracy of one player in one phase, or null if they made no (analysed) moves in it. */
export function phaseAccuracy(moves: MoveView[], evals: MoveEval[], startFen: string, color: Color, phase: Phase): number | null {
  const phases = phasesOf(moves)
  const scores = evals
    .filter((e) => e.winDrop !== null && phases[e.ply - 1] === phase && moverOf(e.ply, startFen) === color)
    .map((e) => moveAccuracy(e.winDrop!))
  return scores.length ? scores.reduce((a, b) => a + b, 0) / scores.length : null
}

/** The turning points: the (up to) 3 moves that lost the most winning chance (10% or more), in game order. */
export function keyMoments(evals: MoveEval[]): MoveEval[] {
  return evals
    .filter((e) => (e.winDrop ?? 0) >= 10)
    .sort((a, b) => b.winDrop! - a.winDrop!)
    .slice(0, 3)
    .sort((a, b) => a.ply - b.ply)
}
