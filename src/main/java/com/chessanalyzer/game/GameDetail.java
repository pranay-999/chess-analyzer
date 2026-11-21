package com.chessanalyzer.game;

import com.chessanalyzer.chess.PgnParser;

import java.util.List;

/**
 * Everything the board page needs to replay one game.
 *
 * @param startFen position before the first move (the board page shows this at "move 0")
 * @param moves    every move with the position after it, so the browser never has to
 *                 know chess rules: it just draws the FEN it is given
 */
public record GameDetail(GameSummary summary, String chesscomUrl, String startFen, List<MoveView> moves) {

    public record MoveView(int ply, String san, String uci, String fenAfter) {
    }

    public static GameDetail from(Game g) {
        List<MoveView> moves = g.getMoves().stream()
                .map(m -> new MoveView(m.getPly(), m.getSan(), m.getUci(), m.getFenAfter()))
                .toList();
        String startFen = PgnParser.parse(g.getPgn()).startingBoard().toFen();
        return new GameDetail(GameSummary.from(g), g.getChesscomUrl(), startFen, moves);
    }
}
