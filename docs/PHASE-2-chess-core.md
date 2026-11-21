# Phase 2 — Chess core (rules, FEN, SAN, PGN)

Goal: plain Java code that knows the rules of chess, so we can replay any
Chess.com game move by move and know the exact position after each move.

There is **no Spring** in `com.chessanalyzer.chess`. That is deliberate:
the rules of chess don't care about web servers or databases. Keeping this
package independent means:
- tests run in milliseconds (no app startup, no DB),
- later the Spring services just *use* it, like any library.

## Files and why each exists

| File | Role |
|---|---|
| `Color` | enum WHITE/BLACK. `forward()` = pawn direction (+1/−1 rank). |
| `PieceType` | enum PAWN…KING with its letter (`n` = knight). |
| `Piece` | enum of the 12 real pieces (WHITE_KNIGHT…). Knows its FEN char. |
| `Square` | squares are ints 0–63 (`a1`=0, `h8`=63). Converts `"e4"` ⇄ 28. |
| `Move` | record (from, to, promotion). Converts to/from UCI (`"e2e4"`). |
| `Board` | a position + FEN read/write + `makeMove()` + check/mate detection. |
| `MoveGenerator` | finds all legal moves. Package-private: use `board.legalMoves()`. |
| `San` | human notation ("Nbd7", "O-O", "exd6") ⇄ `Move`. |
| `PgnParser` / `PgnGame` | reads Chess.com PGN text → headers + SAN list → replay. |
| `PlayedMove` | one replayed move: ply, SAN, UCI, FEN after. Matches the `moves` table. |

## The three notations (you'll see all of them)

| Notation | Example | Used by |
|---|---|---|
| **FEN** | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | describes a whole position; Stockfish takes this |
| **SAN** | `e4`, `Nf3`, `Qxf7#` | humans, PGN files, Chess.com |
| **UCI** | `e2e4`, `g1f3`, `e7e8q` | engines (Stockfish replies with this) |

## How legal move generation works

```
legalMoves(board)
  ├─ pseudoLegalMoves: for each of our pieces, list moves by its movement rules
  │     pawn   → push 1, push 2 from start, diagonal captures, en passant, promotions
  │     knight/king → fixed jumps   (addSteps)
  │     bishop/rook/queen → slide until blocked (addSlides)
  │     king   → also castling (rights + empty path + not through check)
  └─ for each: play it on a copy; keep it only if our king is NOT attacked afterwards
```

`isSquareAttacked` looks *outward* from a square: "is there an enemy knight a
knight-jump away? an enemy rook/queen along a straight line before any blocker?"

## Key Java ideas used

- **enum**: fixed set of constants with fields/methods (`Piece.WHITE_KNIGHT.fenChar()`).
- **record**: immutable data class with auto equals/hashCode (`Move`, `PlayedMove`, `PgnGame`).
- **Immutability / copy-make**: `makeMove` returns a new `Board`; the old one never changes.
- **switch expressions** (`case "w" -> Color.WHITE;`) and **text blocks** (`"""..."""` in tests).
- **static factory methods**: `Board.fromFen(...)` instead of a public constructor.
- **package-private** classes: `MoveGenerator` is hidden; `Board` is the public face.

## How we know it's correct: tests

Run: `mvn test`

- **PerftTest**: counts all move sequences to depth N from famous positions and
  compares with published numbers (e.g. start position depth 4 = 197,281).
  One wrong rule anywhere → wrong count. This is how real engine authors test.
- **BoardTest**: FEN round-trip, en passant, castling, mate/stalemate.
- **SanTest**: SAN writing/reading incl. disambiguation (`Nbd2`, `R1a3`), promotion, mate.
- **PgnParserTest**: Chess.com-style PGN with `{[%clk ...]}` comments.

Also checked by hand: 463 real Chess.com games (40,964 moves) replayed with
every final position matching Chess.com's own `CurrentPosition` header.
Chess960 games (`[Variant "Chess960"]`) use different castling rules and will
be skipped during import.

## Try this (to learn)

1. Write a test: from the start position, after `e4 e5 Nf3`, how many legal
   moves does black have? (Use `San.fromSan` + `makeMove`, then `legalMoves().size()`.)
2. Break something on purpose: in `MoveGenerator.addCastling`, delete the
   "not through an attacked square" check and run `mvn test`. Watch which perft
   lines fail, then undo.
3. Add `Board.isInsufficientMaterial()` (king vs king) with a test.
