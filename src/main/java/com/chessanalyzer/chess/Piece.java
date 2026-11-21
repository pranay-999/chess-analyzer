package com.chessanalyzer.chess;

/**
 * A concrete piece = colour + type. There are exactly 12, so an enum fits.
 * In FEN, white pieces are uppercase ("N") and black pieces lowercase ("n").
 */
public enum Piece {
    WHITE_PAWN(Color.WHITE, PieceType.PAWN),
    WHITE_KNIGHT(Color.WHITE, PieceType.KNIGHT),
    WHITE_BISHOP(Color.WHITE, PieceType.BISHOP),
    WHITE_ROOK(Color.WHITE, PieceType.ROOK),
    WHITE_QUEEN(Color.WHITE, PieceType.QUEEN),
    WHITE_KING(Color.WHITE, PieceType.KING),
    BLACK_PAWN(Color.BLACK, PieceType.PAWN),
    BLACK_KNIGHT(Color.BLACK, PieceType.KNIGHT),
    BLACK_BISHOP(Color.BLACK, PieceType.BISHOP),
    BLACK_ROOK(Color.BLACK, PieceType.ROOK),
    BLACK_QUEEN(Color.BLACK, PieceType.QUEEN),
    BLACK_KING(Color.BLACK, PieceType.KING);

    // Lookup table [colour][type] -> piece, filled once when the class loads.
    // The move generator calls Piece.of(...) millions of times, so this is faster than looping.
    private static final Piece[][] LOOKUP = new Piece[2][6];

    static {
        for (Piece piece : values()) {
            LOOKUP[piece.color.ordinal()][piece.type.ordinal()] = piece;
        }
    }

    private final Color color;
    private final PieceType type;

    Piece(Color color, PieceType type) {
        this.color = color;
        this.type = type;
    }

    public Color color() {
        return color;
    }

    public PieceType type() {
        return type;
    }

    public char fenChar() {
        return color == Color.WHITE ? Character.toUpperCase(type.letter()) : type.letter();
    }

    public static Piece of(Color color, PieceType type) {
        return LOOKUP[color.ordinal()][type.ordinal()];
    }

    public static Piece fromFenChar(char c) {
        Color color = Character.isUpperCase(c) ? Color.WHITE : Color.BLACK;
        return of(color, PieceType.fromLetter(c));
    }
}
