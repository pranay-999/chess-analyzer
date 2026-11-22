# Phase 18 — Brilliant (!!) and Great (!) moves, and *why* each move got its label

Chess.com's Game Review shows Brilliant and Great moves. Its exact formulas are
secret, but the ideas are well known, and we now detect both the same way.

## The rules (`analysis/MoveAnalyzer.classify`)

Checked in this order, all from the **mover's** point of view:

| Label | Rule |
|---|---|
| **Brilliant !!** | A good **sacrifice**: the move gives away **≥ 2 pawns' worth** of material, yet it's the best move (or within 2% winning chance of it); you weren't already completely winning (< 97%) and aren't worse off after it (≥ 40%). |
| **Great !** | The **only good move**: you played Stockfish's best move while its **second-best** move was **≥ 15%** winning chance worse. Not for simple recaptures (taking back on the square the opponent just captured on), and not with only 1–2 legal moves. |
| Best ★ | Stockfish's first choice. |
| Good / Inaccuracy / Mistake / Blunder | Winning chance lost: < 10% / 10–20% / 20–30% / ≥ 30% (unchanged). |

## What each part needed

**"Gave away material": `chess/Sacrifice.java`.** After your move, the opponent tries
every capture; after each, you may take back **on that same square**; the opponent picks
what's worst for you. The material lost compared with before your move is the size of
the sacrifice. Examples (from `SacrificeTest`):

| Move | Result |
|---|---|
| Bxf7+ (Kxf7) | 2: bishop for a pawn, a sacrifice |
| Nxd4 Nxd4 | 0: a fair trade |
| bishop to b5, attacked by a knight but defended by a pawn | 0: Nxb5 axb5 is a trade |
| the same without the defending pawn | 3: the bishop simply hangs |

**"Second-best move": Stockfish MultiPV.** `setoption name MultiPV value 2` makes
Stockfish search its two best lines and report both (`… multipv 1 score cp 120 …` and
`… multipv 2 score cp -40 …`). `Evaluation` gained `secondCp` / `secondMate`. Only the
**analysis** engine uses 2 lines; bots stay at 1 (it's slower). Our own engine doesn't
report a second line, so Great needs a Stockfish analysis.

**"Why": stored with every move.** Migration `V6` adds three columns to `move_evaluations`:
`win_drop` (winning chance lost), `material_given_up`, `only_move_gap`. The page turns
them into a sentence (`chess.ts` → `explain()`), e.g.

> **25… f5 !! Brilliant**: *A good sacrifice: you gave away about 4 pawns' worth of material
> on purpose, and it was as good as the best move.*

## Compared with Chess.com (your game vs Toneo007, 21 Nov 2026)

| You (Edward_nova) | Chess.com | Chess Analyzer |
|---|---|---|
| Brilliant | 2 | **2** (18… Nd3, 25… f5) |
| Great | 1 | 0 |
| Best | 11 | 9 |

Our two candidates for "Great" (20… Bxf2 and 21… Qxe4, both 18% better than the
alternatives) are **recaptures**, which our rule skips on purpose: taking back is nearly
always "the only good move". Chess.com uses a different engine setup and a secret
formula, so small differences like this are normal. The rules here are documented and tested.

## Also new

- **Error boundary** (`components/ErrorBoundary.tsx`): if a page crashes while drawing,
  only that page shows "Something went wrong" (with Reload) instead of the whole app
  going blank. React requires a **class component** for this (`getDerivedStateFromError`).
- Eval graph: dots now also mark Brilliant (teal) and Great (blue) moments.

## Try this

1. Add "Miss" (Chess.com's ✗): the opponent blundered and you didn't punish it,
   i.e. the best move would have won a lot, and yours kept the position level.
2. Tune `ONLY_MOVE_GAP` and re-analyse a few games. Do the Greats feel right?
