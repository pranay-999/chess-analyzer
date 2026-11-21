package com.chessanalyzer.engine.myengine;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.Piece;
import com.chessanalyzer.chess.PieceType;
import com.chessanalyzer.chess.Square;

/**
 * "How good is this position?" without looking ahead. Returns centipawns from the
 * point of view of the SIDE TO MOVE (positive = good for whoever moves next). The
 * search needs that convention (negamax, see Search).
 *
 * Two ingredients, both classic:
 *  1. Material: pawn 100, knight 320, bishop 330, rook 500, queen 900.
 *  2. Piece-square tables: a bonus/penalty for WHERE a piece stands. Knights love
 *     the centre, pawns like to advance, the king hides behind pawns until the
 *     endgame, when it should walk to the centre. The numbers are Tomasz Michniewski's
 *     "Simplified Evaluation Function" (chessprogramming.org).
 *
 * Tables are written the way you look at a board from white's side: first row =
 * rank 8, last row = rank 1. Black uses the same tables mirrored vertically.
 */
final class Evaluator {

    static final int[] VALUE = {100, 320, 330, 500, 900, 0};   // indexed by PieceType.ordinal()

    private static final int[] PAWN = {
              0,   0,   0,   0,   0,   0,   0,   0,
             50,  50,  50,  50,  50,  50,  50,  50,
             10,  10,  20,  30,  30,  20,  10,  10,
              5,   5,  10,  25,  25,  10,   5,   5,
              0,   0,   0,  20,  20,   0,   0,   0,
              5,  -5, -10,   0,   0, -10,  -5,   5,
              5,  10,  10, -20, -20,  10,  10,   5,
              0,   0,   0,   0,   0,   0,   0,   0};

    private static final int[] KNIGHT = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20,   0,   0,   0,   0, -20, -40,
            -30,   0,  10,  15,  15,  10,   0, -30,
            -30,   5,  15,  20,  20,  15,   5, -30,
            -30,   0,  15,  20,  20,  15,   0, -30,
            -30,   5,  10,  15,  15,  10,   5, -30,
            -40, -20,   0,   5,   5,   0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50};

    private static final int[] BISHOP = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10,   0,   0,   0,   0,   0,   0, -10,
            -10,   0,   5,  10,  10,   5,   0, -10,
            -10,   5,   5,  10,  10,   5,   5, -10,
            -10,   0,  10,  10,  10,  10,   0, -10,
            -10,  10,  10,  10,  10,  10,  10, -10,
            -10,   5,   0,   0,   0,   0,   5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20};

    private static final int[] ROOK = {
              0,   0,   0,   0,   0,   0,   0,   0,
              5,  10,  10,  10,  10,  10,  10,   5,
             -5,   0,   0,   0,   0,   0,   0,  -5,
             -5,   0,   0,   0,   0,   0,   0,  -5,
             -5,   0,   0,   0,   0,   0,   0,  -5,
             -5,   0,   0,   0,   0,   0,   0,  -5,
             -5,   0,   0,   0,   0,   0,   0,  -5,
              0,   0,   0,   5,   5,   0,   0,   0};

    private static final int[] QUEEN = {
            -20, -10, -10,  -5,  -5, -10, -10, -20,
            -10,   0,   0,   0,   0,   0,   0, -10,
            -10,   0,   5,   5,   5,   5,   0, -10,
             -5,   0,   5,   5,   5,   5,   0,  -5,
              0,   0,   5,   5,   5,   5,   0,  -5,
            -10,   5,   5,   5,   5,   5,   0, -10,
            -10,   0,   5,   0,   0,   0,   0, -10,
            -20, -10, -10,  -5,  -5, -10, -10, -20};

    private static final int[] KING_MIDDLEGAME = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
             20,  20,   0,   0,   0,   0,  20,  20,
             20,  30,  10,   0,   0,  10,  30,  20};

    private static final int[] KING_ENDGAME = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10,   0,   0, -10, -20, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -10,  30,  40,  40,  30, -10, -30,
            -30, -10,  30,  40,  40,  30, -10, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -30,   0,   0,   0,   0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50};

    /** Below this much knight/bishop/rook/queen material (both sides together) we call it an endgame. */
    private static final int ENDGAME_MATERIAL = 1300;

    private Evaluator() {
    }

    static int evaluate(Board board) {
        int white = 0;
        int black = 0;
        int pieceMaterial = 0;   // non-pawn, non-king material on the board
        int whiteKing = -1;
        int blackKing = -1;

        for (int sq = 0; sq < 64; sq++) {
            Piece piece = board.pieceAt(sq);
            if (piece == null) {
                continue;
            }
            PieceType type = piece.type();
            if (type == PieceType.KING) {        // king's table depends on the game phase: do it after the loop
                if (piece.color() == Color.WHITE) whiteKing = sq; else blackKing = sq;
                continue;
            }
            if (type != PieceType.PAWN) {
                pieceMaterial += VALUE[type.ordinal()];
            }
            int score = VALUE[type.ordinal()] + table(type)[tableIndex(sq, piece.color())];
            if (piece.color() == Color.WHITE) white += score; else black += score;
        }

        int[] kingTable = pieceMaterial <= ENDGAME_MATERIAL ? KING_ENDGAME : KING_MIDDLEGAME;
        white += kingTable[tableIndex(whiteKing, Color.WHITE)];
        black += kingTable[tableIndex(blackKing, Color.BLACK)];

        int fromWhite = white - black;
        return board.sideToMove() == Color.WHITE ? fromWhite : -fromWhite;
    }

    /** Our squares count from a1 = 0; the tables start at a8. Black reads them upside down. */
    private static int tableIndex(int square, Color color) {
        int file = Square.file(square);
        int rank = Square.rank(square);
        return color == Color.WHITE ? (7 - rank) * 8 + file : rank * 8 + file;
    }

    private static int[] table(PieceType type) {
        return switch (type) {
            case PAWN -> PAWN;
            case KNIGHT -> KNIGHT;
            case BISHOP -> BISHOP;
            case ROOK -> ROOK;
            case QUEEN -> QUEEN;
            case KING -> KING_MIDDLEGAME;
        };
    }
}
