# Phase 20: Book and Miss, Retry, accuracy per phase, key moments, sounds

## 1. Book 📖 and Miss ✗ (`analysis/MoveAnalyzer.classify`)

The order the rules are checked in, all from the mover's point of view:

| Label | Rule |
|---|---|
| **Book** | still in known opening theory **and** lost < 10% (theory never hides a mistake) |
| Brilliant / Great | unchanged (Phase 18) |
| **Miss** | the opponent's last move lost ≥ 20% (a chance for you), your move gave ≥ 10% back, but you're still no worse off than before their mistake. If you ended up worse, it's a plain Mistake/Blunder. |
| Best / Good / Inaccuracy / Mistake / Blunder | unchanged |

**Opening theory, `chess/OpeningBook.java`.** It's loaded from the free Lichess list
(`src/main/resources/openings/*.tsv`, public domain, ~3,500 named lines). Every line is played
once at start-up, and every **position** it passes through goes into a `HashSet`. Positions,
not move lists, so transpositions count. The key is the FEN's first three fields (pieces, side
to move, castling): move counters and the en-passant square are dropped so the same position
always gets the same key. A move is Book while **every** position since the start is in the set.

Bonus: `GameDetail.opening` now gives the exact opening name ("Scandinavian Defense:
Mieses-Kotrč Variation") instead of just the ECO code.

Misses also become puzzles now (`PuzzleService.PUZZLE_WORTHY`).

## 2. Retry (`retry.ts`, `components/RetryCard.tsx`)

On a Miss, Mistake or Blunder, **↻ Retry** takes the board back one move and hides the green
answer arrow. Your try is judged:
- Stockfish's best move → ✓ correct;
- the move from the game → "try something else";
- anything else → Stockfish evaluates it (depth 14). Within 10% of the position before
  your mistake counts (it would have been a "Good" move); otherwise ✗, with how much it costs.

"Retry mode" isn't a separate flag that needs resetting: it's **derived**. It lasts while the
board sits on the position before that mistake, so any navigation ends it automatically.

## 3. Accuracy per phase and key moments (`report.ts`, `components/KeyMoments.tsx`)

Worked out in the browser from numbers the analysis already stores (`winDrop` per move),
with the same accuracy formula as the Java side:
- **Endgame** from the first position with ≤ 26 points of queens/rooks/bishops/knights left
  (62 at the start); **Opening** = the first 10 moves each; **Middlegame** = in between.
- **Key moments** = the (up to) 3 moves that lost the most winning chance (≥ 10%). Click to jump there.

## 4. Sounds (`sounds.ts`)

Made with the **Web Audio API**: an oscillator (tone) plus a gain node (volume) that fades out
in ~0.1 s, so no sound files are needed. Move / capture / castle / check / game end / three beeps
when **your** clock drops under 10 s. The kind of sound comes from the move's SAN (`x`, `+`, `#`,
`O-O`). 🔊/🔇 in the top bar (remembered in `localStorage`).

The hooks (`useMoveSound`, `useLowTimeWarning`, `useGameEndSound`) are effects with a **ref**
holding the previous value: playing a sound is a side effect *outside* React, exactly what
effects are for, and the ref lets us play only when the count goes **up** (not on the first draw or
when stepping back).

## Try this

1. Count Misses in the Stats page (`StatsService` counts Inaccuracy/Mistake/Blunder only).
2. Make the endgame threshold a function of both sides' material, not the total.
