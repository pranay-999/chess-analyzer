# Phase 3 — Importing games from Chess.com

Goal: `POST /api/players/{username}/sync` downloads your games from Chess.com,
replays each one with our chess core, and stores the game + every move in Postgres.

## The layers (this is the classic Spring structure)

```
HTTP POST /api/players/hikaru/sync?months=1
        │
        ▼
PlayerController          web layer: reads URL/params, returns JSON. No logic.
        │ importService.sync("hikaru", 1)
        ▼
GameImportService         business logic: what to fetch, skip, save
   │            │                 │
   ▼            ▼                 ▼
ChessComClient  PgnParser/replay  PlayerRepository / GameRepository
(HTTP → DTOs)   (chess core)      (Spring Data JPA → SQL → Postgres)
```

Each layer only talks to the one below it. That's why you can test the service
with fake (mock) client and repositories, and swap Chess.com for Lichess later
without touching the database code.

## Files and why each exists

| File | Layer | Why |
|---|---|---|
| `chesscom/ChessComClient` | HTTP client | All Chess.com URLs/JSON in one place. Uses Spring's `RestClient`. |
| `chesscom/ChessComGame` | DTO | Java shape of Chess.com's JSON. Jackson fills it in. |
| `chesscom/PlayerNotFoundException` | error | `@ResponseStatus(404)` → unknown username gives HTTP 404, not 500. |
| `game/Player`, `Game`, `GameMove` | entities | Java objects ⇄ rows in `players`, `games`, `moves`. |
| `game/PlayerRepository`, `GameRepository` | repositories | Interfaces only; Spring writes the SQL. |
| `game/GameImportService` | service | The import algorithm. |
| `game/SyncResult` | response DTO | What the endpoint returns as JSON. |
| `web/PlayerController` | controller | The `/sync` endpoint. |

## DTO vs Entity — why two classes for "a game"?

- `ChessComGame` (DTO) = **their** format. Changes when Chess.com changes their API.
- `Game` (entity) = **our** format. Changes only when we change our database.

The service converts one into the other (`toEntity`). Keeping them separate
means a change on Chess.com's side never leaks into our database design.

## How the sync works

1. Lower-case the username (Chess.com usernames ignore case).
2. Get the list of monthly archive URLs (404 → `PlayerNotFoundException`).
3. Find the player row, or create it.
4. Decide which months to fetch:
   - `?months=N` → the last N months,
   - first sync → all months,
   - later syncs → from the month of the last sync onward (incremental).
5. For each game: skip variants (`rules != "chess"`), skip games already stored
   (`existsByChesscomUrl`), otherwise parse + replay the PGN and save.
6. Record `last_synced_at` and return the counts.

## JPA things to notice

- **`@ManyToOne(fetch = LAZY)`**: `Game.player` is a real `Player` object, but it's
  loaded from the DB only when you use it.
- **`@OneToMany(mappedBy = "game", cascade = ALL)`**: `games.save(game)` also inserts
  all its `GameMove`s. That's why there is no `GameMoveRepository`.
- **`Game.addMove(...)`** sets both sides of the relationship. If you forget the
  `move.setGame(this)` part, Hibernate inserts moves with no `game_id`.
- **`ddl-auto: validate`**: at startup Hibernate checks every entity against the
  real tables and refuses to start if they don't match.

## Transactions (a common interview question)

`GameImportService.sync` is **not** `@Transactional`. A sync makes many slow HTTP
calls; one transaction around all of it would hold a DB connection open the
whole time. Instead each `games.save(...)` is its own short transaction (Spring
Data repository methods are transactional by default). A game is saved with all
its moves, or not at all. If game #200 fails, games 1–199 are still saved.

## Tests

- `ChessComClientTest`: `MockRestServiceServer` fakes Chess.com, so no internet is
  needed. Checks the URL, header, JSON parsing and 404 handling.
- `GameImportServiceTest`: **Mockito** mocks for the client and repositories.
  Checks that variants/duplicates are skipped, the colour is detected, moves are
  built, and bad PGN is counted as failed. `ArgumentCaptor` grabs the `Game` passed to `save`.

## Try it

```bash
docker compose up -d
mvn spring-boot:run
curl -X POST "localhost:8080/api/players/YOUR_USERNAME/sync"
```

Then in pgAdmin (localhost:5433, chess/chess):

```sql
SELECT player_color, result, count(*) FROM games GROUP BY 1, 2;
```

To remove a player and all their games and moves (the foreign keys use ON DELETE CASCADE):

```sql
DELETE FROM players WHERE username = 'hikaru';
```

## Try this (to learn)

1. Add `List<Game> findByPlayerOrderByPlayedAtDesc(Player player)` to
   `GameRepository`. Spring generates the query from the name.
2. Add a count of games won to `SyncResult`.
3. Delete `move.setGame(this)` in `Game.addMove`, run a sync, and read the error.
