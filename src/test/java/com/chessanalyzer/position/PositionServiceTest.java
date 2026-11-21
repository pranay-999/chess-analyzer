package com.chessanalyzer.position;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.position.PositionService.LegalMove;
import com.chessanalyzer.position.PositionService.PositionEval;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit test: we build the service by hand (no Spring), using our own engine so Stockfish isn't needed. */
class PositionServiceTest {

    private final PositionService service =
            new PositionService(new EngineFactory("/usr/games/stockfish", 1, 4, 2_000));

    @Test
    void listsLegalMovesWithSanAndNewPosition() {
        List<LegalMove> moves = service.legalMoves(Board.START_FEN);
        assertEquals(20, moves.size());
        LegalMove e4 = moves.stream().filter(m -> m.uci().equals("e2e4")).findFirst().orElseThrow();
        assertEquals("e4", e4.san());
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", e4.fenAfter());
    }

    @Test
    void checkmatedPositionHasNoMoves() {
        assertTrue(service.legalMoves("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3").isEmpty());
    }

    @Test
    void badFenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.legalMoves("hello"));
    }

    @Test
    void evaluatesWithChosenEngineAndGivesBestMoveInSan() {
        PositionEval eval = service.evaluate("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", "my-engine", 3);
        assertEquals("d1d8", eval.bestMoveUci());
        assertEquals("Rd8#", eval.bestMoveSan());
        assertEquals(1, eval.mate());
    }

    @Test
    void depthIsCappedForLiveRequests() {
        PositionEval eval = service.evaluate(Board.START_FEN, "my-engine", 99);
        assertEquals(4, eval.depth());   // my-engine max depth in this test = 4
    }
}
