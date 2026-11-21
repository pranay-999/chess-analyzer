# Phase 4 — REST API for games + the board viewer

> **Update:** the plain HTML/JS pages described below were later replaced by a
> React app (see `FRONTEND-react.md`). The backend parts of this document
> (endpoints, DTOs, paging, error handling, lazy loading) are unchanged.

Goal: open http://localhost:8080, type your username, click a game, and step
through it on a board.

## Big picture: who does what

```
Browser (static HTML/CSS/JS)                Spring Boot
──────────────────────────                  ───────────
index.html + js/index.js  ── GET /api/players/{u}/games ──► GameController ─► GameService ─► GameRepository
game.html  + js/game.js   ── GET /api/games/{id}        ──► GameController ─► GameService ─► GameRepository
           + js/board.js  (draws the FEN it was given)
                          ◄────────────── JSON ───────────
```

**All the chess logic stays in Java.** The backend sends the FEN for every
position, so the JavaScript never needs to know how a knight moves. It only
draws what it receives. That keeps the frontend tiny.

Spring Boot serves everything in `src/main/resources/static/` as plain files:
`static/index.html` → `http://localhost:8080/`, `static/js/game.js` → `/js/game.js`.

## Backend files

| File | Why |
|---|---|
| `game/GameRepository` | `findByPlayerUsernameOrderByPlayedAtDesc(username, Pageable)` (paging); `findWithMovesById` with `@EntityGraph` (game + moves in one query). |
| `game/GameService` | `@Transactional(readOnly = true)`; converts entities → DTOs. |
| `game/GameSummary`, `GameDetail`, `PageResponse` | Response DTOs, i.e. the exact JSON the browser gets. |
| `game/GameNotFoundException` | Thrown for an unknown id. |
| `web/GameController` | The two GET endpoints. |
| `web/ApiExceptionHandler` | `@RestControllerAdvice`: exceptions → 404/400 JSON (`ProblemDetail`). |

## API

| Request | Response |
|---|---|
| `GET /api/players/hikaru/games?page=0&size=20` | `{content:[GameSummary...], page, size, totalElements, totalPages}` |
| `GET /api/games/350` | `{summary, chesscomUrl, startFen, moves:[{ply, san, uci, fenAfter}]}` |
| `GET /api/games/999999` | 404 `{"status":404,"detail":"Game not found: 999999",...}` |
| `GET /api/games/abc` | 400 Bad Request |

## Concepts to understand

**Pagination.** `PageRequest.of(page, size)` makes Spring Data add `LIMIT/OFFSET`
and run a `COUNT(*)`. Never return "all rows" from an API, because some players
have 50,000 games.

**Lazy loading and `LazyInitializationException`.** `Game.moves` is LAZY. If you
touch it after the transaction has ended (for example in the controller), Hibernate
can't query any more and throws. Fixes used here: (1) do the entity → DTO
conversion inside the `@Transactional` service method, and (2) load the moves
up front with `@EntityGraph` so it's a single JOIN query instead of two.

**DTOs, not entities, in responses.** We control exactly what JSON goes out, and
we never accidentally serialize a lazy relation or an internal field.

**Global error handling.** One `@RestControllerAdvice` class handles errors for
all controllers. `ProblemDetail` is the standard error JSON. The JS shows its
`detail` field.

## Frontend files (plain HTML/CSS/JS, no frameworks)

| File | What it does |
|---|---|
| `index.html` / `js/index.js` | Username form, Sync button (`POST .../sync`), games table, paging. |
| `game.html` / `js/game.js` | Loads one game; buttons, ← → keys and clicking a move all call `show(ply)`. |
| `js/board.js` | `renderBoard(element, fen, flipped, lastMove)`: 64 `<div>`s in a CSS grid, with Unicode pieces. |
| `css/style.css` | All styles; colours are CSS variables at the top. |

JS ideas to notice: `fetch()` + `await response.json()`; `textContent` instead
of `innerHTML` for data (so an odd username can't inject HTML); event listeners;
`URLSearchParams` to read `?id=42`.

## Tests

- `GameControllerTest`: **MockMvc** sends fake HTTP requests to the controller
  (no server is started) and checks the status codes and JSON fields with `jsonPath`.
  The service is a Mockito mock.
- `GameSummaryTest`: WIN/LOSS/DRAW from your point of view.

## Run it

```bash
docker compose up -d
mvn spring-boot:run
```

Then open http://localhost:8080. If you're running the app from your IDE,
restart it after pulling in new code, because a running app does not reload
new classes.

## Try this (to learn)

1. Add a filter: `GET /api/players/{u}/games?timeClass=blitz` (new repository
   method `findByPlayerUsernameAndTimeClassOrderByPlayedAtDesc`).
2. In `game.js`, show "Move 12 of 64" under the board.
3. Remove `@Transactional(readOnly = true)` from `GameService`, remove the
   `@EntityGraph`, open a game, and read the `LazyInitializationException`. Then put both back.
