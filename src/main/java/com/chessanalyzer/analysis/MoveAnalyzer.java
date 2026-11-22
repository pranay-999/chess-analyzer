package com.chessanalyzer.analysis;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.chess.Sacrifice;
import com.chessanalyzer.chess.San;
import com.chessanalyzer.engine.Evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw engine scores into "was this move good?". Pure function: no Spring,
 * no database, no engine. Give it numbers, get answers. Easy to unit test.
 *
 * Why "win %" instead of centipawns? Losing 200 centipawns when you're +1000 is
 * irrelevant (still totally winning); losing 200 at 0.0 can decide the game.
 * Converting to a winning chance first makes the labels fair. The formula and the
 * 10/20/30 thresholds are the ones Lichess publishes for its own analysis.
 */
public final class MoveAnalyzer {

    private MoveAnalyzer() {
    }

    /** Result for one played move. evalCp/mateIn = position AFTER the move, white's view. */
    /**
     * @param winDrop         winning chance the move lost (0-100)
     * @param materialGivenUp pawns' worth the move gave away (see chess.Sacrifice)
     * @param onlyMoveGap     how much worse (in winning chance) the second-best move was; null if unknown
     */
    public record AnalyzedMove(int ply, Color mover, Integer evalCp, Integer mateIn,
                               String bestMoveUci, String bestMoveSan, int cpLoss,
                               Classification classification, double accuracy,
                               double winDrop, int materialGivenUp, Double onlyMoveGap) {
    }

    /** Everything the labelling rules look at, for one move (all from the mover's point of view). */
    record MoveFacts(double winDrop, boolean playedBest, int materialGivenUp, Double onlyMoveGap,
                     boolean recapture, int legalMoveCount, double winBefore, double winAfter) {
    }

    static final double NEAR_BEST = 2.0;          // within 2% of the best move counts as "as good as best"
    static final int SACRIFICE_PAWNS = 2;         // giving up at least a piece for a pawn (or the exchange)
    static final double ONLY_MOVE_GAP = 15.0;     // second-best move at least 15% worse = "only move"

    public record Result(List<AnalyzedMove> moves, BigDecimal accuracyWhite, BigDecimal accuracyBlack) {
    }

    /**
     * @param fens       n+1 positions: the start, then after each move
     * @param playedUci  n moves actually played
     * @param evals      n+1 engine evaluations, one per position in {@code fens}
     */
    public static Result analyze(List<String> fens, List<String> playedUci, List<Evaluation> evals) {
        List<AnalyzedMove> moves = new ArrayList<>();
        for (int i = 0; i < playedUci.size(); i++) {
            Board before = Board.fromFen(fens.get(i));
            Color mover = before.sideToMove();
            Evaluation evalBefore = evals.get(i);
            Evaluation evalAfter = evals.get(i + 1);

            // Everything from the MOVER's point of view, so "drop" is always "how much worse it got for me".
            double winBefore = winPercentFor(mover, evalBefore, mover);
            double winAfter = winPercentFor(mover, evalAfter, mover.opposite());
            double drop = Math.max(0, winBefore - winAfter);

            String played = playedUci.get(i);
            boolean playedBest = played.equals(evalBefore.bestMoveUci());
            int cpLoss = Math.max(0, cpFor(mover, evalBefore, mover) - cpFor(mover, evalAfter, mover.opposite()));
            String bestSan = evalBefore.bestMoveUci() == null ? null
                    : San.toSan(before, Move.fromUci(evalBefore.bestMoveUci()));

            // Extra facts for the "!" labels:
            int given = Sacrifice.materialGivenUp(before, Move.fromUci(played));
            Double gap = null;   // how much worse the second-best move was (needs Stockfish's 2nd line)
            if (evalBefore.hasSecond()) {
                Evaluation second = new Evaluation(evalBefore.secondCp(), evalBefore.secondMate(), null);
                gap = Math.max(0, winBefore - winPercentFor(mover, second, mover));
            }
            // Taking back on the square the opponent just captured on is "only move" all the time: not Great.
            boolean recapture = i > 0 && played.substring(2, 4).equals(playedUci.get(i - 1).substring(2, 4));
            MoveFacts facts = new MoveFacts(drop, playedBest, given, gap, recapture,
                    before.legalMoves().size(), winBefore, winAfter);

            moves.add(new AnalyzedMove(i + 1, mover, evalAfter.cp(), evalAfter.mate(),
                    evalBefore.bestMoveUci(), bestSan, cpLoss,
                    classify(facts), moveAccuracy(drop), drop, given, gap));
        }
        return new Result(moves, averageAccuracy(moves, Color.WHITE), averageAccuracy(moves, Color.BLACK));
    }

    /**
     * Brilliant (!!): a good SACRIFICE. The move gives away at least {@value #SACRIFICE_PAWNS} pawns'
     *   worth of material, yet it's the best move (or within {@value #NEAR_BEST}%), you weren't already
     *   completely winning, and you're not worse off afterwards.
     * Great (!): the ONLY good move. You found the best move while the second-best was at least
     *   {@value #ONLY_MOVE_GAP}% worse. Simple recaptures and positions with 1-2 legal moves don't count.
     * Everything else: by how much winning chance the move lost (below).
     */
    static Classification classify(MoveFacts f) {
        boolean asGoodAsBest = f.playedBest() || f.winDrop() < NEAR_BEST;
        if (asGoodAsBest && f.materialGivenUp() >= SACRIFICE_PAWNS && f.winBefore() < 97 && f.winAfter() >= 40) {
            return Classification.BRILLIANT;
        }
        if (f.playedBest() && f.onlyMoveGap() != null && f.onlyMoveGap() >= ONLY_MOVE_GAP
                && !f.recapture() && f.legalMoveCount() >= 3) {
            return Classification.GREAT;
        }
        return classify(f.winDrop(), f.playedBest());
    }

    static Classification classify(double winPercentDrop, boolean playedBest) {
        if (playedBest) return Classification.BEST;
        if (winPercentDrop >= 30) return Classification.BLUNDER;
        if (winPercentDrop >= 20) return Classification.MISTAKE;
        if (winPercentDrop >= 10) return Classification.INACCURACY;
        return Classification.GOOD;
    }

    /** White's chance of winning (0..100) for an evaluation. Needs the side to move to read "mate 0". */
    public static double whiteWinPercent(Evaluation e, Color sideToMove) {
        if (e.mate() != null) {
            if (e.mate() == 0) {
                return sideToMove == Color.WHITE ? 0 : 100;   // side to move is checkmated
            }
            return e.mate() > 0 ? 100 : 0;
        }
        int cp = Math.clamp(e.cp(), -1000, 1000);
        return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
    }

    private static double winPercentFor(Color player, Evaluation e, Color sideToMove) {
        double white = whiteWinPercent(e, sideToMove);
        return player == Color.WHITE ? white : 100 - white;
    }

    /** Centipawns from {@code player}'s view, with mates counted as ±1000 (so one blunder can't show "loss 99999"). */
    private static int cpFor(Color player, Evaluation e, Color sideToMove) {
        int whiteCp;
        if (e.mate() != null) {
            boolean whiteWins = e.mate() == 0 ? sideToMove == Color.BLACK : e.mate() > 0;
            whiteCp = whiteWins ? 1000 : -1000;
        } else {
            whiteCp = Math.clamp(e.cp(), -1000, 1000);
        }
        return player == Color.WHITE ? whiteCp : -whiteCp;
    }

    /** 100 = perfect move, falls quickly as the winning chance drops (Lichess formula). */
    static double moveAccuracy(double winPercentDrop) {
        double raw = 103.1668 * Math.exp(-0.04354 * winPercentDrop) - 3.1669;
        return Math.clamp(raw, 0, 100);
    }

    private static BigDecimal averageAccuracy(List<AnalyzedMove> moves, Color color) {
        double avg = moves.stream().filter(m -> m.mover() == color)
                .mapToDouble(AnalyzedMove::accuracy).average().orElse(Double.NaN);
        return Double.isNaN(avg) ? null : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }
}
