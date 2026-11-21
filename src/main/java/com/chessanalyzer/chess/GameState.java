package com.chessanalyzer.chess;

import java.util.ArrayList;
import java.util.List;

/**
 * A game being PLAYED: every position so far, so we can enforce the rules that
 * need history (threefold repetition), plus the end-of-game checks.
 *
 * A Board alone knows checkmate and stalemate; GameState adds:
 *  - threefold repetition: the same position (pieces, side to move, castling,
 *    en passant) occurred 3 times;
 *  - the 50-move rule: 50 moves by each side (100 plies) without a capture or pawn move;
 *  - insufficient material: nobody can possibly checkmate (e.g. king vs king + bishop).
 *
 * Immutable like Board: play(...) returns a new GameState.
 */
public final class GameState {

    private final List<Board> positions;   // positions.getFirst() = start, getLast() = current

    private GameState(List<Board> positions) {
        this.positions = positions;
    }

    public static GameState start(Board board) {
        return new GameState(List.of(board));
    }

    /** Replays UCI moves ("e2e4", ...) from a start position; throws IllegalArgumentException on an illegal one. */
    public static GameState replay(Board start, List<String> uciMoves) {
        GameState state = start(start);
        for (String uci : uciMoves) {
            state = state.play(Move.fromUci(uci));
        }
        return state;
    }

    /** Plays a move after checking it is legal (unlike Board.makeMove, which trusts the caller). */
    public GameState play(Move move) {
        if (status().isOver()) {
            throw new IllegalArgumentException("The game is already over (" + status() + ")");
        }
        Board current = board();
        if (!current.legalMoves().contains(move)) {   // Move is a record: equals compares from/to/promotion
            throw new IllegalArgumentException("Illegal move " + move.toUci() + " in position " + current.toFen());
        }
        List<Board> next = new ArrayList<>(positions);
        next.add(current.makeMove(move));
        return new GameState(List.copyOf(next));
    }

    public Board board() {
        return positions.getLast();
    }

    public int plyCount() {
        return positions.size() - 1;
    }

    public GameStatus status() {
        Board board = board();
        if (board.legalMoves().isEmpty()) {
            return board.isInCheck() ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (isInsufficientMaterial(board)) return GameStatus.INSUFFICIENT_MATERIAL;
        if (board.halfmoveClock() >= 100) return GameStatus.FIFTY_MOVE_RULE;
        if (repetitions(board) >= 3) return GameStatus.THREEFOLD_REPETITION;
        return GameStatus.ONGOING;
    }

    /** The checkmating side, or null if the game isn't won (ongoing or drawn). */
    public Color winner() {
        return status() == GameStatus.CHECKMATE ? board().sideToMove().opposite() : null;
    }

    /** "1-0", "0-1", "1/2-1/2", or "*" while the game is still going. */
    public String result() {
        GameStatus status = status();
        if (!status.isOver()) return "*";
        if (status.isDraw()) return "1/2-1/2";
        return winner() == Color.WHITE ? "1-0" : "0-1";
    }

    /** How often the current position has occurred (first 4 FEN fields: pieces, side, castling, en passant). */
    private int repetitions(Board current) {
        String key = positionKey(current);
        int count = 0;
        for (Board b : positions) {
            if (positionKey(b).equals(key)) count++;
        }
        return count;
    }

    private static String positionKey(Board board) {
        String[] fields = board.toFen().split(" ");
        return fields[0] + " " + fields[1] + " " + fields[2] + " " + fields[3];
    }

    /**
     * Draw by insufficient material: only kings, or kings plus a single knight or bishop,
     * or king+bishop vs king+bishop with both bishops on the same colour of square.
     */
    static boolean isInsufficientMaterial(Board board) {
        List<Integer> bishopSquareColors = new ArrayList<>();
        int minorPieces = 0;
        for (int sq = 0; sq < 64; sq++) {
            Piece piece = board.pieceAt(sq);
            if (piece == null || piece.type() == PieceType.KING) continue;
            switch (piece.type()) {
                case PAWN, ROOK, QUEEN -> {
                    return false;            // these can always (in principle) mate
                }
                case KNIGHT -> minorPieces++;
                case BISHOP -> {
                    minorPieces++;
                    bishopSquareColors.add((Square.file(sq) + Square.rank(sq)) % 2);
                }
                default -> { }
            }
        }
        if (minorPieces <= 1) return true;
        return minorPieces == bishopSquareColors.size()                 // only bishops left
                && bishopSquareColors.stream().distinct().count() == 1;  // all on the same square colour
    }
}
