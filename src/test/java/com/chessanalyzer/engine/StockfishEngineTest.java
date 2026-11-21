package com.chessanalyzer.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StockfishEngineTest {

    private static final String STOCKFISH = "/usr/games/stockfish";

    // ---- parsing: no Stockfish needed ----

    @Test
    void parsesCentipawnsForWhiteToMove() {
        Evaluation e = StockfishEngine.parse(
                "info depth 16 seldepth 22 multipv 1 score cp 31 nodes 99791 pv e2e4 e7e5",
                "bestmove e2e4 ponder e7e5", true);
        assertEquals(new Evaluation(31, null, "e2e4"), e);
    }

    @Test
    void flipsScoreWhenBlackToMove() {
        // Stockfish says "-33 for the side to move (black)" = +33 for white.
        Evaluation e = StockfishEngine.parse("info depth 16 score cp -33 pv e7e5", "bestmove e7e5", false);
        assertEquals(33, e.cp());
    }

    @Test
    void parsesMateAndGameOver() {
        assertEquals(new Evaluation(null, -2, "d8h4"),
                StockfishEngine.parse("info depth 5 score mate 2 pv d8h4", "bestmove d8h4", false));
        Evaluation over = StockfishEngine.parse("info depth 0 score mate 0", "bestmove (none)", true);
        assertEquals(0, over.mate());
        assertNull(over.bestMoveUci());
    }

    // ---- real engine: runs only if Stockfish is installed ----

    static boolean stockfishInstalled() {
        return Files.isExecutable(Path.of(STOCKFISH));
    }

    @Test
    @EnabledIf("stockfishInstalled")
    void findsMateInOne() {
        try (Engine engine = new StockfishEngine(STOCKFISH, 1)) {
            // White: Rd1, Kg1 vs black king g8 behind its own pawns -> Rd8#
            Evaluation e = engine.evaluate("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", 10);
            assertEquals("d1d8", e.bestMoveUci());
            assertEquals(1, e.mate());
        }
    }

    @Test
    @EnabledIf("stockfishInstalled")
    void evaluatesManyPositionsWithOneProcess() {
        try (Engine engine = new StockfishEngine(STOCKFISH, 1)) {
            for (int i = 0; i < 3; i++) {
                Evaluation e = engine.evaluate("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 8);
                assertEquals(null, e.mate());
            }
        }
    }
}
