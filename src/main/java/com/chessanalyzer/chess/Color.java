package com.chessanalyzer.chess;

/**
 * The two sides. An enum is a fixed set of named constants: you can never have
 * a third colour, and comparing with == is safe.
 */
public enum Color {
    WHITE, BLACK;

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    /** Direction this side's pawns move in, measured in ranks: +1 for white (up), -1 for black (down). */
    public int forward() {
        return this == WHITE ? 1 : -1;
    }
}
