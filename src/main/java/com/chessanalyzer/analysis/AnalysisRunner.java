package com.chessanalyzer.analysis;

import com.chessanalyzer.chess.PgnParser;
import com.chessanalyzer.engine.Engine;
import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.engine.Evaluation;
import com.chessanalyzer.game.Game;
import com.chessanalyzer.game.GameMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Does the slow work: asks Stockfish about every position of a game.
 *
 * @Async: when someone calls run(...), Spring does NOT run it on the caller's thread.
 * It hands it to a background thread pool (configured under spring.task.execution in
 * application.yml) and returns immediately. That's why the HTTP request that starts
 * an analysis answers in milliseconds, and the browser polls for progress.
 *
 * Why a separate class instead of an @Async method inside AnalysisService? Spring
 * implements @Async (and @Transactional) with a PROXY object wrapped around the bean.
 * A call from inside the same class (this.run()) skips the proxy, so it would
 * silently run synchronously. Calls from another bean go through the proxy. This
 * "self-invocation" trap is a classic Spring interview question.
 */
@Component
public class AnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

    private final AnalysisRepository analyses;
    private final EngineFactory engines;
    private final TransactionTemplate transactions;   // run a block of code in a transaction, by hand

    public AnalysisRunner(AnalysisRepository analyses, EngineFactory engines, TransactionTemplate transactions) {
        this.analyses = analyses;
        this.engines = engines;
        this.transactions = transactions;
    }

    @Async
    public void run(long analysisId) {
        try {
            // 1. Load everything we need (one query thanks to @EntityGraph).
            Analysis analysis = analyses.findWithGameMovesById(analysisId).orElseThrow();
            Game game = analysis.getGame();
            List<GameMove> moves = game.getMoves();

            List<String> fens = new ArrayList<>();
            fens.add(PgnParser.parse(game.getPgn()).startingBoard().toFen());
            moves.forEach(m -> fens.add(m.getFenAfter()));
            List<String> played = moves.stream().map(GameMove::getUci).toList();

            analyses.markRunning(analysisId, moves.size());
            log.info("Analysing game {} ({} moves, depth {})", game.getId(), moves.size(), analysis.getDepth());

            // 2. The slow part, OUTSIDE any database transaction.
            List<Evaluation> evals = new ArrayList<>();
            try (Engine engine = engines.open(analysis.getEngine())) {
                for (int i = 0; i < fens.size(); i++) {
                    evals.add(engine.evaluate(fens.get(i), analysis.getDepth()));
                    if (i > 0) {
                        analyses.updateProgress(analysisId, i);   // the browser shows "34 / 80"
                    }
                }
            }

            // 3. Turn scores into verdicts and save them (cascade inserts every MoveEvaluation).
            MoveAnalyzer.Result result = MoveAnalyzer.analyze(fens, played, evals);
            List<MoveEvaluation> rows = result.moves().stream()
                    .map(m -> new MoveEvaluation(m.ply(), m.evalCp(), m.mateIn(), m.bestMoveUci(),
                            m.bestMoveSan(), m.cpLoss(), m.classification()))
                    .toList();
            // The `analysis` object from step 1 is DETACHED (its transaction ended), and its
            // lazy `evaluations` list was never loaded, so changing it would throw
            // LazyInitializationException. So: open a short transaction, load a fresh
            // MANAGED copy, change it. Hibernate notices the changes ("dirty checking") and
            // writes them when the transaction commits; no save() call needed.
            transactions.executeWithoutResult(status ->
                    analyses.findById(analysisId).orElseThrow()
                            .complete(rows, result.accuracyWhite(), result.accuracyBlack()));
            log.info("Analysis {} done: accuracy white {} / black {}", analysisId,
                    result.accuracyWhite(), result.accuracyBlack());
        } catch (Exception e) {
            // An exception in an @Async method has nobody to catch it, so we must record it ourselves.
            log.error("Analysis {} failed", analysisId, e);
            analyses.markFailed(analysisId, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
