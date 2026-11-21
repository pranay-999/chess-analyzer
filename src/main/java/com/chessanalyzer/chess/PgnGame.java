package com.chessanalyzer.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed PGN: the [Header "value"] tags plus the list of SAN moves.
 * Produced by {@link PgnParser#parse(String)}.
 */
public record PgnGame(Map<String, String> headers, List<String> sanMoves) {

    public String header(String name) {
        return headers.get(name);
    }

    /** Normally the standard start; a game can begin elsewhere if it has a [FEN "..."] header. */
    public Board startingBoard() {
        String fen = headers.get("FEN");
        return fen == null ? Board.startingPosition() : Board.fromFen(fen);
    }

    /**
     * Plays every move on a board, checking each one is legal, and records the
     * UCI form and resulting position. Throws IllegalArgumentException on an illegal move.
     */
    public List<PlayedMove> replay() {
        Board board = startingBoard();
        List<PlayedMove> played = new ArrayList<>();
        int ply = 1;
        for (String san : sanMoves) {
            Move move = San.fromSan(board, san);
            board = board.makeMove(move);
            played.add(new PlayedMove(ply++, san, move.toUci(), board.toFen()));
        }
        return played;
    }
}
