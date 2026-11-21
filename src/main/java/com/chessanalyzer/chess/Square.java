package com.chessanalyzer.chess;

/**
 * Helpers for board squares. A square is just an int from 0 to 63:
 *
 * <pre>
 *   rank 8 | 56 57 58 59 60 61 62 63
 *   ...
 *   rank 2 |  8  9 10 11 12 13 14 15
 *   rank 1 |  0  1  2  3  4  5  6  7
 *            a  b  c  d  e  f  g  h
 * </pre>
 *
 * index = rank * 8 + file, where file 0 = 'a' and rank 0 = '1'.
 * Using plain ints (not objects) keeps the move generator simple and fast.
 */
public final class Square {

    /** "No square", e.g. when there is no en-passant target. */
    public static final int NONE = -1;

    private Square() {
        // only static helpers, never instantiated
    }

    public static int of(int file, int rank) {
        return rank * 8 + file;
    }

    public static int file(int square) {
        return square % 8;
    }

    public static int rank(int square) {
        return square / 8;
    }

    public static boolean isOnBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    /** 12 -> "e2" */
    public static String name(int square) {
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }

    /** "e2" -> 12 */
    public static int parse(String name) {
        if (name.length() != 2) {
            throw new IllegalArgumentException("Bad square: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (!isOnBoard(file, rank)) {
            throw new IllegalArgumentException("Bad square: " + name);
        }
        return of(file, rank);
    }
}
