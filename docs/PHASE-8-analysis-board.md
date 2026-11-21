# Phase 8 — Analysis board (paste a PGN or FEN)

Goal: analyse **any** game or position, not only Chess.com imports. Paste it,
and it opens on the normal game page with replay, Stockfish analysis and drag-to-explore.

## The trick: reuse, don't rebuild

Instead of writing a second board page, a pasted text becomes a normal `Game` row:

| You paste | Stored as |
|---|---|
| PGN (whole game) | a game with all its moves, `source = PASTED` |
| FEN (one position) | a game with **zero moves** whose PGN is just `[SetUp "1"] [FEN "…"]` |

`PgnParser` already understood a `[FEN]` header (Phase 2), so the game page
starts from that position, and dragging pieces there opens a variation, as before.
Everything else (analysis, eval bar, engines) works without any changes.

## Files

| File | Why |
|---|---|
| `db/migration/V3__pasted_games.sql` | `player_id` and `chesscom_url` become optional; new `source` column. |
| `game/GameSource` | Enum `CHESSCOM` / `PASTED`. |
| `game/Game.pasted(...)` | A **static factory method**: a named way to build a game with no Chess.com data. |
| `game/PastedGameService` | Detects FEN vs PGN, validates, reads the headers (names, Elo, date, result) and stores the game. |
| `web/GameController` → `POST /api/games/import` | `@RequestBody` JSON in; **201 Created** with a `Location` header out. |
| `chess/Board.fromFen` | Now rejects positions without exactly one king per side. |
| `frontend/src/pages/AnalyzePage.tsx` | The paste box, "I played White/Black", examples, and "Empty board". |

## Things worth noticing

**`@RequestBody`.** Until now every input came from the URL (`@PathVariable`,
`@RequestParam`). A whole PGN is too long for a URL, so it travels as a JSON body,
and Spring turns `{"text": "...", "playerColor": "WHITE"}` into the `ImportRequest` record.

**201 Created.** A POST that creates something should answer `201` plus a
`Location: /api/games/5609` header pointing at the new resource
(`ResponseEntity.created(uri)`).

**A test that found a real bug.** The first version accepted a FEN with no black
king, because `legalMoves()` only needs the king of the side to move. The
integration test `badPastesGiveReadable400s` failed and exposed it. The fix
went into `Board.fromFen` itself, so every entry point is protected, including
`/api/positions/*`, which would otherwise send such a position to Stockfish and could crash it.

**Readable errors.** Bad input throws `IllegalArgumentException`, which becomes
HTTP 400 with a message the page shows as-is, for example
`Illegal move 'Ke3' in position rnbqkbnr/…` or
`Only standard chess is supported (this game is Chess960)`.

## Try this

1. Paste a game from Lichess (Share → PGN). Its `[WhiteElo]` / `[Date]` headers are picked up.
2. Copy a FEN from any chess puzzle site and solve it by dragging pieces.
   Stockfish's arrow shows whether you found the best move.
3. Add `GET /api/games/pasted?page=0` that lists your pasted games
   (repository method `findBySourceOrderByPlayedAtDesc`).
