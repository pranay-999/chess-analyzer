package com.chessanalyzer.engine.myengine;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.engine.Evaluation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Positions whose answers were checked with Stockfish first. */
class MyEngineTest {

    private final MyEngine engine = new MyEngine(6, 5_000);

    @Test
    void startPositionIsBalanced() {
        assertEquals(0, Evaluator.evaluate(Board.startingPosition()));   // symmetric tables
    }

    @Test
    void findsMateInOne() {
        Evaluation e = engine.evaluate("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", 4);
        assertEquals("d1d8", e.bestMoveUci());
        assertEquals(1, e.mate());
    }

    @Test
    void findsMateInTwo() {
        // 1. Rd8+ Rxd8 2. Rxd8#  (either rook can start)
        Evaluation e = engine.evaluate("r5k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1", 4);
        assertTrue(Set.of("d2d8", "d1d8").contains(e.bestMoveUci()), e.bestMoveUci());
        assertEquals(2, e.mate());
    }

    @Test
    void takesHangingQueen() {
        Evaluation e = engine.evaluate("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1", 4);
        assertEquals("d2d5", e.bestMoveUci());
        assertTrue(e.cp() > 400, "score " + e.cp());
    }

    @Test
    void scoreIsFromWhitesViewWhenBlackMoves() {
        // Mirror image of the previous test: now black wins the queen, so the score is negative.
        Evaluation e = engine.evaluate("4k3/3r4/8/8/3Q4/8/8/4K3 b - - 0 1", 4);
        assertEquals("d7d4", e.bestMoveUci());
        assertTrue(e.cp() < -400, "score " + e.cp());
    }

    @Test
    void doesNotGrabAPoisonedPawn() {
        // Qxd5?? loses the queen to cxd5 (quiescence search must see the recapture).
        Evaluation e = engine.evaluate("4k3/8/2p5/3p4/8/8/8/3QK3 w - - 0 1", 3);
        assertTrue(!"d1d5".equals(e.bestMoveUci()), "played the poisoned " + e.bestMoveUci());
    }

    @Test
    void gameOverPositions() {
        Evaluation mated = engine.evaluate("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3", 4);
        assertEquals(0, mated.mate());
        assertNull(mated.bestMoveUci());

        Evaluation stalemate = engine.evaluate("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", 4);
        assertEquals(0, stalemate.cp());
    }

    @Test
    void respectsTimeLimit() {
        MyEngine quick = new MyEngine(99, 300);
        long start = System.currentTimeMillis();
        Evaluation e = quick.evaluate(Board.START_FEN, 99);
        long took = System.currentTimeMillis() - start;
        assertTrue(took < 1_500, "took " + took + " ms");
        assertTrue(e.bestMoveUci() != null);
    }
}
