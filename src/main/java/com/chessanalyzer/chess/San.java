package com.chessanalyzer.chess;

import java.util.List;

/**
 * Standard Algebraic Notation: the human move format used in PGN ("e4", "Nbd7",
 * "exd5", "O-O", "e8=Q+", "Qxf7#").
 *
 * Reading SAN is done the lazy-but-reliable way: generate every legal move,
 * write each one as SAN, and pick the one that matches the text. That way the
 * reading and writing rules can never disagree.
 */
public final class San {

    private San() {
    }

    /** Writes {@code move} (which must be legal on {@code board}) as SAN, including "+" or "#". */
    public static String toSan(Board board, Move move) {
        String san = withoutCheckSuffix(board, move, board.legalMoves());
        Board after = board.makeMove(move);
        if (after.isCheckmate()) {
            return san + "#";
        }
        if (after.isInCheck()) {
            return san + "+";
        }
        return san;
    }

    /** Finds the legal move that {@code san} describes, or throws if there is none. */
    public static Move fromSan(Board board, String san) {
        // Ignore check marks and annotations ("!", "?"); accept "0-0" as well as "O-O".
        String wanted = san.replaceAll("[+#!?]", "").replace('0', 'O');
        List<Move> legal = board.legalMoves();
        for (Move move : legal) {
            if (withoutCheckSuffix(board, move, legal).equals(wanted)) {
                return move;
            }
        }
        throw new IllegalArgumentException("Illegal move '" + san + "' in position " + board.toFen());
    }

    private static String withoutCheckSuffix(Board board, Move move, List<Move> legalMoves) {
        int from = move.from();
        int to = move.to();
        Piece piece = board.pieceAt(from);

        if (piece.type() == PieceType.KING && Math.abs(Square.file(to) - Square.file(from)) == 2) {
            return Square.file(to) == 6 ? "O-O" : "O-O-O";
        }

        boolean isPawn = piece.type() == PieceType.PAWN;
        // A pawn changing file is always a capture (that covers en passant, where the target is empty).
        boolean capture = board.pieceAt(to) != null || (isPawn && Square.file(from) != Square.file(to));

        StringBuilder sb = new StringBuilder();
        if (isPawn) {
            if (capture) {
                sb.append(Square.name(from).charAt(0));     // "exd5": pawn captures name their file
            }
        } else {
            sb.append(Character.toUpperCase(piece.type().letter()));
            sb.append(disambiguation(board, move, legalMoves));
        }
        if (capture) {
            sb.append('x');
        }
        sb.append(Square.name(to));
        if (move.promotion() != null) {
            sb.append('=').append(Character.toUpperCase(move.promotion().letter()));
        }
        return sb.toString();
    }

    /**
     * If another identical piece can also reach the same square, add the from-file
     * ("Nbd2"), or the from-rank if the file doesn't tell them apart ("R1a3"),
     * or both if neither does ("Qh4e1").
     */
    private static String disambiguation(Board board, Move move, List<Move> legalMoves) {
        Piece piece = board.pieceAt(move.from());
        List<Move> rivals = legalMoves.stream()
                .filter(m -> m.to() == move.to() && m.from() != move.from() && board.pieceAt(m.from()) == piece)
                .toList();
        if (rivals.isEmpty()) {
            return "";
        }
        String fromName = Square.name(move.from());
        boolean sameFile = rivals.stream().anyMatch(m -> Square.file(m.from()) == Square.file(move.from()));
        boolean sameRank = rivals.stream().anyMatch(m -> Square.rank(m.from()) == Square.rank(move.from()));
        if (!sameFile) {
            return fromName.substring(0, 1);
        }
        if (!sameRank) {
            return fromName.substring(1, 2);
        }
        return fromName;
    }
}
