# Phase 19: move badges on the board, your own arrows and marks, pre-moves

All three are frontend-only (React). The server didn't change.

## 1. Move badge on the board (`pages/GamePage.tsx` → `MoveBadge`)

Like Chess.com, the label of the move that led to the current position (!!, !, ★, ✓,
?!, ?, ??) sits in the corner of the square the piece landed on.

The board library doesn't know about it. It's a `<span>` laid **over** the board:

- each square is 1/8 = **12.5%** of the board, so `e4` → column 4, row 4 → `left`/`top` in %;
  flipped boards just count from the other side (`7 - file`).
- `.board` is a CSS **size container** (`container-type: inline-size`), so the badge
  uses `cqw` units (1% of the board's width). It grows and shrinks with the board,
  on a phone too.
- `pointer-events: none`, so clicks go straight through to the square below.

## 2. Draw arrows and mark squares (`boardInput.ts`)

| Mouse | Effect |
|---|---|
| right-drag from one square to another | arrow (hold Shift / Ctrl / Alt for other colours) |
| right-click a square | mark it red; right-click again to remove |
| left-click anywhere | clears arrows and marks |

The arrows are built into react-chessboard (`allowDrawingArrows`, on by default). We had
turned them off on the play and online boards, and now they're on everywhere. Marks are
ours. **Why not the library's `onSquareRightClick`?** It uses the browser's `contextmenu`
event, which on Linux and Mac fires as the button goes **down**. So every arrow drag
would also mark its start square. Instead we remember the square on right-button
`mousedown` and mark it only if `mouseup` happens on the **same** square.

Marks are stored with the FEN they belong to (`{ fen, squares }`), so making a move
clears them automatically, with no `useEffect`.

## 3. Pre-moves (bot and online games)

While your opponent is thinking, drag (or tap-tap) your next move. The two squares turn
blue. When it's your turn and the server's legal moves arrive:

- still legal → it's played at once (the server checks it as usual, so you can't cheat with it);
- not legal any more (e.g. the piece was captured) → it's silently dropped.

Right-click or tap elsewhere to cancel it. One pre-move at a time.

How it's built (`useBoardInput({ …, premoveColor })`):
- `premoveColor` = your colour while the game is running, else `null`.
- The queued move is **not** tied to a FEN (it has to survive the opponent's move).
- An **effect** waits for `legal` to arrive, then calls `onMove`: an effect is right here,
  because it reacts to data coming from the server.
- When the game ends, `premoveColor` changes. We clear the queue **during render** (React's
  "adjust state when a prop changes" pattern) instead of in another effect.

## Try this

1. Allow several pre-moves in a row (a queue instead of one move).
2. Draw the pre-move as a faded piece on its target square.
