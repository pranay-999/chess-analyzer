package com.chessanalyzer.chess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PgnParserTest {

    // Same layout Chess.com uses: headers, blank line, moves with {[%clk ...]} clock comments.
    // (Text blocks """ ... """ are multi-line strings, Java 15+.)
    private static final String CHESS_COM_STYLE = """
            [Event "Live Chess"]
            [Site "Chess.com"]
            [White "alice"]
            [Black "bob"]
            [Result "1-0"]
            [ECO "C23"]

            1. e4 {[%clk 0:09:58.5]} 1... e5 {[%clk 0:09:57]} 2. Bc4 {[%clk 0:09:55]} 2... Nc6 {[%clk 0:09:50]}
            3. Qh5 {[%clk 0:09:50]} 3... Nf6 {[%clk 0:09:41]} 4. Qxf7# {[%clk 0:09:47]} 1-0
            """;

    @Test
    void readsHeadersAndMoves() {
        PgnGame game = PgnParser.parse(CHESS_COM_STYLE);
        assertEquals("alice", game.header("White"));
        assertEquals("1-0", game.header("Result"));
        assertEquals(List.of("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7#"), game.sanMoves());
    }

    @Test
    void replayProducesUciAndPositions() {
        List<PlayedMove> moves = PgnParser.parse(CHESS_COM_STYLE).replay();
        assertEquals(7, moves.size());

        PlayedMove last = moves.get(6);
        assertEquals(7, last.ply());
        assertEquals("h5f7", last.uci());
        assertEquals("r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", last.fenAfter());
    }

    @Test
    void ignoresVariationsAndAnnotations() {
        PgnGame game = PgnParser.parse("1. e4! (1. d4 d5 (1... Nf6)) 1... e5?! $2 2. Nf3 *");
        assertEquals(List.of("e4!", "e5?!", "Nf3"), game.sanMoves());
        assertEquals("g1f3", game.replay().get(2).uci());
    }

    @Test
    void illegalMoveIsReported() {
        PgnGame game = PgnParser.parse("1. e4 e5 2. Ke3 *");
        assertThrows(IllegalArgumentException.class, game::replay);
    }
}
