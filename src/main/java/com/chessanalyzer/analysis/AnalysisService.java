package com.chessanalyzer.analysis;

import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.game.Game;
import com.chessanalyzer.game.GameNotFoundException;
import com.chessanalyzer.game.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Starting and reading analyses. The heavy lifting is in AnalysisRunner.
 *
 * The "job" pattern used here (very common in real backends):
 *   POST  -> create a row with status PENDING, start background work, answer 202 Accepted at once
 *   GET   -> client polls the row: RUNNING 12/80 ... RUNNING 79/80 ... DONE + results
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final GameRepository games;
    private final AnalysisRepository analyses;
    private final AnalysisRunner runner;
    private final EngineFactory engines;

    public AnalysisService(GameRepository games, AnalysisRepository analyses, AnalysisRunner runner,
                           EngineFactory engines) {
        this.games = games;
        this.analyses = analyses;
        this.runner = runner;
        this.engines = engines;
    }

    /**
     * Not @Transactional on purpose: analyses.save(...) commits right away, so the
     * row EXISTS in the database before the background thread looks for it. Inside a
     * transaction, the background thread could start before our commit and find nothing.
     */
    public AnalysisView start(long gameId, String engine, int depth) {
        int maxDepth = engines.maxDepth(engine);   // also rejects unknown engine names
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be at least 1");
        }
        int effectiveDepth = Math.min(depth, maxDepth);   // my-engine can't go as deep as Stockfish
        Game game = games.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        // Already being analysed by this engine? Return that job instead of starting a second one.
        Optional<Analysis> latest = analyses.findFirstByGameIdAndEngineOrderByCreatedAtDesc(gameId, engine);
        if (latest.isPresent() && latest.get().isInProgress()) {
            return AnalysisView.from(latest.get(), gameId);
        }

        Analysis analysis = analyses.save(new Analysis(game, engine, effectiveDepth));
        runner.run(analysis.getId());   // @Async: returns immediately
        return AnalysisView.from(analysis, gameId);
    }

    @Transactional(readOnly = true)   // keeps the session open to load the lazy evaluations list
    public AnalysisView latest(long gameId, String engine) {
        engines.maxDepth(engine);   // validates the engine name
        Analysis analysis = analyses.findFirstByGameIdAndEngineOrderByCreatedAtDesc(gameId, engine)
                .orElseThrow(() -> new AnalysisNotFoundException(gameId, engine));
        return AnalysisView.from(analysis, gameId);
    }

    /** Runs once when the app has started. A job "RUNNING" from before a restart will never finish. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedAnalyses() {
        int count = analyses.failAllInProgress("The server restarted before this analysis finished. Run it again.");
        if (count > 0) {
            log.warn("Marked {} interrupted analyses as FAILED", count);
        }
    }
}
