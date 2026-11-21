# Phases 11–12 — Themes, and importing games from anywhere (incl. phones)

## Phase 11: themes

Every colour in `index.css` is a **CSS variable** (`--bg`, `--panel`, `--accent`, …).
A theme is just one block of values:

```css
[data-theme="wood"] { --bg: #ede3d3; --panel: #faf5ec; --accent: #9a5a2b; … }
```

`theme.tsx` puts `data-theme="wood"` on `<html>`, and every colour on every page
follows. Nothing else needs to know about themes. **Auto** follows the device's
light/dark setting (`prefers-color-scheme`), even when it changes at sunset.

- The board is drawn by `react-chessboard` from JavaScript, so its square colours
  come from the theme **context** (`useTheme().boardColors`), not from CSS.
- The choice is saved in `localStorage`. A tiny script in `index.html` applies it
  **before** React loads, so the page never flashes the wrong colours.
- The 🎨 menu is `components/ThemePicker.tsx`. The data (theme and board colours) is in `themeData.ts`.

## Phase 12: import from anywhere

One page, **`/import`**, receives games from outside the site and opens their
analysis straight away. It accepts:

| You give it | What happens |
|---|---|
| a PGN | imported as before |
| a FEN | a zero-move game starting there |
| a **Chess.com link** (`chess.com/game/live/123…`) | the server looks the game up (see below) |
| a **Lichess link** (`lichess.org/AbCd1234`) | Lichess's official export API returns the PGN |
| text containing a link ("Check out my game! https://…") | the link is found inside the text (`GameLink.find`) |

### Finished games only (fair play)

Using an engine during a game is cheating on Chess.com and Lichess, and gets
accounts closed. So the **server** refuses games that are still being played:

- **Chess.com**: the game-page endpoint reports `isFinished`, and the PGN is taken
  from the player's monthly **archive**, which only contains finished games.
- **Lichess**: an unfinished game's PGN has `Result "*"`, which is rejected.
- **Pasted PGN**: a Chess.com or Lichess `Site` with `Result "*"` is rejected too.

The refusal is `GameInProgressException` → HTTP 400 with a clear message.

### Chess.com has no "game by id" API

The public API only has monthly archives per player. So `ChessComClient.fetchFinishedGamePgn`:
1. calls `www.chess.com/callback/live/game/{id}` (what Chess.com's own game page uses)
   to learn: finished? who played white/black? when did it end?
2. downloads that player's archive for the month the game **ended** and picks the
   game whose URL ends with the id.

Step 1 is **unofficial**, and Chess.com could change it. If it ever breaks, sharing
the **PGN** instead of the link still works.

### "Which side was I?"

`importGame(text, color, asPlayer)` sends your Chess.com username (remembered from
the home page). If it matches the PGN's `White` or `Black`, that side is "you", and
the board is flipped for you automatically.

### Phones

| | How a game gets here |
|---|---|
| **Android** | Chrome → ⋮ → *Install app*. The site becomes an app (**PWA**) and appears in the **Share** menu. Chess.com app → *Share* → *Chess Analyzer* → `/import?text=…` → analysis. |
| **iPhone** | Apple doesn't let web apps join the Share menu. Chess.com app → *Share → Copy PGN* (or copy the link) → our **📋 Paste from clipboard** button. *Add to Home Screen* makes it one tap. |

What makes the site installable:
- `public/manifest.json`: name, icons, colours, and **`share_target`** (Android's share menu → `/import`);
- `public/sw.js`: a minimal **service worker** (browsers require one; ours caches nothing);
- `<link rel="manifest">` and the Apple icon tags in `index.html`.

**All of this needs HTTPS** (except on `localhost`). That's why Phase 14 deploys the app.

Checked in real headless Chrome through the DevTools protocol: the service worker
is active and there are **no installability errors**.

## Try this

1. Add a "Recently imported" list on the analysis board (`GET /api/games?source=PASTED`).
2. Make the service worker cache the app shell so the site opens instantly, even on a slow connection.
3. Add more sites to `GameLink`, e.g. chess24 or a FIDE broadcast, each with its own client.
