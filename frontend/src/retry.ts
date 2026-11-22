// "Retry": at one of your mistakes, the board goes back one move and you try to find
// something better. judgeRetry decides whether your try was good enough.

import { api, type GameDetail, type LegalMove, type MoveEval } from './api'
import { moverOf, whiteWinPercent } from './chess'
import { asMoveEval } from './variation'

export interface RetryVerdict {
  kind: 'checking' | 'correct' | 'good' | 'wrong' | 'revealed'
  text: string
}

/**
 * Was `move` a good answer at game move `ply` (the mistake)? Stockfish's best move is
 * correct straight away; any other move is evaluated, and counts if it keeps your winning
 * chance within 10% of the position before the mistake (i.e. it would have been "Good").
 */
export async function judgeRetry(game: GameDetail, evals: Map<number, MoveEval>, ply: number, move: LegalMove): Promise<RetryVerdict> {
  const mistake = evals.get(ply)
  if (move.uci === mistake?.bestMoveUci) return { kind: 'correct', text: `${move.san} is the best move. Well found!` }
  if (move.uci === game.moves[ply - 1].uci) return { kind: 'wrong', text: "That's the move from the game. Try something else." }

  const e = await api.evaluatePosition(move.fenAfter, 'stockfish', 14)
  const white = moverOf(ply, game.startFen) === 'WHITE'
  const mine = (whitePercent: number) => (white ? whitePercent : 100 - whitePercent)
  const before = ply === 1 ? 50 : whiteWinPercent(evals.get(ply - 1), ply - 1, game.startFen)
  const after = whiteWinPercent(asMoveEval(e, ply), ply, game.startFen)
  const drop = Math.max(0, mine(before) - mine(after))
  return drop < 10
    ? { kind: 'good', text: `${move.san} works too: it keeps your winning chance (only ${drop.toFixed(1)}% less).` }
    : { kind: 'wrong', text: `${move.san} isn't it: it costs ${Math.round(drop)}% of your winning chance.` }
}
