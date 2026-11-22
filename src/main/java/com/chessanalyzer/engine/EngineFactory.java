package com.chessanalyzer.engine;

import com.chessanalyzer.engine.myengine.MyEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A Spring bean that creates engines by name. Engines themselves are NOT beans:
 * each analysis needs its own instance (a Stockfish process, or MyEngine with its
 * own counters), while a bean is shared by everyone.
 *
 * This is the "factory" pattern: callers say WHICH engine, the factory knows HOW
 * to build it. Adding a third engine only changes this class.
 *
 * @Value("${...}") reads our own settings from application.yml.
 */
@Component
public class EngineFactory {

    public static final String STOCKFISH = "stockfish";
    public static final String MY_ENGINE = "my-engine";
    private static final int STOCKFISH_MAX_DEPTH = 24;

    private final String stockfishPath;
    private final int stockfishThreads;
    private final int myEngineMaxDepth;
    private final long myEngineTimeLimitMs;

    public EngineFactory(@Value("${stockfish.path}") String stockfishPath,
                         @Value("${stockfish.threads:1}") int stockfishThreads,   // ":1" = default if missing
                         @Value("${my-engine.max-depth:5}") int myEngineMaxDepth,
                         @Value("${my-engine.time-limit-ms:1500}") long myEngineTimeLimitMs) {
        this.stockfishPath = stockfishPath;
        this.stockfishThreads = stockfishThreads;
        this.myEngineMaxDepth = myEngineMaxDepth;
        this.myEngineTimeLimitMs = myEngineTimeLimitMs;
    }

    public Engine open(String name) {
        return switch (name) {
            // Analysis: full strength, and the 2 best lines (the second one tells us when a move was "Great").
            case STOCKFISH -> new StockfishEngine(stockfishPath, stockfishThreads, 20, 2);
            case MY_ENGINE -> new MyEngine(myEngineMaxDepth, myEngineTimeLimitMs);
            default -> throw new IllegalArgumentException("Unknown engine: " + name);
        };
    }

    /** A weakened Stockfish for bots (skill 0..20). */
    public Engine openStockfish(int skillLevel) {
        return new StockfishEngine(stockfishPath, 1, skillLevel, 1);   // 1 thread, 1 line: bots don't need more
    }

    /** Our own engine with its own depth limit (bots use small depths to play weaker). */
    public Engine openMyEngine(int maxDepth) {
        return new MyEngine(maxDepth, myEngineTimeLimitMs);
    }

    /** Deepest search we allow for an engine. Also rejects unknown names (-> HTTP 400). */
    public int maxDepth(String name) {
        return switch (name) {
            case STOCKFISH -> STOCKFISH_MAX_DEPTH;
            case MY_ENGINE -> myEngineMaxDepth;
            default -> throw new IllegalArgumentException("Unknown engine: " + name
                    + " (use '" + STOCKFISH + "' or '" + MY_ENGINE + "')");
        };
    }
}
