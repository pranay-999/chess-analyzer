package com.chessanalyzer.chess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The opposite of PgnParser: headers + moves -> PGN text ("[White "a"] ... 1. e4 e5 2. Nf3 1-0"). */
public final class PgnWriter {

    private PgnWriter() {
    }

    /**
     * @param headers in the order they should appear (use a LinkedHashMap); "Result" is added for you
     * @param uciMoves the moves from the standard starting position
     */
    public static String write(Map<String, String> headers, List<String> uciMoves, String result) {
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.put("Result", result);

        StringBuilder pgn = new StringBuilder();
        all.forEach((key, value) -> pgn.append('[').append(key).append(" \"")
                .append(value.replace("\"", "'")).append("\"]\n"));
        pgn.append('\n');

        Board board = Board.startingPosition();
        for (int i = 0; i < uciMoves.size(); i++) {
            Move move = Move.fromUci(uciMoves.get(i));
            if (i % 2 == 0) {
                pgn.append(i / 2 + 1).append(". ");
            }
            pgn.append(San.toSan(board, move)).append(' ');
            board = board.makeMove(move);
        }
        return pgn.append(result).append('\n').toString();
    }
}
