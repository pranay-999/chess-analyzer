package com.chessanalyzer.game;

import com.chessanalyzer.chess.OpeningBook;
import com.chessanalyzer.chess.PgnParser;

import java.util.List;

/**
 * Everything the board page needs to replay one game.
 *
 * @param startFen position before the first move (the board page shows this at "move 0")
 * @param moves    every move with the position after it, so the browser never has to
 *                 know chess rules: it just draws the FEN it is given
 */
public record GameDetail(GameSummary summary, String chesscomUrl, String startFen, List<MoveView> moves,
                         String opening) {

    /** Without the opening name (it's looked up from the moves; see from()). */
    public GameDetail(GameSummary summary, String chesscomUrl, String startFen, List<MoveView> moves) {
        this(summary, chesscomUrl, startFen, moves, null);
    }

    public record MoveView(int ply, String san, String uci, String fenAfter) {
    }

    public static GameDetail from(Game g) {
        List<MoveView> moves = g.getMoves().stream()
                .map(m -> new MoveView(m.getPly(), m.getSan(), m.getUci(), m.getFenAfter()))
                .toList();
        String startFen = PgnParser.parse(g.getPgn()).startingBoard().toFen();
        return new GameDetail(GameSummary.from(g), g.getChesscomUrl(), startFen, moves, openingName(moves));
    }

    /** The most specific named opening the game passed through, e.g. "Sicilian Defense: Najdorf Variation". */
    static String openingName(List<MoveView> moves) {
        String name = null;
        for (MoveView m : moves) {
            if (!OpeningBook.contains(m.fenAfter())) break; // left theory: later names would be coincidences
            name = OpeningBook.nameOf(m.fenAfter()).orElse(name);
        }
        return name;
    }
}
