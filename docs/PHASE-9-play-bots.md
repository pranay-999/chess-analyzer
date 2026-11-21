# Phase 9 — Play against bots

Goal: play a real game (with clocks) against bots of different strength, then
analyse it with one click.

## How a bot game works

```
Browser (PlayPage)                                   Spring (BotService)
──────────────────                                   ───────────────────
you drag a piece ── legal? (GET /api/positions/moves) ─► Board.legalMoves()
bot's turn ─────── POST /api/bots/move {level, moves} ─► GameState.replay(moves)   ← checks EVERY move
                                                          game over? → return status only
                                                          else engine.evaluate(...) → bot move
           ◄────── {move, status, result, winner} ────────
game over → "Analyse this game" → builds a PGN → POST /api/games/import → /games/{id}
```

The server is **stateless** for bot games: the browser sends the whole move list
each time, and the server never trusts it. It replays and validates the list
with `GameState`. Online games (Phase 10) will instead keep the game **on the
server**, because there the opponent is a person and cheating matters.

## New in the chess core: `GameState` and `GameStatus`

A `Board` knows checkmate and stalemate. A *game* also needs history:

| Rule | How it's detected |
|---|---|
| Checkmate / stalemate | no legal moves; in check or not |
| Threefold repetition | the same position (FEN fields 1–4) appears 3 times in the history |
| 50-move rule | halfmove clock ≥ 100 |
| Insufficient material | only kings, a single minor piece, or bishops all on one square colour |

`GameState.play(move)` refuses illegal moves (unlike `Board.makeMove`, which
trusts its caller), so it's the safe entry point for input from users.

## Bots (`BotLevel`)

| Level | Engine | How it's weakened |
|---|---|---|
| Pawnie, Rookie | **my own engine** | search depth 1–2 |
| Casual → Master | **Stockfish** | UCI option `Skill Level` 0–20 + a limited depth |

At low `Skill Level` Stockfish deliberately picks a weaker move now and then,
so its games vary and feel more human than "full strength but shallow".

## Clocks (`frontend/src/clock.ts`)

The clock stores the **remaining time per side** plus **when the side to move
started thinking**. The display is computed as `remaining − (now − runningSince)`,
so it stays correct even if the browser tab sleeps. Pressing the clock charges the
time spent and adds the increment. A 200 ms timer redraws, and ends the game when
the side to move reaches zero.

React detail: the timer calls `setGame(g => …)` with a **function**. The function
always receives the latest game, so the timer never works on a stale copy (a
classic `setInterval` + React bug).

## Try this

1. Add a "Takeback" button that removes the last two moves (yours and the bot's).
2. Show the bot's evaluation after the game: you already have the analysis page
   one click away.
3. Add a "Random mover" bot level 0 that picks any legal move.
