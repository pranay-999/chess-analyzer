# Phase 21: a "club game screen" look

The review, bot and online pages now share one layout, modelled on the game screens of
big chess sites: player strips above and below a green board, and one side panel with the
move table and big buttons. Frontend only.

## What changed

| Piece | File | What it does |
|---|---|---|
| **Club theme** (new default) | `index.css`, `themeData.ts` | Dark warm-grey panels, green board. Your old choice in 🎨 still wins if you picked one. |
| **Player strip** | `components/PlayerStrip.tsx` | Avatar, name, rating, **captured pieces** with the material lead (`+4`), and a **clock box** on the right: white with ⏱ while it's running, red under 20 s. |
| **Move table** | `components/MoveList.tsx` | Opening name on top, numbered rows, **piece icons** instead of letters ("figurine notation": `Nf3` → `♘f3`). |
| **Nav buttons** | `components/NavButtons.tsx` | Big ⏮ ‹ ▶ › ⏭ buttons at the bottom of the panel. **▶ plays through the game** (one move a second). On phones they're pinned to the bottom of the screen. |
| **Tabs** (review) | `pages/GamePage.tsx` | **Moves** (verdict box + move table), **Report** (accuracy, counts, key moments), **Info** (players, date, link). |
| **Panel + Resign** | `PlayPage.tsx`, `OnlineGamePage.tsx` | Same panel; Resign sits at the bottom like a game screen. |

## How the captured pieces are worked out

No extra data needed: compare the current position with the starting one. Whatever the
**opponent** has fewer of, you captured. The lead is the difference in points
(pawn 1, knight/bishop 3, rook 5, queen 9). Counting against the game's real start FEN
(not the standard one) keeps it right for games pasted from a position.

## Board size

`.board-column` gets `max-width: max(300px, min(600px, calc(100vh - 200px)))`: up to 600px wide,
but never so tall that a player strip falls off the screen, and never smaller than 300px.
`min()`, `max()` and `calc()` let CSS do this without any JavaScript.

## Try this

1. Show the time spent per move (Chess.com PGNs contain `[%clk 0:04:57]` after every move).
2. Real avatars: Chess.com's player API returns an `avatar` URL.
