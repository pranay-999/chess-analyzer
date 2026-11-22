// A "variation" = moves YOU try on the board, branching off the real game.

import type { LegalMove, MoveEval, PositionEval } from './api'

export interface Variation {
  fromPly: number      // the game position it branches from (0 = start)
  moves: LegalMove[]   // the moves you played on the board
  index: number        // how many of them are shown (0 = back at the branch point)
}

/**
 * One step back (-1) or forward (+1). Inside a variation we walk its moves;
 * stepping back past its start closes it. Returns the new [variation, ply].
 */
export function stepState(variation: Variation | null, ply: number, delta: number, total: number): [Variation | null, number] {
  if (!variation) return [null, Math.max(0, Math.min(total, ply + delta))]
  const next = variation.index + delta
  if (next < 0) return [null, variation.fromPly]
  return [{ ...variation, index: Math.min(next, variation.moves.length) }, ply]
}

/** Adds a dropped move: re-uses the next move if it's the same, otherwise starts a new branch from here. */
export function playInVariation(variation: Variation | null, ply: number, move: LegalMove): Variation {
  if (variation && variation.moves[variation.index]?.uci === move.uci) {
    return { ...variation, index: variation.index + 1 }
  }
  const kept = variation ? variation.moves.slice(0, variation.index) : []
  return { fromPly: variation?.fromPly ?? ply, moves: [...kept, move], index: kept.length + 1 }
}

/** Lets the eval bar / formatter (written for analysed moves) display a live position eval. */
export function asMoveEval(e: PositionEval, ply: number): MoveEval {
  return { ply, evalCp: e.cp, mateIn: e.mate, bestMoveUci: e.bestMoveUci, bestMoveSan: e.bestMoveSan, cpLoss: null, classification: 'GOOD', winDrop: null, materialGivenUp: null, onlyMoveGap: null }
}
