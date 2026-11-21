package com.chessanalyzer.engine.myengine;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.chess.Piece;
import com.chessanalyzer.chess.PieceType;
import com.chessanalyzer.engine.Engine;
import com.chessanalyzer.engine.Evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Our own chess engine. It plays/analyses by searching the tree of possible moves.
 *
 * <h2>Negamax</h2>
 * "My best score = the best of (minus the opponent's best score after each of my
 * moves)". One function for both sides, because a position worth +3 for me is
 * worth -3 for my opponent. That's why Evaluator scores from the side to move.
 *
 * <h2>Alpha-beta pruning</h2>
 * alpha = the score I'm already guaranteed; beta = the most the opponent will
 * allow. If a move scores >= beta, the opponent would never let us reach this
 * position, so we stop looking at the remaining moves here ("cut-off"). Same
 * answer as plain minimax, but it often looks at far fewer positions.
 *
 * <h2>Move ordering</h2>
 * Cut-offs happen sooner if good moves are tried first. We try captures first,
 * most valuable victim / least valuable attacker first (MVV-LVA): PxQ before QxP.
 *
 * <h2>Quiescence search</h2>
 * Stopping at a fixed depth in the middle of a capture sequence gives silly
 * results ("I took a pawn with my queen, +1!" while the queen is about to be taken).
 * At depth 0 we keep searching CAPTURES ONLY until the position is quiet.
 *
 * <h2>Iterative deepening + time limit</h2>
 * Search depth 1, then 2, then 3 ... until the depth limit or the time is up. When
 * time runs out we use the last depth that finished. Each finished depth also tells
 * the next one which move to try first.
 *
 * Not thread-safe (it keeps counters in fields): one instance per analysis job.
 */
public class MyEngine implements Engine {

    /** Score for "checkmate". Mate in fewer moves scores higher: MATE - plies. */
    static final int MATE = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MATE_THRESHOLD = MATE - 1000;

    private final int maxDepth;
    private final long timeLimitNanos;

    private long deadline;
    private boolean outOfTime;
    private long nodes;

    public MyEngine(int maxDepth, long timeLimitMillis) {
        this.maxDepth = maxDepth;
        this.timeLimitNanos = timeLimitMillis * 1_000_000;
    }

    @Override
    public String name() {
        return "my-engine";
    }

    @Override
    public Evaluation evaluate(String fen, int depth) {
        Board board = Board.fromFen(fen);
        List<Move> rootMoves = board.legalMoves();
        if (rootMoves.isEmpty()) {
            // Game over: "mate 0" = side to move is checkmated (same convention as Stockfish).
            return board.isInCheck() ? new Evaluation(null, 0, null) : new Evaluation(0, null, null);
        }

        deadline = System.nanoTime() + timeLimitNanos;
        outOfTime = false;
        nodes = 0;
        orderMoves(board, rootMoves);

        Move bestMove = rootMoves.get(0);
        int bestScore = 0;
        int depthLimit = Math.min(depth, maxDepth);
        for (int d = 1; d <= depthLimit; d++) {
            Move iterationBest = null;
            int alpha = -INFINITY;
            for (Move move : rootMoves) {
                int score = -search(board.makeMove(move), d - 1, 1, -INFINITY, -alpha);
                if (outOfTime) break;
                if (score > alpha) {
                    alpha = score;
                    iterationBest = move;
                }
            }
            if (outOfTime) break;        // unfinished depth: keep the previous answer
            bestMove = iterationBest;
            bestScore = alpha;
            rootMoves.remove(bestMove);  // try it first next time
            rootMoves.addFirst(bestMove);
            if (Math.abs(bestScore) >= MATE_THRESHOLD) break;   // found a forced mate: no need to go deeper
        }
        return toEvaluation(bestScore, bestMove, board.sideToMove());
    }

    /** Negamax with alpha-beta. Returns the score for the side to move in {@code board}. */
    private int search(Board board, int depth, int ply, int alpha, int beta) {
        if (timeUp()) return 0;
        if (board.halfmoveClock() >= 100) return 0;              // 50-move rule: draw

        List<Move> moves = board.legalMoves();
        if (moves.isEmpty()) {
            // Checkmated: very bad, but a mate further away is "less bad" (so we prefer quick mates).
            return board.isInCheck() ? -(MATE - ply) : 0;        // stalemate = 0
        }
        if (depth == 0) {
            return quiesce(board, alpha, beta);
        }

        orderMoves(board, moves);
        for (Move move : moves) {
            int score = -search(board.makeMove(move), depth - 1, ply + 1, -beta, -alpha);
            if (outOfTime) return 0;
            if (score >= beta) {
                return beta;          // cut-off: opponent won't allow this line
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    /** Only captures/promotions, until nothing more is hanging. */
    private int quiesce(Board board, int alpha, int beta) {
        if (timeUp()) return 0;

        // "Stand pat": we're not forced to capture, so the static score is a lower bound.
        int standPat = Evaluator.evaluate(board);
        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        List<Move> captures = new ArrayList<>();
        for (Move m : board.legalMoves()) {
            if (isCaptureOrPromotion(board, m)) captures.add(m);
        }
        orderMoves(board, captures);
        for (Move move : captures) {
            int score = -quiesce(board.makeMove(move), -beta, -alpha);
            if (outOfTime) return 0;
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private boolean timeUp() {
        // Reading the clock is slowish, so only every 1024 positions.
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) {
            outOfTime = true;
        }
        return outOfTime;
    }

    private static boolean isCaptureOrPromotion(Board board, Move m) {
        return board.pieceAt(m.to()) != null || m.promotion() != null
                || (m.to() == board.enPassantSquare() && board.pieceAt(m.from()).type() == PieceType.PAWN);
    }

    /** MVV-LVA: high score = try first. Quiet moves score 0 and keep their order. */
    private static void orderMoves(Board board, List<Move> moves) {
        moves.sort(Comparator.comparingInt((Move m) -> orderScore(board, m)).reversed());
    }

    private static int orderScore(Board board, Move m) {
        int score = 0;
        Piece victim = board.pieceAt(m.to());
        if (victim != null) {
            Piece attacker = board.pieceAt(m.from());
            score += 10 * Evaluator.VALUE[victim.type().ordinal()] - Evaluator.VALUE[attacker.type().ordinal()];
        }
        if (m.promotion() != null) {
            score += Evaluator.VALUE[m.promotion().ordinal()];
        }
        return score;
    }

    /** Our internal score (side to move, mate as MATE - plies) -> the shared Evaluation format (white's view). */
    static Evaluation toEvaluation(int score, Move best, Color sideToMove) {
        int sign = sideToMove == Color.WHITE ? 1 : -1;
        if (Math.abs(score) >= MATE_THRESHOLD) {
            int plies = MATE - Math.abs(score);
            int movesToMate = (plies + 1) / 2;
            int mate = score > 0 ? movesToMate : -movesToMate;
            return new Evaluation(null, sign * mate, best.toUci());
        }
        return new Evaluation(sign * score, null, best.toUci());
    }

    /** Positions visited by the last evaluate() call (for curiosity and benchmarks). */
    public long nodes() {
        return nodes;
    }

    @Override
    public void close() {
        // Nothing to release: unlike Stockfish there is no external process.
    }
}
