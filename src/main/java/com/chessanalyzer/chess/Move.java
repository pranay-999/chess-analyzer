package com.chessanalyzer.chess;

/**
 * A move: from-square, to-square, and (only for pawn promotions) the new piece type.
 *
 * A record is a compact immutable data class: Java generates the constructor,
 * the accessors from(), to(), promotion(), plus equals() and hashCode().
 * equals() matters here: two Move objects with the same fields are equal, so we
 * can check `legalMoves.contains(Move.fromUci("e2e4"))`.
 *
 * Castling is written as the king's move (e1g1) and en passant as the pawn's
 * diagonal move; Board.makeMove recognises both. This matches UCI, the text
 * protocol Stockfish uses.
 */
public record Move(int from, int to, PieceType promotion) {

    public Move(int from, int to) {
        this(from, to, null);
    }

    /** e.g. "e2e4", or "e7e8q" for a promotion. */
    public String toUci() {
        String uci = Square.name(from) + Square.name(to);
        return promotion == null ? uci : uci + promotion.letter();
    }

    public static Move fromUci(String uci) {
        if (uci.length() != 4 && uci.length() != 5) {
            throw new IllegalArgumentException("Bad UCI move: " + uci);
        }
        int from = Square.parse(uci.substring(0, 2));
        int to = Square.parse(uci.substring(2, 4));
        PieceType promotion = uci.length() == 5 ? PieceType.fromLetter(uci.charAt(4)) : null;
        return new Move(from, to, promotion);
    }

    @Override
    public String toString() {
        return toUci();
    }
}
