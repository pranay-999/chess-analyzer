package com.chessanalyzer.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Stockfish program as a separate process and talks to it in UCI, a
 * plain-text protocol: we write commands to its stdin, it writes answers to stdout.
 *
 * <pre>
 *  us:        uci                                 it: ... uciok
 *  us:        isready                             it: readyok
 *  us:        position fen &lt;fen&gt;
 *  us:        go depth 16                         it: info depth 1 ... score cp 20 ... pv e2e4
 *                                                 it: info depth 16 ... score cp 31 ... pv e2e4 e7e5 ...
 *                                                 it: bestmove e2e4 ponder e7e5
 *  us:        quit
 * </pre>
 *
 * One StockfishEngine = one process. It is NOT thread-safe (one conversation at a
 * time), so each analysis job opens its own and closes it when done.
 */
public class StockfishEngine implements Engine {

    private final Process process;
    private final BufferedReader fromEngine;
    private final Writer toEngine;

    public StockfishEngine(String executablePath, int threads) {
        this(executablePath, threads, 20, 1);
    }

    /**
     * @param skillLevel 0 (weakest) .. 20 (full strength). Below 20 Stockfish deliberately
     *                   picks weaker moves now and then, which is how its "bots" get easier.
     * @param multiPv    how many best lines to search: 2 = also the second-best move (for
     *                   "Great" moves), at the cost of a somewhat slower search
     */
    public StockfishEngine(String executablePath, int threads, int skillLevel, int multiPv) {
        try {
            process = new ProcessBuilder(executablePath).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start Stockfish at " + executablePath, e);
        }
        fromEngine = process.inputReader();    // the engine's stdout
        toEngine = process.outputWriter();     // the engine's stdin
        send("uci");
        waitFor("uciok");
        send("setoption name Threads value " + threads);
        send("setoption name Skill Level value " + skillLevel);
        send("setoption name MultiPV value " + multiPv);
        send("isready");
        waitFor("readyok");
    }

    @Override
    public String name() {
        return "stockfish";
    }

    @Override
    public Evaluation evaluate(String fen, int depth) {
        send("position fen " + fen);
        send("go depth " + depth);
        String bestLine = null;    // "info ... multipv 1 score cp 31 ..." (deeper lines replace shallower ones)
        String secondLine = null;  // "info ... multipv 2 score cp 12 ..." (only with MultiPV 2)
        String line;
        while ((line = readLine()) != null) {
            if (line.startsWith("info") && line.contains(" score ")) {
                if (line.contains(" multipv 2 ")) {
                    secondLine = line;
                } else if (!line.contains(" multipv ") || line.contains(" multipv 1 ")) {
                    bestLine = line;
                }
            } else if (line.startsWith("bestmove")) {
                boolean whiteToMove = fen.split(" ")[1].equals("w");
                return parse(bestLine, secondLine, line, whiteToMove);
            }
        }
        throw new IllegalStateException("Stockfish stopped unexpectedly");
    }

    /** Single best line (as before), kept for the unit tests. */
    static Evaluation parse(String scoreLine, String bestMoveLine, boolean whiteToMove) {
        return parse(scoreLine, null, bestMoveLine, whiteToMove);
    }

    /**
     * Turns the last "info ... score ..." lines and the "bestmove" line into an Evaluation.
     * Stockfish reports scores for the SIDE TO MOVE; we flip them for black so they're
     * always from white's point of view.
     * Package-private and static so it can be unit tested without running Stockfish.
     */
    static Evaluation parse(String bestLine, String secondLine, String bestMoveLine, boolean whiteToMove) {
        Integer[] best = score(bestLine, whiteToMove);
        Integer[] second = secondLine == null ? new Integer[]{null, null} : score(secondLine, whiteToMove);
        String move = bestMoveLine.split(" ")[1];               // "bestmove e2e4 ponder e7e5"
        return new Evaluation(best[0], best[1], move.equals("(none)") ? null : move, second[0], second[1]);
    }

    /** {cp, mate} from "... score cp 31 ..." or "... score mate -2 ...", white's view. */
    private static Integer[] score(String line, boolean whiteToMove) {
        Integer cp = null;
        Integer mate = null;
        String[] tokens = line.split(" ");
        for (int i = 0; i < tokens.length - 2; i++) {
            if (tokens[i].equals("score")) {
                int value = Integer.parseInt(tokens[i + 2]);
                if (tokens[i + 1].equals("cp")) cp = value; else mate = value;
                break;
            }
        }
        if (!whiteToMove) {
            if (cp != null) cp = -cp;
            if (mate != null) mate = -mate;
        }
        return new Integer[]{cp, mate};
    }

    @Override
    public void close() {
        try {
            send("quit");
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            process.destroyForcibly();
        }
    }

    private void send(String command) {
        try {
            toEngine.write(command + "\n");
            toEngine.flush();   // without flush the text sits in a buffer and Stockfish never sees it
        } catch (IOException e) {
            throw new UncheckedIOException("Lost connection to Stockfish", e);
        }
    }

    private String readLine() {
        try {
            return fromEngine.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Lost connection to Stockfish", e);
        }
    }

    private void waitFor(String expected) {
        String line;
        while ((line = readLine()) != null) {
            if (line.equals(expected)) {
                return;
            }
        }
        throw new IllegalStateException("Stockfish exited before sending '" + expected + "'");
    }
}
