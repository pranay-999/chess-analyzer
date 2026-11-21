# Phase 5 — Stockfish analysis

Goal: click **Analyse with Stockfish** on a game and get, for every move, a verdict
(Best / Good / Inaccuracy / Mistake / Blunder), the move you should have played,
an evaluation bar and graph, and an accuracy % for both players.

## The flow

```
Browser                         Spring                                   Stockfish (separate process)
───────                         ──────                                   ────────────────────────────
POST /api/games/344/analysis ─► AnalysisService.start
                                 ├ save Analysis row (PENDING)  ← committed immediately
                                 ├ runner.run(id)  ── @Async ──► background thread "analysis-1"
◄── 202 Accepted {status:PENDING}                                 │
                                                                  ├ load game + moves (one query)
GET  .../analysis  (every 1 s) ─► AnalysisService.latest          ├ markRunning
◄── {status:RUNNING, 12/67}                                       ├ for each position: ──────► "position fen …" / "go depth 16"
◄── {status:RUNNING, 40/67}                                       │      updateProgress(i)  ◄── "info … score cp 31 …" / "bestmove e2e4"
                                                                  ├ MoveAnalyzer.analyze(...)   (pure math)
                                                                  └ save results in a short transaction → DONE
◄── {status:DONE, accuracyWhite, moves:[…verdicts…]}
```

This is the **async job pattern**: a slow task becomes a database row with a
status that the client polls. You'll find it in almost every real backend
(video encoding, report exports, payments…).

## Files

| File | Why |
|---|---|
| `db/migration/V2__analysis_progress.sql` | New columns (progress, error, best_move_san). Your **second migration**: V1 is never edited. |
| `engine/Engine` | Interface: `evaluate(fen, depth)`. Phase 6's own engine will implement it too. |
| `engine/StockfishEngine` | Starts the `stockfish` program with `ProcessBuilder` and talks UCI over stdin/stdout. |
| `engine/Evaluation` | Score (centipawns or mate) from white's view, plus the best move. |
| `engine/EngineFactory` | Spring bean that opens a new engine per job (reads `stockfish.path` from YAML). |
| `analysis/MoveAnalyzer` | **Pure math**: scores → win % → verdict and accuracy. No Spring, so easy to test. |
| `analysis/Analysis`, `MoveEvaluation` | Entities for `analyses` and `move_evaluations`. |
| `analysis/AnalysisRepository` | Includes `@Modifying @Query` UPDATE statements for progress/status. |
| `analysis/AnalysisRunner` | The `@Async` background job. |
| `analysis/AnalysisService` | Starts jobs, reads results, fixes stuck jobs at startup. |
| `AsyncConfig` | `@EnableAsync`. Without it, `@Async` is silently ignored. |
| `web/AnalysisController` | `POST` (202 Accepted) and `GET` `/api/games/{id}/analysis`. |

## How a move gets its verdict

1. Stockfish evaluates the position **before** and **after** your move.
2. Each score becomes your **winning chance** (0–100%), using the formula Lichess publishes:
   `win% = 50 + 50 · (2 / (1 + e^(−0.00368 · cp)) − 1)`
3. The verdict comes from how much your winning chance **dropped**:

| Drop | Verdict |
|---|---|
| you played Stockfish's own move | ★ Best |
| < 10% | Good |
| 10–20% | ?! Inaccuracy |
| 20–30% | ? Mistake |
| ≥ 30% | ?? Blunder |

Why win % and not plain centipawns? Losing 2 pawns when you're already +10
changes nothing, but losing 2 pawns at 0.0 loses the game. Win % reflects that.

**Accuracy** per move is `103.17 · e^(−0.0435 · drop) − 3.17` (also from Lichess).
A player's accuracy is the average over their moves.

## Spring lessons in this phase

**`@Async` and the proxy / self-invocation trap.** Spring adds `@Async` and
`@Transactional` behaviour by wrapping your bean in a *proxy*. Only calls that go
through the proxy (from another bean) get the extra behaviour. That's why
`AnalysisRunner` is its own class: `this.run()` inside `AnalysisService` would
run synchronously, and nothing would warn you.

**Committing before starting background work.** `start()` is deliberately not
`@Transactional`. If it were, the background thread might look for the new row
before it was committed, and find nothing.

**`LazyInitializationException`, for real.** The first version crashed with
exactly this error. The job loaded `Analysis` in one transaction, then (after
the slow Stockfish part) called `analysis.complete(...)`, which touched the lazy
`evaluations` list on a *detached* entity. Fix: a short transaction with
`TransactionTemplate` that loads a fresh *managed* copy and changes it.
Hibernate's **dirty checking** writes the changes on commit, so no `save()` is needed.

**`@Modifying @Query`.** For small UPDATEs (progress counters) we write JPQL
directly instead of loading and saving whole entities.

**Errors in background threads.** Nobody catches an exception thrown inside an
`@Async` method, so `run()` catches everything itself and stores it as `FAILED`
with the error message.

**Restarts.** If the server stops mid-analysis, that row would say RUNNING
forever. `@EventListener(ApplicationReadyEvent.class)` marks such rows FAILED at startup.

**Limiting CPU.** `spring.task.execution.pool` caps it at 2 jobs at once; extra
requests wait in the queue. `stockfish.threads` sets how many cores each job uses.

## Talking to another program: UCI

```
> uci                         < uciok
> isready                     < readyok
> position fen <fen>
> go depth 16                 < info depth 16 ... score cp 31 ... pv e2e4 e7e5 ...
                              < bestmove e2e4 ponder e7e5
```

Scores are reported for the **side to move**; we flip them for black so they're
always from white's view. `score mate 0` + `bestmove (none)` means the side
to move is already checkmated.

## Tests

- `MoveAnalyzerTest`: win %, verdict thresholds, and a full Fool's Mate with
  hand-written scores (no engine needed).
- `StockfishEngineTest`: UCI parsing, plus 2 tests with the real Stockfish
  (`@EnabledIf`: skipped automatically on machines without it).

## Try this (to learn)

1. Change the thresholds in `MoveAnalyzer.classify` and re-analyse a game: how do the labels change?
2. Remove `@EnableAsync` from `AsyncConfig` and click Analyse. Notice the request now
   takes the whole analysis time and the progress bar never appears.
3. Add a "Great move" verdict: you played the best move and it was the only good one
   (the second-best move is ≥ 20% worse). Hint: `setoption name MultiPV value 2`.
