package com.chessanalyzer.chess;

/** The six kinds of piece, each with the lowercase letter used in FEN/UCI ("n" = knight). */
public enum PieceType {
    PAWN('p'), KNIGHT('n'), BISHOP('b'), ROOK('r'), QUEEN('q'), KING('k');

    private final char letter;

    PieceType(char letter) {
        this.letter = letter;
    }

    public char letter() {
        return letter;
    }

    public static PieceType fromLetter(char c) {
        char lower = Character.toLowerCase(c);
        for (PieceType type : values()) {
            if (type.letter == lower) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown piece letter: " + c);
    }
}
