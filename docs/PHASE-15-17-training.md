# Phases 15–17 — Tap-to-move, puzzles from your mistakes, stats dashboard

## Phase 15: tap-to-move

Dragging small pieces on a phone is fiddly, so every board now also accepts
**tap a piece → tap a square**. Dots show where the tapped piece can go.

All of it lives in one hook, `frontend/src/boardInput.ts`:

```ts
const input = useBoardInput({ fen, legal, enabled, onMove })
<Chessboard options={{ onPieceDrop: input.onPieceDrop, onSquareClick: input.onSquareClick,
                       canDragPiece: input.canDragPiece, squareStyles: input.inputStyles, … }} />
```

- Dragging and tapping both end in the same `onMove(move)`. Each page only decides
  what a move *means*: a variation (game page), a bot game, an online game, or a puzzle answer.
- It replaced three copies of the same drop-handling code.
- The tapped square is stored **together with the FEN it belongs to**, so a new
  position automatically clears it. No extra `useEffect` is needed.

## Phase 16: puzzles from your own mistakes

```
Stockfish analysis finishes ──► PuzzleService.createFromAnalysis
                                 for each of YOUR moves rated Mistake/Blunder:
                                   position before it + your move + Stockfish's move → puzzles table
/puzzles ──► GET /api/puzzles/next ──► the most overdue puzzle (no solution sent!)
answer   ──► POST /api/puzzles/{id}/attempt {uci} ──► right/wrong + solution + next due date
```

| Rule | Why |
|---|---|
| Only **Stockfish** analyses | Our own engine is too weak to be trusted as a teacher |
| Only **your** moves (`Game.playerColor`) | The opponent's blunders aren't your lesson |
| A **different** move counts if Stockfish says you lose < 5% winning chance with it | Positions often have two good moves |
| The solution isn't in the GET response | No peeking in the browser's network tab |
| Puzzles for older analyses are made once at startup (`ApplicationReadyEvent`) | Your earlier work counts too |

**Spaced repetition** (`Puzzle.recordAttempt`): solved → due again in 1 day, then
2, 4, 8 … up to 60. Failed (or "Show solution") → back to 0, due in 5 minutes. Easy
puzzles fade away, and your weak spots keep coming back. The schedule is unit-tested
with a fixed "now" (`PuzzleTest`). `AppIntegrationTest.yourBlundersBecomePuzzles`
runs the whole path with real Stockfish.

**Whose puzzles?** `?player=edward_nova` matches games **synced** for that name *or*
games where that name played White/Black (games imported by link or PGN have
no synced player, only names).

## Phase 17: stats dashboard

`/stats?user=edward_nova&tc=rapid`, computed by **PostgreSQL**, not Java:

| Number | SQL technique |
|---|---|
| Your colour, rating and result per game | a `WITH` (CTE) `my`/`scored` reused by every query, `CASE` |
| Win / draw / loss splits | `count(*) FILTER (WHERE outcome = 'WIN')`, `GROUP BY` |
| Opening names | regular expressions on the PGN: Chess.com's `ECOUrl` (`substring(pgn from '…')`), else Lichess's `[Opening]`, else the ECO code |
| Rating / accuracy per month | `date_trunc('month', …)`, `avg`, `to_char` |
| Latest analysis per game | `DISTINCT ON (game_id) … ORDER BY game_id, created_at DESC` |
| Your mistakes by phase | join `move_evaluations`; your moves = odd plies as White (`ply % 2 = 1`) |

All 5,260 of your games are summarised in about **0.25 s**, because Postgres scans and
counts, and Java receives a few small rows. It uses `JdbcClient` (plain SQL, Spring
6.1+), which is the right tool when you want **numbers**, not entities.

### The charts (`components/charts.tsx`, no chart library)

- **Line chart** (rating, accuracy): a 2px line with a 10% area wash; the x axis is real
  time, so months without games stay gaps. A crosshair snaps to the nearest month with
  a tooltip, ← → on the keyboard step through the months, and the newest value is the only direct label.
- **Segmented bar** (win/draw/loss): blocks with 2px gaps and the share printed inside when
  it fits. Green and red are never next to each other (draw grey sits between them);
  hover or focus shows the count.
- **Stacked bars** (mistakes by phase): one shared scale, total at the tip.
- **Mistake colours:** the move list's yellow/orange/red failed the colour-blindness check
  (yellow and orange are too close), so this chart uses **one hue from light to dark**
  (`--sev-1..3`). It was validated as an ordered scale on both light and dark backgrounds.
- Every chart has a legend or title, plus a **"Show as table"** view, so no number
  depends on hovering or on telling colours apart.

## Try this

1. Puzzles: add a "streak" counter, and a filter for "only blunders".
2. Stats: a date filter (last 30 / 90 days) next to the time-control filter.
   It's one more `AND played_at >= …` in `MY_GAMES`.
3. Stats: "your worst opening as Black". There are enough numbers already; it needs one more `ORDER BY`.
