package com.chessanalyzer.chess;

import java.util.List;

/**
 * A chess position: where every piece is, whose turn it is, and the extra
 * state FEN records (castling rights, en-passant square, move counters).
 *
 * Design choice: a Board is never changed after it is built. makeMove() returns
 * a NEW Board. This is called "copy-make". It is a little slower than changing
 * the board in place and undoing, but much easier to get right: no undo bugs,
 * and old positions stay valid (handy for replaying a game move by move).
 *
 * This package has no Spring code at all. It is plain Java, so it can be unit
 * tested in milliseconds and reused anywhere.
 */
public final class Board {

    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final Piece[] squares = new Piece[64];   // null = empty square
    private Color sideToMove = Color.WHITE;
    private boolean whiteKingside;
    private boolean whiteQueenside;
    private boolean blackKingside;
    private boolean blackQueenside;
    private int enPassantSquare = Square.NONE;       // square a pawn can capture onto en passant
    private int halfmoveClock;                       // moves since last capture/pawn move (50-move rule)
    private int fullmoveNumber = 1;                  // goes up after black moves

    private Board() {
        // use fromFen() or startingPosition()
    }

    public static Board startingPosition() {
        return fromFen(START_FEN);
    }

    // ------------------------------------------------------------------ FEN

    /**
     * Reads a FEN string, e.g. "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1".
     * Fields: pieces (rank 8 first) / side to move / castling / en passant / halfmove / fullmove.
     */
    public static Board fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        Board board = new Board();

        String[] rows = parts[0].split("/");
        if (rows.length != 8) {
            throw new IllegalArgumentException("FEN needs 8 ranks: " + fen);
        }
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i;                 // FEN lists rank 8 first
            int file = 0;
            for (char c : rows[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';          // a digit = that many empty squares
                } else {
                    if (file >= 8) {
                        throw new IllegalArgumentException("Too many squares in rank " + (rank + 1) + ": " + fen);
                    }
                    board.squares[Square.of(file, rank)] = Piece.fromFenChar(c);
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Rank " + (rank + 1) + " does not have 8 squares: " + fen);
            }
        }

        board.sideToMove = switch (parts[1]) {
            case "w" -> Color.WHITE;
            case "b" -> Color.BLACK;
            default -> throw new IllegalArgumentException("Side to move must be w or b: " + fen);
        };

        String castling = parts[2];
        board.whiteKingside = castling.contains("K");
        board.whiteQueenside = castling.contains("Q");
        board.blackKingside = castling.contains("k");
        board.blackQueenside = castling.contains("q");

        board.enPassantSquare = parts[3].equals("-") ? Square.NONE : Square.parse(parts[3]);

        if (parts.length >= 6) {
            board.halfmoveClock = Integer.parseInt(parts[4]);
            board.fullmoveNumber = Integer.parseInt(parts[5]);
        }
        return board;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = squares[Square.of(file, rank)];
                if (piece == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(piece.fenChar());
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }

        sb.append(sideToMove == Color.WHITE ? " w " : " b ");

        String castling = (whiteKingside ? "K" : "") + (whiteQueenside ? "Q" : "")
                + (blackKingside ? "k" : "") + (blackQueenside ? "q" : "");
        sb.append(castling.isEmpty() ? "-" : castling);

        sb.append(' ').append(enPassantSquare == Square.NONE ? "-" : Square.name(enPassantSquare));
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    // ------------------------------------------------------------- queries

    public Piece pieceAt(int square) {
        return squares[square];
    }

    public Color sideToMove() {
        return sideToMove;
    }

    public int enPassantSquare() {
        return enPassantSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public boolean canCastleKingside(Color color) {
        return color == Color.WHITE ? whiteKingside : blackKingside;
    }

    public boolean canCastleQueenside(Color color) {
        return color == Color.WHITE ? whiteQueenside : blackQueenside;
    }

    public int kingSquare(Color color) {
        Piece king = Piece.of(color, PieceType.KING);
        for (int sq = 0; sq < 64; sq++) {
            if (squares[sq] == king) {
                return sq;
            }
        }
        throw new IllegalStateException("No " + color + " king on the board");
    }

    /** All moves the side to move may legally play. */
    public List<Move> legalMoves() {
        return MoveGenerator.legalMoves(this);
    }

    public boolean isInCheck() {
        return MoveGenerator.isSquareAttacked(this, kingSquare(sideToMove), sideToMove.opposite());
    }

    public boolean isCheckmate() {
        return isInCheck() && legalMoves().isEmpty();
    }

    public boolean isStalemate() {
        return !isInCheck() && legalMoves().isEmpty();
    }

    // ------------------------------------------------------------ make move

    /**
     * Returns the position after playing {@code move}. This board is not changed.
     *
     * The move is NOT checked for legality here (checking is expensive and the
     * move generator calls this constantly). Only pass moves that came from
     * legalMoves(), San.fromSan() or a trusted engine.
     */
    public Board makeMove(Move move) {
        Board next = copy();
        int from = move.from();
        int to = move.to();
        Piece moving = squares[from];
        Color us = moving.color();
        boolean isPawn = moving.type() == PieceType.PAWN;
        boolean isCapture = squares[to] != null;

        next.squares[from] = null;

        // En passant: the pawn moves diagonally to an EMPTY square; the captured
        // pawn sits one rank behind the target square.
        if (isPawn && to == enPassantSquare && Square.file(from) != Square.file(to)) {
            next.squares[to - 8 * us.forward()] = null;
            isCapture = true;
        }

        // Castling: the king moves two files, so also move the rook next to it.
        if (moving.type() == PieceType.KING && Math.abs(Square.file(to) - Square.file(from)) == 2) {
            int rank = Square.rank(from);
            boolean kingside = Square.file(to) == 6;
            int rookFrom = Square.of(kingside ? 7 : 0, rank);
            int rookTo = Square.of(kingside ? 5 : 3, rank);
            next.squares[rookTo] = next.squares[rookFrom];
            next.squares[rookFrom] = null;
        }

        next.squares[to] = move.promotion() != null ? Piece.of(us, move.promotion()) : moving;

        // After a two-square pawn push, the square it skipped becomes the en-passant target.
        next.enPassantSquare = Square.NONE;
        if (isPawn && Math.abs(to - from) == 16) {
            next.enPassantSquare = (from + to) / 2;
        }

        // Moving a king or rook, or capturing a rook on its home square, removes castling rights.
        next.removeCastlingRightsFor(from);
        next.removeCastlingRightsFor(to);

        next.halfmoveClock = (isPawn || isCapture) ? 0 : halfmoveClock + 1;
        if (us == Color.BLACK) {
            next.fullmoveNumber = fullmoveNumber + 1;
        }
        next.sideToMove = us.opposite();
        return next;
    }

    // Home squares of kings and rooks (switch cases need constants).
    private static final int A1 = 0, E1 = 4, H1 = 7, A8 = 56, E8 = 60, H8 = 63;

    private void removeCastlingRightsFor(int square) {
        switch (square) {
            case E1 -> { whiteKingside = false; whiteQueenside = false; }
            case H1 -> whiteKingside = false;
            case A1 -> whiteQueenside = false;
            case E8 -> { blackKingside = false; blackQueenside = false; }
            case H8 -> blackKingside = false;
            case A8 -> blackQueenside = false;
            default -> { }
        }
    }

    private Board copy() {
        Board b = new Board();
        System.arraycopy(squares, 0, b.squares, 0, 64);
        b.sideToMove = sideToMove;
        b.whiteKingside = whiteKingside;
        b.whiteQueenside = whiteQueenside;
        b.blackKingside = blackKingside;
        b.blackQueenside = blackQueenside;
        b.enPassantSquare = enPassantSquare;
        b.halfmoveClock = halfmoveClock;
        b.fullmoveNumber = fullmoveNumber;
        return b;
    }

    @Override
    public String toString() {
        return toFen();
    }
}
