# Phase 10 — Accounts and online play

Goal: sign up, log in, or play as a guest; create a game, share its link or post it
in the lobby; play another person live, with clocks; then analyse the game.

## Architecture

```
 Browser A                       Spring Boot                                 Browser B
 ─────────                       ───────────                                 ─────────
 POST /api/online/games/{id}/moves ──► SecurityFilterChain (session? CSRF ok?)
   {"uci":"e2e4"}                       │
                                        ▼
                                  OnlineGameService ── load ──► online_games (Postgres)
                                        │  game.play(user, uci, now)   ← all rules + clocks here
                                        │  saveAndFlush  (optimistic lock check)
                                        ▼
                                  SimpMessagingTemplate.convertAndSend("/topic/games/{id}", view)
 ◄────────── WebSocket (STOMP) ─────────┴──────────── WebSocket (STOMP) ─────────►
```

- **Commands** (create, join, move, resign) are ordinary authenticated REST calls.
- **Updates** are **pushed** over a WebSocket to everyone subscribed to that game.
- The **server is the referee**: it holds the moves and clocks. A browser can only
  *ask* to move, so editing JavaScript can't cheat.

## Files

| File | Why |
|---|---|
| `V4__users_and_online_games.sql` | `users` and `online_games` tables. |
| `user/User`, `UserRepository` | Accounts. Guests are real rows with no password. |
| `user/AuthService` | Sign-up validation, BCrypt hashing, password check, guest names. |
| `user/UserPrincipal` | "Who is logged in", stored in the HTTP session. |
| `SecurityConfig` | Filter chain: sessions, CSRF (`csrf.spa()`), 401 instead of redirects, logout. |
| `web/AuthController` | `/api/auth/me`, `/signup`, `/login`, `/guest`; starts the session. |
| `online/OnlineGame` | The entity **with the rules** (join, play, resign, clocks, flagging). |
| `online/OnlineGameService` | Load → call the entity → save → broadcast. Stores finished games for analysis. |
| `online/ClockChecker` | `@Scheduled` every second: ends games where someone ran out of time while idle. |
| `WebSocketConfig` | STOMP endpoint `/ws`, in-memory broker for `/topic/**`. |
| `chess/PgnWriter` | Moves → PGN, used to store finished online games. |
| `frontend/src/auth.tsx` | React **Context** with the logged-in user (`useAuth()`). |
| `frontend/src/live.ts` | One shared STOMP connection + a `useTopic()` hook. |
| `frontend/src/pages/LoginPage`, `OnlinePage`, `OnlineGamePage` | The UI. |

## Security, step by step

**Passwords.** Stored as `{bcrypt}$2a$10$…`. BCrypt is one-way and deliberately slow,
which makes guessing millions of passwords from a stolen database impractical.
It only reads 72 bytes, hence the 72-character limit.

**Sessions.** After login, `AuthController` puts a `SecurityContext` into the HTTP
session. The browser only has the random session id in an **HttpOnly** cookie, so
JavaScript can't read it. `request.changeSessionId()` at login prevents
*session fixation*.

**CSRF.** Because cookies are sent automatically, a malicious site could make your
browser POST to us. With `csrf.spa()`, Spring sets a readable `XSRF-TOKEN` cookie
and requires every POST to echo it in an `X-XSRF-TOKEN` header. Our `api.ts`
does this, but other sites can't read our cookies, so they can't. `SameSite=Lax`
on the session cookie is a second line of defence. The test
`postWithoutCsrfTokenIsRejected` proves it: such a request gets 403.

**Authorization.** `/api/online/**` and `/ws` need a login. Everything else
(analysis tools) stays public. APIs answer `401` instead of redirecting to a login
page (`HttpStatusEntryPoint`).

## Clocks on the server

The table stores each side's time **at the start of the current turn** plus
`turn_started_at`. Time left now = `stored − (now − turn_started_at)` for the
side to move. Nothing is written every second. Time is only charged when a move
arrives (plus the increment).

If a player just stops moving, no request arrives, so `ClockChecker` looks at
running timed games every second and flags the ones out of time. The browser counts
down locally from when it **received** the latest state, so the two computers'
clocks don't need to agree.

`OnlineGameTest` checks the clock rules with fixed `Instant`s: exact and instant,
with no `Thread.sleep`.

## Concurrency: optimistic locking

`OnlineGame` has `@Version long version`. Every UPDATE becomes
`... WHERE id = ? AND version = ?` and increments the version. If a move and the
clock checker both load version 7, only the first save succeeds. The second gets
`ObjectOptimisticLockingFailureException`, which becomes **409 Conflict**, instead of silently
overwriting the first. `saveAndFlush` makes that check happen **before** we broadcast,
so nobody is told about a state that was never saved.

## Tests

- `OnlineGameTest`: clocks, increment, flagging, turn order, resign. No database.
- `OnlinePlayIntegrationTest`: sign-up/login/logout/guest, 401, CSRF 403, and a full
  game between two `TestBrowser`s, including a real **WebSocket** subscription that
  receives the opponent's move.
- `TestBrowser`: a mini browser for tests that keeps cookies and sends the CSRF header.

## Try this

1. **Draw offers**: `offerDraw` / `acceptDraw` methods on `OnlineGame` + two endpoints + buttons.
2. **Rematch**: after a game, create a new one with colours swapped and push its id to both players.
3. **Ratings**: an Elo column on `users`, updated when a game finishes.
4. **Several servers**: the in-memory broker only works on one server. Replace
   `enableSimpleBroker` with a relay to RabbitMQ (`enableStompBrokerRelay`).
