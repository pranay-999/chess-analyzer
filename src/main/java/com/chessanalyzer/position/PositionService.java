package com.chessanalyzer.position;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.chess.San;
import com.chessanalyzer.engine.Engine;
import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.engine.Evaluation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lets the browser explore positions that are NOT part of a stored game: the user
 * drags a piece, and we answer "is that legal, what's the new position, and what
 * does the engine think?". No database involved. It reuses our chess core and engines.
 */
@Service
public class PositionService {

    /** Live requests must answer quickly; Stockfish at depth 18 is well under a second. */
    static final int MAX_LIVE_DEPTH = 18;

    private final EngineFactory engines;

    public PositionService(EngineFactory engines) {
        this.engines = engines;
    }

    /** Every legal move in the position, with its SAN and the position after it. */
    public List<LegalMove> legalMoves(String fen) {
        Board board = Board.fromFen(fen);   // throws IllegalArgumentException for a bad FEN -> HTTP 400
        return board.legalMoves().stream()
                .map(m -> new LegalMove(m.toUci(), San.toSan(board, m), board.makeMove(m).toFen()))
                .toList();
    }

    /** Asks an engine about one position, right now (synchronously). */
    public PositionEval evaluate(String fen, String engineName, int depth) {
        Board board = Board.fromFen(fen);
        int effectiveDepth = Math.clamp(depth, 1, Math.min(engines.maxDepth(engineName), MAX_LIVE_DEPTH));
        try (Engine engine = engines.open(engineName)) {
            Evaluation e = engine.evaluate(board.toFen(), effectiveDepth);
            String bestSan = e.bestMoveUci() == null ? null : San.toSan(board, Move.fromUci(e.bestMoveUci()));
            return new PositionEval(e.cp(), e.mate(), e.bestMoveUci(), bestSan, engineName, effectiveDepth);
        }
    }

    public record LegalMove(String uci, String san, String fenAfter) {
    }

    /** cp/mate are from white's view, like everywhere else in the app. */
    public record PositionEval(Integer cp, Integer mate, String bestMoveUci, String bestMoveSan,
                               String engine, int depth) {
    }
}
