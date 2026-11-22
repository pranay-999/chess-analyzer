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

    // MoveFacts(winDrop, playedBest, materialGivenUp, onlyMoveGap, recapture, legalMoves, winBefore, winAfter)

    @Test
    void goodSacrificeIsBrilliant() {
        assertEquals(Classification.BRILLIANT, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 3, 5.0, false, 30, 55, 56)));
        // Not the engine's move, but within 2%: still brilliant.
        assertEquals(Classification.BRILLIANT, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(1.5, false, 3, null, false, 30, 55, 53.5)));
    }

    @Test
    void sacrificesThatArentBrilliant() {
        // Already completely winning: a sacrifice there is no achievement.
        assertEquals(Classification.BEST, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 3, null, false, 30, 99, 99)));
        // A sacrifice that's simply bad is still a mistake.
        assertEquals(Classification.MISTAKE, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(25, false, 3, null, false, 30, 60, 35)));
        // Giving up just a pawn is not enough.
        assertEquals(Classification.BEST, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 1, null, false, 30, 60, 60)));
    }

    @Test
    void onlyGoodMoveIsGreat() {
        assertEquals(Classification.GREAT, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 0, 30.0, false, 25, 50, 50)));
        // Second-best was nearly as good: just Best.
        assertEquals(Classification.BEST, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 0, 4.0, false, 25, 50, 50)));
        // Recapturing is "the only move" all the time: not Great.
        assertEquals(Classification.BEST, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 0, 40.0, true, 25, 50, 50)));
        // Only two legal moves: not much of a find.
        assertEquals(Classification.BEST, MoveAnalyzer.classify(
                new MoveAnalyzer.MoveFacts(0, true, 0, 40.0, false, 2, 50, 50)));
    }

    @Test
    void bxf7SacrificeInARealPositionIsBrilliant() {
        // Italian Game, 4. Bxf7+!? with hand-written scores saying it's the best move and fine for white.
        String fen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4";
        String after = Board.fromFen(fen).makeMove(Move.fromUci("c4f7")).toFen();
        MoveAnalyzer.Result r = MoveAnalyzer.analyze(List.of(fen, after), List.of("c4f7"), List.of(
                new Evaluation(40, null, "c4f7", -20, null),
                new Evaluation(45, null, "e8f7")));
        MoveAnalyzer.AnalyzedMove m = r.moves().getFirst();
        assertEquals(Classification.BRILLIANT, m.classification());
        assertEquals(2, m.materialGivenUp());   // bishop for a pawn
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
