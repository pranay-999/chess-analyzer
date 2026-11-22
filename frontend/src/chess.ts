// Small display helpers. No chess rules here: the backend did all of that.

import type { Classification, Color, MoveEval } from './api'

/** Who played move number `ply` (1 = first move). Usually white plays odd plies. */
export function moverOf(ply: number, startFen: string): Color {
  const whiteStarts = startFen.split(' ')[1] === 'w'
  return (ply % 2 === 1) === whiteStarts ? 'WHITE' : 'BLACK'
}

/**
 * White's chance of winning, 0..100, after move `ply`. Same formula as the Java
 * MoveAnalyzer, so the bar and the move labels agree.
 */
export function whiteWinPercent(evaluation: MoveEval | undefined, ply: number, startFen: string): number {
  if (!evaluation) return 50
  const { evalCp, mateIn } = evaluation
  if (mateIn !== null) {
    if (mateIn === 0) return moverOf(ply, startFen) === 'WHITE' ? 100 : 0 // the mover just gave checkmate
    return mateIn > 0 ? 100 : 0
  }
  const cp = Math.max(-1000, Math.min(1000, evalCp ?? 0))
  return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1)
}

/** "+1.3", "-0.4", "M3", "-M2", or "1-0" when the game ended in mate. */
export function formatEval(evaluation: MoveEval | undefined, ply: number, startFen: string): string {
  if (!evaluation) return '0.0'
  const { evalCp, mateIn } = evaluation
  if (mateIn !== null) {
    if (mateIn === 0) return moverOf(ply, startFen) === 'WHITE' ? '1-0' : '0-1'
    return mateIn > 0 ? `M${mateIn}` : `-M${-mateIn}`
  }
  const pawns = (evalCp ?? 0) / 100
  return (pawns > 0 ? '+' : '') + pawns.toFixed(1)
}

export const CLASSIFICATION: Record<Classification, { label: string; symbol: string; color: string }> = {
  BOOK: { label: 'Book', symbol: '📖', color: 'var(--book)' },
  BRILLIANT: { label: 'Brilliant', symbol: '!!', color: 'var(--brilliant)' },
  GREAT: { label: 'Great', symbol: '!', color: 'var(--great)' },
  BEST: { label: 'Best', symbol: '★', color: 'var(--best)' },
  GOOD: { label: 'Good', symbol: '', color: 'var(--good)' },
  MISS: { label: 'Miss', symbol: '✗', color: 'var(--miss)' },
  INACCURACY: { label: 'Inaccuracy', symbol: '?!', color: 'var(--inaccuracy)' },
  MISTAKE: { label: 'Mistake', symbol: '?', color: 'var(--mistake)' },
  BLUNDER: { label: 'Blunder', symbol: '??', color: 'var(--blunder)' },
}

/** In words: why this move got its label (from the numbers the server stored). */
export function explain(e: MoveEval, playedUci: string): string | null {
  const drop = e.winDrop
  if (drop === null) return null // analysed before the reasons were stored: re-analyse to see them
  const pct = (n: number) => `${Math.round(n)}%`
  switch (e.classification) {
    case 'BOOK':
      return 'A known opening move: it is in the opening theory list.'
    case 'MISS':
      return `Your opponent's last move was a mistake, but this move let the chance slip: it cost ${pct(drop)} of your winning chance.`
    case 'BRILLIANT':
      return `A good sacrifice: you gave away about ${e.materialGivenUp} pawns' worth of material on purpose, and it was ${
        e.bestMoveUci === playedUci ? "Stockfish's best move" : 'as good as the best move'}.`
    case 'GREAT':
      return `The only good move: the next-best move would have cost about ${pct(e.onlyMoveGap ?? 0)} of your winning chance.`
    case 'BEST':
      return "Stockfish's first choice."
    case 'GOOD':
      return `A fine move: it cost only ${drop.toFixed(1)}% winning chance.`
    case 'INACCURACY':
    case 'MISTAKE':
    case 'BLUNDER':
      return `This move cost ${pct(drop)} of your winning chance (${
        e.classification === 'INACCURACY' ? '10–20% = inaccuracy' : e.classification === 'MISTAKE' ? '20–30% = mistake' : '30% or more = blunder'}).`
  }
}
