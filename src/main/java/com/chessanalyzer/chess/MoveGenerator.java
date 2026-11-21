package com.chessanalyzer.chess;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates legal moves in two steps:
 *
 *  1. Pseudo-legal moves: every move that follows how the pieces move,
 *     ignoring whether it leaves our own king in check.
 *  2. Legal filter: play each one and throw it away if our king is attacked afterwards.
 *
 * Step 2 is the simplest correct way to handle pins, moving into check, and
 * discovered checks without special cases. Real engines are cleverer, but this is
 * easy to read and fast enough for analysing games.
 *
 * Directions are written as {fileChange, rankChange} pairs, e.g. {1, 2} = one file
 * right, two ranks up (a knight jump).
 *
 * The class is package-private (no "public"): outside code calls Board.legalMoves().
 */
final class MoveGenerator {

    private static final int[][] KNIGHT_STEPS = {
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final PieceType[] PROMOTIONS = {
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};

    private MoveGenerator() {
    }

    static List<Move> legalMoves(Board board) {
        Color us = board.sideToMove();
        List<Move> legal = new ArrayList<>();
        for (Move move : pseudoLegalMoves(board)) {
            Board after = board.makeMove(move);
            if (!isSquareAttacked(after, after.kingSquare(us), us.opposite())) {
                legal.add(move);
            }
        }
        return legal;
    }

    private static List<Move> pseudoLegalMoves(Board board) {
        List<Move> moves = new ArrayList<>();
        Color us = board.sideToMove();
        for (int sq = 0; sq < 64; sq++) {
            Piece piece = board.pieceAt(sq);
            if (piece == null || piece.color() != us) {
                continue;
            }
            switch (piece.type()) {
                case PAWN -> addPawnMoves(board, sq, us, moves);
                case KNIGHT -> addSteps(board, sq, us, KNIGHT_STEPS, moves);
                case BISHOP -> addSlides(board, sq, us, BISHOP_DIRECTIONS, moves);
                case ROOK -> addSlides(board, sq, us, ROOK_DIRECTIONS, moves);
                case QUEEN -> {
                    addSlides(board, sq, us, ROOK_DIRECTIONS, moves);
                    addSlides(board, sq, us, BISHOP_DIRECTIONS, moves);
                }
                case KING -> {
                    addSteps(board, sq, us, KING_STEPS, moves);
                    addCastling(board, sq, us, moves);
                }
            }
        }
        return moves;
    }

    private static void addPawnMoves(Board board, int from, Color us, List<Move> moves) {
        int file = Square.file(from);
        int rank = Square.rank(from);
        int nextRank = rank + us.forward();
        int startRank = us == Color.WHITE ? 1 : 6;
        boolean promotes = nextRank == (us == Color.WHITE ? 7 : 0);

        // Forward one square, and two from the starting rank, only onto empty squares.
        int oneAhead = Square.of(file, nextRank);
        if (board.pieceAt(oneAhead) == null) {
            addPawnMove(from, oneAhead, promotes, moves);
            int twoAhead = Square.of(file, rank + 2 * us.forward());
            if (rank == startRank && board.pieceAt(twoAhead) == null) {
                moves.add(new Move(from, twoAhead));
            }
        }

        // Diagonal captures, including en passant.
        for (int fileChange : new int[]{-1, 1}) {
            int targetFile = file + fileChange;
            if (!Square.isOnBoard(targetFile, nextRank)) {
                continue;
            }
            int target = Square.of(targetFile, nextRank);
            Piece victim = board.pieceAt(target);
            boolean enemyThere = victim != null && victim.color() != us;
            if (enemyThere || target == board.enPassantSquare()) {
                addPawnMove(from, target, promotes, moves);
            }
        }
    }

    private static void addPawnMove(int from, int to, boolean promotes, List<Move> moves) {
        if (promotes) {
            for (PieceType type : PROMOTIONS) {
                moves.add(new Move(from, to, type));
            }
        } else {
            moves.add(new Move(from, to));
        }
    }

    /** Knights and kings: one jump in each direction, onto an empty or enemy square. */
    private static void addSteps(Board board, int from, Color us, int[][] steps, List<Move> moves) {
        for (int[] step : steps) {
            int file = Square.file(from) + step[0];
            int rank = Square.rank(from) + step[1];
            if (!Square.isOnBoard(file, rank)) {
                continue;
            }
            int to = Square.of(file, rank);
            Piece target = board.pieceAt(to);
            if (target == null || target.color() != us) {
                moves.add(new Move(from, to));
            }
        }
    }

    /** Bishops, rooks, queens: keep sliding until we hit the edge or a piece. */
    private static void addSlides(Board board, int from, Color us, int[][] directions, List<Move> moves) {
        for (int[] dir : directions) {
            int file = Square.file(from) + dir[0];
            int rank = Square.rank(from) + dir[1];
            while (Square.isOnBoard(file, rank)) {
                int to = Square.of(file, rank);
                Piece target = board.pieceAt(to);
                if (target == null) {
                    moves.add(new Move(from, to));
                } else {
                    if (target.color() != us) {
                        moves.add(new Move(from, to));   // capture, then stop
                    }
                    break;
                }
                file += dir[0];
                rank += dir[1];
            }
        }
    }

    /**
     * Castling rules: we still have the right, the squares between king and rook are
     * empty, and the king is not in check now, does not pass through an attacked
     * square, and does not land on one.
     */
    private static void addCastling(Board board, int from, Color us, List<Move> moves) {
        int rank = us == Color.WHITE ? 0 : 7;
        if (from != Square.of(4, rank)) {
            return;
        }
        Color them = us.opposite();
        Piece ownRook = Piece.of(us, PieceType.ROOK);
        if (isSquareAttacked(board, from, them)) {
            return;
        }

        if (board.canCastleKingside(us)
                && board.pieceAt(Square.of(7, rank)) == ownRook
                && isEmpty(board, rank, 5, 6)
                && !isSquareAttacked(board, Square.of(5, rank), them)
                && !isSquareAttacked(board, Square.of(6, rank), them)) {
            moves.add(new Move(from, Square.of(6, rank)));
        }

        if (board.canCastleQueenside(us)
                && board.pieceAt(Square.of(0, rank)) == ownRook
                && isEmpty(board, rank, 1, 2, 3)
                && !isSquareAttacked(board, Square.of(3, rank), them)
                && !isSquareAttacked(board, Square.of(2, rank), them)) {
            moves.add(new Move(from, Square.of(2, rank)));
        }
    }

    private static boolean isEmpty(Board board, int rank, int... files) {
        for (int file : files) {
            if (board.pieceAt(Square.of(file, rank)) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Is {@code square} attacked by any piece of colour {@code by}?
     * Trick: look OUTWARD from the square. E.g. if a knight-jump away there is an
     * enemy knight, that knight attacks us.
     */
    static boolean isSquareAttacked(Board board, int square, Color by) {
        int file = Square.file(square);
        int rank = Square.rank(square);

        // Enemy pawns attack diagonally forward, so they sit one rank "behind" the square from their view.
        Piece pawn = Piece.of(by, PieceType.PAWN);
        int pawnRank = rank - by.forward();
        for (int fileChange : new int[]{-1, 1}) {
            int f = file + fileChange;
            if (Square.isOnBoard(f, pawnRank) && board.pieceAt(Square.of(f, pawnRank)) == pawn) {
                return true;
            }
        }

        return attackedByStep(board, file, rank, KNIGHT_STEPS, Piece.of(by, PieceType.KNIGHT))
                || attackedByStep(board, file, rank, KING_STEPS, Piece.of(by, PieceType.KING))
                || attackedBySlide(board, file, rank, ROOK_DIRECTIONS,
                        Piece.of(by, PieceType.ROOK), Piece.of(by, PieceType.QUEEN))
                || attackedBySlide(board, file, rank, BISHOP_DIRECTIONS,
                        Piece.of(by, PieceType.BISHOP), Piece.of(by, PieceType.QUEEN));
    }

    private static boolean attackedByStep(Board board, int file, int rank, int[][] steps, Piece attacker) {
        for (int[] step : steps) {
            int f = file + step[0];
            int r = rank + step[1];
            if (Square.isOnBoard(f, r) && board.pieceAt(Square.of(f, r)) == attacker) {
                return true;
            }
        }
        return false;
    }

    private static boolean attackedBySlide(Board board, int file, int rank, int[][] directions,
                                           Piece slider, Piece queen) {
        for (int[] dir : directions) {
            int f = file + dir[0];
            int r = rank + dir[1];
            while (Square.isOnBoard(f, r)) {
                Piece piece = board.pieceAt(Square.of(f, r));
                if (piece != null) {
                    if (piece == slider || piece == queen) {
                        return true;
                    }
                    break;   // first piece in this direction blocks the rest
                }
                f += dir[0];
                r += dir[1];
            }
        }
        return false;
    }
}
