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
        String lastScoreLine = null;
        String line;
        while ((line = readLine()) != null) {
            if (line.startsWith("info") && line.contains(" score ")) {
                lastScoreLine = line;              // deeper lines replace shallower ones
            } else if (line.startsWith("bestmove")) {
                boolean whiteToMove = fen.split(" ")[1].equals("w");
                return parse(lastScoreLine, line, whiteToMove);
            }
        }
        throw new IllegalStateException("Stockfish stopped unexpectedly");
    }

    /**
     * Turns the last "info ... score ..." line and the "bestmove" line into an Evaluation.
     * Stockfish reports the score for the SIDE TO MOVE; we flip it for black so it's
     * always from white's point of view.
     * Package-private and static so it can be unit tested without running Stockfish.
     */
    static Evaluation parse(String scoreLine, String bestMoveLine, boolean whiteToMove) {
        Integer cp = null;
        Integer mate = null;
        String[] tokens = scoreLine.split(" ");
        for (int i = 0; i < tokens.length - 2; i++) {
            if (tokens[i].equals("score")) {
                int value = Integer.parseInt(tokens[i + 2]);
                if (tokens[i + 1].equals("cp")) {
                    cp = value;
                } else {
                    mate = value;
                }
                break;
            }
        }
        if (!whiteToMove) {
            if (cp != null) cp = -cp;
            if (mate != null) mate = -mate;
        }
        String best = bestMoveLine.split(" ")[1];               // "bestmove e2e4 ponder e7e5"
        return new Evaluation(cp, mate, best.equals("(none)") ? null : best);
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
