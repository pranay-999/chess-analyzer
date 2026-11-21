package com.chessanalyzer.analysis;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.engine.Evaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveAnalyzerTest {

    @Test
    void winPercentIsFiftyAtEqualityAndSymmetric() {
        assertEquals(50.0, MoveAnalyzer.whiteWinPercent(new Evaluation(0, null, null), Color.WHITE), 1e-9);
        double plus = MoveAnalyzer.whiteWinPercent(new Evaluation(300, null, null), Color.WHITE);
        double minus = MoveAnalyzer.whiteWinPercent(new Evaluation(-300, null, null), Color.WHITE);
        assertEquals(100.0, plus + minus, 1e-9);
        assertTrue(plus > 70);
    }

    @Test
    void checkmatedSideHasZeroPercent() {
        Evaluation mated = new Evaluation(null, 0, null);
        assertEquals(0.0, MoveAnalyzer.whiteWinPercent(mated, Color.WHITE));
        assertEquals(100.0, MoveAnalyzer.whiteWinPercent(mated, Color.BLACK));
    }

    @ParameterizedTest
    @CsvSource({"0, true, BEST", "50, true, BEST", "5, false, GOOD", "12, false, INACCURACY",
            "25, false, MISTAKE", "45, false, BLUNDER"})
    void classifiesByWinPercentDrop(double drop, boolean playedBest, Classification expected) {
        assertEquals(expected, MoveAnalyzer.classify(drop, playedBest));
    }

    @Test
    void analyzesFoolsMate() {
        // 1. f3 e5 2. g4?? Qh4#  — with hand-written "engine" scores (white's view).
        List<String> played = List.of("f2f3", "e7e5", "g2g4", "d8h4");
        List<Evaluation> evals = List.of(
                new Evaluation(30, null, "e2e4"),    // start
                new Evaluation(-60, null, "e7e5"),   // after f3, black to move
                new Evaluation(-50, null, "d2d4"),   // after e5, white to move
                new Evaluation(null, -1, "d8h4"),    // after g4: black mates in 1
                new Evaluation(null, 0, null));      // after Qh4#: white is mated

        MoveAnalyzer.Result result = MoveAnalyzer.analyze(positions(played), played, evals);
        List<MoveAnalyzer.AnalyzedMove> moves = result.moves();

        assertEquals(Classification.GOOD, moves.get(0).classification());      // f3: small drop
        assertEquals(Classification.BEST, moves.get(1).classification());      // e5 = engine's choice
        assertEquals(Classification.BLUNDER, moves.get(2).classification());   // g4 allows mate
        assertEquals(950, moves.get(2).cpLoss());                              // -50 -> "mate" (-1000)
        assertEquals("d4", moves.get(2).bestMoveSan());
        assertEquals(Classification.BEST, moves.get(3).classification());
        assertEquals("Qh4#", moves.get(3).bestMoveSan());
        assertEquals(0, moves.get(3).mateIn());

        assertTrue(result.accuracyWhite().doubleValue() < 50);
        assertTrue(result.accuracyBlack().doubleValue() > 90);
    }

    private static List<String> positions(List<String> uciMoves) {
        List<String> fens = new ArrayList<>();
        Board board = Board.startingPosition();
        fens.add(board.toFen());
        for (String uci : uciMoves) {
            board = board.makeMove(Move.fromUci(uci));
            fens.add(board.toFen());
        }
        return fens;
    }
}
