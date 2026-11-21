package com.chessanalyzer.engine;

/**
 * Anything that can evaluate a chess position. Today: Stockfish. In Phase 6: our
 * own engine. The analysis code only knows this interface, so swapping engines
 * needs no change there ("program to an interface, not an implementation").
 *
 * AutoCloseable lets callers use try-with-resources, so the engine (for Stockfish,
 * an operating-system process) is always shut down, even if an error happens.
 */
public interface Engine extends AutoCloseable {

    /** Short name stored in analyses.engine, e.g. "stockfish". */
    String name();

    /** Searches the position (FEN) to the given depth (in half-moves). */
    Evaluation evaluate(String fen, int depth);

    @Override
    void close();   // no "throws Exception": closing an engine shouldn't force callers to catch
}
