# The React frontend

The UI is a **React + TypeScript** app in `frontend/`, built with **Vite**.

## Why React + Vite (and not Next.js)

Next.js runs its own Node.js server, a second backend next to Spring. We
already have a backend, so we only need the UI. Vite builds a plain set of
static files (HTML + JS + CSS) that Spring serves. One server, one port.

## Two ways to run it

**Development** (edit React and see changes instantly):

```bash
docker compose up -d                 # Postgres
mvn spring-boot:run                  # Spring on :8080
cd frontend && npm run dev           # React on :5173 → open http://localhost:5173
```

Vite's dev server forwards `/api/...` to Spring (see `vite.config.ts` → `proxy`).

**"Production"** (everything from Spring on :8080):

```bash
cd frontend && npm run build         # writes into src/main/resources/static/
cd .. && mvn spring-boot:run         # open http://localhost:8080
```

`src/main/resources/static/` is generated, so don't edit it; rebuild instead.
It's in `.gitignore`.

## Files

```
frontend/
├─ index.html               the single HTML page; React fills <div id="root">
├─ vite.config.ts           dev proxy + build output folder
└─ src/
   ├─ main.tsx              starts React, wraps the app in BrowserRouter
   ├─ App.tsx               header + routes:  /  → HomePage,  /games/:id → GamePage
   ├─ api.ts                every fetch() to Spring + TypeScript types matching the Java records
   ├─ chess.ts              display helpers: win %, "+1.3"/"M3", verdict colours
   ├─ index.css             all styles (dark theme, colours as CSS variables)
   ├─ pages/
   │  ├─ HomePage.tsx       username, sync, game list, paging (state kept in the URL)
   │  └─ GamePage.tsx       board, controls, analysis card, move insight
   └─ components/
      ├─ EvalBar.tsx        the vertical white/black bar
      ├─ EvalGraph.tsx      SVG chart of the whole game; click to jump
      └─ MoveList.tsx       "1. e4 e5 …" with ?? / ? / ?! / ★
```

The board itself is the `react-chessboard` library. It draws pieces from a FEN
string, plus our arrows and square highlights.

## React in 5 ideas (as used in this code)

1. **Components are functions that return UI.** `function EvalBar(props) { return <div>…</div> }`
2. **Props** are a component's inputs, passed by its parent: `<EvalBar whitePercent={62} … />`.
3. **State** (`useState`) is data that changes. Calling the setter re-runs the
   component and React updates the page. Example: `const [ply, setPly] = useState(0)`.
4. **Derived values** are just computed on every render: `fen`, `arrows` and
   `squareStyles` in `GamePage` are plain variables, not state.
5. **Effects** (`useEffect`) do things outside React: load data, start a timer
   (polling the analysis every second), listen to the keyboard. They return a
   cleanup function (cancel the timer, remove the listener).

## TypeScript ↔ Java

`api.ts` has an interface for each Java record the API returns:

```ts
// Java: public record GameSummary(long id, String white, ..., Instant playedAt)
export interface GameSummary { id: number; white: string; ...; playedAt: string }
```

If you rename a field in Java, TypeScript will flag every place in the UI that
uses the old name (run `npx tsc -b`). That's the main reason to use TS.

## Exploring your own lines (drag pieces)

On the game page you can drag a piece to try a different move. The browser has
**no chess rules**, so it asks the backend:

```
board shows a position ──► GET /api/positions/moves?fen=…   (all legal moves + SAN + resulting FEN)
you drop a piece      ──► is "e2e4" in that list?  no → piece snaps back   yes → add it to your variation
new position          ──► GET /api/positions/eval?fen=…&depth=14   (Stockfish, ~0.5 s) → eval bar + green arrow
```

- `variation.ts`: the variation is `{ fromPly, moves, index }`. `playInVariation`
  adds a move; `stepState` handles ← / →. They're pure functions, so they're easy to reason about.
- `components/VariationCard.tsx`: the "Your variation" box (moves, eval, back button).
- If you drag the move that was actually played in the game, it just follows the
  game instead of opening a variation.
- Only the side to move can pick up pieces (`canDragPiece`). Promotions always
  become a queen.
- Keys: ← → step through your line, **Esc** returns to the game.

Backend side: `position/PositionService` and `web/PositionController`. They
reuse the chess core (`Board.legalMoves`, `San.toSan`) and `EngineFactory`, with
no database involved.

## Refreshing on /games/42

React handles URLs in the browser. If you refresh on `/games/42`, the browser
asks Spring for that path. `web/SpaController` forwards it to `index.html`, and
React then shows game 42.
